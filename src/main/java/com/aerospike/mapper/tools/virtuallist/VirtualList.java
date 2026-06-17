package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.tools.IAeroMapper;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class VirtualList<E> extends BaseVirtualList<E> implements IVirtualList<E> {

    private final IAeroMapper aeroMapper;

    public VirtualList(@NotNull IAeroMapper mapper, @NotNull Class<?> owningClazz, @NotNull Object key,
                       @NotNull String binName, @NotNull Class<E> clazz) {
        super(mapper, null, owningClazz, key, binName, clazz);
        this.aeroMapper = mapper;
    }

    public VirtualList(@NotNull IAeroMapper mapper, @NotNull Object object, @NotNull String binName,
                       @NotNull Class<E> clazz) {
        super(mapper, object, null, null, binName, clazz);
        this.aeroMapper = mapper;
    }

    public VirtualList<E> changeKey(Object newKey) {
        String set = alignedSet();
        this.key = new Key(owningEntry.getNamespace(), set, Value.get(owningEntry.translateKeyToAerospikeKey(newKey)));
        return this;
    }

    public MultiOperation<E> beginMultiOperation() {
        return new MultiOperation<>(this);
    }

    @Override
    public List<E> getByValue(Object value, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByValueOperation(value, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByValueRangeOperation(startValue, endValue, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByValueList(List<Object> values, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByValueListOperation(values, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByValueRelativeRankRangeOperation(value, rank, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByValueRelativeRankRangeOperation(value, rank, count, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByIndexRange(int index, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByIndexRangeOperation(index, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByIndexRange(int index, int count, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByIndexRangeOperation(index, count, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByRank(int rank, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByRankOperation(rank, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByRankRange(int rank, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByRankRangeOperation(rank, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByRankRange(int rank, int count, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByRankRangeOperation(rank, count, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByKey(Object key, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByKeyOperation(key, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> getByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType) {
        Record record = executeOperations(getGetByKeyRangeOperation(startKey, endKey, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByKey(Object key, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByKeyOperation(key, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByValue(Object value, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByValueOperation(value, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByValueList(List<Object> values, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByValueListOperation(values, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByValueRangeOperation(startValue, endValue, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByValueRelativeRankRangeOperation(value, rank, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByValueRelativeRankRangeOperation(value, rank, count, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByIndex(int index, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByIndexOperation(index, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByIndexRange(int index, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByIndexRangeOperation(index, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByIndexRange(int index, int count, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByIndexRangeOperation(index, count, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByRank(int rank, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByRankOperation(rank, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByRankRange(int rank, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByRankRangeOperation(rank, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByRankRange(int rank, int count, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByRankRangeOperation(rank, count, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public List<E> removeByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType) {
        Record record = executeOperations(getRemoveByKeyRangeOperation(startKey, endKey, returnResultsOfType));
        return unpackAndResolve(record);
    }

    @Override
    public long append(E element) {
        Object aerospikeValue = listMapper.toAerospikeInstanceFormat(element);
        Record record = executeOperations(getAppendOperation(aerospikeValue));
        return record == null ? -1L : record.getLong(binName);
    }

    @Override
    public E get(int index) {
        Record record = executeOperations(getByIndexOperation(index));
        if (record == null) {
            return null;
        }
        Object rawResult = record.getValue(binName);
        E result = unpackSingleResult(rawResult);
        if (result != null) {
            aeroMapper.getMappingConverter().resolveDependencies(getSession());
        }
        return result;
    }

    @Override
    public long size() {
        Record record = executeOperations(getSizeOperation());
        return record == null ? -1L : record.getLong(binName);
    }

    @Override
    public void clear() {
        executeOperations(getClearOperation());
    }

    private List<E> unpackAndResolve(Record record) {
        if (record == null) {
            return null;
        }
        List<E> result = unpackListResult(record.getValue(binName));
        if (result != null && !result.isEmpty()) {
            aeroMapper.getMappingConverter().resolveDependencies(getSession());
        }
        return result;
    }
}
