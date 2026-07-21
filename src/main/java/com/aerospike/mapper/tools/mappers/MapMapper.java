package com.aerospike.mapper.tools.mappers;

import java.util.Map;
import java.util.TreeMap;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.tools.*;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObject;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObjectSetter;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredSetter;
import com.aerospike.mapper.tools.utils.TypeUtils;
import com.aerospike.mapper.tools.utils.TypeUtils.AnnotatedType;

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

            TypeMapper keyMap = keyMapper != null ? keyMapper
                    : (key != null ? TypeUtils.getMapper(key.getClass(), AnnotatedType.getDefaultAnnotateType(), engine) : null);
            TypeMapper itemMap = itemMapper != null ? itemMapper
                    : (item != null ? TypeUtils.getMapper(item.getClass(), AnnotatedType.getDefaultAnnotateType(), engine) : null);
            Object aeroKey = keyMap != null ? keyMap.toAerospikeFormat(key) : key;
            Object aeroItem = itemMap != null ? itemMap.toAerospikeFormat(item) : item;
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

            TypeMapper keyMap = keyMapper != null ? keyMapper
                    : (key != null ? TypeUtils.getMapper(key.getClass(), AnnotatedType.getDefaultAnnotateType(), engine) : null);
            TypeMapper itemMap = itemMapper != null ? itemMapper
                    : (item != null ? TypeUtils.getMapper(item.getClass(), AnnotatedType.getDefaultAnnotateType(), engine) : null);

            final Object javaKey = keyMap != null ? keyMap.fromAerospikeFormat(key, ctx) : key;
            final Object javaItem = itemMap != null ? itemMap.fromAerospikeFormat(item, ctx) : item;
            if (javaKey instanceof DeferredObject) {
                throw new AerospikeException("Deferred object references as map keys are not supported");
            }
            if (javaItem instanceof DeferredObject) {
                DeferredSetter setter = object -> results.put(javaKey, object);
                ctx.addDeferred(new DeferredObjectSetter(setter, (DeferredObject) javaItem));
            } else {
                results.put(javaKey, javaItem);
            }
        }
        return results;
    }
}
