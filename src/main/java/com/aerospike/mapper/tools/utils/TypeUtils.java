package com.aerospike.mapper.tools.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.cdt.ListReturnType;
import com.aerospike.client.sdk.cdt.MapReturnType;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeEnum;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.AerospikeReference.ReferenceType;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.configuration.BinConfig;
import com.aerospike.mapper.tools.configuration.ClassConfig;
import com.aerospike.mapper.tools.configuration.EmbedConfig;
import com.aerospike.mapper.tools.configuration.ReferenceConfig;
import com.aerospike.mapper.tools.mappers.ArrayMapper;
import com.aerospike.mapper.tools.mappers.BigDecimalMapper;
import com.aerospike.mapper.tools.mappers.BigIntegerMapper;
import com.aerospike.mapper.tools.mappers.BooleanMapper;
import com.aerospike.mapper.tools.mappers.ByteMapper;
import com.aerospike.mapper.tools.mappers.CharacterMapper;
import com.aerospike.mapper.tools.mappers.DateMapper;
import com.aerospike.mapper.tools.mappers.DefaultMapper;
import com.aerospike.mapper.tools.mappers.EnumMapper;
import com.aerospike.mapper.tools.mappers.FloatMapper;
import com.aerospike.mapper.tools.mappers.InstantMapper;
import com.aerospike.mapper.tools.mappers.IntMapper;
import com.aerospike.mapper.tools.mappers.ListMapper;
import com.aerospike.mapper.tools.mappers.LocalDateMapper;
import com.aerospike.mapper.tools.mappers.LocalDateTimeMapper;
import com.aerospike.mapper.tools.mappers.LocalTimeMapper;
import com.aerospike.mapper.tools.mappers.MapMapper;
import com.aerospike.mapper.tools.mappers.ObjectEmbedMapper;
import com.aerospike.mapper.tools.mappers.ObjectReferenceMapper;
import com.aerospike.mapper.tools.mappers.ShortMapper;
import com.aerospike.mapper.tools.virtuallist.ReturnType;

public class TypeUtils {

    private static final Map<Class<?>, TypeMapper> mappers = new ConcurrentHashMap<>();

    private enum GenericPosition {
        LIST_ELEMENT("list element", true),
        MAP_KEY("map key", false),
        MAP_VALUE("map value", true);

        private final String description;
        private final boolean upperBoundWildcardSupported;

        GenericPosition(String description, boolean upperBoundWildcardSupported) {
            this.description = description;
            this.upperBoundWildcardSupported = upperBoundWildcardSupported;
        }
    }

    private record CollectionMappingOptions(EmbedType embedType, boolean saveKey, boolean allowBatchLoad) {
    }

    private TypeUtils() {
    }

    /**
     * This method adds a new type mapper into the system. This type mapper will replace any other mappers
     * registered for the same class. If there was another mapper for the same type already registered,
     * the old mapper will be replaced with this mapper and the old mapper returned.
     *
     * @param clazz  The class to register for the new type mapper.
     * @param mapper The new type mapper to create.
     * @return Return existing mapper registered for the requested class, null in case there isn't one.
     */
    public static TypeMapper addTypeMapper(Class<?> clazz, TypeMapper mapper) {
        TypeMapper returnValue = mappers.get(clazz);
        mappers.put(clazz, mapper);
        return returnValue;
    }

