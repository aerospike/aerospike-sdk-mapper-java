package com.aerospike.mapper.tools;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeExclude;
import com.aerospike.mapper.annotations.AerospikeGeneration;
import com.aerospike.mapper.annotations.AerospikeGetter;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeOrdinal;
import com.aerospike.mapper.annotations.AerospikeSetter;
import com.aerospike.mapper.tools.configuration.BinConfig;
import com.aerospike.mapper.tools.configuration.ClassConfig;
import com.aerospike.mapper.tools.configuration.KeyConfig;
import com.aerospike.mapper.tools.utils.ParserUtils;
import com.aerospike.mapper.tools.utils.TypeUtils;
import com.aerospike.mapper.tools.utils.TypeUtils.AnnotatedType;

final class ClassCacheEntryMetadataResolver<T> {

    private final ClassCacheEntry<T> entry;

    ClassCacheEntryMetadataResolver(ClassCacheEntry<T> entry) {
        this.entry = entry;
    }

    void resolve() {
        loadFieldsFromClass();
        loadPropertiesFromClass();
        formOrdinalsFromValues();
    }

    private PropertyDefinition getOrCreateProperty(String name, Map<String, PropertyDefinition> properties) {
        return properties.computeIfAbsent(name, n -> new PropertyDefinition(n, entry.getEngineInternal()));
    }

    private void loadPropertiesFromClass() {
        Map<String, PropertyDefinition> properties = new HashMap<>();
        ClassConfig config = entry.getConfigInternal();
        KeyConfig keyConfig = config != null ? config.getKey() : null;

        PropertyDefinition keyProperty = null;
        PropertyDefinition generationProperty = null;

        for (Method thisMethod : entry.getRawClassInternal().getDeclaredMethods()) {
            String methodName = thisMethod.getName();
            BinConfig getterConfig = getBinFromGetter(methodName);
            BinConfig setterConfig = getBinFromSetter(methodName);

            boolean isKey = false;
            boolean isKeyViaConfig = keyConfig != null
                    && (keyConfig.isGetter(methodName) || keyConfig.isSetter(methodName));

            if (thisMethod.isAnnotationPresent(AerospikeKey.class) || isKeyViaConfig) {
                if (keyProperty == null) {
                    keyProperty = new PropertyDefinition("_key_", entry.getEngineInternal());
                }

                if (isKeyViaConfig) {
                    if (keyConfig.isGetter(methodName)) {
                        keyProperty.setGetter(thisMethod);
                    } else {
                        keyProperty.setSetter(thisMethod);
                    }
                } else {
                    AerospikeKey key = thisMethod.getAnnotation(AerospikeKey.class);
                    if (key.setter()) {
                        keyProperty.setSetter(thisMethod);
                    } else {
                        keyProperty.setGetter(thisMethod);
                    }
                }

                isKey = true;
            }

            if (thisMethod.isAnnotationPresent(AerospikeGeneration.class)) {
                if (generationProperty == null) {
                    generationProperty = new PropertyDefinition("_generation_", entry.getEngineInternal());
                }

                if (methodName.startsWith("get")) {
                    Class<?> returnType = thisMethod.getReturnType();
                    if (!returnType.equals(Integer.class) && !returnType.equals(int.class)) {
                        throw new AerospikeException(String.format(
                                "@AerospikeGeneration getter %s in class %s must return Integer or int type, but returned %s",
                                methodName, entry.getRawClassInternal().getName(), returnType.getSimpleName()));
                    }
                    generationProperty.setGetter(thisMethod);
                } else if (methodName.startsWith("set")) {
                    if (thisMethod.getParameterCount() != 1) {
                        throw new AerospikeException(String.format(
                                "@AerospikeGeneration setter %s in class %s must accept a single parameter, but %d were found",
                                methodName, entry.getRawClassInternal().getName(), thisMethod.getParameterCount()));
                    }
                    Class<?> paramType = thisMethod.getParameterTypes()[0];
                    if (!paramType.equals(Integer.class) && !paramType.equals(int.class)) {
                        throw new AerospikeException(String.format(
                                "@AerospikeGeneration setter %s in class %s must accept Integer or int type, but accepted %s",
                                methodName, entry.getRawClassInternal().getName(), paramType.getSimpleName()));
                    }
                    generationProperty.setSetter(thisMethod);
                }
            }

            if (thisMethod.isAnnotationPresent(AerospikeGetter.class) || getterConfig != null) {
                String getterName = getterConfig != null ? getterConfig.getName()
                        : thisMethod.getAnnotation(AerospikeGetter.class).name();

                String name = ParserUtils.getInstance().get(ParserUtils.getInstance().get(getterName));
                PropertyDefinition thisProperty = getOrCreateProperty(name, properties);
                thisProperty.setGetter(thisMethod);
                if (isKey) {
                    entry.setKeyNameInternal(name);
                }
            }

            if (thisMethod.isAnnotationPresent(AerospikeSetter.class) || setterConfig != null) {
                String setterName = setterConfig != null ? setterConfig.getName()
                        : thisMethod.getAnnotation(AerospikeSetter.class).name();

                String name = ParserUtils.getInstance().get(ParserUtils.getInstance().get(setterName));
                PropertyDefinition thisProperty = getOrCreateProperty(name, properties);
                thisProperty.setSetter(thisMethod);
            }
        }

        if (keyProperty != null) {
            if (entry.getKeyInternal() != null) {
                throw new AerospikeException(String.format("Class %s cannot have more than one key",
                        entry.getRawClassInternal().getName()));
            }

            keyProperty.validate(entry.getRawClassInternal().getName(), config, true);

            AnnotatedType annotatedType = new AnnotatedType(config, keyProperty.getGetter());
            TypeMapper typeMapper = TypeUtils.getMapper(keyProperty.getType(), annotatedType, entry.getEngineInternal());
            entry.setKeyInternal(new ValueType.MethodValue(keyProperty, typeMapper, annotatedType));
        }

        if (generationProperty != null) {
            if (entry.getGenerationFieldInternal() != null) {
                throw new AerospikeException(String.format(
                        "Class %s cannot have more than one @AerospikeGeneration field",
                        entry.getRawClassInternal().getName()));
            }

            generationProperty.validate(entry.getRawClassInternal().getName(), config, false);

            AnnotatedType annotatedType = new AnnotatedType(config, generationProperty.getGetter());
            TypeMapper typeMapper = TypeUtils.getMapper(generationProperty.getType(), annotatedType,
                    entry.getEngineInternal());
            entry.setGenerationFieldInternal(new ValueType.MethodValue(generationProperty, typeMapper, annotatedType));
        }

        for (String thisPropertyName : properties.keySet()) {
            PropertyDefinition thisProperty = properties.get(thisPropertyName);
            thisProperty.validate(entry.getRawClassInternal().getName(), config, false);

            if (entry.getValuesInternal().get(thisPropertyName) != null) {
                throw new AerospikeException(String.format("Class %s cannot define the mapped name %s more than once",
                        entry.getRawClassInternal().getName(), thisPropertyName));
            }

            AnnotatedType annotatedType = new AnnotatedType(config, thisProperty.getGetter());
            TypeMapper typeMapper = TypeUtils.getMapper(thisProperty.getType(), annotatedType, entry.getEngineInternal());
            ValueType value = new ValueType.MethodValue(thisProperty, typeMapper, annotatedType);
            entry.getValuesInternal().put(thisPropertyName, value);
        }
    }

