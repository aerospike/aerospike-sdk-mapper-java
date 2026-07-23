package com.aerospike.mapper.tools.mappers;

import java.util.function.Function;

import com.aerospike.mapper.tools.TypeMapper;

class LongBackedNumberMapper extends TypeMapper {

    private final Function<Number, Object> fromLongConverter;

    protected LongBackedNumberMapper(Function<Number, Object> fromLongConverter) {
        this.fromLongConverter = fromLongConverter;
    }

    @Override
    public Object toAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    @Override
    public Object fromAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        return fromLongConverter.apply((Number) value);
    }
}