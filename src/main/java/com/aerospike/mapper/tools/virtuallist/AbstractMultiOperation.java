package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.tools.HydrationContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Common base for {@link MultiOperation} and {@link AsyncMultiOperation}.
 * Contains all fluent builder methods and operation accumulation state;
 * subclasses only override {@code end()} to choose sync vs async execution.
 *
 * @param <E> the virtual list element type
 * @param <S> the concrete self type (for fluent return)
 */
@SuppressWarnings("unchecked")
public abstract class AbstractMultiOperation<E, S extends AbstractMultiOperation<E, S>> {

    protected final List<Operation> operations;
    protected final List<Boolean> isWriteOp;
    protected int indexToReturn = -1;
    protected final BaseVirtualList<E> virtualList;

    protected AbstractMultiOperation(BaseVirtualList<E> virtualList) {
        this.operations = new ArrayList<>();
        this.isWriteOp = new ArrayList<>();
        this.virtualList = virtualList;
    }

    private S self() {
        return (S) this;
    }

    public S append(E item) {
        Object aerospikeItem = virtualList.listMapper.toAerospikeInstanceFormat(item);
        operations.add(virtualList.getAppendOperation(aerospikeItem));
        isWriteOp.add(true);
        return self();
    }

    public S removeByKey(Object key) {
        operations.add(virtualList.getRemoveByKeyOperation(key, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByKeyRange(Object startKey, Object endKey) {
        operations.add(virtualList.getRemoveByKeyRangeOperation(startKey, endKey, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByValue(Object value) {
        operations.add(virtualList.getRemoveByValueOperation(value, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByValueList(List<Object> values) {
        operations.add(virtualList.getRemoveByValueListOperation(values, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByValueRange(Object startValue, Object endValue) {
        operations.add(virtualList.getRemoveByValueRangeOperation(startValue, endValue, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByValueRelativeRankRange(Object value, int rank) {
        operations.add(virtualList.getRemoveByValueRelativeRankRangeOperation(value, rank, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByValueRelativeRankRange(Object value, int rank, int count) {
        operations.add(virtualList.getRemoveByValueRelativeRankRangeOperation(value, rank, count, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByIndex(int index) {
        operations.add(virtualList.getRemoveByIndexOperation(index, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByIndexRange(int index) {
        operations.add(virtualList.getRemoveByIndexRangeOperation(index, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByIndexRange(int index, int count) {
        operations.add(virtualList.getRemoveByIndexRangeOperation(index, count, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByRank(int rank) {
        operations.add(virtualList.getRemoveByRankOperation(rank, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByRankRange(int rank) {
        operations.add(virtualList.getRemoveByRankRangeOperation(rank, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S removeByRankRange(int rank, int count) {
        operations.add(virtualList.getRemoveByRankRangeOperation(rank, count, ReturnType.NONE));
        isWriteOp.add(true);
        return self();
    }

    public S getByValue(Object value) {
        operations.add(virtualList.getGetByValueOperation(value, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByValueRange(Object startValue, Object endValue) {
        operations.add(virtualList.getGetByValueRangeOperation(startValue, endValue, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByValueList(List<Object> values) {
        operations.add(virtualList.getGetByValueListOperation(values, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByValueRelativeRankRange(Object value, int rank) {
        operations.add(virtualList.getGetByValueRelativeRankRangeOperation(value, rank, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByValueRelativeRankRange(Object value, int rank, int count) {
        operations.add(virtualList.getGetByValueRelativeRankRangeOperation(value, rank, count, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByIndexRange(int index) {
        operations.add(virtualList.getGetByIndexRangeOperation(index, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByIndexRange(int index, int count) {
        operations.add(virtualList.getGetByIndexRangeOperation(index, count, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByRank(int rank) {
        operations.add(virtualList.getGetByRankOperation(rank, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByRankRange(int rank) {
        operations.add(virtualList.getGetByRankRangeOperation(rank, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByRankRange(int rank, int count) {
        operations.add(virtualList.getGetByRankRangeOperation(rank, count, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByKey(Object key) {
        operations.add(virtualList.getGetByKeyOperation(key, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S getByKeyRange(Object startKey, Object endKey) {
        operations.add(virtualList.getGetByKeyRangeOperation(startKey, endKey, ReturnType.ELEMENTS));
        isWriteOp.add(false);
        return self();
    }

    public S get(int index) {
        operations.add(virtualList.getByIndexOperation(index));
        isWriteOp.add(false);
        return self();
    }

    public S size() {
        operations.add(virtualList.getSizeOperation());
        isWriteOp.add(false);
        return self();
    }

    public S clear() {
        operations.add(virtualList.getClearOperation());
        isWriteOp.add(true);
        return self();
    }

    public S asResult() {
        return this.asResultOfType(ReturnType.DEFAULT);
    }

    public S asResultOfType(ReturnType type) {
        if (operations.isEmpty()) {
            throw new AerospikeException("asResult() cannot mark an item as the function result if there are no items to process");
        } else if (this.indexToReturn >= 0) {
            throw new AerospikeException("asResult() can only be called once in a multi operation");
        } else {
            this.indexToReturn = this.operations.size() - 1;
        }
        return self();
    }

    protected int resolveIndexToReturn() {
        int listSize = operations.size();
        if (this.indexToReturn < 0) {
            for (int i = listSize - 1; i >= 0; i--) {
                if (!isWriteOp.get(i)) {
                    return i;
                }
            }
            return listSize - 1;
        }
        return this.indexToReturn;
    }

    @SuppressWarnings("unchecked")
    protected <T> T extractResult(Record record, HydrationContext ctx) {
        if (record == null) {
            return null;
        }

        int idx = resolveIndexToReturn();
        Object rawResult;

        if (operations.size() == 1) {
            rawResult = record.getValue(virtualList.binName);
        } else {
            rawResult = record.operationResult(idx).getValue();
        }

        T result = (T) virtualList.unpackListResult(rawResult, ctx);

        if (result != null) {
            Object object = result;
            if (result instanceof Collection) {
                Collection<T> collection = (Collection<T>) result;
                object = collection.isEmpty() ? null : collection.iterator().next();
            }
            if (object != null) {
                virtualList.mapper.getMappingConverter().resolveDependencies(ctx);
            }
        }
        return result;
    }
}