    private void loadFieldsFromClass() {
        for (Field thisField : entry.getRawClassInternal().getDeclaredFields()) {
            BinConfig thisBin = getBinFromField(thisField);

            if (Modifier.isFinal(thisField.getModifiers()) && Modifier.isStatic(thisField.getModifiers())) {
                continue;
            }

            boolean isGenerationField = handleGenerationField(thisField, thisBin);
            if (thisField.isAnnotationPresent(AerospikeExclude.class)
                    || (thisBin != null && thisBin.isExclude() != null && thisBin.isExclude())) {
                continue;
            }
            if (isGenerationField) {
                continue;
            }

            if (entry.isMapAllInternal() || thisField.isAnnotationPresent(AerospikeBin.class) || thisBin != null) {
                boolean isKey = handleKeyField(thisField, thisBin);
                mapField(thisField, thisBin, isKey);
            }
        }
    }

    private boolean handleKeyField(Field thisField, BinConfig thisBin) {
        ClassConfig config = entry.getConfigInternal();
        KeyConfig keyConfig = config != null ? config.getKey() : null;
        String keyField = keyConfig == null ? null : keyConfig.getField();
        if (thisField.isAnnotationPresent(AerospikeKey.class)
                || (!StringUtils.isBlank(keyField) && keyField.equals(thisField.getName()))) {
            if (thisField.isAnnotationPresent(AerospikeExclude.class)
                    || (thisBin != null && thisBin.isExclude() != null && thisBin.isExclude())) {
                throw new AerospikeException(String.format(
                        "Class %s cannot have a field which is both a key and excluded.",
                        entry.getRawClassInternal().getName()));
            }

            if (entry.getKeyInternal() != null) {
                throw new AerospikeException(String.format("Class %s cannot have more than one key",
                        entry.getRawClassInternal().getName()));
            }

            AerospikeKey keyAnnotation = thisField.getAnnotation(AerospikeKey.class);
            boolean storeAsBin = keyAnnotation == null || keyAnnotation.storeAsBin();

            if (keyConfig != null && keyConfig.getStoreAsBin() != null) {
                storeAsBin = keyConfig.getStoreAsBin();
            }

            if (!storeAsBin && entry.getSendKey() != null && !entry.getSendKey()) {
                throw new AerospikeException(String.format(
                        "Class %s attempts to store primary key information inside the aerospike key, but sendKey is not true at the record level",
                        entry.getRawClassInternal().getName()));
            }

            AnnotatedType annotatedType = new AnnotatedType(config, thisField);
            TypeMapper typeMapper = TypeUtils.getMapper(thisField.getType(), annotatedType, entry.getEngineInternal());
            entry.setKeyInternal(new ValueType.FieldValue(thisField, typeMapper, annotatedType));
            entry.setKeyAsBinInternal(storeAsBin);
            return true;
        }
        return false;
    }