    @SuppressWarnings("unchecked")
    private static TypeMapper getMapper(Class<?> clazz, AnnotatedType type, MappingEngine engine, boolean isForSubType) {
        if (clazz == null) {
            return null;
        }
        if (engine != null) {
            TypeMapper customMapper = engine.getCustomTypeMapper(clazz);
            if (customMapper != null) {
                return customMapper;
            }
        }
        boolean isEmbedded = type != null && type.annotations != null && Arrays.stream(type.annotations)
                .anyMatch(a -> a.annotationType().isAssignableFrom(AerospikeEmbed.class));

        TypeMapper typeMapper = mappers.get(clazz);
        boolean addToMap = true;
        if (typeMapper == null) {
            if (Date.class.isAssignableFrom(clazz)) {
                typeMapper = new DateMapper(); 
            } else if (LocalDateTime.class.isAssignableFrom(clazz)) {
                typeMapper = new LocalDateTimeMapper();
            } else if (LocalDate.class.isAssignableFrom(clazz)) {
                typeMapper = new LocalDateMapper();
            } else if (LocalTime.class.isAssignableFrom(clazz)) {
                typeMapper = new LocalTimeMapper();
            } else if (Instant.class.isAssignableFrom(clazz)) {
                typeMapper = new InstantMapper();
            } else if (BigInteger.class.isAssignableFrom(clazz)) {
                typeMapper = new BigIntegerMapper();
            } else if (BigDecimal.class.isAssignableFrom(clazz)) {
                typeMapper = new BigDecimalMapper();
            } else if (Byte.class.isAssignableFrom(clazz) || Byte.TYPE.isAssignableFrom(clazz)) {
                typeMapper = new ByteMapper();
            } else if (Character.class.isAssignableFrom(clazz) || Character.TYPE.isAssignableFrom(clazz)) {
                typeMapper = new CharacterMapper();
            } else if (Short.class.isAssignableFrom(clazz) || Short.TYPE.isAssignableFrom(clazz)) {
                typeMapper = new ShortMapper();
            } else if (Integer.class.isAssignableFrom(clazz) || Integer.TYPE.isAssignableFrom(clazz)) {
                typeMapper = new IntMapper();
            } else if (Boolean.class.isAssignableFrom(clazz) || Boolean.TYPE.isAssignableFrom(clazz)) {
                typeMapper = new BooleanMapper();
            } else if (Float.class.isAssignableFrom(clazz) || Float.TYPE.isAssignableFrom(clazz)) {
                typeMapper = new FloatMapper();
            } else if (clazz.isEnum()) {
                String aeroEnumField = "";
                if (type != null && type.getAnnotations() != null) {
                    AerospikeEnum aeroEnum = type.getAnnotation(AerospikeEnum.class);
                    if (aeroEnum != null) {
                        aeroEnumField = aeroEnum.enumField();
                    }
                }
                typeMapper = new EnumMapper((Class<? extends Enum<?>>) clazz, aeroEnumField);
                addToMap = false;
            } else if (clazz.isArray()) {
                Class<?> elementType = clazz.getComponentType();
                if (isByteType(elementType)) {
                    // Byte arrays are natively supported
                    typeMapper = new DefaultMapper();
                } else {
                    // TODO: The type mapped into this type mapper should be the element type
//					ClassConfig config = ClassCache.getInstance().getClassConfig(elementType.getClass());
//					AnnotatedType elementAnnotateType = type.replaceClassConfig(config);
                    boolean allowBatch = true;
                    if (type != null) {
                        AerospikeReference reference = type.getAnnotation(AerospikeReference.class);
                        if (reference != null) {
                            allowBatch = reference.batchLoad();
                        }
                    }
                    TypeMapper subMapper = getMapper(elementType, type, engine, true);
                    typeMapper = new ArrayMapper(elementType, subMapper, allowBatch);
                    addToMap = false;
                }
            } else if (Map.class.isAssignableFrom(clazz)) {
                if (type != null && type.isParameterizedType()) {
                    ParameterizedType paramType = type.getParameterizedType();
                    Type[] types = paramType.getActualTypeArguments();
                    if (types.length != 2) {
                        throw new AerospikeException(String.format("Type %s is a parameterized type as expected, but has %d type parameters, not the expected 2",
                                clazz.getName(), types.length));
                    }

                    Type keyType = normalizeSupportedTypeArgument(types[0], GenericPosition.MAP_KEY, clazz);
                    Type itemType = normalizeSupportedTypeArgument(types[1], GenericPosition.MAP_VALUE, clazz);
                    Class<?> keyClazz = rawClassOf(keyType);
                    Class<?> itemClazz = rawClassOf(itemType);
                    TypeMapper keyMapper = getMapper(keyClazz, forTypeArgument(type, keyType), engine, true);
                    TypeMapper itemMapper = getMapper(itemClazz, forTypeArgument(type, itemType), engine, true);
                    typeMapper = new MapMapper(clazz, keyClazz, itemClazz, keyMapper, itemMapper, engine);

                } else {
                    typeMapper = new MapMapper(clazz, null, null, null, null, engine);
                }
                addToMap = false;
            } else if (List.class.isAssignableFrom(clazz)) {
                CollectionMappingOptions options = resolveListMappingOptions(type);

                if (type != null && type.isParameterizedType()) {
                    ParameterizedType paramType = type.getParameterizedType();
                    Type[] types = paramType.getActualTypeArguments();
                    if (types.length != 1) {
                        throw new AerospikeException(String.format("Type %s is a parameterized type as expected, but has %d type parameters, not the expected 1",
                                clazz.getName(), types.length));
                    }

                    Type subType = normalizeSupportedTypeArgument(types[0], GenericPosition.LIST_ELEMENT, clazz);
                    Class<?> subClazz = rawClassOf(subType);
                    TypeMapper subMapper = getMapper(subClazz, forTypeArgument(type, subType), engine, true);
                    typeMapper = new ListMapper(clazz, subClazz, subMapper, engine, options.embedType(), options.saveKey(),
                            options.allowBatchLoad());
                } else {
                    typeMapper = new ListMapper(clazz, null, null, engine, options.embedType(), options.saveKey(),
                            options.allowBatchLoad());
                }
                addToMap = false;
            } else if (clazz.isAnnotationPresent(AerospikeRecord.class) || ClassCache.getInstance().hasClassConfig(clazz) || isEmbedded) {
                boolean throwError = false;
                if (type != null) {
                    BinConfig binConfig = type.getBinConfig();
                    if (binConfig != null && (binConfig.getEmbed() != null || binConfig.getReference() != null)) {
                        // The config parameters take precedence over the annotations.
                        if (binConfig.getEmbed() != null && binConfig.getReference() != null) {
                            throwError = true;
                        } else if (binConfig.getEmbed() != null) {
                            EmbedConfig embedConfig = binConfig.getEmbed();
                            EmbedType embedType = isForSubType ? embedConfig.getElementType() : embedConfig.getType();
                            if (embedType == null) {
                                embedType = EmbedType.MAP;
                            }
                            boolean saveKey = embedConfig.getSaveKey() != null;
                            boolean skipKey = isForSubType && (embedConfig.getType() == EmbedType.MAP && embedConfig.getElementType() == EmbedType.LIST && (!saveKey));
                            typeMapper = new ObjectEmbedMapper(clazz, embedType, engine, skipKey);
                            addToMap = false;
                        } else {
                            // Reference
                            ReferenceConfig ref = binConfig.getReference();
                            typeMapper = new ObjectReferenceMapper(
                                    ClassCache.getInstance().loadClass(clazz, engine),
                                    ref.getLazy() == null ? false : ref.getLazy(),
                                    ref.getBatchLoad() == null ? true : ref.getBatchLoad(),
                                    ref.getType(), engine);
                            addToMap = false;
                        }
                    } else {
                        if (type.getAnnotations() != null) {
                            for (Annotation annotation : type.getAnnotations()) {
                                if (annotation.annotationType().equals(AerospikeReference.class)) {
                                    if (typeMapper != null) {
                                        throwError = true;
                                        break;
                                    } else {
                                        AerospikeReference ref = (AerospikeReference) annotation;
                                        typeMapper = new ObjectReferenceMapper(ClassCache.getInstance().loadClass(clazz, engine), ref.lazy(), ref.batchLoad(), ref.type(), engine);
                                        addToMap = false;
                                    }
                                }
                                if (annotation.annotationType().equals(AerospikeEmbed.class)) {
                                    AerospikeEmbed embed = (AerospikeEmbed) annotation;
                                    if (typeMapper != null) {
                                        throwError = true;
                                        break;
                                    } else {
                                        EmbedType embedType = isForSubType ? embed.elementType() : embed.type();
                                        boolean skipKey = isForSubType && (embed.type() == EmbedType.MAP && embed.elementType() == EmbedType.LIST && (!embed.saveKey()));
                                        typeMapper = new ObjectEmbedMapper(clazz, embedType, engine, skipKey);
                                        addToMap = false;
                                    }
                                }
                            }
                        }
                    }
                }
                if (throwError) {
                    throw new AerospikeException(String.format("A class with a reference to %s specifies multiple annotations for storing the reference", clazz.getName()));
                }
                if (typeMapper == null) {
                    // No annotations were specified, so use the ObjectReferenceMapper with non-lazy references
                    typeMapper = new ObjectReferenceMapper(ClassCache.getInstance().loadClass(clazz, engine), false, true, ReferenceType.ID, engine);
                    addToMap = false;
                }
            }
            if (typeMapper == null) {
                typeMapper = new DefaultMapper();
            }
            if (addToMap) {
                mappers.put(clazz, typeMapper);
            }
        }
        return typeMapper;
    }

