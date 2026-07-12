package com.aerospike.mapper.tools.virtuallist;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IAsyncVirtualList<E> {

    CompletableFuture<List<E>> getByValue(Object value, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByValueList(List<Object> values, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByIndexRange(int index, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByIndexRange(int index, int count, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByRank(int rank, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByRankRange(int rank, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByRankRange(int rank, int count, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByKey(Object key, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> getByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByKey(Object key, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByValue(Object value, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByValueList(List<Object> values, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByIndex(int index, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByIndexRange(int index, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByIndexRange(int index, int count, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByRank(int rank, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByRankRange(int rank, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByRankRange(int rank, int count, ReturnType returnResultsOfType);

    CompletableFuture<List<E>> removeByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType);

    CompletableFuture<Long> append(E element);

    CompletableFuture<E> get(int index);

    CompletableFuture<Long> size();

    CompletableFuture<Void> clear();
}
