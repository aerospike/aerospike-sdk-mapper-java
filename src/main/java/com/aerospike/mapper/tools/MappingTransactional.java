package com.aerospike.mapper.tools;

@FunctionalInterface
public interface MappingTransactional<T> {
    T execute(MappingSession txnView);
}
