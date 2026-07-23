package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.mapper.annotations.AerospikeVersion;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObject;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObjectSetter;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredSetter;
import com.aerospike.mapper.tools.utils.TypeUtils.AnnotatedType;

import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Implementation of a value, which can be either a method on a class (getter) or a field
 *
 * @author timfaulkes
 */
public abstract class ValueType {
    private int minimumVersion = 1;
    private int maximumVersion = Integer.MAX_VALUE;
    private final TypeMapper mapper;
    private final AnnotatedType annotatedType;

    public ValueType(@NotNull final TypeMapper mapper, final AnnotatedType annotatedType) {
        this.mapper = mapper;
        this.annotatedType = annotatedType;
    }

    public abstract Object get(Object obj) throws ReflectiveOperationException;

    public abstract void set(Object obj, Object value) throws ReflectiveOperationException;

    public abstract void set(Object obj, Object value, HydrationContext ctx) throws ReflectiveOperationException;

    public abstract Class<?> getType();

    public abstract Annotation[] getAnnotations();

    public int getMinimumVersion() {
        return minimumVersion;
    }

    public int getMaximumVersion() {
        return maximumVersion;
    }

    protected void setVersion(AerospikeVersion version) {
        if (version.min() <= 0) {
            throw new AerospikeException("Minimum version must be greater than or equal to 1, not " + version.min());
        }
        if (version.max() <= 0) {
            throw new AerospikeException("Maximum version must be greater than or equal to 1, not " + version.max());
        }
        if (version.min() > version.max()) {
            throw new AerospikeException("Maximum version must be greater than or equal to the minumum version, not " + version.max());
        }
        this.maximumVersion = version.max();
        this.minimumVersion = version.min();
    }

    public TypeMapper getTypeMapper() {
        return this.mapper;
    }

    public AnnotatedType getAnnotatedType() {
        return annotatedType;
    }

    private static boolean deferIfNeeded(Object value, HydrationContext ctx, DeferredSetter setter) {
        if (value instanceof DeferredObject deferredObject) {
            ctx.addDeferred(new DeferredObjectSetter(setter, deferredObject));
            return true;
        }
        return false;
    }

    public static class FieldValue extends ValueType {
        private final Field field;

        public FieldValue(Field field, TypeMapper typeMapper, AnnotatedType annotatedType) {
            super(typeMapper, annotatedType);
            this.field = field;
            this.field.setAccessible(true);
            if (this.field.isAnnotationPresent(AerospikeVersion.class)) {
                AerospikeVersion version = this.field.getAnnotation(AerospikeVersion.class);
                super.setVersion(version);
            }
        }

        @Override
        public Object get(Object obj) throws ReflectiveOperationException {
            return this.field.get(obj);
        }

        @Override
        public void set(final Object obj, final Object value) throws ReflectiveOperationException {
            set(obj, value, (HydrationContext) null);
        }

        @Override
        public void set(final Object obj, final Object value, HydrationContext ctx) throws ReflectiveOperationException {
            if (deferIfNeeded(value, ctx, object -> {
                    try {
                        field.set(obj, object);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new AerospikeException(String.format("Could not set field %s on %s to %s. Error is %s (%s)", field, obj, value, e.getMessage(), e.getClass()));
                    }
                })) {
                return;
            }
            this.field.set(obj, value);
        }

        @Override
        public Class<?> getType() {
            return this.field.getType();
        }

        @Override
        public Annotation[] getAnnotations() {
            return this.field.getAnnotations();
        }

        @Override
        public String toString() {
            return String.format("Value(Field): %s (%s)", this.field.getName(), this.field.getType().getSimpleName());
        }
    }

    public static class MethodValue extends ValueType {
        private final PropertyDefinition property;

        public MethodValue(PropertyDefinition property, TypeMapper typeMapper, AnnotatedType annotatedType) {
            super(typeMapper, annotatedType);
            this.property = property;
        }

        @Override
        public Object get(Object obj) throws ReflectiveOperationException {
            if (obj == null) {
                return null;
            }
            return this.property.getGetter().invoke(obj);
        }

        @Override
        public void set(final Object obj, final Object value) throws ReflectiveOperationException {
            set(obj, value, new HydrationContext(null));
        }

        @Override
        public void set(final Object obj, final Object value, HydrationContext ctx) throws ReflectiveOperationException {
            if (this.property.getSetter() == null) {
                throw new AerospikeException("Lazy loading cannot be used on objects with a property key type and no annotated key setter method");
            }

            switch (this.property.getSetterParamType()) {
                case KEY -> {
                    final Key key = requireCurrentKey(ctx, obj,
                            "Could not set property %s from the record key on %s because no current key was available while hydrating the object. Was this record written with sendKey = true?");
                    if (deferIfNeeded(value, ctx, resolved -> invokeSetter(obj, resolved, key))) {
                        return;
                    }
                    invokeSetter(obj, value, key);
                }
                case VALUE -> {
                    final Key key = requireCurrentKey(ctx, obj,
                            "No current key in HydrationContext for VALUE-type setter %s on %s");
                    if (deferIfNeeded(value, ctx, resolved -> invokeSetter(obj, resolved, key.userKey))) {
                        return;
                    }
                    invokeSetter(obj, value, key.userKey);
                }
                case NONE -> {
                    if (deferIfNeeded(value, ctx, resolved -> invokeSetter(obj, resolved))) {
                        return;
                    }
                    invokeSetter(obj, value);
                }
            }
        }

        private Key requireCurrentKey(HydrationContext ctx, Object obj, String messageTemplate) {
            Key key = ctx.currentKey();
            if (key == null) {
                throw new AerospikeException(String.format(messageTemplate, property, obj.getClass().getSimpleName()));
            }
            return key;
        }

        private void invokeSetter(Object obj, Object... args) {
            try {
                property.getSetter().invoke(obj, args);
            } catch (ReflectiveOperationException e) {
                Object assignedValue = args.length == 0 ? null : args[0];
                throw new AerospikeException(String.format("Could not set field %s on %s to %s", property, obj, assignedValue));
            }
        }

        @Override
        public Class<?> getType() {
            return this.property.getType();
        }

        @Override
        public Annotation[] getAnnotations() {
            return this.property.getAnnotations();
        }

        @Override
        public String toString() {
            return String.format("Value(Method): %s/%s (%s)", this.property.getGetter(), this.property.getSetter(), this.property.getType().getSimpleName());
        }
    }
}
