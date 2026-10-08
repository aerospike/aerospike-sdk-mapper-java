package com.aerospike.mapper.reactive;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ErrorStrategy;
import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.ExampleContext;
import com.aerospike.mapper.RunnableExample;
import com.aerospike.mapper.tools.IAeroMapper;
import com.aerospike.mapper.tools.ReactiveAeroMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static com.aerospike.mapper.ExampleAssertions.drainPublisher;
import static com.aerospike.mapper.ExampleAssertions.equal;
import static com.aerospike.mapper.ExampleAssertions.require;

/**
 * Introduces the asynchronous mapper facade: CRUD operations return
 * {@link java.util.concurrent.CompletableFuture} and scans/queries return Flow
 * publishers.
 */
public final class ReactiveMapperExample implements RunnableExample {
    public static final String SET = "sdk_mapper_examples_reactive";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = SET)
    public static class Person {
        @AerospikeKey public String id;
        public String name;
        public int age;
        public Person() {}
        public Person(String id, String name, int age) {
            this.id = id; this.name = name; this.age = age;
        }
    }

    @Override
    public void run(ExampleContext context) throws Exception {
        Session session = context.session();
        // @start region="reactive-mapper"
        ReactiveAeroMapper mapper = new ReactiveAeroMapper.Builder(session).build();

        // Every asynchronous operation returns a CompletableFuture. Applications can
        // compose these futures; this small example waits with a bounded timeout so a
        // server problem cannot block forever.
        mapper.save(new Person("person-1", "Ada Lovelace", 36))
                .get(5, TimeUnit.SECONDS);
        Person ada = mapper.read(Person.class, "person-1")
                .get(5, TimeUnit.SECONDS);

        // Like the synchronous mapper, the selected-bin update() overload merges only
        // that bin.
        ada.age = 37;
        mapper.update(ada, "age").get(5, TimeUnit.SECONDS);

        // The SDK also exposes native asynchronous execution. Here the Session owns
        // the async read while the mapper supplies only typed object materialization.
        IAeroMapper mappingMapper = mapper.asMapper();
        Cluster cluster = context.cluster();
        RecordMappingFactory previousFactory = cluster.getRecordMappingFactory();
        Person sdkAda;
        try {
            cluster.setRecordMappingFactory(mappingMapper.asMappingFactory());
            TypedDataSet<Person> people = mappingMapper.getTypedDataSet(Person.class);
            sdkAda = session.query(people.id("person-1"))
                    .executeAsync(ErrorStrategy.IN_STREAM)
                    .asCompletableFutureMappedSingle()
                    .get(5, TimeUnit.SECONDS)
                    .orElse(null);
        } finally {
            cluster.setRecordMappingFactory(previousFactory);
        }

        // Scans and queries use the standard java.util.concurrent.Flow API. Subscribe
        // promptly: the current publisher starts producing as soon as scan() is called.
        Flow.Publisher<Person> publisher = mapper.scan(Person.class);
        // @end region="reactive-mapper"

        // The example helper subscribes immediately, requests all items, propagates
        // onError(), and cancels the subscription if the timeout expires.
        List<Person> observed = drainPublisher(publisher, Duration.ofSeconds(5));
        require(observed != null, "publisher should complete without error");
        require(sdkAda != null, "SDK async typed read should return the person");
        equal(37, sdkAda.age, "SDK async mapped age");
        equal(37, mapper.read(Person.class, "person-1").get(5, TimeUnit.SECONDS).age,
                "selected-bin reactive update");
        require(mapper.delete(Person.class, "person-1").get(5, TimeUnit.SECONDS),
                "reactive delete");
        require(mapper.read(Person.class, "person-1").get(5, TimeUnit.SECONDS) == null,
                "reactive record should be absent");
    }
}
