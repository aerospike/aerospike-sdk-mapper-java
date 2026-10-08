package com.aerospike.mapper;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.annotations.*;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeReference.ReferenceType;
import com.aerospike.mapper.tools.AeroMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.aerospike.mapper.ExampleAssertions.equal;
import static com.aerospike.mapper.ExampleAssertions.expectThrows;
import static com.aerospike.mapper.ExampleAssertions.require;

/**
 * Collection of the original documentation-sized mapping demonstrations.
 *
 * <p>Every public example method is executed by {@link #run(ExampleContext)}. Keeping
 * the examples together preserves their original copyable shape while the catalog
 * supplies connection lifecycle and safe set cleanup.</p>
 */
public class AeroMapperDocExamples implements RunnableExample {
    public static final String PRODUCT_SET = "sdk_mapper_examples_docs_product";
    public static final String ACCOUNT_SET = "sdk_mapper_examples_docs_account";
    public static final String PEOPLE_SET = "sdk_mapper_examples_docs_people";
    public static final String VERSIONED_SET = "sdk_mapper_examples_docs_versioned";
    public static final String TRANSACTION_ACCOUNT_SET = "sdk_mapper_examples_docs_transaction_accounts";
    public static final String TRANSACTION_SET = "sdk_mapper_examples_docs_transactions";
    public static final String PARENT_SET = "sdk_mapper_examples_docs_parent";
    public static final String CHILD_SET = "sdk_mapper_examples_docs_child";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = PRODUCT_SET)
    public static class Product {
        public String productId;
        public int version;
        public String name;
        public Date createdDate;
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = ACCOUNT_SET)
    public static class Account {
        @AerospikeKey
        public long id;
        public String title;
        public int balance;
        @AerospikeEmbed(type = EmbedType.LIST)
        public Product product;
    }

    public void runEmbed() {
        // Product is encoded positionally in the account record because this field uses
        // LIST embedding. It is not written to PRODUCT_SET by this save.
        Product product = new Product();
        product.createdDate = new Date();
        product.name = "Sample Product";
        product.productId = "SP-1";
        product.version = 1;

        Account account = new Account();
        account.id = 123;
        account.title = "Test Account";
        account.balance = 111;
        account.product = product;

        mapper.save(account);
    }

    @AerospikeRecord(
            namespace = "${aerospike.examples.namespace:test}",
            set = PEOPLE_SET,
            mapAll = false)
    public static class Person {

        @AerospikeKey
        @AerospikeBin(name = "ssn")
        public String ssn;
        @AerospikeBin
        public String firstName;

        @AerospikeBin(name = "lastName")
        public String lastName;

        @AerospikeBin(name = "age")
        public int age;

        @AerospikeBin(name = "primAcc")
        // DIGEST stores the referenced account's digest rather than its user key.
        @AerospikeReference(type = ReferenceType.DIGEST)
        public Account primaryAccount;

        @AerospikeBin(name = "accts")
        // Lazy references defer loading until application code reads the collection.
        @AerospikeReference(lazy = true)
        public List<Account> accounts;
    }

    private AeroMapper mapper;

    @Override
    public void run(ExampleContext context) {
        mapper = new AeroMapper.Builder(context.session()).build();

        // Execute every demonstration that existed in the legacy class. Set lifecycle
        // belongs to the registry fixture, so these methods remain focused on API use.
        runEmbed();
        run();
        testListOrdering();
        testAccounts();
        showParentWithChildren(context);
    }

    /** Demonstrates explicitly mapped fields plus eager and lazy references. */
    public void run() {
        Account account = new Account();
        account.id = 103;
        account.title = "Primary Savings Account";
        account.balance = 137;

        Person person = new Person();
        person.ssn = "123-456-7890";
        person.firstName = "John";
        person.lastName = "Doe";
        person.age = 43;
        person.primaryAccount = account;

        Account acc1 = new Account();
        acc1.id = 101;
        acc1.title = "Primary Checking Account";
        acc1.balance = 100;

        Account acc2 = new Account();
        acc2.id = 102;
        acc2.title = "Secondary Savings Account";
        acc2.balance = 200;

        person.accounts = new ArrayList<>();
        person.accounts.add(acc1);
        person.accounts.add(acc2);

        mapper.save(account);
        mapper.save(person);

        Person loadedPerson = mapper.read(Person.class, "123-456-7890");
        System.out.printf("ssn = %s, name = %s %s, balance = %d",
                loadedPerson.ssn, loadedPerson.firstName, loadedPerson.lastName,
                loadedPerson.primaryAccount.balance);

    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = VERSIONED_SET)
    public static class IntContainer {
        public int a;
        public int b;
        public int c;
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = VERSIONED_SET)
    public static class IntMapper {
        @AerospikeKey
        public int id;
        @AerospikeEmbed(type = EmbedType.LIST)
        public IntContainer container;
    }

    @AerospikeRecord(
            namespace = "${aerospike.examples.namespace:test}",
            set = VERSIONED_SET,
            version = 2)
    public static class IntContainerV2 {
        // Version one stored a/b/c. Version two removes a and appends d while preserving
        // the positional meaning of the existing b/c list entries.
        @AerospikeVersion(max = 1)
        public int a;
        public int b;
        public int c;
        @AerospikeVersion(min = 2)
        public int d;
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = VERSIONED_SET)
    public static class IntMapperV2 {
        @AerospikeKey
        public int id;
        @AerospikeEmbed(type = EmbedType.LIST)
        public IntContainerV2 container;
    }

    public void testListOrdering() {
        // Save a version-one list and read it through the version-two schema.
        IntContainer container = new IntContainer();
        container.a = 1;
        container.b = 2;
        container.c = 3;
        IntMapper mapper2 = new IntMapper();
        mapper2.id = 1;
        mapper2.container = container;
        mapper.save(mapper2);

        IntMapperV2 v2 = mapper.read(IntMapperV2.class, 1);

        IntContainerV2 containerV2 = new IntContainerV2();
        containerV2.b = 2;
        containerV2.c = 3;
        containerV2.d = 4;
        IntMapperV2 mapperV2 = new IntMapperV2();
        mapperV2.id = 2;
        mapperV2.container = containerV2;
        mapper.save(mapperV2);


        for (int i = 1; i <= 2; i++) {
            mapperV2 = mapper.read(mapperV2.getClass(), i);
            System.out.println("Read " + i);
        }
    }

    public enum AccountType {
        SAVINGS, CHEQUING
    }

    @AerospikeRecord(
            namespace = "${aerospike.examples.namespace:test}",
            set = TRANSACTION_ACCOUNT_SET)
    public static class Accounts {
        @AerospikeKey
        public int id;
        public String name;
        public AccountType type;

        // Each map value is a positional list. @AerospikeOrdinal below determines the
        // value used for ordered collection operations.
        @AerospikeEmbed(elementType = EmbedType.LIST)
        public Map<String, Transactions> transactions;

        public Accounts() {
            this.transactions = new HashMap<>();
        }

        public Accounts(int id, String name, AccountType type) {
            this();
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = TRANSACTION_SET)
    public static class Transactions {
        public String txnId;
        @AerospikeOrdinal()
        public Instant date;
        public double amt;
        public String merchant;

        public Transactions() {
        }

        public Transactions(String txnId, Instant date, double amt, String merchant) {
            super();
            this.txnId = txnId;
            this.date = date;
            this.amt = amt;
            this.merchant = merchant;
        }
    }

    public void testAccounts() {
        // Instant values make transaction chronology explicit while the map key keeps
        // each transaction directly addressable by its business identifier.
        Accounts account = new Accounts(1, "Savings Account", AccountType.SAVINGS);
        Transactions txn1 = new Transactions("Txn1", Instant.now(), 100.0, "Bob's store");
        Transactions txn2 = new Transactions("Txn2", Instant.now().minus(Duration.ofHours(8)), 134.99, "Kim's store");
        Transactions txn3 = new Transactions("Txn3", Instant.now().minus(Duration.ofHours(20)), 75.43, "Sue's store");

        account.transactions.put(txn1.txnId, txn1);
        account.transactions.put(txn2.txnId, txn2);
        account.transactions.put(txn3.txnId, txn3);

        mapper.save(account);
        System.out.println("done");
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = PARENT_SET)
    public static class Parent {
        @AerospikeKey
        int id;
        String name;

        @AerospikeEmbed(type = EmbedType.MAP)
        public Child mapEmbedChild;

        @AerospikeEmbed(type = EmbedType.LIST)
        public Child listEmbedChild;

        @AerospikeReference(type = ReferenceType.DIGEST)
        public Child refChild;

        public Parent(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("refChild") Child child) {
            super();
            this.id = id;
            this.name = name;
            this.mapEmbedChild = child;
            this.listEmbedChild = child;
            this.refChild = child;
        }
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = PARENT_SET)
    public static class ParentWithBadChild {
        @AerospikeKey
        int id;
        String name;

        @AerospikeEmbed(type = EmbedType.MAP)
        public Child mapEmbedChild;

        @AerospikeEmbed(type = EmbedType.LIST)
        public Child listEmbedChild;

        @AerospikeReference(type = ReferenceType.DIGEST, lazy = true)
        public Child refChild;

        public ParentWithBadChild(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("refChild") Child child) {
            super();
            this.id = id;
            this.name = name;
            this.mapEmbedChild = child;
            this.listEmbedChild = child;
            this.refChild = child;
        }
    }

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = CHILD_SET)
    public static class Child {
        @AerospikeKey
        int id;
        String name;
        Date date;

        public Child(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("date") Date date) {
            super();
            this.id = id;
            this.name = name;
            this.date = date;
        }
    }

    public void showParentWithChildren(ExampleContext context) {
        // The same Child is embedded twice and referenced once, illustrating that field
        // annotations—not the Java type alone—control persisted representation.
        Child child = new Child(1, "child", new Date());
        Parent parent = new Parent(10, "parent", child);
        mapper.save(parent);

        ParentWithBadChild parent2 = new ParentWithBadChild(11, "parent2", child);
        expectThrows(AerospikeException.class, () -> mapper.save(parent2),
                "lazy reference to an unsaved child should be rejected");

        // Since the child is referenced, it must also be saved as its own record.
        // If it were only embedded, this explicit save would not be necessary.
        mapper.save(child);

        /*
         * Inspect the stored parent to verify that each annotation selected its intended
         * physical representation. A direct Parent read is deliberately avoided here:
         * its constructor binds one Child parameter to three fields with different
         * encodings, which is useful for illustrating writes but is not an unambiguous
         * constructor-hydration contract.
         */
        Record storedParent = readRawRecord(context, PARENT_SET, parent.id);
        require(storedParent != null, "parent record should exist");
        equal((long) parent.id, storedParent.getLong("id"), "stored parent key");
        equal(parent.name, storedParent.getString("name"), "stored parent name");
        require(storedParent.getValue("mapEmbedChild") instanceof Map,
                "MAP-embedded child should be stored as a map");
        require(storedParent.getValue("listEmbedChild") instanceof List,
                "LIST-embedded child should be stored as a list");
        require(storedParent.getValue("refChild") != null,
                "DIGEST reference should be stored in the parent");

        // Reading the independently saved Child proves that the stored digest has a
        // valid target record with the original domain values.
        assertChild(child, mapper.read(Child.class, child.id), "referenced child");
    }

    private static void assertChild(Child expected, Child actual, String description) {
        require(actual != null, description + " should load");
        equal(expected.id, actual.id, description + " id");
        equal(expected.name, actual.name, description + " name");
        equal(expected.date, actual.date, description + " date");
    }

    private static Record readRawRecord(ExampleContext context, String set, Object userKey) {
        Key key = new Key(context.namespace(), set, Value.get(userKey));
        try (RecordStream stream = context.session().query(List.of(key)).execute()) {
            RecordResult result = stream.hasNext() ? stream.next() : null;
            return result != null && result.isOk() ? result.getRecord() : null;
        }
    }
}
