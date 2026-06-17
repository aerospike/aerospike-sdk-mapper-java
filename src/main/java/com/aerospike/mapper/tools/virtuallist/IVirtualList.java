package com.aerospike.mapper.tools.virtuallist;

import java.util.List;

public interface IVirtualList<E> {

    /**
     * Get items from the list matching the specified value.
     */
    List<E> getByValue(Object value, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified value range.
     * Inclusive of start, exclusive of end.
     */
    List<E> getByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified list of values.
     */
    List<E> getByValueList(List<Object> values, ReturnType returnResultsOfType);

    /**
     * Get items nearest to value and greater by relative rank.
     */
    List<E> getByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType);

    /**
     * Get items nearest to value and greater by relative rank with a count limit.
     */
    List<E> getByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType);

    /**
     * Get items starting at specified index to the end of virtual list.
     */
    List<E> getByIndexRange(int index, ReturnType returnResultsOfType);

    /**
     * Get "count" items starting at specified index.
     */
    List<E> getByIndexRange(int index, int count, ReturnType returnResultsOfType);

    /**
     * Get items identified by rank.
     */
    List<E> getByRank(int rank, ReturnType returnResultsOfType);

    /**
     * Get items starting at specified rank to the last ranked item.
     */
    List<E> getByRankRange(int rank, ReturnType returnResultsOfType);

    /**
     * Get "count" items starting at specified rank.
     */
    List<E> getByRankRange(int rank, int count, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified key.
     * For MAP: matches the map key. For LIST: matches the value.
     */
    List<E> getByKey(Object key, ReturnType returnResultsOfType);

    /**
     * Get items from the list matching the specified key range.
     * For MAP: range of map keys. For LIST: range of values.
     */
    List<E> getByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType);

    /**
     * Remove items matching the specified key.
     */
    List<E> removeByKey(Object key, ReturnType returnResultsOfType);

    /**
     * Remove items identified by value.
     */
    List<E> removeByValue(Object value, ReturnType returnResultsOfType);

    /**
     * Remove items identified by list of values.
     */
    List<E> removeByValueList(List<Object> values, ReturnType returnResultsOfType);

    /**
     * Remove items matching the specified value range.
     */
    List<E> removeByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType);

    /**
     * Remove items nearest to value and greater by relative rank.
     */
    List<E> removeByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType);

    /**
     * Remove items nearest to value and greater by relative rank with a count limit.
     */
    List<E> removeByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType);

    /**
     * Remove item identified by index.
     */
    List<E> removeByIndex(int index, ReturnType returnResultsOfType);

    /**
     * Remove items starting at specified index to the end of list.
     */
    List<E> removeByIndexRange(int index, ReturnType returnResultsOfType);

    /**
     * Remove "count" items starting at specified index.
     */
    List<E> removeByIndexRange(int index, int count, ReturnType returnResultsOfType);

    /**
     * Remove item identified by rank.
     */
    List<E> removeByRank(int rank, ReturnType returnResultsOfType);

    /**
     * Remove items starting at specified rank to the last ranked item.
     */
    List<E> removeByRankRange(int rank, ReturnType returnResultsOfType);

    /**
     * Remove "count" items starting at specified rank.
     */
    List<E> removeByRankRange(int rank, int count, ReturnType returnResultsOfType);

    /**
     * Remove items matching the specified key range.
     */
    List<E> removeByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType);

    /**
     * Append a new element at the end of the virtual list.
     *
     * @return The list size after append.
     */
    long append(E element);

    /**
     * Get an element from the virtual list at a specific index.
     */
    E get(int index);

    /**
     * Get the size of the virtual list (number of elements).
     *
     * @return The size of the list, or -1 if the record is not found.
     */
    long size();

    /**
     * Remove all items in the virtual list.
     */
    void clear();
}
