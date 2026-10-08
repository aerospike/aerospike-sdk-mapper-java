package com.aerospike.mapper.relationships;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.ExampleContext;
import com.aerospike.mapper.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;

import java.util.List;

import static com.aerospike.mapper.ExampleAssertions.equal;
import static com.aerospike.mapper.ExampleAssertions.require;

/**
 * Contrasts embedded objects, which live inside a parent record, with referenced
 * objects, which are stored as independent Aerospike records.
 */
public final class RelationshipsExample implements RunnableExample {
    public static final String PEOPLE_SET = "sdk_mapper_examples_people";
    public static final String ACCOUNTS_SET = "sdk_mapper_examples_accounts";

    // @start region="constructor-mapping"
    // An embedded type does not need a namespace or set because it is never written as
    // an independent top-level record.
    @AerospikeRecord
    public static class Address {
        public final String city;
        public final String country;

        // @ParamFrom is explicit constructor metadata. It works even when Java classes
        // were compiled without the javac -parameters option.
        public Address(
                @ParamFrom("city") String city,
                @ParamFrom("country") String country) {
            this.city = city;
            this.country = country;
        }
    }
    // @end region="constructor-mapping"

    @AerospikeRecord(
            namespace = "${aerospike.examples.namespace:test}",
            set = ACCOUNTS_SET,
            sendKey = true)
    public static class Account {
        // The key is omitted from bins. sendKey=true stores it in record metadata so
        // the mapper can restore it while reading.
        @AerospikeKey(storeAsBin = false)
        public String id;
        public String name;
        public Account() {}
        public Account(String id, String name) { this.id = id; this.name = name; }
    }

    // @start region="metadata-key"
    // sendKey=true is required when a key uses storeAsBin=false.
    @AerospikeRecord(
            namespace = "${aerospike.examples.namespace:test}",
            set = "sdk_mapper_examples_people",
            sendKey = true)
    public static class Person {
        @AerospikeKey(storeAsBin = false)
        public String id;
        public String name;

        // MAP embedding stores Address fields inside the Person record. No separate
        // Address record is created.
        @AerospikeEmbed(type = AerospikeEmbed.EmbedType.MAP)
        public Address address;

        // References store account IDs and load the corresponding Account records.
        // batchLoad=true resolves collection references with one batch request.
        @AerospikeReference(batchLoad = true)
        public List<Account> accounts;

        public Person() {}
    }
    // @end region="metadata-key"

    @Override
    public void run(ExampleContext context) {
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();

        // @start region="relationships"
        // Referenced objects must exist independently, so call save() for the accounts
        // before saving the Person that refers to them.
        Account checking = new Account("account-1", "Checking");
        Account savings = new Account("account-2", "Savings");
        mapper.save(checking, savings);

        Person ada = new Person();
        ada.id = "person-1";
        ada.name = "Ada Lovelace";
        ada.address = new Address("London", "UK");
        ada.accounts = List.of(checking, savings);
        mapper.save(ada);

        // A normal read() call hydrates the embedded Address and resolves both references.
        Person loaded = mapper.read(Person.class, "person-1");

        // Parent batch reads also batch-load referenced account collections.
        Person[] batch = mapper.read(Person.class, new Object[] {"person-1"});
        // @end region="relationships"

        require(loaded != null, "person should load");
        equal("person-1", loaded.id, "metadata key should be restored");
        equal("London", loaded.address.city, "embedded address");
        equal(2, loaded.accounts.size(), "referenced accounts");
        equal("Checking", loaded.accounts.get(0).name, "first account");
        equal("Savings", batch[0].accounts.get(1).name, "batch-loaded account");
    }
}
