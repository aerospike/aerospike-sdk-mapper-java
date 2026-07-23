package com.aerospike.mapper.tools.mappers;

import java.util.Map;
import java.util.TreeMap;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.tools.*;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredSetter;
import com.aerospike.mapper.tools.utils.TypeUtils;

public class MapMapper extends TypeMapper {

    @SuppressWarnings("unused")
    private final Class<?> referencedClass;
    @SuppressWarnings("unused")
    private final Class<?> itemClass;
    @SuppressWarnings("unused")
    private final Class<?> keyClass;
    private final MappingEngine engine;
    private final boolean supportedWithoutTranslation;
    private final TypeMapper itemMapper;
    private final TypeMapper keyMapper;

    public MapMapper(final Class<?> clazz, final Class<?> keyClass, final Class<?> itemClass,
                     final TypeMapper keyMapper, final TypeMapper itemMapper, final MappingEngine engine) {
        this.referencedClass = clazz;
        this.engine = engine;
        this.keyMapper = keyMapper;
        this.keyClass = keyClass;
        this.itemMapper = itemMapper;
        this.itemClass = itemClass;
        this.supportedWithoutTranslation = TypeUtils.isAerospikeNativeType(itemClass) && TypeUtils.isAerospikeNativeType(keyClass);
    }

    @Override
    public Object toAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        if (map.size() == 0 || this.supportedWithoutTranslation) {
            return value;
        }

        Map<Object, Object> results = new TreeMap<>();
        for (Object key : map.keySet()) {
            Object item = map.get(key);

            Object aeroKey = CollectionMapperSupport.toAerospikeFormat(keyMapper, key != null ? key.getClass() : null, key, engine);
            Object aeroItem = CollectionMapperSupport.toAerospikeFormat(itemMapper, item != null ? item.getClass() : null, item, engine);
            results.put(aeroKey, aeroItem);
        }
        return results;
    }

    @Override
    public Object fromAerospikeFormat(Object value) {
        return fromAerospikeFormat(value, new HydrationContext(null));
    }

    @Override
    public Object fromAerospikeFormat(Object value, HydrationContext ctx) {
        if (value == null) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        if (map.size() == 0 || this.supportedWithoutTranslation) {
            return value;
        }

        final Map<Object, Object> results = new TreeMap<>();
        for (Object key : map.keySet()) {
            Object item = map.get(key);

            final Object javaKey = CollectionMapperSupport.fromAerospikeFormat(keyMapper,
                    key != null ? key.getClass() : null, key, ctx, engine);
            final Object javaItem = CollectionMapperSupport.fromAerospikeFormat(itemMapper,
                    item != null ? item.getClass() : null, item, ctx, engine);
            if (CollectionMapperSupport.addDeferredIfNeeded(javaKey, ctx, object -> {
                throw new AerospikeException("Deferred object references as map keys are not supported");
            })) {
                throw new AerospikeException("Deferred object references as map keys are not supported");
            }
            if (CollectionMapperSupport.addDeferredIfNeeded(javaItem, ctx, object -> results.put(javaKey, object))) {
                continue;
            } else {
                DeferredSetter setter = object -> results.put(javaKey, object);
                results.put(javaKey, javaItem);
            }
        }
        return results;
    }
}