    private boolean handleGenerationField(Field thisField, BinConfig thisBin) {
        ClassConfig config = entry.getConfigInternal();
        boolean isGenerationField = thisField.isAnnotationPresent(AerospikeGeneration.class)
                || (thisBin != null && thisBin.isGeneration() != null && thisBin.isGeneration());

        if (isGenerationField) {
            if (thisField.isAnnotationPresent(AerospikeExclude.class)
                    || (thisBin != null && thisBin.isExclude() != null && thisBin.isExclude())) {
                throw new AerospikeException(String.format(
                        "Class %s cannot have a field which is both a generation field and excluded.",
                        entry.getRawClassInternal().getName()));
            }

            if (entry.getGenerationFieldInternal() != null) {
                throw new AerospikeException(String.format(
                        "Class %s cannot have more than one @AerospikeGeneration field",
                        entry.getRawClassInternal().getName()));
            }

            Class<?> fieldType = thisField.getType();
            if (!fieldType.equals(Integer.class) && !fieldType.equals(int.class)) {
                throw new AerospikeException(String.format(
                        "@AerospikeGeneration field %s in class %s must be of Integer or int type, but was %s",
                        thisField.getName(), entry.getRawClassInternal().getName(), fieldType.getSimpleName()));
            }

            AnnotatedType annotatedType = new AnnotatedType(config, thisField);
            TypeMapper typeMapper = TypeUtils.getMapper(thisField.getType(), annotatedType, entry.getEngineInternal());
            entry.setGenerationFieldInternal(new ValueType.FieldValue(thisField, typeMapper, annotatedType));
        }
        return isGenerationField;
    }

    private void mapField(Field thisField, BinConfig thisBin, boolean isKey) {
        AerospikeBin bin = thisField.getAnnotation(AerospikeBin.class);
        String binName = bin == null ? null : ParserUtils.getInstance().get(bin.name());
        if (thisBin != null && !StringUtils.isBlank(thisBin.getDerivedName())) {
            binName = thisBin.getDerivedName();
        }
        String name = StringUtils.isBlank(binName) ? thisField.getName() : binName;
        if (isKey) {
            entry.setKeyNameInternal(name);
        }

        if (entry.getValuesInternal().get(name) != null) {
            throw new AerospikeException(String.format("Class %s cannot define the mapped name %s more than once",
                    entry.getRawClassInternal().getName(), name));
        }
        if ((bin != null && bin.useAccessors())
                || (thisBin != null && thisBin.getUseAccessors() != null && thisBin.getUseAccessors())) {
            validateAccessorsForField(name, thisField);
        } else {
            thisField.setAccessible(true);
            AnnotatedType annotatedType = new AnnotatedType(entry.getConfigInternal(), thisField);
            TypeMapper typeMapper = TypeUtils.getMapper(thisField.getType(), annotatedType, entry.getEngineInternal());
            ValueType valueType = new ValueType.FieldValue(thisField, typeMapper, annotatedType);
            entry.getValuesInternal().put(name, valueType);
        }
    }

    private Method findMethodWithNameAndParams(String name, Class<?>... params) {
        try {
            return entry.getRawClassInternal().getDeclaredMethod(name, params);
        } catch (NoSuchMethodException nsme) {
            return null;
        }
    }

