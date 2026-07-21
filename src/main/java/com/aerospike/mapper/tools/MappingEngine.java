package com.aerospike.mapper.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.validation.constraints.NotNull;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ErrorStrategy;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.SessionExtension;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.Value;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.virtuallist.VirtualList;
import com.aerospike.mapper.tools.configuration.ClassConfig;
import com.aerospike.mapper.tools.configuration.Configuration;
import com.aerospike.mapper.tools.converters.MappingConverter;
import com.aerospike.mapper.tools.utils.MapperUtils;
import com.aerospike.mapper.tools.utils.TypeUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Shared mapping engine that owns all mapping state: class introspection,
 * YAML/class configuration, custom converters, and the
 * {@link RecordMappingFactory} for SDK typed mapping.
 *
 * <p>Note: {@link ClassCache} is a process-wide singleton, so all
 * {@code MappingEngine} instances in the same JVM share the same
 * class metadata. Multiple engines with conflicting configurations
 * (e.g. different YAML) will interfere with each other.</p>
 *
 * <p>Use {@link #builder()} to construct, then {@link #installOn(Cluster)}
 * to register the factory, and {@link #sessionExtension()} to create
 * {@link MappingSession} instances.</p>
 *
 * <pre>{@code
 * MappingEngine engine = MappingEngine.builder().build();
 * engine.installOn(cluster);
 *
 * MappingSession session = cluster.createSession(behavior, engine.sessionExtension());
 * session.save(myObject);
 * }</pre>
 */
public class MappingEngine implements AutoCloseable {

    private final MappingConverter mappingConverter;
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private MappingEngine() {
        this.mappingConverter = new MappingConverter(this);
    }

    // -- Public API --

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Registers the mapping factory on the cluster so SDK typed mapping
     * ({@code TypedDataSet}, {@code .object()}, {@code .toObjectList()}) works
     * on any plain {@link Session}.
     */
    public void installOn(Cluster cluster) {
        cluster.setRecordMappingFactory(asRecordMappingFactory());
    }

    /**
     * Returns a {@link SessionExtension} that creates {@link MappingSession}
     * instances backed by this engine.
     */
    public SessionExtension<MappingSession> sessionExtension() {
        return new MappingSessionExtension(this);
    }

    /**
     * Returns a {@link TypedDataSet} for the given annotated class, deriving
     * namespace and set from annotations/YAML configuration.
     */
    public <T> TypedDataSet<T> typedDataSet(Class<T> clazz) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        if ("".equals(set)) {
            set = null;
        }
        return TypedDataSet.of(entry.getNamespace(), set, clazz);
    }

    public String getNamespace(Class<?> clazz) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(clazz, this);
        return entry == null ? null : entry.getNamespace();
    }

    public String getSet(Class<?> clazz) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(clazz, this);
        return entry == null ? null : entry.getSetName();
    }

    public Object getKey(Object obj) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(obj.getClass(), this);
        return entry == null ? null : entry.getKey(obj);
    }

    public Key getRecordKey(Object obj) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(obj.getClass(), this);
        return entry == null ? null : new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(obj)));
    }

    /**
     * Loads and returns the {@link ClassCacheEntry} for the given class,
     * triggering annotation introspection if the class has not been loaded yet.
     */
    public <T> ClassCacheEntry<T> loadClass(Class<T> clazz) {
        return ClassCache.getInstance().loadClass(clazz, this);
    }

    public RecordMappingFactory asRecordMappingFactory() {
        return new AeroRecordMappingFactory(this);
    }

    // -- Accessors --

    public MappingConverter getMappingConverter() {
        return mappingConverter;
    }

    /**
     * Continue a nested read within an existing hydration scope.
     * Intended for internal use by built-in mappers that must preserve the
     * parent {@link HydrationContext}'s cache, deferred queue, and key stack.
     */
    public <T> Optional<T> readWithContext(HydrationContext ctx, Class<T> clazz, Object userKey,
                                           boolean resolveDependencies) {
        return read(ctx, clazz, userKey, resolveDependencies);
    }

    /**
     * Continue a nested digest read within an existing hydration scope.
     * Intended for internal use by built-in mappers that must preserve the
     * parent {@link HydrationContext}'s cache, deferred queue, and key stack.
     */
    public <T> Optional<T> readFromDigestWithContext(HydrationContext ctx, Class<T> clazz, byte[] digest,
                                                     boolean resolveDependencies) {
        return readFromDigest(ctx, clazz, digest, resolveDependencies);
    }

    /**
     * Returns the executor used by {@link MappingSession} async methods.
     * Backed by virtual threads — ideal for blocking database I/O.
     */
    public Executor asyncExecutor() {
        return asyncExecutor;
    }

    @Override
    public void close() {
        asyncExecutor.close();
    }

    // -- Internal CRUD methods (called by MappingSession) --

    enum WriteOp { REPLACE, INSERT, UPSERT }

    @SuppressWarnings("unchecked")
    <T> void writeObject(Session session, T object, WriteOp op, OptionalInt ttlOverrideSeconds, String... binNames) {
        Class<T> clazz = (Class<T>) object.getClass();
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);

        String set = entry.getSetName();
        if ("".equals(set)) {
            set = null;
        }
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.getKey(object)));

        boolean allowNullBins = (op != WriteOp.REPLACE);
        Map<String, Object> binMap = entry.getMap(object, allowNullBins, binNames);

        if (binMap.isEmpty()) {
            return;
        }

        int size = binMap.size();
        String[] names = new String[size];
        Object[] values = new Object[size];
        int i = 0;
        for (var e : binMap.entrySet()) {
            names[i] = e.getKey();
            values[i] = e.getValue();
            i++;
        }

        var builder = switch (op) {
            case REPLACE -> session.replace(key);
            case INSERT -> session.insert(key);
            case UPSERT -> session.upsert(key);
        };

        var binsBuilder = builder.bins(names[0],
                names.length > 1 ? Arrays.copyOfRange(names, 1, names.length) : new String[0]);
        binsBuilder.values(values);

        if (ttlOverrideSeconds.isPresent()) {
            binsBuilder.expireRecordAfterSeconds(ttlOverrideSeconds.getAsInt());
        } else {
            Integer ttl = entry.getTtl();
            if (ttl != null) {
                binsBuilder.expireRecordAfterSeconds(ttl);
            }
        }

        Integer generationValue = entry.getGenerationValue(object);
        if (generationValue != null && generationValue > 0) {
            binsBuilder.ensureGenerationIs(generationValue);
        }

        binsBuilder.execute().close();
    }

    <T> void writeObject(Session session, T object, WriteOp op, String... binNames) {
        writeObject(session, object, op, OptionalInt.empty(), binNames);
    }

    @SuppressWarnings("unchecked")
    <T> CompletableFuture<Void> writeObjectAsync(Session session, T object, WriteOp op, OptionalInt ttlOverrideSeconds, String... binNames) {
        Class<T> clazz = (Class<T>) object.getClass();
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);

        String set = entry.getSetName();
        if ("".equals(set)) {
            set = null;
        }
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.getKey(object)));

        boolean allowNullBins = (op != WriteOp.REPLACE);
        Map<String, Object> binMap = entry.getMap(object, allowNullBins, binNames);

        if (binMap.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        int size = binMap.size();
        String[] names = new String[size];
        Object[] values = new Object[size];
        int i = 0;
        for (var e : binMap.entrySet()) {
            names[i] = e.getKey();
            values[i] = e.getValue();
            i++;
        }

        var builder = switch (op) {
            case REPLACE -> session.replace(key);
            case INSERT -> session.insert(key);
            case UPSERT -> session.upsert(key);
        };

        var binsBuilder = builder.bins(names[0],
                names.length > 1 ? Arrays.copyOfRange(names, 1, names.length) : new String[0]);
        binsBuilder.values(values);

        if (ttlOverrideSeconds.isPresent()) {
            binsBuilder.expireRecordAfterSeconds(ttlOverrideSeconds.getAsInt());
        } else {
            Integer ttl = entry.getTtl();
            if (ttl != null) {
                binsBuilder.expireRecordAfterSeconds(ttl);
            }
        }

        Integer generationValue = entry.getGenerationValue(object);
        if (generationValue != null && generationValue > 0) {
            binsBuilder.ensureGenerationIs(generationValue);
        }

        return binsBuilder.executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results != null && !results.isEmpty()) {
                        results.get(0).orThrow();
                    }
                    return null;
                });
    }

    <T> CompletableFuture<Optional<T>> readSingleAsync(Session session, Class<T> clazz, Object userKey) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKey)));

        return session.query(key)
                .executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFutureSingle()
                .thenApply(firstResult -> mapSingleReadResult(firstResult, session, clazz, key, entry, true));
    }

    <T> CompletableFuture<Optional<T>> readFromDigestAsync(Session session, Class<T> clazz, byte[] digest) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Key key = new Key(entry.getNamespace(), digest, entry.getSetName(), null);

        return session.query(key)
                .executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFutureSingle()
                .thenApply(firstResult -> mapSingleReadResult(firstResult, session, clazz, key, entry, true));
    }

    <T> CompletableFuture<Boolean> deleteByKeyAsync(Session session, Class<T> clazz, Object userKey) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Object asKey = entry.translateKeyToAerospikeKey(userKey);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(asKey));

        return session.delete(key)
                .executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return false;
                    }
                    RecordResult rr = results.get(0);
                    return rr.isOk();
                });
    }

    CompletableFuture<Boolean> deleteByObjectAsync(Session session, Object object) {
        ClassCacheEntry<?> entry = MapperUtils.getEntryAndValidateNamespace(object.getClass(), this);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(object)));

        return session.delete(key)
                .executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return false;
                    }
                    RecordResult rr = results.get(0);
                    return rr.isOk();
                });
    }

    <T> void save(Session session, T object, String... binNames) {
        writeObject(session, object, WriteOp.REPLACE, binNames);
    }

    <T> void save(Session session, T object, int ttlSeconds) {
        writeObject(session, object, WriteOp.REPLACE, OptionalInt.of(requirePositiveTtl(ttlSeconds)));
    }

    <T> void save(Session session, T object, Duration ttl) {
        save(session, object, durationToTtlSeconds(ttl));
    }

    <T> void save(Session session, T object, int ttlSeconds, String[] binNames) {
        writeObject(session, object, WriteOp.REPLACE, OptionalInt.of(requirePositiveTtl(ttlSeconds)), binNames);
    }

    <T> void save(Session session, T object, Duration ttl, String[] binNames) {
        save(session, object, durationToTtlSeconds(ttl), binNames);
    }

    <T> void insert(Session session, T object, String... binNames) {
        writeObject(session, object, WriteOp.INSERT, binNames);
    }

    <T> void insert(Session session, T object, int ttlSeconds) {
        writeObject(session, object, WriteOp.INSERT, OptionalInt.of(requirePositiveTtl(ttlSeconds)));
    }

    <T> void insert(Session session, T object, Duration ttl) {
        insert(session, object, durationToTtlSeconds(ttl));
    }

    <T> void insert(Session session, T object, int ttlSeconds, String[] binNames) {
        writeObject(session, object, WriteOp.INSERT, OptionalInt.of(requirePositiveTtl(ttlSeconds)), binNames);
    }

    <T> void insert(Session session, T object, Duration ttl, String[] binNames) {
        insert(session, object, durationToTtlSeconds(ttl), binNames);
    }

    <T> void update(Session session, T object, String... binNames) {
        writeObject(session, object, WriteOp.UPSERT, binNames);
    }

    <T> void update(Session session, T object, int ttlSeconds) {
        writeObject(session, object, WriteOp.UPSERT, OptionalInt.of(requirePositiveTtl(ttlSeconds)));
    }

    <T> void update(Session session, T object, Duration ttl) {
        update(session, object, durationToTtlSeconds(ttl));
    }

    <T> void update(Session session, T object, int ttlSeconds, String[] binNames) {
        writeObject(session, object, WriteOp.UPSERT, OptionalInt.of(requirePositiveTtl(ttlSeconds)), binNames);
    }

    <T> void update(Session session, T object, Duration ttl, String[] binNames) {
        update(session, object, durationToTtlSeconds(ttl), binNames);
    }

    private static int requirePositiveTtl(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL must be positive, got: " + ttlSeconds);
        }
        return ttlSeconds;
    }

    private static int durationToTtlSeconds(Duration ttl) {
        if (ttl == null) {
            throw new IllegalArgumentException("TTL Duration must not be null");
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive, got: " + ttl);
        }
        long seconds = ttl.toSeconds();
        if (seconds == 0) {
            throw new IllegalArgumentException(
                    "TTL must be at least 1 second (Aerospike TTL resolution is seconds), got: " + ttl);
        }
        return Math.toIntExact(seconds);
    }

    <T> Optional<T> readFromDigest(Session session, Class<T> clazz, byte[] digest, boolean resolveDependencies) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Key key = new Key(entry.getNamespace(), digest, entry.getSetName(), null);
        return readSingle(session, clazz, key, entry, resolveDependencies);
    }

    <T> Optional<T> read(Session session, Class<T> clazz, Object userKey, boolean resolveDependencies) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKey)));
        return readSingle(session, clazz, key, entry, resolveDependencies);
    }

    <T> Optional<T> readFromDigest(HydrationContext ctx, Class<T> clazz, byte[] digest, boolean resolveDependencies) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Key key = new Key(entry.getNamespace(), digest, entry.getSetName(), null);
        return readSingle(ctx, clazz, key, entry, resolveDependencies);
    }

    <T> Optional<T> read(HydrationContext ctx, Class<T> clazz, Object userKey, boolean resolveDependencies) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKey)));
        return readSingle(ctx, clazz, key, entry, resolveDependencies);
    }

    private <T> Optional<T> readSingle(Session session, Class<T> clazz, Key key, ClassCacheEntry<T> entry,
                                       boolean resolveDependencies) {
        try (RecordStream stream = session.query(key).execute()) {
            return mapSingleReadResult(stream.getFirst(false), session, clazz, key, entry, resolveDependencies);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> readSingle(HydrationContext ctx, Class<T> clazz, Key key, ClassCacheEntry<T> entry,
                                       boolean resolveDependencies) {
        Object objectForKey = ctx.getCached(key);
        if (objectForKey != null) {
            return Optional.of((T) objectForKey);
        }

        try (RecordStream stream = ctx.session().query(key).execute()) {
            Optional<RecordResult> firstResult = stream.getFirst(false);
            if (firstResult.isEmpty()) {
                return Optional.empty();
            }

            RecordResult recordResult = firstResult.get();
            if (isMissingSingleRead(recordResult)) {
                return Optional.empty();
            }

            recordResult.orThrow();
            Record loadedRecord = recordResult.recordOrNull();

            ctx.pushKey(key);
            try {
                return Optional.ofNullable(
                        mappingConverter.convertToObject(clazz, key, loadedRecord, entry, resolveDependencies, ctx));
            } finally {
                ctx.popKey();
            }
        }
    }

    private static boolean isMissingSingleRead(RecordResult recordResult) {
        return recordResult.resultCode() == ResultCode.KEY_NOT_FOUND_ERROR || recordResult.recordOrNull() == null;
    }

    private <T> Optional<T> mapSingleReadResult(Optional<RecordResult> firstResult, Session session, Class<T> clazz,
                                                Key key, ClassCacheEntry<T> entry, boolean resolveDependencies) {
        if (firstResult.isEmpty()) {
            return Optional.empty();
        }

        RecordResult recordResult = firstResult.get();
        if (isMissingSingleRead(recordResult)) {
            return Optional.empty();
        }

        recordResult.orThrow();
        Record loadedRecord = recordResult.recordOrNull();

        HydrationContext ctx = new HydrationContext(session);
        ctx.pushKey(key);
        try {
            return Optional.ofNullable(
                    mappingConverter.convertToObject(clazz, key, loadedRecord, entry, resolveDependencies, ctx));
        } finally {
            ctx.popKey();
        }
    }

    @SuppressWarnings("unchecked")
    <T> T[] readBatch(Session session, Class<T> clazz, Object[] userKeys) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        Key[] keys = new Key[userKeys.length];
        for (int i = 0; i < userKeys.length; i++) {
            if (userKeys[i] == null) {
                throw new AerospikeException("Cannot pass null to object " + i + " in multi-read call");
            }
            keys[i] = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKeys[i])));
        }

        Map<Key, Record> recordMap = new HashMap<>();
        try (RecordStream stream = session.query(List.of(keys)).execute()) {
            while (stream.hasNext()) {
                RecordResult rr = stream.next();
                if (rr.isOk() && rr.recordOrNull() != null) {
                    recordMap.put(rr.key(), rr.recordOrNull());
                }
            }
        }

        T[] results = (T[]) Array.newInstance(clazz, keys.length);
        HydrationContext ctx = new HydrationContext(session);
        for (int i = 0; i < keys.length; i++) {
            Record loadedRecord = recordMap.get(keys[i]);
            if (loadedRecord == null) {
                results[i] = null;
            } else {
                ctx.pushKey(keys[i]);
                try {
                    results[i] = mappingConverter.convertToObject(clazz, keys[i], loadedRecord, entry, false, ctx);
                } finally {
                    ctx.popKey();
                }
            }
        }
        mappingConverter.resolveDependencies(ctx);
        return results;
    }

    <T> boolean deleteByKey(Session session, Class<T> clazz, Object userKey) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Object asKey = entry.translateKeyToAerospikeKey(userKey);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(asKey));

        try (RecordStream stream = session.delete(key).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return rr != null && rr.isOk();
        }
    }

    boolean deleteByObject(Session session, Object object) {
        ClassCacheEntry<?> entry = MapperUtils.getEntryAndValidateNamespace(object.getClass(), this);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(object)));

        try (RecordStream stream = session.delete(key).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return rr != null && rr.isOk();
        }
    }

    <T> void query(Session session, Class<T> clazz, Processor<T> processor) {
        doQuery(session, clazz, processor, null, null);
    }

    <T> List<T> query(Session session, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        doQuery(session, clazz, record -> {
            result.add(record);
            return true;
        }, null, null);
        return result;
    }

    <T> void query(Session session, Class<T> clazz, Processor<T> processor, String aelExpression) {
        doQuery(session, clazz, processor, aelExpression, null);
    }

    <T> List<T> query(Session session, Class<T> clazz, String aelExpression) {
        List<T> result = new ArrayList<>();
        doQuery(session, clazz, record -> {
            result.add(record);
            return true;
        }, aelExpression, null);
        return result;
    }

    <T> void query(Session session, Class<T> clazz, Processor<T> processor, Expression filterExp) {
        doQuery(session, clazz, processor, null, filterExp);
    }

    <T> List<T> query(Session session, Class<T> clazz, Expression filterExp) {
        List<T> result = new ArrayList<>();
        doQuery(session, clazz, record -> {
            result.add(record);
            return true;
        }, null, filterExp);
        return result;
    }

    RecordStream buildQueryStream(Session session, Class<?> clazz, String aelFilter, Expression expFilter) {
        ClassCacheEntry<?> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String namespace = entry.getNamespace();
        String setName = entry.getSetName();

        var dataSet = com.aerospike.client.sdk.DataSet.of(namespace, setName);
        var queryBuilder = session.query(dataSet);
        if (aelFilter != null && !aelFilter.isBlank()) {
            queryBuilder.where(aelFilter);
        } else if (expFilter != null) {
            queryBuilder.where(expFilter);
        }
        return queryBuilder.execute();
    }

    private <T> void doQuery(Session session, Class<T> clazz, Processor<T> processor,
                             String aelFilter, Expression expFilter) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String namespace = entry.getNamespace();
        String setName = entry.getSetName();

        var dataSet = com.aerospike.client.sdk.DataSet.of(namespace, setName);
        var queryBuilder = session.query(dataSet);
        if (aelFilter != null && !aelFilter.isBlank()) {
            queryBuilder.where(aelFilter);
        } else if (expFilter != null) {
            queryBuilder.where(expFilter);
        }

        HydrationContext ctx = new HydrationContext(session);
        try (RecordStream stream = queryBuilder.execute()) {
            while (stream.hasNext()) {
                RecordResult rr = stream.next();
                if (!rr.isOk() || rr.recordOrNull() == null) {
                    continue;
                }
                ctx.pushKey(rr.key());
                T object;
                try {
                    object = mappingConverter.convertToObject(clazz, rr.key(), rr.recordOrNull(), entry,
                            true, ctx);
                } finally {
                    ctx.popKey();
                }
                if (!processor.process(object)) {
                    break;
                }
            }
        }
    }

    // -- Builder --

    public static final class Builder {
        private List<Class<?>> classesToPreload = null;
        private final MappingEngine engine;

        private Builder() {
            this.engine = new MappingEngine();
        }

        public Builder addConverter(Object converter) {
            GenericTypeMapper typeMapper = new GenericTypeMapper(converter);
            TypeUtils.addTypeMapper(typeMapper.getMappedClass(), typeMapper);
            return this;
        }

        public Builder preLoadClass(Class<?> clazz) {
            if (classesToPreload == null) {
                classesToPreload = new ArrayList<>();
            }
            classesToPreload.add(clazz);
            return this;
        }

        public Builder preLoadClasses(Class<?>... clazzes) {
            if (classesToPreload == null) {
                classesToPreload = new ArrayList<>();
            }
            classesToPreload.addAll(Arrays.asList(clazzes));
            return this;
        }

        public Builder preLoadClassesFromPackage(Class<?> classInPackage) {
            return preLoadClassesFromPackage(BuilderHelper.getPackageName(classInPackage));
        }

        public Builder preLoadClassesFromPackage(String thePackage) {
            Set<Class<?>> clazzes = BuilderHelper.findAllClassesUsingClassLoader(thePackage);
            for (Class<?> thisClazz : clazzes) {
                if (thisClazz.getAnnotation(AerospikeRecord.class) != null) {
                    this.preLoadClass(thisClazz);
                }
            }
            return this;
        }

        public Builder withConfigurationFile(File file) throws IOException {
            return this.withConfigurationFile(file, false);
        }

        public Builder withConfigurationFile(File file, boolean allowsInvalid) throws IOException {
            try (InputStream fis = new FileInputStream(file)) {
                return this.withConfigurationFile(fis, allowsInvalid);
            }
        }

        public Builder withConfigurationFile(InputStream ios) throws IOException {
            return this.withConfigurationFile(ios, false);
        }

        public Builder withConfigurationFile(InputStream ios, boolean allowsInvalid) throws IOException {
            Yaml yaml = BuilderHelper.createYamlParser();
            Configuration configuration = yaml.load(ios);
            BuilderHelper.loadConfiguration(configuration, allowsInvalid, MappingEngine.class);
            return this;
        }

        public Builder withConfiguration(String configurationYaml) {
            return this.withConfiguration(configurationYaml, false);
        }

        public Builder withConfiguration(String configurationYaml, boolean allowsInvalid) {
            Yaml yaml = BuilderHelper.createYamlParser();
            Configuration configuration = yaml.load(configurationYaml);
            BuilderHelper.loadConfiguration(configuration, allowsInvalid, MappingEngine.class);
            return this;
        }

        public Builder withClassConfigurations(ClassConfig classConfig, ClassConfig... classConfigs) {
            Configuration configuration = new Configuration();
            configuration.add(classConfig);
            for (ClassConfig thisConfig : classConfigs) {
                configuration.add(thisConfig);
            }
            ClassCache.getInstance().addConfiguration(configuration);
            return this;
        }

        public MappingEngine build() {
            if (classesToPreload != null) {
                for (Class<?> clazz : classesToPreload) {
                    ClassCache.getInstance().loadClass(clazz, engine);
                }
            }
            return engine;
        }

    }
}
