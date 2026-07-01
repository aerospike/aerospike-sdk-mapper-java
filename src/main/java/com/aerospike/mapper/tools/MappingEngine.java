package com.aerospike.mapper.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.SessionExtension;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.Value;
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
public class MappingEngine {

    private final MappingConverter mappingConverter;
    private final IBaseAeroMapper internalMapper;
    private final ThreadLocal<Session> currentSession = new ThreadLocal<>();
    private final ThreadLocal<Integer> sessionNestingDepth = new ThreadLocal<>();

    private MappingEngine() {
        this.internalMapper = new InternalMapperAdapter(this);
        this.mappingConverter = new MappingConverter(this.internalMapper);
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
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);
        String set = entry.getSetName();
        if ("".equals(set)) {
            set = null;
        }
        return TypedDataSet.of(entry.getNamespace(), set, clazz);
    }

    public String getNamespace(Class<?> clazz) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(clazz, internalMapper);
        return entry == null ? null : entry.getNamespace();
    }

    public String getSet(Class<?> clazz) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(clazz, internalMapper);
        return entry == null ? null : entry.getSetName();
    }

    public Object getKey(Object obj) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(obj.getClass(), internalMapper);
        return entry == null ? null : entry.getKey(obj);
    }

    public Key getRecordKey(Object obj) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(obj.getClass(), internalMapper);
        return entry == null ? null : new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(obj)));
    }

    /**
     * Loads and returns the {@link ClassCacheEntry} for the given class,
     * triggering annotation introspection if the class has not been loaded yet.
     */
    public <T> ClassCacheEntry<T> loadClass(Class<T> clazz) {
        return ClassCache.getInstance().loadClass(clazz, internalMapper);
    }

    public RecordMappingFactory asRecordMappingFactory() {
        return new AeroRecordMappingFactory(this);
    }

    // -- Accessors --

    public MappingConverter getMappingConverter() {
        return mappingConverter;
    }

    IBaseAeroMapper getInternalMapper() {
        return internalMapper;
    }

    /**
     * Sets the current session for use by internal type mappers during hydration.
     * Re-entrant: nested calls increment a depth counter without changing the
     * session. This means the outermost caller's session is used throughout
     * the entire operation, including any recursive reads triggered by
     * reference resolution (e.g. {@code ObjectReferenceMapper}).
     */
    private void pushSession(Session session) {
        Integer depth = sessionNestingDepth.get();
        if (depth == null || depth == 0) {
            currentSession.set(session);
            sessionNestingDepth.set(1);
        } else {
            sessionNestingDepth.set(depth + 1);
        }
    }

    private void popSession() {
        Integer depth = sessionNestingDepth.get();
        if (depth != null && depth > 1) {
            sessionNestingDepth.set(depth - 1);
        } else {
            currentSession.remove();
            sessionNestingDepth.remove();
        }
    }

    // -- Internal CRUD methods (called by MappingSession) --

    enum WriteOp { REPLACE, INSERT, UPSERT }

    @SuppressWarnings("unchecked")
    <T> void writeObject(Session session, T object, WriteOp op, String... binNames) {
        Class<T> clazz = (Class<T>) object.getClass();
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);

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

        pushSession(session);
        try {
            var builder = switch (op) {
                case REPLACE -> session.replace(key);
                case INSERT -> session.insert(key);
                case UPSERT -> session.upsert(key);
            };

            var binsBuilder = builder.bins(names[0],
                    names.length > 1 ? Arrays.copyOfRange(names, 1, names.length) : new String[0]);
            binsBuilder.values(values);

            Integer ttl = entry.getTtl();
            if (ttl != null) {
                binsBuilder.expireRecordAfterSeconds(ttl);
            }

            Integer generationValue = entry.getGenerationValue(object);
            if (generationValue != null && generationValue > 0) {
                binsBuilder.ensureGenerationIs(generationValue);
            }

            binsBuilder.execute().close();
        } finally {
            popSession();
        }
    }

    <T> void save(Session session, T object, String... binNames) {
        writeObject(session, object, WriteOp.REPLACE, binNames);
    }

    <T> void insert(Session session, T object, String... binNames) {
        writeObject(session, object, WriteOp.INSERT, binNames);
    }

    <T> void update(Session session, T object, String... binNames) {
        writeObject(session, object, WriteOp.UPSERT, binNames);
    }

    <T> T readFromDigest(Session session, Class<T> clazz, byte[] digest, boolean resolveDependencies) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);
        Key key = new Key(entry.getNamespace(), digest, entry.getSetName(), null);
        return readSingle(session, clazz, key, entry, resolveDependencies);
    }

    <T> T read(Session session, Class<T> clazz, Object userKey, boolean resolveDependencies) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);
        String set = entry.getSetName();
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKey)));
        return readSingle(session, clazz, key, entry, resolveDependencies);
    }

    @SuppressWarnings("unchecked")
    private <T> T readSingle(Session session, Class<T> clazz, Key key, ClassCacheEntry<T> entry,
                             boolean resolveDependencies) {
        Object objectForKey = LoadedObjectResolver.get(key);
        if (objectForKey != null) {
            return (T) objectForKey;
        }

        Record record;
        try (RecordStream stream = session.query(List.of(key)).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            record = (rr != null && rr.isOk()) ? rr.recordOrNull() : null;
        }

        if (record == null) {
            return null;
        }
        pushSession(session);
        try {
            ThreadLocalKeySaver.save(key);
            return mappingConverter.convertToObject(clazz, key, record, entry, resolveDependencies, session);
        } finally {
            ThreadLocalKeySaver.clear();
            popSession();
        }
    }

    @SuppressWarnings("unchecked")
    <T> T[] readBatch(Session session, Class<T> clazz, Object[] userKeys) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);
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
        pushSession(session);
        try {
            for (int i = 0; i < keys.length; i++) {
                Record record = recordMap.get(keys[i]);
                if (record == null) {
                    results[i] = null;
                } else {
                    try {
                        ThreadLocalKeySaver.save(keys[i]);
                        results[i] = mappingConverter.convertToObject(clazz, keys[i], record, entry, false, session);
                    } finally {
                        ThreadLocalKeySaver.clear();
                    }
                }
            }
            mappingConverter.resolveDependencies(session);
        } finally {
            popSession();
        }
        return results;
    }

    <T> boolean deleteByKey(Session session, Class<T> clazz, Object userKey) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);
        Object asKey = entry.translateKeyToAerospikeKey(userKey);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(asKey));

        try (RecordStream stream = session.delete(key).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return rr != null && rr.isOk();
        }
    }

    boolean deleteByObject(Session session, Object object) {
        ClassCacheEntry<?> entry = MapperUtils.getEntryAndValidateNamespace(object.getClass(), internalMapper);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(object)));

        try (RecordStream stream = session.delete(key).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return rr != null && rr.isOk();
        }
    }

    <T> void query(Session session, Class<T> clazz, Processor<T> processor, String aelExpression) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, internalMapper);
        String namespace = entry.getNamespace();
        String setName = entry.getSetName();

        var dataSet = com.aerospike.client.sdk.DataSet.of(namespace, setName);
        var queryBuilder = session.query(dataSet);
        if (aelExpression != null && !aelExpression.isBlank()) {
            queryBuilder.where(aelExpression);
        }

        pushSession(session);
        try (RecordStream stream = queryBuilder.execute()) {
            while (stream.hasNext()) {
                RecordResult rr = stream.next();
                if (!rr.isOk() || rr.recordOrNull() == null) {
                    continue;
                }
                T object = mappingConverter.convertToObject(clazz, rr.key(), rr.recordOrNull(), entry,
                        true, session);
                if (!processor.process(object)) {
                    break;
                }
            }
        } finally {
            popSession();
        }
    }

    <T> List<T> query(Session session, Class<T> clazz, String aelExpression) {
        List<T> result = new ArrayList<>();
        query(session, clazz, record -> {
            result.add(record);
            return true;
        }, aelExpression);
        return result;
    }

    // -- Internal IBaseAeroMapper adapter --

    private static final class InternalMapperAdapter implements IBaseAeroMapper {
        private final MappingEngine engine;
        private final SessionBoundMapper sessionBoundMapper;

        InternalMapperAdapter(MappingEngine engine) {
            this.engine = engine;
            this.sessionBoundMapper = new SessionBoundMapper(engine);
        }

        @Override
        public MappingConverter getMappingConverter() {
            return engine.mappingConverter;
        }

        @Override
        public IAeroMapper asMapper() {
            return sessionBoundMapper;
        }

        @Override
        public Session getSession() {
            Session session = engine.currentSession.get();
            if (session == null) {
                throw new UnsupportedOperationException(
                        "getSession() is not available outside of a CRUD operation context. " +
                        "Use MappingSession for operations that require a session.");
            }
            return session;
        }
    }

    /**
     * Lightweight IAeroMapper that reads the active session from the engine's
     * ThreadLocal. Used by type mappers (e.g. ObjectReferenceMapper) that call
     * {@code mapper.asMapper().read(...)} during object hydration.
     */
    private static final class SessionBoundMapper implements IAeroMapper {
        private final MappingEngine engine;

        SessionBoundMapper(MappingEngine engine) {
            this.engine = engine;
        }

        private Session requireSession() {
            Session session = engine.currentSession.get();
            if (session == null) {
                throw new IllegalStateException(
                        "No active session. This mapper can only be used during a CRUD operation on MappingSession.");
            }
            return session;
        }

        @Override
        @SafeVarargs
        public final <T> void save(T... objects) {
            Session s = requireSession();
            for (T obj : objects) { engine.save(s, obj); }
        }

        @Override
        public <T> void save(T object, String... binNames) { engine.save(requireSession(), object, binNames); }

        @Override
        public <T> void insert(T object, String... binNames) { engine.insert(requireSession(), object, binNames); }

        @Override
        public <T> void update(T object, String... binNames) { engine.update(requireSession(), object, binNames); }

        @Override
        public <T> T read(Class<T> clazz, Object userKey) { return engine.read(requireSession(), clazz, userKey, true); }

        @Override
        public <T> T read(Class<T> clazz, Object userKey, boolean resolveDependencies) {
            return engine.read(requireSession(), clazz, userKey, resolveDependencies);
        }

        @Override
        public <T> T readFromDigest(Class<T> clazz, byte[] digest) {
            return engine.readFromDigest(requireSession(), clazz, digest, true);
        }

        @Override
        public <T> T readFromDigest(Class<T> clazz, byte[] digest, boolean resolveDependencies) {
            return engine.readFromDigest(requireSession(), clazz, digest, resolveDependencies);
        }

        @Override
        public <T> T[] read(Class<T> clazz, Object[] userKeys) { return engine.readBatch(requireSession(), clazz, userKeys); }

        @Override
        public <T> boolean delete(Class<T> clazz, Object userKey) { return engine.deleteByKey(requireSession(), clazz, userKey); }

        @Override
        public boolean delete(Object object) { return engine.deleteByObject(requireSession(), object); }

        @Override
        public <T> void scan(Class<T> clazz, Processor<T> processor) { engine.query(requireSession(), clazz, processor, null); }

        @Override
        public <T> List<T> scan(Class<T> clazz) { return engine.query(requireSession(), clazz, (String) null); }

        @Override
        public <T> void query(Class<T> clazz, Processor<T> processor, String aelExpression) {
            engine.query(requireSession(), clazz, processor, aelExpression);
        }

        @Override
        public <T> List<T> query(Class<T> clazz, String aelExpression) {
            return engine.query(requireSession(), clazz, aelExpression);
        }

        @Override
        public <T> VirtualList<T> asBackedList(Object object, String binName, Class<T> elementClazz) {
            return new VirtualList<>(this, object, binName, elementClazz);
        }

        @Override
        public <T> VirtualList<T> asBackedList(Class<?> owningClazz, Object key, String binName, Class<T> elementClazz) {
            return new VirtualList<>(this, owningClazz, key, binName, elementClazz);
        }

        @Override
        public RecordMappingFactory asMappingFactory() { return engine.asRecordMappingFactory(); }

        @Override
        public <T> TypedDataSet<T> getTypedDataSet(Class<T> clazz) { return engine.typedDataSet(clazz); }

        @Override
        public String getNamespace(Class<?> clazz) { return engine.getNamespace(clazz); }

        @Override
        public String getSet(Class<?> clazz) { return engine.getSet(clazz); }

        @Override
        public Object getKey(Object obj) { return engine.getKey(obj); }

        @Override
        public Key getRecordKey(Object obj) { return engine.getRecordKey(obj); }

        @Override
        public MappingConverter getMappingConverter() { return engine.mappingConverter; }

        @Override
        public IAeroMapper asMapper() { return this; }

        @Override
        public Session getSession() { return requireSession(); }
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
                    ClassCache.getInstance().loadClass(clazz, engine.internalMapper);
                }
            }
            return engine;
        }

    }
}
