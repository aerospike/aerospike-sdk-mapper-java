package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;

public class MappingSessionTransactionTest extends AeroMapperBaseTest {

    @AerospikeRecord(namespace = "test", set = "txnTest")
    public static class TxnEntity {
        @AerospikeKey
        public int id;
        public String name;

        public TxnEntity() {}

        public TxnEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Test
    public void testTransactionalSaveAndRead() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();
        mapper.delete(TxnEntity.class, 1);

        mapper.doInMappingTransaction(txn -> {
            txn.save(new TxnEntity(1, "Alice"));
        });

        TxnEntity result = mapper.read(TxnEntity.class, 1);
        assertNotNull(result);
        assertEquals("Alice", result.name);
    }

    @Test
    public void testTransactionalRollbackOnException() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();
        mapper.delete(TxnEntity.class, 2);

        assertThrows(RuntimeException.class, () ->
                mapper.doInMappingTransaction(txn -> {
                    txn.save(new TxnEntity(2, "Bob"));
                    throw new RuntimeException("force rollback");
                }));

        TxnEntity result = mapper.read(TxnEntity.class, 2);
        assertNull(result);
    }

    @Test
    public void testTransactionReturning() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();
        mapper.delete(TxnEntity.class, 3);
        mapper.save(new TxnEntity(3, "Charlie"));

        TxnEntity result = mapper.doInMappingTransactionReturning(txn ->
                txn.read(TxnEntity.class, 3));

        assertNotNull(result);
        assertEquals("Charlie", result.name);
    }

    @Test
    public void testSessionForThrowsInsideTransaction() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();

        mapper.doInMappingTransaction(txn ->
                assertThrows(UnsupportedOperationException.class, () ->
                        txn.sessionFor(Behavior.DEFAULT)));
    }

    @Test
    public void testSessionForReturnsMappingSessionWithSameEngine() {
        MappingSession mapper = createMapper();

        MappingSession derived = mapper.sessionFor(Behavior.DEFAULT);

        assertNotNull(derived);
        assertNotSame(mapper, derived);
        assertInstanceOf(MappingSession.class, derived);
        assertSame(mapper.getEngine(), derived.getEngine());
    }
}
