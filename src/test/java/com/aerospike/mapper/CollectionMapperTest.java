package com.aerospike.mapper;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;
import com.aerospike.mapper.tools.virtuallist.VirtualList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CollectionMapperTest extends MapperBaseTest {
    @AerospikeRecord
    public static class CollectionElement {
        @AerospikeKey
        public int id;
        public String name;
        public long date;

        public CollectionElement(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("date") long date) {
            super();
            this.id = id;
            this.name = name;
            this.date = date;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public long getDate() {
            return date;
        }

        @Override
        public String toString() {
            return String.format("{id=%d, name=%s, date=%d}", id, name, date);
        }
    }

    @AerospikeRecord(namespace = "test", set = "testSet1")
    public static class Collection {
        @AerospikeEmbed(type = EmbedType.MAP, elementType = EmbedType.LIST)
        public List<CollectionElement> elements;

        @AerospikeKey
        public int id;

        public Collection() {
            elements = new ArrayList<>();
        }
    }

    @Test
    public void test() {
        Collection collection = new Collection();
        collection.id = 1;

        collection.elements.add(new CollectionElement(102, "bob", 12345));
        collection.elements.add(new CollectionElement(101, "joe", 23456));
        collection.elements.add(new CollectionElement(100, "sue", 34567));

        MappingSession mapper = createMapper();
        mapper.save(collection);

        VirtualList<CollectionElement> list = mapper.asBackedList(collection, "elements", CollectionElement.class);
        @SuppressWarnings("unchecked")
        List<CollectionElement> results = (List<CollectionElement>) list.beginMultiOperation()
                .append(new CollectionElement(103, "tom", 45678))
                .append(new CollectionElement(104, "tim", 22222))
                .append(new CollectionElement(105, "sam", 33333))
                .append(new CollectionElement(106, "rob", 44444))
                .getByKeyRange(101, 105)
                .end();

        assertNotNull(results);
        assertFalse(results.isEmpty());
    }
}
