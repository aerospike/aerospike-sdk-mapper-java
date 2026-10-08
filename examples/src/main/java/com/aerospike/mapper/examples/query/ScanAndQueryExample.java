package com.aerospike.mapper.examples.query;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.examples.ExampleContext;
import com.aerospike.mapper.examples.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerospike.mapper.examples.ExampleAssertions.equal;

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
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();

        // @start region="scan-query"
        // save() accepts varargs, so several independent records can be written together.
        mapper.save(
                new Person(1, "Twenty", 20),
                new Person(2, "Thirty", 30),
                new Person(3, "Forty", 40),
                new Person(4, "Fifty", 50),
                new Person(5, "Sixty", 60));

        // scan() reads every record in the mapped set and requires no query expression.
        List<Person> allPeople = mapper.scan(Person.class);

        // query() applies an Aerospike Expression Language expression on the server.
        // This range query requires the integer "age" index owned by the fixture.
        List<Person> workingAge = mapper.query(
                Person.class, "$.age >= 30 and $.age <= 50");

        // Query result order is not guaranteed, so sort before relying on order.
        workingAge.sort(java.util.Comparator.comparingInt(person -> person.age));

        AtomicInteger processed = new AtomicInteger();
        // The processor overload streams mapped records to a callback.
        // Returning false stops processing immediately.
        mapper.query(Person.class, person -> {
            processed.incrementAndGet();
            return false;
        }, "$.age >= 30 and $.age <= 50");
        // @end region="scan-query"

        equal(5, allPeople.size(), "scan count");
        equal(List.of(30, 40, 50), workingAge.stream().map(person -> person.age).toList(),
                "query ages");
        equal(1, processed.get(), "callback abort count");
    }
}
