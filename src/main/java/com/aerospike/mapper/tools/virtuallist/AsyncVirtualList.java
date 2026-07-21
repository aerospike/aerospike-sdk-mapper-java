package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.client.sdk.ErrorStrategy;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.converters.MappingConverter;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AsyncVirtualList<E> extends BaseVirtualList<E> implements IAsyncVirtualList<E> {

    private final MappingConverter mappingConverter;

    public AsyncVirtualList(@NotNull Session session, @NotNull MappingEngine engine,
                            @NotNull Class<?> owningClazz, @NotNull Object key,
                            @NotNull String binName, @NotNull Class<E> clazz) {
        super(session, engine, null, owningClazz, key, binName, clazz);
        this.mappingConverter = engine.getMappingConverter();
    }

    public AsyncVirtualList(@NotNull Session session, @NotNull MappingEngine engine,
                            @NotNull Object object, @NotNull String binName, @NotNull Class<E> clazz) {
        super(session, engine, object, null, null, binName, clazz);
        this.mappingConverter = engine.getMappingConverter();
    }

    public AsyncMultiOperation<E> beginMultiOperation() {
        return new AsyncMultiOperation<>(this);
    }

    private CompletableFuture<Record> executeAsync(Operation... operations) {
        return getSession().upsert(key)
                .appendOperations(operations)
                .executeAsync(ErrorStrategy.IN_STREAM)
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return null;
                    }
                    RecordResult first = results.get(0);
                    first.orThrow();
                    return first.recordOrNull();
                });
    }

    private CompletableFuture<List<E>> executeAndUnpack(Operation operation) {
        return executeAsync(operation).thenApply(record -> {
            if (record == null) {
                return null;
            }
            HydrationContext ctx = new HydrationContext(getSession());
            ctx.pushKey(key);
            try {
                List<E> result = unpackListResult(record.getValue(binName), ctx);
                if (result != null && !result.isEmpty()) {
                    mappingConverter.resolveDependencies(ctx);
                }
                return result;
            } finally {
                ctx.popKey();
            }
        });
    }

    @Override
    public CompletableFuture<List<E>> getByValue(Object value, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByValueOperation(value, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByValueRangeOperation(startValue, endValue, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByValueList(List<Object> values, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByValueListOperation(values, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByValueRelativeRankRangeOperation(value, rank, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByValueRelativeRankRangeOperation(value, rank, count, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByIndexRange(int index, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByIndexRangeOperation(index, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByIndexRange(int index, int count, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByIndexRangeOperation(index, count, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByRank(int rank, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByRankOperation(rank, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByRankRange(int rank, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByRankRangeOperation(rank, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByRankRange(int rank, int count, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByRankRangeOperation(rank, count, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByKey(Object key, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByKeyOperation(key, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> getByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType) {
        return executeAndUnpack(getGetByKeyRangeOperation(startKey, endKey, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByKey(Object key, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByKeyOperation(key, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByValue(Object value, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByValueOperation(value, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByValueList(List<Object> values, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByValueListOperation(values, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByValueRangeOperation(startValue, endValue, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByValueRelativeRankRangeOperation(value, rank, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByValueRelativeRankRangeOperation(value, rank, count, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByIndex(int index, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByIndexOperation(index, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByIndexRange(int index, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByIndexRangeOperation(index, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByIndexRange(int index, int count, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByIndexRangeOperation(index, count, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByRank(int rank, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByRankOperation(rank, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByRankRange(int rank, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByRankRangeOperation(rank, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByRankRange(int rank, int count, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByRankRangeOperation(rank, count, returnResultsOfType));
    }

    @Override
    public CompletableFuture<List<E>> removeByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType) {
        return executeAndUnpack(getRemoveByKeyRangeOperation(startKey, endKey, returnResultsOfType));
    }

    @Override
    public CompletableFuture<Long> append(E element) {
        Object aerospikeValue = listMapper.toAerospikeInstanceFormat(element);
        return executeAsync(getAppendOperation(aerospikeValue))
                .thenApply(record -> record == null ? -1L : record.getLong(binName));
    }

    @Override
    public CompletableFuture<E> get(int index) {
        return executeAsync(getByIndexOperation(index)).thenApply(record -> {
            if (record == null) {
                return null;
            }
            HydrationContext ctx = new HydrationContext(getSession());
            ctx.pushKey(key);
            try {
                Object rawResult = record.getValue(binName);
                E result = unpackSingleResult(rawResult, ctx);
                if (result != null) {
                    mappingConverter.resolveDependencies(ctx);
                }
                return result;
            } finally {
                ctx.popKey();
            }
        });
    }

    @Override
    public CompletableFuture<Long> size() {
        return executeAsync(getSizeOperation())
                .thenApply(record -> record == null ? -1L : record.getLong(binName));
    }

    @Override
    public CompletableFuture<Void> clear() {
        return executeAsync(getClearOperation()).thenApply(record -> null);
    }

    /**
     * Returns a new {@code AsyncVirtualList} targeting a different record key,
     * sharing all other configuration. The original list is not modified.
     */
    public AsyncVirtualList<E> withKey(Object newKey) {
        String set = alignedSet();
        Key newAeroKey = new Key(owningEntry.getNamespace(), set,
                Value.get(owningEntry.translateKeyToAerospikeKey(newKey)));
        return new AsyncVirtualList<>(this, newAeroKey);
    }

    private AsyncVirtualList(AsyncVirtualList<E> source, Key newKey) {
        super(source.session, source.engine, source.owningEntry, source.elementEntry,
                source.binName, source.listMapper, source.listType, newKey);
        this.mappingConverter = source.mappingConverter;
    }
}
