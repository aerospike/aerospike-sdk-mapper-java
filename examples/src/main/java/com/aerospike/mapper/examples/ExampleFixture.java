package com.aerospike.mapper.examples;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.query.IndexCollectionType;
import com.aerospike.client.sdk.query.IndexType;

import java.util.Arrays;
import java.util.List;

public interface ExampleFixture {
    default void setup(ExampleContext context) throws Exception {}
    default void verify(ExampleContext context) throws Exception {}
    default void cleanup(ExampleContext context) throws Exception {}
    default List<String> ownedSets() { return List.of(); }
    default List<String> ownedIndexes() { return List.of(); }

    static ExampleFixture cleanSets(String... sets) {
        List<String> owned = List.copyOf(Arrays.asList(sets));
        return new ExampleFixture() {
            @Override public void setup(ExampleContext context) {
                owned.forEach(set -> context.session().truncate(context.dataSet(set)));
            }
            @Override public void cleanup(ExampleContext context) {
                owned.forEach(set -> context.session().truncate(context.dataSet(set)));
            }
            @Override public List<String> ownedSets() { return owned; }
        };
    }

    static ExampleFixture cleanSetWithIndex(String set, String index, String bin) {
        return new ExampleFixture() {
            @Override public void setup(ExampleContext context) {
                context.session().truncate(context.dataSet(set));
                dropIfPresent(context, set, index);
                context.session().createIndex(context.dataSet(set), index, bin,
                        IndexType.INTEGER, IndexCollectionType.DEFAULT).waitTillComplete();
            }
            @Override public void cleanup(ExampleContext context) {
                Throwable failure = null;
                try {
                    dropIfPresent(context, set, index);
                } catch (Throwable t) {
                    failure = t;
                }
                try {
                    context.session().truncate(context.dataSet(set));
                } catch (Throwable t) {
                    if (failure == null) failure = t; else failure.addSuppressed(t);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                if (failure != null) throw new RuntimeException(failure);
            }
            @Override public List<String> ownedSets() { return List.of(set); }
            @Override public List<String> ownedIndexes() { return List.of(index); }
        };
    }

    private static void dropIfPresent(ExampleContext context, String set, String index) {
        try {
            context.session().dropIndex(context.dataSet(set), index).waitTillComplete();
        } catch (AerospikeException e) {
            if (e.getResultCode() != ResultCode.INDEX_NOTFOUND) throw e;
        }
    }
}
