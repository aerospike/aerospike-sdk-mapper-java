package com.aerospike.mapper;

@FunctionalInterface
public interface RunnableExample {
    void run(ExampleContext context) throws Exception;
}
