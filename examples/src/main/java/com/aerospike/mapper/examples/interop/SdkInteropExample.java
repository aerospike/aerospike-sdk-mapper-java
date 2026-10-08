package com.aerospike.mapper.examples.interop;

import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.TypedRecordStream;
import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.examples.ExampleContext;
import com.aerospike.mapper.examples.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;

import java.util.Map;

import static com.aerospike.mapper.examples.ExampleAssertions.equal;
import static com.aerospike.mapper.examples.ExampleAssertions.require;

/**
 * Shows that mapper metadata can be reused by ordinary SDK operations and that the
 * mapper can provide the SDK's typed-object mapping implementation.
 */
public final class SdkInteropExample implements RunnableExample {
    public static final String SET = "sdk_mapper_examples_interop";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = SET)
    public static class Person {
        @AerospikeKey public String id;
        public String name;

        // Deliberately rename this bin so the example cannot accidentally assume that
        // Java field names and stored Aerospike bin names are always identical.
        @AerospikeBin(name = "years") public int age;
        public Person() {}
        public Person(String id, String name, int age) {
            this.id = id; this.name = name; this.age = age;
        }
    }

    @Override
    public void run(ExampleContext context) {
        Session session = context.session();
        Cluster cluster = context.cluster();
        AeroMapper mapper = new AeroMapper.Builder(session).build();

        // Seed one record through the mapper before updating it through the SDK API.
        Person ada = new Person("person-1", "Ada Lovelace", 36);
        mapper.insert(ada);

        // @start region="interop-bin-names"
        // getBinNames() translates Java fields to stored bins. Always use this metadata
        // when an SDK operation targets a field whose bin may have been renamed.
        Map<String, String> bins = mapper.getBinNames(Person.class);
        String ageBin = bins.get("age");

        // A TypedDataSet carries namespace, set, and Java type information together.
        TypedDataSet<Person> people = mapper.getTypedDataSet(Person.class);

        // This is a normal Java SDK operation; the mapper only supplied its metadata.
        session.update(people.id("person-1"))
                .bin(ageBin)
                .setTo(37)
                .execute();
        // @end region="interop-bin-names"

        equal(37, mapper.read(Person.class, "person-1").age, "SDK update is mapper-visible");

        // Record-mapping configuration belongs to the shared Cluster. Preserve and
        // restore the caller's prior factory so this example has no global side effect.
        RecordMappingFactory previous = cluster.getRecordMappingFactory();
        try {
            // @start region="interop-typed"
            // Registering the mapper factory enables TypedDataSet object writes and
            // typed query results in the SDK.
            cluster.setRecordMappingFactory(mapper.asMappingFactory());

            Person grace = new Person("person-2", "Grace Hopper", 85);
            session.insert(people).object(grace).execute();

            Person loaded;
            // SDK result streams own server/network resources and must be closed.
            try (TypedRecordStream<Person> records =
                         session.query(people.id("person-2")).execute()) {
                loaded = records.getFirstObject().orElse(null);
            }
            // @end region="interop-typed"

            require(loaded != null, "typed SDK read should return the inserted object");
            equal("Grace Hopper", loaded.name, "typed SDK mapping");
        } finally {
            cluster.setRecordMappingFactory(previous);
        }
    }
}
