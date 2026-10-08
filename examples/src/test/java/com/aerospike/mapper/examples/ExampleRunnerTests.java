package com.aerospike.mapper.examples;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExampleRunnerTests {
    private static final ExampleContext UNUSED_CONTEXT = new ExampleContext(null, null, "test");

    @Test
    void runsLifecycleInOrderAndRecordsResultsInSelectionOrder() {
        List<String> events = new ArrayList<>();
        ExampleDefinition first = definition("first", events, false, false, false);
        ExampleDefinition second = definition("second", events, false, false, false);

        List<ExampleResult> results = new ExampleRunner().run(
                List.of(first, second), UNUSED_CONTEXT, false);

        assertEquals(List.of(
                "first:setup", "first:run", "first:verify", "first:cleanup",
                "second:setup", "second:run", "second:verify", "second:cleanup"), events);
        assertEquals(List.of("first", "second"),
                results.stream().map(ExampleResult::name).toList());
        assertTrue(results.stream().allMatch(result -> result.status() == ExampleStatus.PASSED));
    }

    @Test
    void cleanupAlwaysRunsAndItsFailureIsSuppressedOnBodyFailure() {
        List<String> events = new ArrayList<>();
        ExampleDefinition definition = definition("broken", events, false, true, true);

        ExampleResult result = new ExampleRunner().run(
                List.of(definition), UNUSED_CONTEXT, false).getFirst();

        assertEquals(ExampleStatus.FAILED, result.status());
        assertEquals("run failure", result.cause().getMessage());
        assertEquals(1, result.cause().getSuppressed().length);
        assertEquals("cleanup failure", result.cause().getSuppressed()[0].getMessage());
        assertEquals(List.of("broken:setup", "broken:run", "broken:cleanup"), events);
    }

    @Test
    void failFastHappensAfterCleanupAndResultRecording() {
        List<String> events = new ArrayList<>();
        ExampleDefinition first = definition("first", events, true, false, false);
        ExampleDefinition second = definition("second", events, false, false, false);

        List<ExampleResult> results = new ExampleRunner().run(
                List.of(first, second), UNUSED_CONTEXT, true);

        assertEquals(1, results.size());
        assertEquals("first", results.getFirst().name());
        assertEquals(List.of("first:setup", "first:cleanup"), events);
    }

    private ExampleDefinition definition(
            String name, List<String> events, boolean failSetup, boolean failRun,
            boolean failCleanup) {
        ExampleFixture fixture = new ExampleFixture() {
            @Override public void setup(ExampleContext context) {
                events.add(name + ":setup");
                if (failSetup) throw new IllegalStateException("setup failure");
            }
            @Override public void verify(ExampleContext context) {
                events.add(name + ":verify");
            }
            @Override public void cleanup(ExampleContext context) {
                events.add(name + ":cleanup");
                if (failCleanup) throw new IllegalStateException("cleanup failure");
            }
        };
        return new ExampleDefinition(name, name, context -> {
            events.add(name + ":run");
            if (failRun) throw new IllegalStateException("run failure");
        }, fixture);
    }
}
