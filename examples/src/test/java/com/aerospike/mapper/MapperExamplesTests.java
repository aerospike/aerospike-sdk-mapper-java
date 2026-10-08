package com.aerospike.mapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MapperExamplesTests {
    private static final String PROPERTY = "aerospike.examples.namespace";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void listIsOfflineAndDeterministic() {
        AtomicInteger connections = new AtomicInteger();
        MapperExamples command = new MapperExamples((host, port) -> {
            connections.incrementAndGet();
            throw new AssertionError("list must be offline");
        });
        Capture capture = run(command, "list");
        assertEquals(0, capture.code);
        assertEquals(0, connections.get());
        assertTrue(capture.out.startsWith("quick-start"));
        assertTrue(capture.out.indexOf("quick-start") < capture.out.indexOf("reactive"));
        assertTrue(capture.out.indexOf("reactive") < capture.out.indexOf("banking-domain"));
        assertTrue(capture.out.contains("virtual-list-advanced"));
        assertTrue(capture.err.isEmpty());
    }

    @Test
    void cliFailuresDoNotConnect() {
        AtomicInteger connections = new AtomicInteger();
        MapperExamples command = new MapperExamples((host, port) -> {
            connections.incrementAndGet();
            throw new AssertionError();
        });
        Capture capture = run(command, "unknown");
        assertEquals(2, capture.code);
        assertEquals(0, connections.get());
        assertTrue(capture.err.contains("CLI error"));
    }

    @Test
    void bootstrapFailureIsReportedOnceAndNamespacePropertyIsRestored() {
        System.setProperty(PROPERTY, "prior");
        MapperExamples command = new MapperExamples((host, port) -> {
            throw new IllegalStateException("offline");
        });
        Capture capture = run(command,
                "quick-start", "--namespace", "dev", "--allow-non-test-namespace");
        assertEquals(1, capture.code);
        assertEquals("prior", System.getProperty(PROPERTY));
        assertEquals(1, capture.err.lines().filter(line -> line.contains("Runner failure")).count());
        assertFalse(capture.out.contains("quick-start"));
    }

    private Capture run(MapperExamples command, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = command.run(args, new PrintStream(out), new PrintStream(err));
        return new Capture(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Capture(int code, String out, String err) {}
}
