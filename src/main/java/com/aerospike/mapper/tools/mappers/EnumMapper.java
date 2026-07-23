package com.aerospike.mapper.tools.mappers;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.tools.TypeMapper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class EnumMapper extends TypeMapper {

    private final Class<? extends Enum<?>> clazz;
    private final String enumField;
    private final Field enumRequestedField;
    private final Map<String, Enum<?>> aerospikeValueLookup;

    public EnumMapper(Class<? extends Enum<?>> clazz, String enumField) {
        this.clazz = clazz;
        this.enumField = enumField;
        if (!enumField.isEmpty()) {
            try {
                this.enumRequestedField = clazz.getDeclaredField(enumField);
                this.enumRequestedField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw toAerospikeException(e);
            }
        } else {
            this.enumRequestedField = null;
        }
        this.aerospikeValueLookup = buildAerospikeValueLookup();
    }

    @Override
    public Object toAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }

        return toStoredValue(value);
    }

    @Override
    public Object fromAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = (String) value;
        Enum<?> enumValue = aerospikeValueLookup.get(stringValue);
        if (enumValue != null) {
            return enumValue;
        }
        throw new AerospikeException(String.format("Enum value of \"%s\" not found in type %s", stringValue, clazz));
    }

    private Map<String, Enum<?>> buildAerospikeValueLookup() {
        Map<String, Enum<?>> lookup = new HashMap<>();
        for (Enum<?> constant : clazz.getEnumConstants()) {
            String storedValue = toStoredValue(constant);
            if (storedValue != null) {
                lookup.putIfAbsent(storedValue, constant);
            }
        }
        return lookup;
    }

    private String toStoredValue(Object value) {
        if (enumRequestedField == null) {
            return value.toString();
        }
        try {
            Object enumValue = enumRequestedField.get(value);
            return enumValue == null ? null : enumValue.toString();
        } catch (IllegalAccessException e) {
            throw toAerospikeException(e);
        }
    }

    private AerospikeException toAerospikeException(Exception e) {
        return new AerospikeException("Cannot Map requested enum, issue with the requested enumField.", e);
    }
}
