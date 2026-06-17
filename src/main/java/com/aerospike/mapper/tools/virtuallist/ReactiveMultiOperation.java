package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;

import java.util.concurrent.CompletableFuture;

public class ReactiveMultiOperation<E> extends AbstractMultiOperation<E, ReactiveMultiOperation<E>> {

    ReactiveMultiOperation(BaseVirtualList<E> virtualList) {
        super(virtualList);
    }

    public <T> CompletableFuture<T> end() {
        return end(null);
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> end(Class<T> resultType) {
        if (operations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Operation[] ops = operations.toArray(new Operation[0]);
        return virtualList.getSession().upsert(virtualList.key)
                .appendOperations(ops)
                .execute()
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return null;
                    }
                    Record record = results.get(0).recordOrNull();
                    return extractResult(record);
                });
    }
}
