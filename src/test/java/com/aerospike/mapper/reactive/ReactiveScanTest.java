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

public class ReactiveScanTest extends ReactiveAeroMapperBaseTest {
    private static final DataSet DATA_SET = DataSet.of("test", "testScan");
    private static final String NAME_INDEX = "mapper_reactive_scan_test_name_idx";

    @BeforeAll
    public static void createNameIndex() {
        recreateIndex(DATA_SET, NAME_INDEX, "name", IndexType.STRING);
    }

    @AfterAll
    public static void dropNameIndex() {
        dropIndexIfPresent(DATA_SET, NAME_INDEX);
    }

    @AerospikeRecord(namespace = "test", set = "testScan")
    public static class Person {
        @AerospikeKey
        private final int id;
        private final String name;
        private final int age;

        public Person(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("age") int age) {
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
    }

    private ReactiveAeroMapper populate() throws Exception {
        session.truncate(DATA_SET);
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(new Person(1, "Tim", 312)).get();
        reactiveMapper.save(new Person(2, "Bob", 44)).get();
        reactiveMapper.save(new Person(3, "Sue", 56)).get();
        reactiveMapper.save(new Person(4, "Rob", 23)).get();
        reactiveMapper.save(new Person(5, "Jim", 32)).get();
        reactiveMapper.save(new Person(6, "Bob", 78)).get();
        return reactiveMapper;
    }

    @Test
    public void scanTest() throws Exception {
        ReactiveAeroMapper reactiveMapper = populate();
        IAeroMapper syncMapper = reactiveMapper.asMapper();
        List<Person> results = syncMapper.scan(Person.class);
        assert results != null;
        assertEquals(6, results.size());
    }

    @Test
    public void scanTestWithFilter() throws Exception {
        assumeAelSupported();
        ReactiveAeroMapper reactiveMapper = populate();
        IAeroMapper syncMapper = reactiveMapper.asMapper();
        // TODO: Adapt for SDK - ScanPolicy with filterExp replaced by AEL query expression
        List<Person> results = syncMapper.query(Person.class, "$.name == \"Bob\"");
        assert results != null;
        assertEquals(2, results.size());
    }
}
