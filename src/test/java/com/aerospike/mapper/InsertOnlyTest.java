package com.aerospike.mapper;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InsertOnlyTest extends MapperBaseTest {

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
        session.delete(new Key("test", "testSet", 1)).execute().close();
    }

    @Test
    public void testInsertOnly() {
        MappingSession mapper = createMapper();

        DataClass dataClass = new DataClass();
        dataClass.a = 1;
        dataClass.b = 2;
        dataClass.c = 3;
        dataClass.d = 4;
        dataClass.e = 5;

        // Insert
        mapper.insert(dataClass);

        Record record = readRawRecord(new Key("test", "testSet", 1));
        assertEquals(5, record.bins.size());
        assertEquals(3, record.getInt("c"));

        // Try to insert again and get an exception
        dataClass.c = 9;
        dataClass.e = 11;
        assertThrows(AerospikeException.class, () -> mapper.insert(dataClass, "a", "c", "e"));
    }
}
