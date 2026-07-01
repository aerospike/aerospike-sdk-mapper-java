package com.aerospike.mapper.tools;

@FunctionalInterface
public interface MappingTransactionalVoid {
    void execute(MappingSession txnView);
}
