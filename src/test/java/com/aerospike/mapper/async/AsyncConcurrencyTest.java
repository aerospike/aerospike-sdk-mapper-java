package com.aerospike.mapper.async;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.AerospikeReference.ReferenceType;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that concurrent async reads with reference resolution
 * do not corrupt each other's HydrationContext.
 */
public class AsyncConcurrencyTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "concDetail")
    public static class Detail {
        @AerospikeKey
        public int id;
        public String info;

        public Detail() {}

        public Detail(@ParamFrom("id") int id, @ParamFrom("info") String info) {
            this.id = id;
            this.info = info;
        }
    }

    @AerospikeRecord(namespace = "test", set = "concParent")
    public static class Parent {
        @AerospikeKey
        public int id;
        public String name;
        @AerospikeReference(type = ReferenceType.ID)
        public Detail detail;

        public Parent() {}

        public Parent(int id, String name, Detail detail) {
            this.id = id;
            this.name = name;
            this.detail = detail;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void concurrentReadAsyncDoNotInterfere() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "concDetail");
        truncateSet("test", "concParent");

        int count = 20;
        for (int i = 1; i <= count; i++) {
            Detail d = new Detail(i, "detail-" + i);
            mapper.save(d);
            Parent p = new Parent(i, "parent-" + i, d);
            mapper.save(p);
        }

        List<CompletableFuture<Parent>> futures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            futures.add(mapper.readAsync(Parent.class, i));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get();

        for (int i = 0; i < count; i++) {
            Parent p = futures.get(i).get();
            int expectedId = i + 1;
            assertNotNull(p, "Parent " + expectedId + " should not be null");
            assertEquals(expectedId, p.id);
            assertEquals("parent-" + expectedId, p.name);
            assertNotNull(p.detail, "Detail for parent " + expectedId + " should be resolved");
            assertEquals(expectedId, p.detail.id);
            assertEquals("detail-" + expectedId, p.detail.info);
        }
    }

    @Test
    void concurrentSaveAsyncDoNotInterfere() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "concDetail");

        int count = 20;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            futures.add(mapper.saveAsync(new Detail(i, "concurrent-" + i)));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        for (int i = 1; i <= count; i++) {
            Detail d = mapper.read(Detail.class, i);
            assertNotNull(d);
            assertEquals("concurrent-" + i, d.info);
        }
    }
}
