package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.Session;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.converters.MappingConverter;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class VirtualList<E> extends BaseVirtualList<E> implements IVirtualList<E> {

    private final MappingConverter mappingConverter;

    public VirtualList(Session session, @NotNull MappingEngine engine, @NotNull Class<?> owningClazz,
                       @NotNull Object key, @NotNull String binName, @NotNull Class<E> clazz) {
        super(session, engine, null, owningClazz, key, binName, clazz);
        this.mappingConverter = engine.getMappingConverter();
    }

    public VirtualList(Session session, @NotNull MappingEngine engine, @NotNull Object object,
                       @NotNull String binName, @NotNull Class<E> clazz) {
        super(session, engine, object, null, null, binName, clazz);
        this.mappingConverter = engine.getMappingConverter();
    }

    private VirtualList(VirtualList<E> source, Session newSession) {
        super(newSession, source.engine,
                source.owningEntry, source.elementEntry, source.binName, source.listMapper,
                source.listType, source.key);
        this.mappingConverter = source.mappingConverter;
    }

    /**
     * Returns a new {@code VirtualList} view that uses the given session for
     * the next (or subsequent) operations. The original list is unchanged.
     */
    public VirtualList<E> withSession(Session session) {
        return new VirtualList<>(this, session);
    }

    public MultiOperation<E> beginMultiOperation() {
        return new MultiOperation<>(this);
    }

    @Override
    public List<E> getByValue(Object value, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByValueOperation(value, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByValueRangeOperation(startValue, endValue, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByValueList(List<Object> values, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByValueListOperation(values, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByValueRelativeRankRangeOperation(value, rank, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByValueRelativeRankRangeOperation(value, rank, count, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByIndexRange(int index, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByIndexRangeOperation(index, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByIndexRange(int index, int count, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByIndexRangeOperation(index, count, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByRank(int rank, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByRankOperation(rank, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByRankRange(int rank, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByRankRangeOperation(rank, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByRankRange(int rank, int count, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByRankRangeOperation(rank, count, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByKey(Object key, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByKeyOperation(key, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> getByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getGetByKeyRangeOperation(startKey, endKey, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByKey(Object key, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByKeyOperation(key, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByValue(Object value, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByValueOperation(value, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByValueList(List<Object> values, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByValueListOperation(values, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByValueRange(Object startValue, Object endValue, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByValueRangeOperation(startValue, endValue, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByValueRelativeRankRange(Object value, int rank, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByValueRelativeRankRangeOperation(value, rank, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByValueRelativeRankRange(Object value, int rank, int count, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByValueRelativeRankRangeOperation(value, rank, count, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByIndex(int index, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByIndexOperation(index, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByIndexRange(int index, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByIndexRangeOperation(index, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByIndexRange(int index, int count, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByIndexRangeOperation(index, count, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByRank(int rank, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByRankOperation(rank, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByRankRange(int rank, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByRankRangeOperation(rank, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByRankRange(int rank, int count, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByRankRangeOperation(rank, count, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public List<E> removeByKeyRange(Object startKey, Object endKey, ReturnType returnResultsOfType) {
        Record operationResult = executeOperations(getRemoveByKeyRangeOperation(startKey, endKey, returnResultsOfType));
        return unpackAndResolve(operationResult);
    }

    @Override
    public long append(E element) {
        Object aerospikeValue = listMapper.toAerospikeInstanceFormat(element);
        Record operationResult = executeOperations(getAppendOperation(aerospikeValue));
        return operationResult == null ? -1L : operationResult.getLong(binName);
    }

    @Override
    public E get(int index) {
        Record operationResult = executeOperations(getByIndexOperation(index));
        if (operationResult == null) {
            return null;
        }
        Object rawResult = operationResult.getValue(binName);
        HydrationContext ctx = new HydrationContext(getSession());
        E result = unpackSingleResult(rawResult, ctx);
        if (result != null) {
            mappingConverter.resolveDependencies(ctx);
        }
        return result;
    }

    @Override
    public long size() {
        Record operationResult = executeOperations(getSizeOperation());
        return operationResult == null ? -1L : operationResult.getLong(binName);
    }

    @Override
    public void clear() {
        executeOperations(getClearOperation());
    }

    private List<E> unpackAndResolve(Record operationResult) {
        if (operationResult == null) {
            return null;
        }
        HydrationContext ctx = new HydrationContext(getSession());
        List<E> result = unpackListResult(operationResult.getValue(binName), ctx);
        if (result != null && !result.isEmpty()) {
            mappingConverter.resolveDependencies(ctx);
        }
        return result;
    }
}
