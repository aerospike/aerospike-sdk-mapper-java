package com.aerospike.mapper.reactive;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReactiveBatchLoadTest extends ReactiveAeroMapperBaseTest {

    private static final String DATA_BIN = "data";
    private final B[] bees = new B[100];
    private final A[] as = new A[10];

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
        // TODO: Adapt truncate for SDK - needs cluster/session truncate API
    }

    @SneakyThrows
    private ReactiveAeroMapper populate() {
        // TODO: Adapt truncate for SDK - needs cluster/session truncate API
        Thread.sleep(1000);

        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();

        for (B b : bees) {
            reactiveMapper.save(b).get();
        }
        for (A a : as) {
            reactiveMapper.save(a).get();
        }

        return reactiveMapper;
    }

    @Test
    void testBatchLoad() throws Exception {
        ReactiveAeroMapper reactiveMapper = populate();

        B resultB = reactiveMapper.read(B.class, bees[1].id).get();
        compare(bees[1], resultB);

        A resultA = reactiveMapper.read(A.class, as[1].id).get();
        compare(as[1], resultA);

        Integer[] ids = new Integer[6];
        ids[0] = as[4].id;
        ids[1] = as[7].id;
        ids[2] = as[5].id;
        ids[3] = as[0].id;
        ids[4] = as[1].id;
        ids[5] = 3000;

        List<A> expected = Stream.of(as[4], as[7], as[5], as[0], as[1])
                .sorted(Comparator.nullsFirst(A::compareTo))
                .collect(Collectors.toList());

        List<A> resultsList = reactiveMapper.read(A.class, ids).get();

        assertNotNull(resultsList);
        resultsList.removeIf(java.util.Objects::isNull);
        resultsList.sort(Comparator.nullsFirst(A::compareTo));
        compare(expected, resultsList);
    }

    @Test
    void testBatchLoadWithOperations() {
        // TODO: Adapt for SDK - batch read with Operations not supported in new reactive API
    }

    @AerospikeRecord(namespace = "test", set = "batchB")
    public static class B implements Comparable<B> {
        @AerospikeKey
        public int id;
        public String name;

        public B(@ParamFrom("id") int id, @ParamFrom("name") String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public int compareTo(B o) {
            if (o == null) return 1;
            return Integer.compare(id, o.id);
        }
    }

    @AerospikeRecord(namespace = "test", set = "batchA")
    public static class A implements Comparable<A> {
        @AerospikeKey
        public int id;
        public String name;
        public List<B> data;

        public A(int id, String name) {
            this.id = id;
            this.name = name;
            data = new ArrayList<>();
        }

        public A() {
        }

        public void setBList(List<B> bees) {
            data = bees;
        }

        @Override
        public int compareTo(A o) {
            if (o == null) return 1;
            return Integer.compare(id, o.id);
        }
    }
}
