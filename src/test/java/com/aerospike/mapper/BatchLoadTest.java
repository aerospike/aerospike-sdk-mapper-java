package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BatchLoadTest extends MapperBaseTest {

    private static final String DATA_BIN = "data";
    private final B[] bees = new B[100];
    private final A[] as = new A[10];

    @AerospikeRecord(namespace = "test", set = "batchB")
    public static class B {
        @AerospikeKey
        public int id;
        public String name;

        public B(@ParamFrom("id") int id, @ParamFrom("name") String name) {
            this.id = id;
            this.name = name;
        }
    }

    @AerospikeRecord(namespace = "test", set = "batchA")
    public static class A {
        @AerospikeKey
        public int id;
        public String name;
        public List<B> data;

        public A(int id, String name) {
            this.id = id;
            this.name = name;
            data = new ArrayList<>();
        }

        public void setBList(List<B> bees) {
            data = bees;
        }

        public A() {
        }
    }

    @BeforeAll
    public void populateStaticData() {
        for (int i = 0; i < 100; i++) {
            bees[i] = new B(i, "B-" + i);
        }

        for (int i = 0; i < 10; i++) {
            as[i] = new A(100 + i, "A-" + i);
            as[i].setBList(Arrays.asList(
                    Arrays.copyOfRange(bees, i * 10, (i + 1) * 10)));
        }
    }

    @AfterAll
    public void clear() {
        session.truncate(DataSet.of("test", "batchA"));
        session.truncate(DataSet.of("test", "batchB"));
    }

    private MappingSession populate() {
        session.truncate(DataSet.of("test", "batchA"));
        session.truncate(DataSet.of("test", "batchB"));

        MappingSession mapper = createMapper();

        mapper.save((Object[]) bees);
        mapper.save((Object[]) as);

        return mapper;
    }

    @Test
    public void testBatchLoad() {
        MappingSession mapper = populate();

        B resultB = mapper.read(B.class, bees[1].id);
        compare(bees[1], resultB);

        A resultA = mapper.read(A.class, as[1].id);
        compare(as[1], resultA);

        Integer[] ids = new Integer[6];
        ids[0] = as[4].id;
        ids[1] = as[7].id;
        ids[2] = as[5].id;
        ids[3] = as[0].id;
        ids[4] = as[1].id;
        ids[5] = 3000;

        A[] results = mapper.read(A.class, ids);
        compare(results[0], as[4]);
        compare(results[1], as[7]);
        compare(results[2], as[5]);
        compare(results[3], as[0]);
        compare(results[4], as[1]);
        compare(results[5], null);
    }

    /**
     * Validates that MappingSession (as a Session) supports batch CDT operations
     * on mapper-managed sets. Mapper-level batch read and object conversion are
     * covered by {@link #testBatchLoad()}; CDT operation results have a different
     * structure than stored bins and cannot go through convertToObject.
     */
    @Test
    public void testBatchLoadWithOperations() {
        MappingSession mapper = populate();

        B resultB = mapper.read(B.class, bees[1].id);
        compare(bees[1], resultB);

        A resultA = mapper.read(A.class, as[1].id);
        compare(as[1], resultA);

        String ns = mapper.getEngine().getNamespace(A.class);
        String set = mapper.getEngine().getSet(A.class);

        int[] asIndices = {4, 7, 5, 0, 1};
        List<Key> keys = new ArrayList<>(6);
        for (int idx : asIndices) {
            keys.add(new Key(ns, set, Value.get(as[idx].id)));
        }
        keys.add(new Key(ns, set, Value.get(3000)));

        RecordResult[] results = new RecordResult[keys.size()];
        try (RecordStream stream = mapper.query(keys)
                .bin(DATA_BIN).listSize()
                .bin(DATA_BIN).onListIndex(-1).getValues()
                .includeMissingKeys()
                .execute()) {
            while (stream.hasNext()) {
                RecordResult rr = stream.next();
                results[rr.index()] = rr;
            }
        }

        for (int i = 0; i < asIndices.length; i++) {
            assertNotNull(results[i].recordOrNull(), "Expected record for as[" + asIndices[i] + "]");
            Record record = results[i].recordOrThrow();
            assertEquals(10L, record.operationResult(0).getLong());
            int lastBId = as[asIndices[i]].data.get(as[asIndices[i]].data.size() - 1).id;
            assertEquals((long) lastBId, record.operationResult(1).getLong());
        }

        assertNotNull(results[5], "Expected result entry for missing key");
        assertNull(results[5].recordOrNull());
    }
}
