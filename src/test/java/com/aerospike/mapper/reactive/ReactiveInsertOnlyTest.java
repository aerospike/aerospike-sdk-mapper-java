package com.aerospike.mapper.reactive;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReactiveInsertOnlyTest extends ReactiveAeroMapperBaseTest {

    @AerospikeRecord(namespace = "test", set = "testSet")
    public static class DataClass {
        @AerospikeKey
        int a;
        int b;
        int c;
        int d;
        int e;
    }

    @BeforeEach
    public void setup() {
        Key key = new Key("test", "testSet", 1);
        session.delete(key).execute().close();
    }

    @Test
    public void testInsertOnly() throws Exception {
        ReactiveAeroMapper mapper = new ReactiveAeroMapper.Builder(session).build();

        DataClass dataClass = new DataClass();
        dataClass.a = 1;
        dataClass.b = 2;
        dataClass.c = 3;
        dataClass.d = 4;
        dataClass.e = 5;

        mapper.insert(dataClass).get();

        Key key = new Key("test", "testSet", 1);
        Record record = readRawRecord(key);
        assertEquals(5, record.bins.size());
        assertEquals(3, record.getInt("c"));

        dataClass.c = 9;
        dataClass.e = 11;
        assertThrows(CompletionException.class, () -> mapper.insert(dataClass, "a", "c", "e").join());
    }
}
