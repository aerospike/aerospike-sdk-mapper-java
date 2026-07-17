package com.aerospike.mapper.async;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.AerospikeReference.ReferenceType;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncReferenceReadTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "asyncRefItem")
    public static class RefItem {
        @AerospikeKey
        public int id;
        public String desc;

        public RefItem() {}

        public RefItem(int id, String desc) {
            this.id = id;
            this.desc = desc;
        }
    }

    @AerospikeRecord(namespace = "test", set = "asyncRefContainer")
    public static class RefContainer {
        @AerospikeKey
        public int id;
        public String name;
        @AerospikeReference(type = ReferenceType.ID)
        public List<RefItem> items;

        public RefContainer() {
            this.items = new ArrayList<>();
        }
    }

    @Test
    void readAsyncResolvesObjectReferences() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncRefItem");
        truncateSet("test", "asyncRefContainer");

        RefItem item1 = new RefItem(100, "Item A");
        RefItem item2 = new RefItem(200, "Item B");
        RefItem item3 = new RefItem(300, "Item C");
        mapper.save(item1);
        mapper.save(item2);
        mapper.save(item3);

        RefContainer container = new RefContainer();
        container.id = 1;
        container.name = "async-container";
        container.items.add(item1);
        container.items.add(item2);
        container.items.add(item3);
        mapper.save(container);

        RefContainer read = mapper.readAsync(RefContainer.class, 1).get().orElse(null);
        assertNotNull(read);
        assertEquals("async-container", read.name);
        assertEquals(3, read.items.size());
        assertEquals(100, read.items.get(0).id);
        assertEquals("Item A", read.items.get(0).desc);
        assertEquals(200, read.items.get(1).id);
        assertEquals("Item B", read.items.get(1).desc);
        assertEquals(300, read.items.get(2).id);
        assertEquals("Item C", read.items.get(2).desc);
    }

    @Test
    void batchReadAsyncResolvesReferences() throws Exception {
        MappingSession mapper = createMapper();
        truncateSet("test", "asyncRefItem");
        truncateSet("test", "asyncRefContainer");

        RefItem item1 = new RefItem(10, "Batch-A");
        RefItem item2 = new RefItem(20, "Batch-B");
        mapper.save(item1);
        mapper.save(item2);

        RefContainer c1 = new RefContainer();
        c1.id = 1;
        c1.name = "batch-1";
        c1.items.add(item1);
        mapper.save(c1);

        RefContainer c2 = new RefContainer();
        c2.id = 2;
        c2.name = "batch-2";
        c2.items.add(item1);
        c2.items.add(item2);
        mapper.save(c2);

        List<RefContainer> results = mapper.readAsync(RefContainer.class, new Object[]{1, 2}).get();
        assertNotNull(results);
        assertEquals(2, results.size());

        RefContainer r1 = results.get(0);
        assertNotNull(r1);
        assertEquals("batch-1", r1.name);
        assertEquals(1, r1.items.size());
        assertEquals("Batch-A", r1.items.get(0).desc);

        RefContainer r2 = results.get(1);
        assertNotNull(r2);
        assertEquals("batch-2", r2.name);
        assertEquals(2, r2.items.size());
    }
}
