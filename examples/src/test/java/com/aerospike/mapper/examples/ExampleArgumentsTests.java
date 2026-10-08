package com.aerospike.mapper.examples;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExampleArgumentsTests {
    private final List<ExampleDefinition> registry = ExampleRegistry.examples();

    @Test
    void defaultsAndSelections() {
        ExampleArguments defaults = ExampleArguments.parse(new String[] {"list"}, registry);
        assertTrue(defaults.list());
        assertEquals("localhost", defaults.host());
        assertEquals(3000, defaults.port());
        assertEquals("test", defaults.namespace());

        ExampleArguments named = ExampleArguments.parse(new String[] {
                "reactive", "quick-start", "--host", "db", "--port", "3010",
                "--namespace", "dev", "--allow-non-test-namespace", "--fail-fast"}, registry);
        assertEquals(List.of("reactive", "quick-start"), named.names());
        assertEquals("db", named.host());
        assertEquals(3010, named.port());
        assertEquals("dev", named.namespace());
        assertTrue(named.allowNonTestNamespace());
        assertTrue(named.failFast());

        ExampleArguments all = ExampleArguments.parse(new String[] {"all"}, registry);
        assertEquals(registry.stream().map(ExampleDefinition::name).toList(), all.names());
    }

    @Test
    void rejectsInvalidInputBeforeExecution() {
        assertInvalid("quick-start", "quick-start");
        assertInvalid("--host", "", "quick-start");
        assertInvalid("--namespace", "", "quick-start");
        assertInvalid("--port", "0", "quick-start");
        assertInvalid("--port", "65536", "quick-start");
        assertInvalid("--port", "abc", "quick-start");
        assertInvalid("unknown");
        assertInvalid("--unknown", "quick-start");
        assertInvalid("all", "quick-start");
        assertInvalid("list", "quick-start");
        assertInvalid("--namespace", "production", "quick-start");
        assertDoesNotThrow(() -> ExampleArguments.parse(new String[] {
                "--namespace", "production", "--allow-non-test-namespace", "quick-start"}, registry));
    }

    private void assertInvalid(String... args) {
        assertThrows(IllegalArgumentException.class, () -> ExampleArguments.parse(args, registry));
    }
}
