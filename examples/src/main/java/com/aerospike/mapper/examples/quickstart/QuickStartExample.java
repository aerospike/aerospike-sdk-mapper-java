package com.aerospike.mapper.examples.quickstart;

import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.examples.ExampleContext;
import com.aerospike.mapper.examples.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;

import static com.aerospike.mapper.examples.ExampleAssertions.equal;
import static com.aerospike.mapper.examples.ExampleAssertions.require;

/**
 * Introduces the core mapper workflow: annotate a model, build a mapper from an SDK
 * session, and perform CRUD and batch-read operations.
 */
public final class QuickStartExample implements RunnableExample {
    // The fixture registered in ExampleRegistry owns and cleans this set.
    public static final String SET = "sdk_mapper_examples_quickstart";

    // @start region="quick-start-model"
    // The namespace placeholder lets the runner select a namespace at startup. In an
    // application, a literal namespace such as "test" is also valid.
    @AerospikeRecord(
            namespace = "${aerospike.examples.namespace:test}",
            set = "sdk_mapper_examples_quickstart")
    public static class Person {
        // Exactly one mapped field (or method) identifies the Aerospike user key.
        @AerospikeKey
        public String id;

        // Java field names are used as bin names by default. This annotation stores
        // the Java field "name" in an Aerospike bin named "fullName" instead.
        @AerospikeBin(name = "fullName")
        public String name;

        // No annotation is needed because @AerospikeRecord maps all fields by default.
        public int age;

        // A no-argument constructor is the simplest way to let the mapper hydrate a POJO.
        public Person() {}

        public Person(String id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }
    }
    // @end region="quick-start-model"

    @Override
    public void run(ExampleContext context) {
        // AeroMapper uses the caller-owned SDK Session; closing the Cluster remains the
        // caller's responsibility.
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();

        // @start region="quick-start-crud"
        Person ada = new Person("person-1", "Ada Lovelace", 36);

        // insert() is create-only and fails if this key already exists.
        mapper.insert(ada);

        // read() returns null when no record exists for the requested key.
        Person saved = mapper.read(Person.class, "person-1");
        saved.age = 37;

        // update() merges only the named stored bins. Here the Java and stored names for
        // age are identical, so "age" is the correct selected-bin name.
        mapper.update(saved, "age");

        saved.name = "Augusta Ada King";

        // save() uses replace semantics: it creates a missing record or completely
        // replaces an existing record with the mapped object.
        mapper.save(saved);

        // Both delete() overloads return whether an existing record was deleted.
        boolean deleted = mapper.delete(Person.class, "person-1");
        // @end region="quick-start-crud"

        require(deleted, "delete should report the existing record");
        require(mapper.read(Person.class, "person-1") == null, "deleted record should be absent");

        mapper.insert(ada);
        // @start region="crud-batch"
        mapper.insert(new Person("person-2", "Grace Hopper", 85));

        // Batch reads preserve the requested key order. Missing records are represented
        // by null entries rather than being removed from the returned array.
        Person[] people = mapper.read(
                Person.class, new Object[] {"person-1", "missing", "person-2"});
        // @end region="crud-batch"

        equal(3, people.length, "batch size");
        equal("person-1", people[0].id, "first batch item");
        require(people[1] == null, "missing batch item should remain null");
        equal("person-2", people[2].id, "third batch item");
        equal("Ada Lovelace", people[0].name, "selected-bin update preserved name");
    }
}
