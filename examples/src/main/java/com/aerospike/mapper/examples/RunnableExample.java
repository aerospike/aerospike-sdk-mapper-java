package com.aerospike.mapper.examples;

@FunctionalInterface
public interface RunnableExample {
    void run(ExampleContext context) throws Exception;
}
