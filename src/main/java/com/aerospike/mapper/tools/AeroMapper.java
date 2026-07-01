package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.tools.converters.MappingConverter;
import com.aerospike.mapper.tools.utils.MapperUtils;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Remove once ReactiveAeroMapper is migrated to MappingEngine + MappingSession
class AeroMapper implements IAeroMapper {

    private final Session session;
    private final MappingConverter mappingConverter;

    private AeroMapper(@NotNull Session session) {
        this.session = session;
        this.mappingConverter = new MappingConverter(this);
    }

    public static class Builder extends AbstractBuilder<AeroMapper> {
        public Builder(Session session) {
            super(new AeroMapper(session));
        }
    }

    @Override
    @SafeVarargs
    public final <T> void save(@NotNull T... objects) throws AerospikeException {
        for (T thisObject : objects) {
            this.save(thisObject);
        }
    }

    @Override
    public <T> void save(@NotNull T object, String... binNames) throws AerospikeException {
        writeObject(object, WriteOp.REPLACE, binNames);
    }

    @Override
    public <T> void insert(@NotNull T object, String... binNames) {
        writeObject(object, WriteOp.INSERT, binNames);
    }

    @Override
    public <T> void update(@NotNull T object, String... binNames) throws AerospikeException {
        writeObject(object, WriteOp.UPSERT, binNames);
    }

    private enum WriteOp { REPLACE, INSERT, UPSERT }

    @SuppressWarnings("unchecked")
    private <T> void writeObject(T object, WriteOp op, String... binNames) {
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
                names.length > 1 ? java.util.Arrays.copyOfRange(names, 1, names.length) : new String[0]);
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
    }

    @Override
    public <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest) throws AerospikeException {
        return this.readFromDigest(clazz, digest, true);
    }

    @Override
    public <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest, boolean resolveDependencies)
            throws AerospikeException {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Key key = new Key(entry.getNamespace(), digest, entry.getSetName(), null);
        return this.readSingle(clazz, key, entry, resolveDependencies);
    }

    @Override
    public <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey) throws AerospikeException {
        return this.read(clazz, userKey, true);
    }

    @Override
    public <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey, boolean resolveDependencies)
            throws AerospikeException {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        Key key = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKey)));
        return readSingle(clazz, key, entry, resolveDependencies);
    }

    @SuppressWarnings("unchecked")
    private <T> T readSingle(@NotNull Class<T> clazz, @NotNull Key key, @NotNull ClassCacheEntry<T> entry,
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
        try {
            ThreadLocalKeySaver.save(key);
            return mappingConverter.convertToObject(clazz, key, record, entry, resolveDependencies, session);
        } finally {
            ThreadLocalKeySaver.clear();
        }
    }

    @Override
    public <T> T[] read(@NotNull Class<T> clazz, @NotNull Object[] userKeys) throws AerospikeException {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        Key[] keys = new Key[userKeys.length];
        for (int i = 0; i < userKeys.length; i++) {
            if (userKeys[i] == null) {
                throw new AerospikeException("Cannot pass null to object " + i + " in multi-read call");
            }
            keys[i] = new Key(entry.getNamespace(), set, Value.get(entry.translateKeyToAerospikeKey(userKeys[i])));
        }
        return readBatch(clazz, keys, entry);
    }

    @SuppressWarnings("unchecked")
    private <T> T[] readBatch(@NotNull Class<T> clazz, @NotNull Key[] keys, @NotNull ClassCacheEntry<T> entry) {
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
        return results;
    }

    @Override
    public <T> boolean delete(@NotNull Class<T> clazz, @NotNull Object userKey) throws AerospikeException {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        Object asKey = entry.translateKeyToAerospikeKey(userKey);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(asKey));

        try (RecordStream stream = session.delete(key).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return rr != null && rr.isOk();
        }
    }

    @Override
    public boolean delete(@NotNull Object object) throws AerospikeException {
        ClassCacheEntry<?> entry = MapperUtils.getEntryAndValidateNamespace(object.getClass(), this);
        Key key = new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(object)));

        try (RecordStream stream = session.delete(key).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return rr != null && rr.isOk();
        }
    }

    @Override
    public <T> void scan(@NotNull Class<T> clazz, @NotNull Processor<T> processor) {
        query(clazz, processor, null);
    }

    @Override
    public <T> List<T> scan(@NotNull Class<T> clazz) {
        return query(clazz, (String) null);
    }

    @Override
    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, String aelExpression) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String namespace = entry.getNamespace();
        String setName = entry.getSetName();

        var dataSet = com.aerospike.client.sdk.DataSet.of(namespace, setName);
        var queryBuilder = session.query(dataSet);
        if (aelExpression != null && !aelExpression.isBlank()) {
            queryBuilder.where(aelExpression);
        }

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
        }
    }

    @Override
    public <T> List<T> query(@NotNull Class<T> clazz, String aelExpression) {
        List<T> result = new ArrayList<>();
        query(clazz, record -> {
            result.add(record);
            return true;
        }, aelExpression);
        return result;
    }

    @Override
    public <T> VirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(this, object, binName, elementClazz);
    }

    @Override
    public <T> VirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key, @NotNull String binName,
                                           Class<T> elementClazz) {
        return new VirtualList<>(this, owningClazz, key, binName, elementClazz);
    }

    @Override
    public Session getSession() {
        return this.session;
    }

    @Override
    public MappingConverter getMappingConverter() {
        return this.mappingConverter;
    }

    @Override
    public IAeroMapper asMapper() {
        return this;
    }

    @Override
    public RecordMappingFactory asMappingFactory() {
        throw new UnsupportedOperationException(
                "asMappingFactory() is not supported on legacy AeroMapper. " +
                "Use MappingEngine.installOn(cluster) instead.");
    }

    @Override
    public <T> TypedDataSet<T> getTypedDataSet(Class<T> clazz) {
        ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
        String set = entry.getSetName();
        if ("".equals(set)) {
            set = null;
        }
        return TypedDataSet.of(entry.getNamespace(), set, clazz);
    }

    @Override
    public String getNamespace(Class<?> clazz) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(clazz, this);
        return entry == null ? null : entry.getNamespace();
    }

    @Override
    public String getSet(Class<?> clazz) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(clazz, this);
        return entry == null ? null : entry.getSetName();
    }

    @Override
    public Object getKey(Object obj) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(obj.getClass(), this);
        return entry == null ? null : entry.getKey(obj);
    }

    @Override
    public Key getRecordKey(Object obj) {
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(obj.getClass(), this);
        return entry == null ? null : new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.getKey(obj)));
    }
}
