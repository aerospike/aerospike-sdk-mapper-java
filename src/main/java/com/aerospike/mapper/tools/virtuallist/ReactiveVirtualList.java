package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.tools.IReactiveAeroMapper;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReactiveVirtualList<E> extends BaseVirtualList<E> implements IReactiveVirtualList<E> {

    private final IReactiveAeroMapper reactiveMapper;

    public ReactiveVirtualList(@NotNull IReactiveAeroMapper reactiveMapper, @NotNull Class<?> owningClazz,
                               @NotNull Object key, @NotNull String binName, @NotNull Class<E> clazz) {
        super(reactiveMapper, null, owningClazz, key, binName, clazz);
        this.reactiveMapper = reactiveMapper;
    }

    public ReactiveVirtualList(@NotNull IReactiveAeroMapper reactiveMapper, @NotNull Object object,
                               @NotNull String binName, @NotNull Class<E> clazz) {
        super(reactiveMapper, object, null, null, binName, clazz);
        this.reactiveMapper = reactiveMapper;
    }

    public ReactiveVirtualList<E> changeKey(Object newKey) {
        String set = alignedSet();
        this.key = new Key(owningEntry.getNamespace(), set, Value.get(owningEntry.translateKeyToAerospikeKey(newKey)));
        return this;
    }

    public ReactiveMultiOperation<E> beginMultiOperation() {
        return new ReactiveMultiOperation<>(this);
    }

    private CompletableFuture<Record> executeAsync(Operation... operations) {
        return getSession().upsert(key)
                .appendOperations(operations)
                .execute()
                .asCompletableFuture()
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return null;
                    }
                    RecordResult first = results.get(0);
                    return first.recordOrNull();
                });
    }

    private CompletableFuture<List<E>> executeAndUnpack(Operation operation) {
        return executeAsync(operation).thenApply(record -> {
            if (record == null) {
                return null;
            }
            List<E> result = unpackListResult(record.getValue(binName));
            if (result != null && !result.isEmpty()) {
                reactiveMapper.getMappingConverter().resolveDependencies(getSession());
            }
            return result;
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
            Object rawResult = record.getValue(binName);
            E result = unpackSingleResult(rawResult);
            if (result != null) {
                reactiveMapper.getMappingConverter().resolveDependencies(getSession());
            }
            return result;
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
}
