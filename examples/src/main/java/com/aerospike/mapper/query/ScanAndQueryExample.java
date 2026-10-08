package com.aerospike.mapper.query;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.TypedRecordStream;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.ExampleContext;
import com.aerospike.mapper.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Demonstrates full-set scans, indexed AEL queries, and callback-based early
 * termination. The registry fixture creates the age index before this code runs.
 */
public final class ScanAndQueryExample implements RunnableExample {
    // ExampleRegistry uses these constants to own the set and secondary index.
    public static final String SET = "sdk_mapper_examples_query";
    public static final String INDEX = "sdk_mapper_examples_query_age_idx";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = SET)
    public static class Person {
        @AerospikeKey public final int id;
        public final String name;
        public final int age;

        // Immutable models can be hydrated through a constructor.
        // @ParamFrom maps each argument explicitly to its stored field.
        public Person(
                @ParamFrom("id") int id,
                @ParamFrom("name") String name,
                @ParamFrom("age") int age) {
            this.id = id; this.name = name; this.age = age;
        }
    }

    @Override
    public void run(ExampleContext context) {
        Session session = context.session();
        AeroMapper mapper = new AeroMapper.Builder(session).build();

        // @start region="scan-query"
        // save() accepts varargs, so several independent records can be written together.
        mapper.save(
                new Person(1, "Twenty", 20),
                new Person(2, "Thirty", 30),
                new Person(3, "Forty", 40),
                new Person(4, "Fifty", 50),
                new Person(5, "Sixty", 60));

        String workingAgeFilter = "$.age >= 30 and $.age <= 50";

        // The mapper API provides concise scan and query operations.
        // scan() reads every record in the mapped set and requires no query expression.
        List<Person> mapperAllPeople = mapper.scan(Person.class);

        // query() applies an Aerospike Expression Language expression on the server.
        // This range query requires the integer "age" index owned by the fixture.
        List<Person> mapperWorkingAge = mapper.query(Person.class, workingAgeFilter);

        // Query result order is not guaranteed, so sort before relying on order.
        mapperWorkingAge.sort(java.util.Comparator.comparingInt(person -> person.age));

        AtomicInteger mapperProcessed = new AtomicInteger();
        // The processor overload streams mapped records to a callback.
        // Returning false stops processing immediately.
        mapper.query(Person.class, person -> {
            mapperProcessed.incrementAndGet();
            return false;
        }, workingAgeFilter);

        // Applications can instead use the SDK Session for database operations and
        // use the mapper only to provide dataset metadata and object mapping.
        Cluster cluster = context.cluster();
        RecordMappingFactory previousFactory = cluster.getRecordMappingFactory();
        List<Person> sdkAllPeople;
        List<Person> sdkWorkingAge;
        int sdkProcessed;
        try {
            cluster.setRecordMappingFactory(mapper.asMappingFactory());
            TypedDataSet<Person> people = mapper.getTypedDataSet(Person.class);

            // session.query() without a where clause performs the same full-set scan.
            sdkAllPeople = session.query(people)
                    .execute()
                    .toObjectList();

            // The SDK owns the query behavior; its typed result stream asks the
            // mapper-backed factory to materialize each Person.
            sdkWorkingAge = session.query(people)
                    .where(workingAgeFilter)
                    .execute()
                    .toObjectList();
            sdkWorkingAge.sort(java.util.Comparator.comparingInt(person -> person.age));

            // Close a partially consumed stream to stop the server query early.
            try (TypedRecordStream<Person> records = session.query(people)
                    .where(workingAgeFilter)
                    .execute()) {
                sdkProcessed = records.popObject().isPresent() ? 1 : 0;
            }
        } finally {
            // The example runner shares its cluster, so do not leak global mapping state.
            cluster.setRecordMappingFactory(previousFactory);
        }
        // @end region="scan-query"

        equal(5, mapperAllPeople.size(), "mapper scan count");
        equal(List.of(30, 40, 50),
                mapperWorkingAge.stream().map(person -> person.age).toList(),
                "mapper query ages");
        equal(1, mapperProcessed.get(), "mapper callback abort count");

        equal(5, sdkAllPeople.size(), "SDK scan count");
        equal(List.of(30, 40, 50),
                sdkWorkingAge.stream().map(person -> person.age).toList(),
                "SDK query ages");
        equal(1, sdkProcessed, "SDK early close count");
    }
}
