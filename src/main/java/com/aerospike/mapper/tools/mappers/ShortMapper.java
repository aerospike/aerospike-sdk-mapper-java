package com.aerospike.mapper.tools.mappers;

public class ShortMapper extends LongBackedNumberMapper {

    public ShortMapper() {
        super(Number::shortValue);
    }
}
