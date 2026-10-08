package com.aerospike.mapper;

import java.util.Objects;

public record ExampleDefinition(
        String name,
        String description,
        RunnableExample example,
        ExampleFixture fixture) {

    public ExampleDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        Objects.requireNonNull(example);
        Objects.requireNonNull(fixture);
    }
}
