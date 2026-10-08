package com.aerospike.mapper.reactive;

import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.query.IndexType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.IAeroMapper;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveQueryTest extends ReactiveAeroMapperBaseTest {
    private static final DataSet DATA_SET = DataSet.of("test", "testScan");
    private static final String AGE_INDEX = "mapper_reactive_query_test_age_idx";

    @BeforeAll
    public static void createAgeIndex() {
        recreateIndex(DATA_SET, AGE_INDEX, "age", IndexType.INTEGER);
    }

    @AfterAll
    public static void dropAgeIndex() {
        dropIndexIfPresent(DATA_SET, AGE_INDEX);
    }

    @AerospikeRecord(namespace = "test", set = "testScan")
    public static class A {
        @AerospikeKey
        private final int id;
        private final String name;
        private final int age;

        public A(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("age") int age) {
            super();
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return String.format("id:%d, name:%s, age:%d", id, name, age);
        }
    }

    private ReactiveAeroMapper populate() throws Exception {
        session.truncate(DATA_SET);
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(new A(1, "Tim", 312)).get();
        reactiveMapper.save(new A(2, "Bob", 44)).get();
        reactiveMapper.save(new A(3, "Sue", 56)).get();
        reactiveMapper.save(new A(4, "Rob", 23)).get();
        reactiveMapper.save(new A(5, "Jim", 32)).get();
        reactiveMapper.save(new A(6, "Bob", 78)).get();
        reactiveMapper.save(new A(7, "Fred", 23)).get();
        reactiveMapper.save(new A(8, "Wilma", 11)).get();
        reactiveMapper.save(new A(9, "Barney", 54)).get();
        reactiveMapper.save(new A(10, "Steve", 72)).get();
        reactiveMapper.save(new A(11, "Bam Bam", 19)).get();
        reactiveMapper.save(new A(12, "Betty", 34)).get();
        reactiveMapper.save(new A(13, "Del", 7)).get();
        reactiveMapper.save(new A(14, "Khon", 98)).get();
        reactiveMapper.save(new A(15, "Dave", 21)).get();
        reactiveMapper.save(new A(16, "Mike", 32)).get();
        reactiveMapper.save(new A(17, "Darren", 14)).get();
        reactiveMapper.save(new A(18, "Lucy", 45)).get();
        reactiveMapper.save(new A(19, "Gertrude", 36)).get();
        reactiveMapper.save(new A(20, "Lucinda", 63)).get();
        return reactiveMapper;
    }

    @Test
    public void queryTest() throws Exception {
        ReactiveAeroMapper reactiveMapper = populate();
        IAeroMapper syncMapper = reactiveMapper.asMapper();
        // TODO: Adapt for SDK - Filter.range replaced by AEL expression; verify AEL syntax
        List<A> results = syncMapper.query(A.class, "$.age >= 30 and $.age <= 54");
        assert results != null;
        assertEquals(7, results.size());
    }
}
