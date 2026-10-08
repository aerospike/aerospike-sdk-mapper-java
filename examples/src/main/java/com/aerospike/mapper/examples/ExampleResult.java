package com.aerospike.mapper.examples;

import java.time.Duration;

public record ExampleResult(
        String name,
        ExampleStatus status,
        Duration duration,
        String message,
        Throwable cause) {
}
