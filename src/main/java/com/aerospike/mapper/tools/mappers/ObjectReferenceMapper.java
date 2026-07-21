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
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObject;
import com.aerospike.mapper.tools.MappingEngine;

public class ObjectReferenceMapper extends ObjectMapper {

    private static final String IMMEDIATE_READS_REQUIRE_SESSION =
            "Immediate reference hydration requires a session-backed mapping context. " +
                    "Use fromAerospikeFormat(value, Session) or a mapping API that supplies session context.";

    private final ClassCacheEntry<?> referencedClass;
    private final MappingEngine engine;
    private final boolean lazy;
    private final boolean allowBatch;
    private final ReferenceType type;

    public ObjectReferenceMapper(ClassCacheEntry<?> entry, boolean lazy, boolean allowBatch,
                                 ReferenceType type, MappingEngine engine) {
        this.referencedClass = entry;
        this.engine = engine;
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
            classToUse = ClassCache.getInstance().loadClass(value.getClass(), engine);
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
        } else {
            throw new IllegalStateException(IMMEDIATE_READS_REQUIRE_SESSION);
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
        if (!this.lazy && !allowBatch && ctx.session() == null) {
            throw new IllegalStateException(IMMEDIATE_READS_REQUIRE_SESSION);
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
            if (ReferenceType.DIGEST.equals(type)) {
                return engine.readFromDigestWithContext(
                        ctx, reference.classToUse.getUnderlyingClass(), (byte[]) reference.key, false).orElse(null);
            } else {
                return engine.readWithContext(
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
