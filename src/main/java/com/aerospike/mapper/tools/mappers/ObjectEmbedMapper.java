package com.aerospike.mapper.tools.mappers;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.ClassCacheEntry;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.MappingEngine;

import java.util.List;
import java.util.Map;

public class ObjectEmbedMapper extends ObjectMapper {

    private final Class<?> referencedClass;
    private final MappingEngine engine;
    private final EmbedType type;
    private final boolean skipKey;

    public ObjectEmbedMapper(final Class<?> clazz, final EmbedType type, final MappingEngine engine, boolean skipKey) {
        this.referencedClass = clazz;
        this.engine = engine;
        this.type = type;
        this.skipKey = skipKey;
    }

    @Override
    public Object toAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        if (isSimple(value)) {
            return value;
        }
        // In this case we want to store a reference to the object.
        boolean needsType = !(referencedClass.equals(value.getClass()));
        // Use the actual class here in case a subclass is passed. In that case needsType will be true.
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(value.getClass(), engine, false);
        switch (type) {
            case LIST:
                return entry.getList(value, skipKey, needsType);
            case MAP:        // Fall through
                // If unspecified, default to a MAP for embedded objects
            case DEFAULT:
                return entry.getMap(value, needsType);
            default:
                throw new AerospikeException("Unspecified EmbedType");
        }
    }

    @Override
    public Object fromAerospikeFormat(Object value) {
        return fromAerospikeFormat(value, new HydrationContext(null));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object fromAerospikeFormat(Object value, HydrationContext ctx) {
        if (value == null) {
            return null;
        }
        if (isSimple(value)) {
            return value;
        }
        ClassCacheEntry<?> entry = ClassCache.getInstance().loadClass(referencedClass, engine);
        try {
            if (value instanceof Map) {
                return entry.constructAndHydrate((Map<String, Object>) value, ctx);
            } else if (value instanceof List) {
                return entry.constructAndHydrate((List<Object>) value, skipKey, ctx);
            }
            throw new AerospikeException("Unexpected type for embedded object: " + value.getClass().getName());
        } catch (AerospikeException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AerospikeException(e);
        }
    }

    private boolean isSimple(Object value) {
        Class<?> clazz = value.getClass();
        return clazz.isPrimitive() || clazz.equals(Object.class) || clazz.equals(String.class)
                || clazz.equals(Character.class) || Number.class.isAssignableFrom(clazz);
    }
}
