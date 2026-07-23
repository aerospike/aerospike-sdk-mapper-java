package com.aerospike.mapper.tools.mappers;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObject;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObjectSetter;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredSetter;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.utils.TypeUtils;
import com.aerospike.mapper.tools.utils.TypeUtils.AnnotatedType;

final class CollectionMapperSupport {

    private CollectionMapperSupport() {
    }

    static TypeMapper getMapper(TypeMapper configuredMapper, Class<?> valueClass, MappingEngine engine) {
        if (configuredMapper != null) {
            return configuredMapper;
        }
        return valueClass != null ? TypeUtils.getMapper(valueClass, AnnotatedType.getDefaultAnnotateType(), engine) : null;
    }

    static Object toAerospikeFormat(TypeMapper configuredMapper, Class<?> valueClass, Object value, MappingEngine engine) {
        TypeMapper mapper = getMapper(configuredMapper, valueClass, engine);
        return mapper != null ? mapper.toAerospikeFormat(value) : value;
    }

    static Object fromAerospikeFormat(TypeMapper configuredMapper, Class<?> valueClass, Object value,
                                      HydrationContext ctx, MappingEngine engine) {
        TypeMapper mapper = getMapper(configuredMapper, valueClass, engine);
        return mapper != null ? mapper.fromAerospikeFormat(value, ctx) : value;
    }

    static boolean addDeferredIfNeeded(Object value, HydrationContext ctx, DeferredSetter setter) {
        if (value instanceof DeferredObject deferredObject) {
            ctx.addDeferred(new DeferredObjectSetter(setter, deferredObject));
            return true;
        }
        return false;
    }

    static List<Object> mapArrayToAerospikeList(Object array, Function<Object, Object> mapper) {
        int length = Array.getLength(array);
        List<Object> results = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            results.add(mapper.apply(Array.get(array, i)));
        }
        return results;
    }

    static List<Object> mapListToAerospikeList(List<?> list, Function<Object, Object> mapper) {
        List<Object> results = new ArrayList<>(list.size());
        for (Object item : list) {
            results.add(mapper.apply(item));
        }
        return results;
    }

    static void hydrateArray(List<?> list, Object array, Function<Object, Object> mapper,
                             HydrationContext ctx, boolean allowDeferred) {
        for (int i = 0; i < list.size(); i++) {
            Object result = mapper.apply(list.get(i));
            if (!allowDeferred) {
                Array.set(array, i, result);
            } else {
                final int thisIndex = i;
                DeferredSetter setter = object -> Array.set(array, thisIndex, object);
                if (!addDeferredIfNeeded(result, ctx, setter)) {
                    Array.set(array, i, result);
                }
            }
        }
    }

    static List<Object> hydrateList(List<?> list, Function<Object, Object> mapper,
                                    HydrationContext ctx, boolean allowDeferred) {
        List<Object> results = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object result = mapper.apply(list.get(i));
            if (!allowDeferred) {
                results.add(result);
            } else {
                final int thisIndex = i;
                DeferredSetter setter = object -> results.set(thisIndex, object);
                if (addDeferredIfNeeded(result, ctx, setter)) {
                    results.add(null);
                } else {
                    results.add(result);
                }
            }
        }
        return results;
    }
}