    private void validateAccessorsForField(String binName, Field thisField) {
        String fieldName = thisField.getName();
        String methodNameBase = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        String getterName = "get" + methodNameBase;
        String setterName = "set" + methodNameBase;

        Method getter = findMethodWithNameAndParams(getterName);
        if (getter == null) {
            throw new AerospikeException(String.format(
                    "Expected to find getter for field %s on class %s due to it being configured to useAccessors, but no method with the signature \"%s %s()\" was found",
                    fieldName, entry.getRawClassInternal().getSimpleName(), thisField.getType().getSimpleName(),
                    getterName));
        }

        Method setter = findMethodWithNameAndParams(setterName, thisField.getType());
        if (setter == null) {
            setter = findMethodWithNameAndParams(setterName, thisField.getType(), Key.class);
        }
        if (setter == null) {
            setter = findMethodWithNameAndParams(setterName, thisField.getType(), Value.class);
        }
        if (setter == null) {
            throw new AerospikeException(String.format(
                    "Expected to find setter for field %s on class %s due to it being configured to useAccessors, but no method with the name \"%s\" was found",
                    fieldName, entry.getRawClassInternal().getSimpleName(), setterName));
        }

        AnnotatedType annotatedType = new AnnotatedType(entry.getConfigInternal(), thisField);
        TypeMapper typeMapper = TypeUtils.getMapper(thisField.getType(), annotatedType, entry.getEngineInternal());
        PropertyDefinition property = new PropertyDefinition(binName, entry.getEngineInternal());
        property.setGetter(getter);
        property.setSetter(setter);
        property.validate(entry.getRawClassInternal().getName(), entry.getConfigInternal(), false);

        ValueType value = new ValueType.MethodValue(property, typeMapper, annotatedType);
        entry.getValuesInternal().put(binName, value);
    }

    private void formOrdinalsFromValues() {
        Map<Integer, String> ordinals = null;
        Set<String> fieldsWithOrdinals = null;

        for (String thisValueName : entry.getValuesInternal().keySet()) {
            ValueType thisValue = entry.getValuesInternal().get(thisValueName);

            BinConfig binConfig = getBinFromName(thisValueName);
            Integer ordinal = binConfig == null ? null : binConfig.getOrdinal();

            if (ordinal == null) {
                for (Annotation thisAnnotation : thisValue.getAnnotations()) {
                    if (thisAnnotation instanceof AerospikeOrdinal) {
                        ordinal = ((AerospikeOrdinal) thisAnnotation).value();
                        break;
                    }
                }
            }
            if (ordinal != null) {
                if (ordinals == null) {
                    ordinals = new HashMap<>();
                    fieldsWithOrdinals = new HashSet<>();
                }
                if (ordinals.containsKey(ordinal)) {
                    throw new AerospikeException(String.format("Class %s has multiple values with the ordinal of %d",
                            entry.getRawClassInternal().getSimpleName(), ordinal));
                }
                ordinals.put(ordinal, thisValueName);
                fieldsWithOrdinals.add(thisValueName);
            }
        }

        if (ordinals != null) {
            for (int i = 1; i <= ordinals.size(); i++) {
                if (!ordinals.containsKey(i)) {
                    throw new AerospikeException(String.format(
                            "Class %s has %d values specifying ordinals. These should be 1..%d, but %d is missing",
                            entry.getRawClassInternal().getSimpleName(), ordinals.size(), ordinals.size(), i));
                }
            }
        }

        entry.setOrdinalsInternal(ordinals);
        entry.setFieldsWithOrdinalsInternal(fieldsWithOrdinals);
    }

    private BinConfig getBinFromName(String name) {
        ClassConfig classConfig = entry.getClassConfig();
        if (classConfig == null || classConfig.getBins() == null) {
            return null;
        }
        for (BinConfig thisBin : classConfig.getBins()) {
            if (thisBin.getDerivedName().equals(name)) {
                return thisBin;
            }
        }
        return null;
    }

    private BinConfig getBinFromField(Field field) {
        ClassConfig classConfig = entry.getClassConfig();
        if (classConfig == null || classConfig.getBins() == null) {
            return null;
        }
        for (BinConfig thisBin : classConfig.getBins()) {
            if (thisBin.getField() != null && thisBin.getField().equals(field.getName())) {
                return thisBin;
            }
        }
        return null;
    }

    private BinConfig getBinFromGetter(String name) {
        ClassConfig classConfig = entry.getClassConfig();
        if (classConfig == null || classConfig.getBins() == null) {
            return null;
        }
        for (BinConfig thisBin : classConfig.getBins()) {
            if (thisBin.getGetter() != null && thisBin.getGetter().equals(name)) {
                return thisBin;
            }
        }
        return null;
    }

    private BinConfig getBinFromSetter(String name) {
        ClassConfig classConfig = entry.getClassConfig();
        if (classConfig == null || classConfig.getBins() == null) {
            return null;
        }
        for (BinConfig thisBin : classConfig.getBins()) {
            if (thisBin.getSetter() != null && thisBin.getSetter().equals(name)) {
                return thisBin;
            }
        }
        return null;
    }
}