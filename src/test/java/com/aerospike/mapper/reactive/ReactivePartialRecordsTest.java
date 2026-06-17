package com.aerospike.mapper.reactive;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactivePartialRecordsTest extends ReactiveAeroMapperBaseTest {
    @AerospikeRecord(namespace = "test", set = "testSet")
    public static class DataClass {
        @AerospikeKey
        int a;
        int b;
        int c;
        int d;
        int e;
    }

    @Test
    public void testPartialSave() throws Exception {
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();

        DataClass dataClass = new DataClass();
        dataClass.a = 1;
        dataClass.b = 2;
        dataClass.c = 3;
        dataClass.d = 4;
        dataClass.e = 5;

        reactiveMapper.save(dataClass).get();

        Key key = new Key("test", "testSet", 1);
        Record record = readRawRecord(key);
        assertEquals(5, record.bins.size());
        assertEquals(3, record.getInt("c"));

        dataClass.c = 9;
        dataClass.e = 11;
        reactiveMapper.save(dataClass, "a", "c", "e").get();

        record = readRawRecord(key);
        assertEquals(3, record.bins.size());
        assertEquals(1, record.getInt("a"));
        assertEquals(9, record.getInt("c"));
        assertEquals(11, record.getInt("e"));

        dataClass.c = 99;
        reactiveMapper.update(dataClass, "c", "d").get();

        record = readRawRecord(key);
        assertEquals(4, record.bins.size());
        assertEquals(1, record.getInt("a"));
        assertEquals(99, record.getInt("c"));
        assertEquals(4, record.getInt("d"));
        assertEquals(11, record.getInt("e"));
    }
}
