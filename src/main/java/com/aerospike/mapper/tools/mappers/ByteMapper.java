package com.aerospike.mapper.tools.mappers;

public class ByteMapper extends LongBackedNumberMapper {

    public ByteMapper() {
        super(Number::byteValue);
    }
}
