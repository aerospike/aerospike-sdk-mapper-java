package com.aerospike.mapper.tools.mappers;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.tools.*;
import com.aerospike.mapper.tools.utils.TypeUtils;

import java.util.*;
import java.util.Map.Entry;

public class ListMapper extends TypeMapper {

    private record EmbeddedMapElement(Object key, Object value) {
    }

    @SuppressWarnings("unused")
    private final Class<?> referencedClass;
    private final Class<?> instanceClass;
    private final MappingEngine engine;
    private final boolean supportedWithoutTranslation;
    private final TypeMapper instanceClassMapper;
    private final EmbedType embedType;
    private final ClassCacheEntry<?> subTypeEntry;
    private final boolean saveKey;
    private final boolean allowBatchLoad;

    public ListMapper(final Class<?> clazz, final Class<?> instanceClass, final TypeMapper instanceClassMapper, final MappingEngine engine, final EmbedType embedType, final boolean saveKey, boolean allowBatchLoad) {
        this.referencedClass = clazz;
        this.engine = engine;
        this.instanceClass = instanceClass;
        this.supportedWithoutTranslation = TypeUtils.isAerospikeNativeType(instanceClass);
        this.instanceClassMapper = instanceClassMapper;
        this.saveKey = saveKey;
        this.allowBatchLoad = allowBatchLoad;

        if (embedType == EmbedType.DEFAULT) {
            this.embedType = EmbedType.LIST;
        } else {
            this.embedType = embedType;
        }
        if (this.embedType == EmbedType.MAP && (instanceClassMapper == null || (!ObjectMapper.class.isAssignableFrom(instanceClassMapper.getClass())))) {
            subTypeEntry = null;
            // TODO: Should this throw an exception or just change the embedType back to LIST?
            throw new AerospikeException("Annotations embedding lists of objects can only map those objects to maps " +
                    "instead of lists if the object is an AerospikeRecord on instance of class " + clazz.getSimpleName());
        } else {
            if (instanceClass != null) {
                subTypeEntry = ClassCache.getInstance().loadClass(instanceClass, engine);
            } else {
                subTypeEntry = null;
            }
        }
    }

    private boolean isListEmbedType() {
        return embedType == null || embedType == EmbedType.LIST;
    }

    private Object toAerospikeKnownTypeFormat(Object obj) {
        if (obj == null || obj.getClass().equals(instanceClass)) {
            return this.instanceClassMapper.toAerospikeFormat(obj);
        }
        return this.instanceClassMapper.toAerospikeFormat(obj, false, true);
    }

    private Object fromAerospikeListInstanceFormat(Object obj, HydrationContext ctx) {
        if (instanceClass == null) {
            if (obj == null) {
                return null;
            }
            TypeMapper thisMapper = CollectionMapperSupport.getMapper(null, getClassToUse(obj), engine);
            return thisMapper == null ? obj : thisMapper.fromAerospikeFormat(obj, ctx);
        }
        return this.instanceClassMapper.fromAerospikeFormat(obj, ctx);
    }

    public Object toAerospikeInstanceFormat(Object obj) {
        if (isListEmbedType()) {
            if (instanceClass == null) {
                // We don't have any hints as to how to translate them, we have to look up each type
                if (obj == null) {
                    return null;
                } else {
                    TypeMapper thisMapper = TypeUtils.getMapper(obj.getClass(), null, engine);
                    return thisMapper == null ? obj : thisMapper.toAerospikeFormat(obj, true, false);
                }
            } else {
                return toAerospikeKnownTypeFormat(obj);
            }
        } else {
            Object key = subTypeEntry.getKey(obj);
            Object item = toAerospikeKnownTypeFormat(obj);
            return new AbstractMap.SimpleEntry<>(key, item);
        }
    }


    @Override
    public Object toAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        List<?> list = (List<?>) value;
        if (isListEmbedType()) {
            return CollectionMapperSupport.mapListToAerospikeList(list, this::toAerospikeInstanceFormat);
        } else {
            Map<Object, Object> results = new TreeMap<>();
            for (Object obj : list) {
                Entry<?, ?> entry = (Entry<?, ?>) this.toAerospikeInstanceFormat(obj);
                Object key = entry.getKey();
                Object item = entry.getValue();
                results.put(key, item);
            }
            return results;

        }
    }

    private Class<?> getClassToUse(Object obj) {
        if (obj instanceof List<?> list) {
            int lastElementIndex = list.size() - 1;
            if ((!list.isEmpty()) && (list.get(lastElementIndex) instanceof String lastElement)) {
                if (lastElement.startsWith(ClassCacheEntry.TYPE_PREFIX)) {
                    String className = lastElement.substring(ClassCacheEntry.TYPE_PREFIX.length());
                    ClassCacheEntry<?> thisClass = ClassCache.getInstance().getCacheEntryFromStoredName(className);
                    if (thisClass != null) {
                        return thisClass.getUnderlyingClass();
                    }
                }
            }
        }
        return obj.getClass();
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
        if (isListEmbedType()) {
            List<?> list = (List<?>) value;
            if (list.isEmpty() || this.supportedWithoutTranslation) {
                return value;
            }
            boolean allowDeferred = instanceClass == null || allowBatchLoad;
            return CollectionMapperSupport.hydrateList(list,
                    obj -> fromAerospikeListInstanceFormat(obj, ctx), ctx, allowDeferred);
        } else {
            List<Object> results = new ArrayList<>();
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object item = entry.getValue();
                Object result = this.instanceClassMapper.fromAerospikeFormat(item, ctx);
                subTypeEntry.setKey(result, key, ctx);
                results.add(result);
            }
            return results;
        }
    }

    public Object fromAerospikeInstanceFormat(Object obj, HydrationContext ctx) {
        if (isListEmbedType()) {
            return fromAerospikeListInstanceFormat(obj, ctx);
        }

        EmbeddedMapElement element = switch (obj) {
            case Map<?, ?> map -> {
                Map.Entry<?, ?> entry = map.entrySet().iterator().next();
                yield new EmbeddedMapElement(entry.getKey(), entry.getValue());
            }
            case Entry<?, ?> entry -> new EmbeddedMapElement(entry.getKey(), entry.getValue());
            case List<?> pair when pair.size() >= 2 -> new EmbeddedMapElement(pair.get(0), pair.get(1));
            default -> throw new AerospikeException("Unexpected type for MAP embed element: " + obj.getClass().getName());
        };

        Object result = this.instanceClassMapper.fromAerospikeFormat(element.value(), ctx);
        subTypeEntry.setKey(result, element.key(), ctx);
        return result;
    }
}
