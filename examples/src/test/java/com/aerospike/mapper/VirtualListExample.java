package com.aerospike.mapper;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.virtuallist.ReturnType;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Preserves the original advanced virtual-list operation chain.
 *
 * <p>The newer catalog example focuses on append/range basics; this scenario also
 * demonstrates single operations, removals, typed operation results, and MAP/LIST
 * embedded encoding.</p>
 */
public class VirtualListExample implements RunnableExample {
    public static final String ITEM_SET = "sdk_mapper_examples_legacy_virtual_item";
    public static final String CONTAINER_SET = "sdk_mapper_examples_legacy_virtual_container";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = ITEM_SET)
    public static class Item {
        @AerospikeKey
        private int id;
        private Date due;
        private String desc;

        public Item(int id, Date due, String desc) {
            super();
            this.id = id;
            this.due = due;
            this.desc = desc;
        }

        public Item() {
        }
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = CONTAINER_SET)
    public static class Container {
        @AerospikeKey
        private int id;
        private String name;

        // The map layer uses Item.id as the collection key; LIST controls the compact
        // positional representation of each Item value.
        @AerospikeEmbed(type = EmbedType.MAP, elementType = EmbedType.LIST)
        private final List<Item> items;

        public Container() {
            this.items = new ArrayList<>();
        }
    }

    @Override
    public void run(ExampleContext context) {
        Container container = new Container();
        container.id = 1;
        container.name = "container";

        container.items.add(new Item(100, new Date(), "Item 1"));
        container.items.add(new Item(200, new Date(), "Item 2"));
        container.items.add(new Item(300, new Date(), "Item 3"));
        container.items.add(new Item(400, new Date(), "Item 4"));

        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();
        mapper.save(container);

        VirtualList<Item> list = mapper.asBackedList(container, "items", Item.class);

        // A direct append changes only the stored bin. It intentionally does not mutate
        // container.items, which remains the caller's ordinary in-memory collection.
        list.append(new Item(500, new Date(), "Item5"));

        /*
         * Multi-operations execute in order in one server request. Remove key 200 and
         * then remove the half-open key range [20, 350). These are write-only
         * operations, so their end() result is intentionally ignored. The following
         * size and range reads demonstrate the updated collection.
         */
        list.beginMultiOperation()
                .append(new Item(600, new Date(), "Item6"))
                .removeByKey(200)
                .removeByKeyRange(20, 350)
                .end();

        long count = list.size();
        List<Item> selected = list.getByKeyRange(100, 450, ReturnType.ELEMENTS);
        System.out.println(count);
        equal(3L, count, "stored item count after removals");
        equal(1, selected.size(), "one item should remain in the requested key range");
    }
}
