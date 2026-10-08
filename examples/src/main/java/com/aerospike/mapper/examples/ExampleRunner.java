package com.aerospike.mapper.examples;

import com.aerospike.mapper.tools.ClassCache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ExampleRunner {
    public List<ExampleResult> run(
            List<ExampleDefinition> definitions, ExampleContext context, boolean failFast) {
        List<ExampleResult> results = new ArrayList<>();
        for (ExampleDefinition definition : definitions) {
            long started = System.nanoTime();
            Throwable failure = null;
            boolean setupEntered = false;
            ClassCache.getInstance().clear();
            try {
                setupEntered = true;
                definition.fixture().setup(context);
                definition.example().run(context);
                definition.fixture().verify(context);
            } catch (Throwable t) {
                failure = t;
            } finally {
                if (setupEntered) {
                    try {
                        definition.fixture().cleanup(context);
                    } catch (Throwable cleanupFailure) {
                        if (failure == null) failure = cleanupFailure;
                        else failure.addSuppressed(cleanupFailure);
                    }
                }
                ClassCache.getInstance().clear();
            }
            Duration duration = Duration.ofNanos(System.nanoTime() - started);
            if (failure == null) {
                results.add(new ExampleResult(definition.name(), ExampleStatus.PASSED,
                        duration, "passed", null));
            } else {
                results.add(new ExampleResult(definition.name(), ExampleStatus.FAILED,
                        duration, failure.getMessage(), failure));
                if (failFast) break;
            }
        }
        return List.copyOf(results);
    }
}
