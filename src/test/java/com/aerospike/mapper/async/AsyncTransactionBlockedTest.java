package com.aerospike.mapper.async;

import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIf("isEnterprise")
public class AsyncTransactionBlockedTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "asyncTxn")
    public static class Dummy {
        @AerospikeKey
        public int id;
        public String name;

        public Dummy() {}

        public Dummy(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static boolean isEnterprise() {
        return enterprise;
    }

    @Test
    void saveAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.saveAsync(new Dummy(1, "a"))
            )
        );
    }

    @Test
    void readAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.readAsync(Dummy.class, 1)
            )
        );
    }

    @Test
    void deleteAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.deleteAsync(Dummy.class, 1)
            )
        );
    }

    @Test
    void queryPublisherThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.queryPublisher(Dummy.class, (String) null)
            )
        );
    }

    @Test
    void queryPublisherWithExpressionThrowsInTransaction() {
        MappingSession mapper = createMapper();
        Expression filter = Exp.build(Exp.val(true));

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.queryPublisher(Dummy.class, filter)
            )
        );
    }

    @Test
    void scanPublisherThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.scanPublisher(Dummy.class)
            )
        );
    }

    @Test
    void asBackedListAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();
        mapper.save(new Dummy(99, "test"));

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.asBackedListAsync(Dummy.class, 99, "name", String.class)
            )
        );
    }

    @Test
    void insertAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.insertAsync(new Dummy(1, "a"))
            )
        );
    }

    @Test
    void updateAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.updateAsync(new Dummy(1, "a"))
            )
        );
    }

    @Test
    void readFromDigestAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.readFromDigestAsync(Dummy.class, new byte[20])
            )
        );
    }

    @Test
    void batchReadAsyncThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.readAsync(Dummy.class, new Object[]{1, 2})
            )
        );
    }

    @Test
    void deleteAsyncByObjectThrowsInTransaction() {
        MappingSession mapper = createMapper();

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.deleteAsync(new Dummy(1, "a"))
            )
        );
    }

    @Test
    void asBackedListAsyncByObjectThrowsInTransaction() {
        MappingSession mapper = createMapper();
        Dummy dummy = new Dummy(98, "test");
        mapper.save(dummy);

        assertThrows(UnsupportedOperationException.class, () ->
            mapper.doInMappingTransaction(txn ->
                txn.asBackedListAsync(dummy, "name", String.class)
            )
        );
    }
}
