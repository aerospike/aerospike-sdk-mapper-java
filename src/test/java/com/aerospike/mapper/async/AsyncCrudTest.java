package com.aerospike.mapper.async;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncCrudTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "asyncCrud")
    public static class Item {
        @AerospikeKey
        public int id;
        public String name;

        public Item() {}

        public Item(@ParamFrom("id") int id, @ParamFrom("name") String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Test
    void saveAsyncReadAsync() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncCrud");

        Item item = new Item(1, "widget");
        mapper.saveAsync(item).get();

        Item read = mapper.readAsync(Item.class, 1).get();
        assertNotNull(read);
        assertEquals("widget", read.name);
    }

    @Test
    void deleteAsyncByClass() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncCrud");

        mapper.save(new Item(2, "gadget"));

        Boolean existed = mapper.deleteAsync(Item.class, 2).get();
        assertTrue(existed);

        Item gone = mapper.readAsync(Item.class, 2).get();
        assertNull(gone);
    }

    @Test
    void deleteAsyncByObject() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncCrud");

        Item item = new Item(3, "doohickey");
        mapper.save(item);

        Boolean existed = mapper.deleteAsync(item).get();
        assertTrue(existed);

        assertNull(mapper.read(Item.class, 3));
    }

    @Test
    void insertAsyncDuplicateKeyCompletesExceptionally() {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncCrud");

        mapper.save(new Item(10, "original"));

        CompletableFuture<Void> future = mapper.insertAsync(new Item(10, "duplicate"));

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ex.getCause());
    }

    @Test
    void readAsyncMissingKeyReturnsNull() throws Exception {
        MappingSession mapper = createMapper();

        Item result = mapper.readAsync(Item.class, 99999).get();
        assertNull(result);
    }

    @Test
    void updateAsyncRoundTrip() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncCrud");

        mapper.save(new Item(20, "before"));
        Item updated = new Item(20, "after");
        mapper.updateAsync(updated).get();

        Item read = mapper.read(Item.class, 20);
        assertNotNull(read);
        assertEquals("after", read.name);
    }

    @Test
    void readFromDigestAsyncRoundTrip() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncCrud");

        Item item = new Item(30, "digest-test");
        mapper.save(item);

        byte[] digest = mapper.getEngine().getRecordKey(item).digest;
        Item read = mapper.readFromDigestAsync(Item.class, digest).get();
        assertNotNull(read);
        assertEquals("digest-test", read.name);
    }
}
