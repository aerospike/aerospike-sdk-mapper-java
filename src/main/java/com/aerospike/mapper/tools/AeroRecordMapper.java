package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordMapper;
import com.aerospike.client.sdk.RecordReadContext;
import com.aerospike.client.sdk.Session;
import com.aerospike.mapper.tools.converters.MappingConverter;

import java.util.Map;

/**
 * JOM-backed implementation of the SDK's {@link RecordMapper} interface.
 * Bridges the JOM's introspection/conversion engine into the SDK's typed object mapping system.
 *
 * @param <T> the mapped entity type
 */
public class AeroRecordMapper<T> implements RecordMapper<T> {

    private final Class<T> clazz;
    private final ClassCacheEntry<T> entry;
    private final IBaseAeroMapper baseMapper;

    public AeroRecordMapper(Class<T> clazz, ClassCacheEntry<T> entry, IBaseAeroMapper baseMapper) {
        this.clazz = clazz;
        this.entry = entry;
        this.baseMapper = baseMapper;
    }

    @Override
    public T fromMap(Map<String, Object> map, Key recordKey, int generation) {
        Session session = baseMapper.asMapper().getSession();
        return hydrate(map, recordKey, generation, session);
    }

    @Override
    public T fromMap(Map<String, Object> map, Key recordKey, int generation, RecordReadContext<T> ctx) {
        return hydrate(map, recordKey, generation, ctx.getSession());
    }

    private T hydrate(Map<String, Object> map, Key recordKey, int generation, Session session) {
        if (map == null) {
            return null;
        }
        Record rec = new Record(map, null, generation, 0);
        try {
            ThreadLocalKeySaver.save(recordKey);
            T result = entry.constructAndHydrate(recordKey, rec);
            baseMapper.getMappingConverter().resolveDependencies(session);
            return result;
        } finally {
            ThreadLocalKeySaver.clear();
        }
    }

    @Override
    public Map<String, Object> toMap(T element) {
        return entry.getMap(element, true);
    }

    @Override
    public Object id(T element) {
        return entry.getKey(element);
    }
}
