package com.aerospike.mapper.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleAssertions {
    private ExampleAssertions() {}

    public static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void equal(Object expected, Object actual, String message) {
        if (!Objects.deepEquals(expected, actual)) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    public static <T extends Throwable> T expectThrows(
            Class<T> type, ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) return type.cast(thrown);
            throw new AssertionError(message + " (unexpected " + thrown + ")", thrown);
        }
        throw new AssertionError(message + " (nothing was thrown)");
    }

    public static void recordCount(int expected, Collection<?> records, String message) {
        equal(expected, records.size(), message);
    }

    public static <T> List<T> drainPublisher(Flow.Publisher<T> publisher, Duration timeout)
            throws Exception {
        CompletableFuture<List<T>> terminal = new CompletableFuture<>();
        List<T> items = new ArrayList<>();
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription value) {
                subscription.set(value);
                value.request(Long.MAX_VALUE);
            }
            @Override public void onNext(T item) { items.add(item); }
            @Override public void onError(Throwable throwable) { terminal.completeExceptionally(throwable); }
            @Override public void onComplete() { terminal.complete(List.copyOf(items)); }
        });
        try {
            return terminal.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Flow.Subscription current = subscription.get();
            if (current != null) current.cancel();
            terminal.cancel(true);
            throw e;
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
