package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;

public class MappingSessionTransactionTest extends MapperBaseTest {

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

        mapper.doInMappingTransaction(txn -> {
            // A real operation is needed so the transaction has a namespace for commit.
            txn.save(new TxnEntity(999, "dummy"));
            assertThrows(UnsupportedOperationException.class, () ->
                    txn.sessionFor(Behavior.DEFAULT));
        });
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

    @Test
    public void testTransactionalDelete() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();

        mapper.save(new TxnEntity(10, "to-delete"));
        assertNotNull(mapper.read(TxnEntity.class, 10));

        mapper.doInMappingTransaction(txn -> {
            txn.delete(TxnEntity.class, 10);
        });

        assertNull(mapper.read(TxnEntity.class, 10));
    }

    @AerospikeRecord(namespace = "test", set = "txnQueryTest")
    public static class TxnQueryEntity {
        @AerospikeKey
        public int id;
        public String name;
        public int score;

        public TxnQueryEntity() {}

        public TxnQueryEntity(@ParamFrom("id") int id, @ParamFrom("name") String name,
                              @ParamFrom("score") int score) {
            this.id = id;
            this.name = name;
            this.score = score;
        }
    }

    @Test
    public void testTransactionalQuery() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();
        truncateSet("test", "txnQueryTest");

        mapper.save(new TxnQueryEntity(1, "Alice", 90));
        mapper.save(new TxnQueryEntity(2, "Bob", 80));

        List<TxnQueryEntity> results = mapper.doInMappingTransactionReturning(txn -> {
            txn.save(new TxnQueryEntity(3, "Carol", 70));
            return txn.query(TxnQueryEntity.class);
        });

        assertNotNull(results);
        assertTrue(results.size() >= 2, "Should see at least the pre-existing records");
    }

    @Test
    public void testTransactionalScan() {
        assumeTrue(enterprise, "Transactions require Aerospike Enterprise Edition");
        MappingSession mapper = createMapper();
        truncateSet("test", "txnQueryTest");

        mapper.save(new TxnQueryEntity(1, "Dan", 60));

        List<TxnQueryEntity> results = mapper.doInMappingTransactionReturning(txn -> {
            txn.save(new TxnQueryEntity(2, "Eve", 50));
            return txn.scan(TxnQueryEntity.class);
        });

        assertNotNull(results);
        assertTrue(results.size() >= 1, "Should see at least the pre-existing record");
    }
}
