package com.aerospike.mapper.tools.mappers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Value;
import com.aerospike.client.sdk.util.Crypto;
import com.aerospike.mapper.annotations.AerospikeReference.ReferenceType;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.ClassCacheEntry;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.IBaseAeroMapper;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObject;

public class ObjectReferenceMapper extends ObjectMapper {

    private final ClassCacheEntry<?> referencedClass;
    private final IBaseAeroMapper mapper;
    private final MappingEngine engine;
    private final boolean lazy;
    private final boolean allowBatch;
    private final ReferenceType type;

    public ObjectReferenceMapper(ClassCacheEntry<?> entry, boolean lazy, boolean allowBatch,
                                 ReferenceType type, IBaseAeroMapper mapper) {
        this.referencedClass = entry;
        this.mapper = mapper;
        this.engine = MappingEngine.fromMapper(mapper);
        this.lazy = lazy;
        this.type = type;
        this.allowBatch = allowBatch;

        if (ReferenceType.DIGEST.equals(this.type) && this.lazy) {
            throw new AerospikeException("An object reference to a " + entry.getClass().getSimpleName()
                    + " cannot be both lazy and map to a digest");
        }
    }

    @Override
    public Object toAerospikeFormat(Object value) {
        return toAerospikeFormat(value, false, false);
    }

    @Override
    public Object toAerospikeFormat(Object value, boolean isUnknownType, boolean isSubclassOfKnownType) {
        if (value == null) {
            return null;
        }
        // In this case we want to store a reference to the object.
        ClassCacheEntry<?> classToUse;
        if (value.getClass().equals(referencedClass.getUnderlyingClass())) {
            classToUse = referencedClass;
        } else {
            classToUse = ClassCache.getInstance().loadClass(value.getClass(), mapper);
            isSubclassOfKnownType = true;
        }
        Object key = classToUse.getKey(value);
        if (ReferenceType.DIGEST.equals(type)) {
            key = Crypto.computeDigest(classToUse.getSetName(), Value.get(key));
        }
        if (isSubclassOfKnownType || isUnknownType) {
            // Need to put the class name in the key so we can recreate the class
            List<Object> keyParts = new ArrayList<>();
            keyParts.add(key);
            if (isUnknownType) {
                // Must put in an identifier to mark this as an unknown type
                keyParts.add(ClassCacheEntry.TYPE_PREFIX + classToUse.getShortenedClassName());
            } else {
                keyParts.add(classToUse.getShortenedClassName());
            }
            return keyParts;
        }
        return key;
    }

    @Override
    public Object fromAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        ResolvedReference reference = resolveReference(value);

        if (this.lazy) {
            Map<String, Object> map = new HashMap<>();
            Object instance = reference.classToUse.constructAndHydrate(map);
            reference.classToUse.setKey(instance, reference.key);
            return instance;
        } else if (allowBatch) {
            return new DeferredObject(reference.key, reference.classToUse.getUnderlyingClass(),
                    ReferenceType.DIGEST.equals(type));
        } else if (ReferenceType.DIGEST.equals(type)) {
            if (engine == null) {
                throw new IllegalStateException(
                        "ObjectReferenceMapper requires a MappingEngine; the legacy IAeroMapper path was removed");
            }
            return engine.readFromDigestWithContext(
                    new HydrationContext(mapper.getSession()),
                    reference.classToUse.getUnderlyingClass(), (byte[]) reference.key, false).orElse(null);
        } else {
            if (engine == null) {
                throw new IllegalStateException(
                        "ObjectReferenceMapper requires a MappingEngine; the legacy IAeroMapper path was removed");
            }
            return engine.readWithContext(
                    new HydrationContext(mapper.getSession()),
                    reference.classToUse.getUnderlyingClass(), reference.key, false).orElse(null);
        }
    }

    @Override
    public Object fromAerospikeFormat(Object value, HydrationContext ctx) {
        if (value == null) {
            return null;
        }
        if (ctx == null) {
            throw new IllegalStateException(
                    "ObjectReferenceMapper requires a HydrationContext; it must not be called outside a hydration scope");
        }
        ResolvedReference reference = resolveReference(value);

        if (this.lazy) {
            Map<String, Object> map = new HashMap<>();
            Object instance = reference.classToUse.constructAndHydrate(map);
            reference.classToUse.setKey(instance, reference.key);
            return instance;
        } else if (allowBatch) {
            return new DeferredObject(reference.key, reference.classToUse.getUnderlyingClass(),
                    ReferenceType.DIGEST.equals(type));
        } else {
            MappingEngine resolvedEngine = this.engine;
            if (resolvedEngine == null) {
                throw new IllegalStateException(
                        "ObjectReferenceMapper has no MappingEngine; cannot perform immediate reads");
            }
            if (ReferenceType.DIGEST.equals(type)) {
                return resolvedEngine.readFromDigestWithContext(
                        ctx, reference.classToUse.getUnderlyingClass(), (byte[]) reference.key, false).orElse(null);
            } else {
                return resolvedEngine.readWithContext(
                        ctx, reference.classToUse.getUnderlyingClass(), reference.key, false).orElse(null);
            }
        }
    }

    private ResolvedReference resolveReference(Object value) {
        ClassCacheEntry<?> classToUse = referencedClass;
        Object key = value;
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            key = list.get(0);
            String typeName = (String) list.get(1);
            if (typeName.startsWith(ClassCacheEntry.TYPE_PREFIX)) {
                typeName = typeName.substring(ClassCacheEntry.TYPE_PREFIX.length());
            }
            classToUse = ClassCache.getInstance().getCacheEntryFromStoredName(typeName);
        }
        return new ResolvedReference(classToUse, key);
    }

    private static final class ResolvedReference {
        private final ClassCacheEntry<?> classToUse;
        private final Object key;

        private ResolvedReference(ClassCacheEntry<?> classToUse, Object key) {
            this.classToUse = classToUse;
            this.key = key;
        }
    }
}
