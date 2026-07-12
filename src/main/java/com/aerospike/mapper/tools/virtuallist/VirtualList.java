package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.IAeroMapper;
import com.aerospike.mapper.tools.IBaseAeroMapper;
import com.aerospike.mapper.tools.converters.MappingConverter;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class VirtualList<E> extends BaseVirtualList<E> implements IVirtualList<E> {

    private final MappingConverter mappingConverter;

    public VirtualList(@NotNull IAeroMapper mapper, @NotNull Class<?> owningClazz, @NotNull Object key,
                       @NotNull String binName, @NotNull Class<E> clazz) {
        super(mapper, null, owningClazz, key, binName, clazz);
        this.mappingConverter = mapper.getMappingConverter();
    }

    public VirtualList(@NotNull IAeroMapper mapper, @NotNull Object object, @NotNull String binName,
                       @NotNull Class<E> clazz) {
        super(mapper, object, null, null, binName, clazz);
        this.mappingConverter = mapper.getMappingConverter();
    }

    public VirtualList(Session session, @NotNull IBaseAeroMapper internalMapper, @NotNull Class<?> owningClazz,
                       @NotNull Object key, @NotNull String binName, @NotNull Class<E> clazz) {
        super(session, internalMapper, null, owningClazz, key, binName, clazz);
        this.mappingConverter = internalMapper.getMappingConverter();
    }

    public VirtualList(Session session, @NotNull IBaseAeroMapper internalMapper, @NotNull Object object,
                       @NotNull String binName, @NotNull Class<E> clazz) {
        super(session, internalMapper, object, null, null, binName, clazz);
        this.mappingConverter = internalMapper.getMappingConverter();
    }

    private VirtualList(VirtualList<E> source, Session newSession) {
        super(newSession, source.mapper,
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
        HydrationContext ctx = new HydrationContext(getSession());
        E result = unpackSingleResult(rawResult, ctx);
        if (result != null) {
            mappingConverter.resolveDependencies(ctx);
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
        HydrationContext ctx = new HydrationContext(getSession());
        List<E> result = unpackListResult(record.getValue(binName), ctx);
        if (result != null && !result.isEmpty()) {
            mappingConverter.resolveDependencies(ctx);
        }
        return result;
    }
}
