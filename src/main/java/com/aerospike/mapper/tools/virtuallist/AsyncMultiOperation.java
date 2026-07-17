package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.ErrorStrategy;
import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.tools.HydrationContext;

import java.util.concurrent.CompletableFuture;

public class AsyncMultiOperation<E> extends AbstractMultiOperation<E, AsyncMultiOperation<E>> {

    AsyncMultiOperation(BaseVirtualList<E> virtualList) {
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
                .executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return null;
                    }
                    Record operationResult = results.get(0).recordOrNull();
                    HydrationContext ctx = new HydrationContext(virtualList.getSession());
                    ctx.pushKey(virtualList.key);
                    try {
                        return extractResult(operationResult, ctx);
                    } finally {
                        ctx.popKey();
                    }
                });
    }
}
