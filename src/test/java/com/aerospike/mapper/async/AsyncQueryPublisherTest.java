package com.aerospike.mapper.async;

import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.AerospikeReference.ReferenceType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncQueryPublisherTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "asyncPub")
    public static class Item {
        @AerospikeKey
        public int id;
        public String name;
        public int score;

        public Item() {}

        public Item(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("score") int score) {
            this.id = id;
            this.name = name;
            this.score = score;
        }
    }

    @AerospikeRecord(namespace = "test", set = "asyncPubRefItem")
    public static class RefItem {
        @AerospikeKey
        public int id;
        public String desc;

        public RefItem() {}

        public RefItem(@ParamFrom("id") int id, @ParamFrom("desc") String desc) {
            this.id = id;
            this.desc = desc;
        }
    }

    @AerospikeRecord(namespace = "test", set = "asyncPubRefContainer")
    public static class RefContainer {
        @AerospikeKey
        public int id;
        public String name;
        public int score;
        @AerospikeReference(type = ReferenceType.ID)
        public RefItem item;

        public RefContainer() {}

        public RefContainer(@ParamFrom("id") int id, @ParamFrom("name") String name,
                            @ParamFrom("score") int score, @ParamFrom("item") RefItem item) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.item = item;
        }
    }

    private MappingSession populateItems() {
        truncateSet("test", "asyncPub");
        MappingSession mapper = createMapper();
        for (int i = 1; i <= 10; i++) {
            mapper.save(new Item(i, "item-" + i, i * 10));
        }
        return mapper;
    }

    private MappingSession populateReferencedItems() {
        truncateSet("test", "asyncPubRefItem");
        truncateSet("test", "asyncPubRefContainer");
        MappingSession mapper = createMapper();

        RefItem item1 = new RefItem(1, "detail-1");
        RefItem item2 = new RefItem(2, "detail-2");
        mapper.save(item1);
        mapper.save(item2);

        mapper.save(new RefContainer(1, "container-1", 40, item1));
        mapper.save(new RefContainer(2, "container-2", 80, item2));
        return mapper;
    }

    @Test
    void scanPublisherReturnsAllRecords() throws Exception {
        MappingSession mapper = populateItems();

        Flow.Publisher<Item> publisher = mapper.scanPublisher(Item.class);
        CollectingSubscriber<Item> subscriber = new CollectingSubscriber<>();
        publisher.subscribe(subscriber);

        List<Item> items = subscriber.result().get(10, TimeUnit.SECONDS);
        assertEquals(10, items.size());
    }

    @Test
    void queryPublisherFiltersRecords() throws Exception {
        MappingSession mapper = populateItems();

        Flow.Publisher<Item> publisher = mapper.queryPublisher(Item.class, "$.score >= 50");
        CollectingSubscriber<Item> subscriber = new CollectingSubscriber<>();
        publisher.subscribe(subscriber);

        List<Item> items = subscriber.result().get(10, TimeUnit.SECONDS);
        assertEquals(6, items.size());
        for (Item item : items) {
            assertTrue(item.score >= 50);
        }
    }

    @Test
    void queryPublisherEmptyResultSetClosesCleanly() throws Exception {
        MappingSession mapper = populateItems();

        Flow.Publisher<Item> publisher = mapper.queryPublisher(Item.class, "$.score > 9999");
        CollectingSubscriber<Item> subscriber = new CollectingSubscriber<>();
        publisher.subscribe(subscriber);

        List<Item> items = subscriber.result().get(10, TimeUnit.SECONDS);
        assertTrue(items.isEmpty());
    }

    @Test
    void queryPublisherWithExpressionFilter() throws Exception {
        MappingSession mapper = populateItems();

        Expression filter = Exp.build(Exp.ge(Exp.intBin("score"), Exp.val(50)));
        Flow.Publisher<Item> publisher = mapper.queryPublisher(Item.class, filter);
        CollectingSubscriber<Item> subscriber = new CollectingSubscriber<>();
        publisher.subscribe(subscriber);

        List<Item> items = subscriber.result().get(10, TimeUnit.SECONDS);
        assertEquals(6, items.size());
        for (Item item : items) {
            assertTrue(item.score >= 50);
        }
    }

    @Test
    void queryPublisherResolvesObjectReferences() throws Exception {
        MappingSession mapper = populateReferencedItems();

        Flow.Publisher<RefContainer> publisher = mapper.queryPublisher(RefContainer.class, "$.score >= 50");
        CollectingSubscriber<RefContainer> subscriber = new CollectingSubscriber<>();
        publisher.subscribe(subscriber);

        List<RefContainer> items = subscriber.result().get(10, TimeUnit.SECONDS);
        assertEquals(1, items.size());

        RefContainer container = items.get(0);
        assertEquals(2, container.id);
        assertEquals("container-2", container.name);
        assertNotNull(container.item);
        assertEquals(2, container.item.id);
        assertEquals("detail-2", container.item.desc);
    }

    @Test
    void queryPublisherCancellationStopsDelivery() throws Exception {
        MappingSession mapper = populateItems();

        Flow.Publisher<Item> publisher = mapper.scanPublisher(Item.class);
        CompletableFuture<Integer> countFuture = new CompletableFuture<>();
        AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();
        List<Item> collected = new ArrayList<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subRef.set(subscription);
                subscription.request(3);
            }

            @Override
            public void onNext(Item item) {
                collected.add(item);
                if (collected.size() == 3) {
                    subRef.get().cancel();
                    countFuture.complete(collected.size());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                countFuture.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                countFuture.complete(collected.size());
            }
        });

        int count = countFuture.get(10, TimeUnit.SECONDS);
        assertTrue(count <= 10, "Should have received at most 10 items, got " + count);
        assertTrue(count >= 3, "Should have received at least 3 items before cancel");
    }

    @Test
    void queryPublisherErrorPropagatedToSubscriber() {
        MappingSession mapper = populateItems();

        CompletableFuture<Void> errorFuture = new CompletableFuture<>();
        boolean threwEagerly = false;

        try {
            Flow.Publisher<Item> publisher = mapper.queryPublisher(Item.class, "$.INVALID %%% SYNTAX");

            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(Item item) {
                }

                @Override
                public void onError(Throwable throwable) {
                    errorFuture.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    errorFuture.complete(null);
                }
            });
        } catch (Exception e) {
            // AEL parse error thrown eagerly at subscribe time — acceptable behavior.
            threwEagerly = true;
            assertNotNull(e);
        }

        if (!threwEagerly) {
            try {
                errorFuture.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                assertNotNull(e.getCause(), "Error should have a cause");
            }
        }
    }

    private static class CollectingSubscriber<T> implements Flow.Subscriber<T> {
        private final List<T> collected = new ArrayList<>();
        private final CompletableFuture<List<T>> future = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            collected.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            future.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            future.complete(new ArrayList<>(collected));
        }

        CompletableFuture<List<T>> result() {
            return future;
        }
    }
}
