package com.aerospike.mapper.virtuallist;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.ExampleContext;
import com.aerospike.mapper.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import java.util.ArrayList;
import java.util.List;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Demonstrates a {@link VirtualList}: a mapper view that performs Aerospike collection
 * data type operations directly against an embedded list in an existing record.
 */
public final class VirtualListOperationsExample implements RunnableExample {
    public static final String SET = "sdk_mapper_examples_virtual_list";

    // Item is embedded and therefore does not need its own namespace or set.
    @AerospikeRecord
    public static class Item {
        @AerospikeKey public int id;
        public String name;
        public Item() {}
        public Item(int id, String name) { this.id = id; this.name = name; }
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = SET)
    public static class Cart {
        @AerospikeKey public String id;

        // MAP/MAP stores the collection as a key-ordered Aerospike map whose values are
        // self-describing embedded maps. Item.id supplies each map key.
        @AerospikeEmbed(
                type = AerospikeEmbed.EmbedType.MAP,
                elementType = AerospikeEmbed.EmbedType.MAP)
        public List<Item> items = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(ExampleContext context) {
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();
        Cart cart = new Cart();
        cart.id = "cart-1";
        cart.items.add(new Item(100, "one hundred"));
        cart.items.add(new Item(200, "two hundred"));

        // The owning record must exist before a VirtualList can operate on its bin.
        mapper.save(cart);

        // @start region="virtual-list"
        // asBackedList() binds the Cart record and "items" bin to mapped Item values.
        VirtualList<Item> items = mapper.asBackedList(cart, "items", Item.class);

        // beginMultiOperation() groups both operations into one server request. The range
        // is half-open: keys 150 (inclusive) through 350 (exclusive).
        List<Item> selected = (List<Item>) items.beginMultiOperation()
                .append(new Item(300, "three hundred"))
                .getByKeyRange(150, 350)
                .end();
        // @end region="virtual-list"

        equal(List.of(200, 300), selected.stream().map(item -> item.id).toList(),
                "key-range results");

        // Server-backed operations do not mutate cart.items in memory. Call read() again
        // when application code needs a refreshed local collection.
        equal(2, cart.items.size(), "in-memory list is unchanged");
        equal(3, mapper.read(Cart.class, "cart-1").items.size(), "stored list is updated");
    }
}