    private static Class<?> rawClassOf(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType parameterizedType -> {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClazz) {
                    yield rawClazz;
                }
                throw new AerospikeException("Unsupported reflective type for mapper resolution: " + typeName(type));
            }
            default -> throw new AerospikeException("Unsupported reflective type for mapper resolution: " + typeName(type));
        };
    }

    private static Type normalizeSupportedTypeArgument(Type type, GenericPosition position, Class<?> owningClass) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType parameterizedType -> parameterizedType;
            case WildcardType wildcardType -> normalizeWildcardType(wildcardType, position, owningClass);
            case TypeVariable<?> typeVariable -> throw unsupportedGenericType(owningClass, position, typeVariable,
                    "unresolved type variables are not supported");
            default -> throw unsupportedGenericType(owningClass, position, type,
                    "only concrete classes, parameterized types, and supported upper-bounded wildcards are supported");
        };
    }

    private static CollectionMappingOptions resolveListMappingOptions(AnnotatedType type) {
        EmbedType embedType = EmbedType.DEFAULT;
        boolean saveKey = true;
        boolean allowBatchLoad = true;

        if (type != null && type.getAnnotations() != null) {
            AerospikeEmbed embed = type.getAnnotation(AerospikeEmbed.class);
            if (embed != null) {
                embedType = embed.type();
                saveKey = embed.saveKey();
            }

            AerospikeReference reference = type.getAnnotation(AerospikeReference.class);
            if (reference != null) {
                allowBatchLoad = reference.batchLoad();
            }
        }

        BinConfig binConfig = type != null ? type.getBinConfig() : null;
        if (binConfig != null && binConfig.getEmbed() != null) {
            if (binConfig.getEmbed().getSaveKey() != null) {
                saveKey = binConfig.getEmbed().getSaveKey();
            }
            if (binConfig.getEmbed().getType() != null) {
                embedType = binConfig.getEmbed().getType();
            }
        }
        if (binConfig != null && binConfig.getReference() != null && binConfig.getReference().getBatchLoad() != null) {
            allowBatchLoad = binConfig.getReference().getBatchLoad();
        }
        return new CollectionMappingOptions(embedType, saveKey, allowBatchLoad);
    }

    private static Type normalizeWildcardType(WildcardType wildcardType, GenericPosition position, Class<?> owningClass) {
        if (!position.upperBoundWildcardSupported) {
            throw unsupportedGenericType(owningClass, position, wildcardType,
                    "wildcards are not supported in this position");
        }

        Type[] lowerBounds = wildcardType.getLowerBounds();
        if (lowerBounds.length > 0) {
            throw unsupportedGenericType(owningClass, position, wildcardType,
                    "lower-bounded wildcards are not supported");
        }

        Type[] upperBounds = wildcardType.getUpperBounds();
        if (upperBounds.length != 1) {
            throw unsupportedGenericType(owningClass, position, wildcardType,
                    "wildcards must declare exactly one upper bound");
        }

        Type upperBound = upperBounds[0];
        if (Object.class.equals(upperBound)) {
            throw unsupportedGenericType(owningClass, position, wildcardType,
                    "unbounded wildcards and '? extends Object' are not supported");
        }

        return normalizeSupportedTypeArgument(upperBound, position, owningClass);
    }

    private static AnnotatedType forTypeArgument(AnnotatedType parent, Type childType) {
        if (parent == null) {
            return new AnnotatedType(null, childType, null);
        }
        return new AnnotatedType(parent.binConfig, childType, parent.annotations);
    }

    private static AerospikeException unsupportedGenericType(Class<?> owningClass, GenericPosition position, Type type, String reason) {
        return new AerospikeException(String.format(
                "Unsupported generic declaration for %s %s '%s': %s",
                owningClass.getName(), position.description, typeName(type), reason));
    }

    private static String typeName(Type type) {
        return type == null ? "null" : type.getTypeName();
    }

    public static TypeMapper getMapper(Class<?> clazz, AnnotatedType type, MappingEngine engine) {
        return getMapper(clazz, type, engine, false);
    }

    public static boolean isByteType(Class<?> clazz) {
        return Byte.class.equals(clazz) ||
                Byte.TYPE.equals(clazz);
    }

    public static boolean isVoidType(Class<?> clazz) {
        return clazz == null ||
                Void.class.equals(clazz) ||
                Void.TYPE.equals(clazz);
    }

    public static boolean isAerospikeNativeType(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return
                Long.TYPE.equals(clazz) ||
                        Long.class.equals(clazz) ||
                        Double.TYPE.equals(clazz) ||
                        Double.class.equals(clazz) ||
                        String.class.equals(clazz);
    }

    public static void clear() {
        mappers.clear();
    }

    public static int returnTypeToListReturnType(ReturnType returnType) {
        return switch (returnType) {
            case DEFAULT, ELEMENTS -> ListReturnType.VALUE;
            case COUNT -> ListReturnType.COUNT;
            case INDEX -> ListReturnType.INDEX;
            case NONE -> ListReturnType.NONE;
        };
    }

    public static int returnTypeToMapReturnType(ReturnType returnType) {
        return switch (returnType) {
            case DEFAULT, ELEMENTS -> MapReturnType.KEY_VALUE;
            case COUNT -> MapReturnType.COUNT;
            case INDEX -> MapReturnType.INDEX;
            case NONE -> MapReturnType.NONE;
        };
    }

    public static class AnnotatedType {

        private static final AnnotatedType defaultAnnotatedType = new AnnotatedType(null, null, null);
        private final Annotation[] annotations;
        private final Type type;
        private final ParameterizedType parameterizedType;
        private final BinConfig binConfig;

        private AnnotatedType(BinConfig binConfig, Type type, Annotation[] annotations) {
            this.binConfig = binConfig;
            this.annotations = annotations;
            this.type = type;
            this.parameterizedType = type instanceof ParameterizedType parameterizedType ? parameterizedType : null;
        }

        public AnnotatedType(ClassConfig config, Field field) {
            this(config == null ? null : config.getBinByFieldName(field.getName()), field.getGenericType(), field.getAnnotations());
        }

        public AnnotatedType(ClassConfig config, Method getter) {
            this(config == null ? null : config.getBinByGetterName(getter.getName()), getter.getGenericReturnType(), getter.getAnnotations());
        }

        public static AnnotatedType getDefaultAnnotateType() {
            return defaultAnnotatedType;
        }

        public Annotation[] getAnnotations() {
            return annotations;
        }

        public BinConfig getBinConfig() {
            return binConfig;
        }

        public Type getType() {
            return type;
        }

        public ParameterizedType getParameterizedType() {
            return parameterizedType;
        }

        public boolean isParameterizedType() {
            return parameterizedType != null;
        }

        public <T> T getAnnotation(Class<T> clazz) {
            if (this.annotations == null) {
                return null;
            }
            for (Annotation annotation : this.annotations) {
                if (annotation.annotationType().equals(clazz)) {
                    return clazz.cast(annotation);
                }
            }
            return null;
        }
    }
}
