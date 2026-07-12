package com.aerospike.mapper.async;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncBatchReadTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "asyncBatch")
    public static class Entry {
        @AerospikeKey
        public int id;
        public String value;

        public Entry() {}

        public Entry(@ParamFrom("id") int id, @ParamFrom("value") String value) {
            this.id = id;
            this.value = value;
        }
    }

    @Test
    void batchReadOrderMatchesInputKeys() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncBatch");

        mapper.save(new Entry(1, "a"));
        mapper.save(new Entry(2, "b"));
        mapper.save(new Entry(3, "c"));

        Integer[] keys = {3, 1, 2};
        List<Entry> results = mapper.readAsync(Entry.class, keys).get();

        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals("c", results.get(0).value);
        assertEquals("a", results.get(1).value);
        assertEquals("b", results.get(2).value);
    }

    @Test
    void batchReadMissingKeysReturnNull() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncBatch");

        mapper.save(new Entry(10, "exists"));

        Integer[] keys = {10, 999, 10};
        List<Entry> results = mapper.readAsync(Entry.class, keys).get();

        assertNotNull(results);
        assertEquals(3, results.size());
        assertNotNull(results.get(0));
        assertNull(results.get(1));
        assertNotNull(results.get(2));
    }
}
