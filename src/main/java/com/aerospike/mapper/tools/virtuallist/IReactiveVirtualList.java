package com.aerospike.mapper.tools.virtuallist;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IReactiveVirtualList<E> {

    /**
     * Get items from the list matching the specified value.
     */
    CompletableFuture<List<E>> getByValue(Object value, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified value range.
     */
    CompletableFuture<List<E>> getByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified list of values.
     */
    CompletableFuture<List<E>> getByValueList(List<Object> values, ReturnType returnResultsOfType);

    /**
     * Get items nearest to value and greater by relative rank.
     */
    CompletableFuture<List<E>> getByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType);

    /**
     * Get items nearest to value and greater by relative rank with a count limit.
     */
    CompletableFuture<List<E>> getByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType);

    /**
     * Get items starting at specified index to the end of virtual list.
     */
    CompletableFuture<List<E>> getByIndexRange(int index, ReturnType returnResultsOfType);

    /**
     * Get "count" items starting at specified index.
     */
    CompletableFuture<List<E>> getByIndexRange(int index, int count, ReturnType returnResultsOfType);

    /**
     * Get items identified by rank.
     */
    CompletableFuture<List<E>> getByRank(int rank, ReturnType returnResultsOfType);

    /**
     * Get items starting at specified rank to the last ranked item.
     */
    CompletableFuture<List<E>> getByRankRange(int rank, ReturnType returnResultsOfType);

    /**
     * Get "count" items starting at specified rank.
     */
    CompletableFuture<List<E>> getByRankRange(int rank, int count, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified key.
     */
    CompletableFuture<List<E>> getByKey(Object key, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified key range.
     */
    CompletableFuture<List<E>> getByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType);

    /**
     * Remove items matching the specified key.
     */
    CompletableFuture<List<E>> removeByKey(Object key, ReturnType returnResultsOfType);

    /**
     * Remove items identified by value.
     */
    CompletableFuture<List<E>> removeByValue(Object value, ReturnType returnResultsOfType);

    /**
     * Remove items identified by list of values.
     */
    CompletableFuture<List<E>> removeByValueList(List<Object> values, ReturnType returnResultsOfType);

    /**
     * Remove items matching the specified value range.
     */
    CompletableFuture<List<E>> removeByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType);

    /**
     * Remove items nearest to value and greater by relative rank.
     */
    CompletableFuture<List<E>> removeByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType);

    /**
     * Remove items nearest to value and greater by relative rank with a count limit.
     */
    CompletableFuture<List<E>> removeByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType);

    /**
     * Remove item identified by index.
     */
    CompletableFuture<List<E>> removeByIndex(int index, ReturnType returnResultsOfType);

    /**
     * Remove items starting at specified index to the end of list.
     */
    CompletableFuture<List<E>> removeByIndexRange(int index, ReturnType returnResultsOfType);

    /**
     * Remove "count" items starting at specified index.
     */
    CompletableFuture<List<E>> removeByIndexRange(int index, int count, ReturnType returnResultsOfType);

    /**
     * Remove item identified by rank.
     */
    CompletableFuture<List<E>> removeByRank(int rank, ReturnType returnResultsOfType);

    /**
     * Remove items starting at specified rank to the last ranked item.
     */
    CompletableFuture<List<E>> removeByRankRange(int rank, ReturnType returnResultsOfType);

    /**
     * Remove "count" items starting at specified rank.
     */
    CompletableFuture<List<E>> removeByRankRange(int rank, int count, ReturnType returnResultsOfType);

    /**
     * Remove items matching the specified key range.
     */
    CompletableFuture<List<E>> removeByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType);

    /**
     * Append a new element at the end of the virtual list.
     *
     * @return Future resolving to the list size after append.
     */
    CompletableFuture<Long> append(E element);

    /**
     * Get an element from the virtual list at a specific index.
     */
    CompletableFuture<E> get(int index);

    /**
     * Get the size of the virtual list (number of elements).
     */
    CompletableFuture<Long> size();

    /**
     * Remove all items in the virtual list.
     */
    CompletableFuture<Void> clear();
}
