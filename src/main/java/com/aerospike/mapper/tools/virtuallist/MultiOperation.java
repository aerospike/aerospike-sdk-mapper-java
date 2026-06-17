package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;

public class MultiOperation<E> extends AbstractMultiOperation<E, MultiOperation<E>> {

    MultiOperation(BaseVirtualList<E> virtualList) {
        super(virtualList);
    }

    public Object end() {
        return end(null);
    }

    @SuppressWarnings("unchecked")
    public <T> T end(Class<T> resultType) {
        if (operations.isEmpty()) {
            return null;
        }

        Operation[] ops = operations.toArray(new Operation[0]);
        Record record = virtualList.getSession().upsert(virtualList.key)
                .appendOperations(ops)
                .execute()
                .getFirstRecord();

        return extractResult(record);
    }
}
