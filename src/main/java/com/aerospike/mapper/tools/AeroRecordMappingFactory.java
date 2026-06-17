package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.RecordMapper;
import com.aerospike.client.sdk.RecordMappingFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * JOM-backed implementation of the SDK's {@link RecordMappingFactory} interface.
 * Provides {@link AeroRecordMapper} instances with lazy, on-the-fly creation and caching
 * for annotated classes. This allows it to plug directly into the SDK's typed mapping framework.
 *
 * <p>Usage:
 * <pre>{@code
 * AeroMapper mapper = new AeroMapper.Builder(session).build();
 * cluster.setRecordMappingFactory(mapper.asMappingFactory());
 * }</pre>
 */
public class AeroRecordMappingFactory implements RecordMappingFactory {

    private final IBaseAeroMapper mapper;
    private final ConcurrentHashMap<Class<?>, RecordMapper<?>> cache = new ConcurrentHashMap<>();

    public AeroRecordMappingFactory(IBaseAeroMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RecordMapper<T> getMapper(Class<T> clazz) {
        RecordMapper<?> cached = cache.get(clazz);
        if (cached != null) {
            return (RecordMapper<T>) cached;
        }
        ClassCacheEntry<T> entry = ClassCache.getInstance().loadClass(clazz, mapper, false);
        if (entry == null) {
            return null;
        }
        AeroRecordMapper<T> newMapper = new AeroRecordMapper<>(clazz, entry, mapper);
        cache.putIfAbsent(clazz, newMapper);
        return (RecordMapper<T>) cache.get(clazz);
    }
}
