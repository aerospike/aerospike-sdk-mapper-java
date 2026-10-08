# Aerospike Java SDK Object Mapper

The Aerospike Java SDK Object Mapper persists annotated or externally configured Java POJOs as
Aerospike records and reconstructs them on read (reads them back as objects). 
It is built on the [Aerospike Java SDK](https://github.com/aerospike/aerospike-client-java-sdk), integrates through Session API
and provides object CRUD, batch reads, scans, AEL queries, typed SDK interoperability,
references, embeds, and database-backed virtual lists.

## Requirements

- Java 21
- Aerospike Server version >= 8.2.0.0

## Installation

Add the mapper dependency to your application:

```xml
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-sdk-mapper-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

When building this repository from source, run the normal Maven build:

```shell
mvn install
```

## Table of contents

1. [Running examples](#running-examples)
2. [Quick start](#quick-start)
3. [Using Session and SDK APIs](#using-session-and-sdk-apis)
4. [Mapper API basics](#mapper-api-basics)
5. [Mapping Java objects](#mapping-java-objects)
6. [Advanced features](#advanced-features)
7. [Logging](#logging)
8. [Reference notes](#reference-notes)

## Running examples

The examples are part of the normal Maven test source roots. By default, `mvn test`
compiles the examples, runs their non-live support tests, and runs the live example
catalog through a JUnit smoke test. The live smoke test skips on Aerospike Server
versions older than 8.2 because the query example uses AEL.

The same catalog can also be run separately with the wrapper script or directly
through Maven.

### Wrapper script

List the catalog without connecting to Aerospike, run one example, or run all examples:

```shell
./examples/run_examples list
./examples/run_examples quick-start
./examples/run_examples all --fail-fast
```

### Maven

Activate the `examples` profile, compile its test-classpath sources, and pass runner
arguments as a comma-separated list:

```shell
mvn -Pexamples -DskipTests test-compile exec:java \
  -Dexec.arguments=all,--fail-fast
```

The defaults are `localhost:3000` and namespace `test`. Override them with `--host`,
`--port`, and `--namespace`. Because the runner truncates its fixed
`sdk_mapper_examples_*` sets, a namespace other than `test` is rejected unless
`--allow-non-test-namespace` is supplied explicitly. See
[examples/README.md](examples/README.md) for prerequisites, safety details, and the
complete catalog.

## Quick start

Create the SDK `Cluster` and `Session`, pass the session to the mapper, and annotate the
class to describe its namespace, set, key, and bins:

<!-- example-region:quick-start-model:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/quickstart/QuickStartExample.java)
<!-- example-region:quick-start-model:end -->

Create and close the owning SDK cluster explicitly; the runnable scenario receives the
same `Session` from the shared harness:

```java
try (Cluster cluster = new ClusterDefinition("127.0.0.1", 3000).connect()) {
    Session session = cluster.createSession(Behavior.DEFAULT);
    AeroMapper mapper = new AeroMapper.Builder(session).build();
    // Mapper operations shown below run here.
}
```

<!-- example-region:quick-start-crud:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/quickstart/QuickStartExample.java)
<!-- example-region:quick-start-crud:end -->

The mapper retains and uses the caller-provided `Session`; it does not own the
`Cluster`. The same session can be used for mapper calls and ordinary SDK operations.
Close the owning cluster when the application is finished with it.

## Using Session and SDK APIs

### Mapping metadata

`AeroMapper` exposes `getNamespace`, `getSet`, `getKey`, `getRecordKey`, and
`getBinNames`. Use `getBinNames(Class<?>)` to resolve Java field names to stored bin
names before issuing ordinary SDK operations:

<!-- example-region:interop-bin-names:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/interop/SdkInteropExample.java)
<!-- example-region:interop-bin-names:end -->

The returned bin-name map is immutable, includes mapped superclass fields, and respects
annotation, YAML, and code-configured names. It excludes non-persistent fields,
generation fields, method-only `@AerospikeGetter` bins, and keys with
`storeAsBin = false`. Resolve and retain these names rather than repeatedly looking them
up on hot paths.

### Typed SDK mapping

Register the mapper's `RecordMappingFactory` on the cluster before using SDK typed
reads or object writes:

<!-- example-region:interop-typed:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/interop/SdkInteropExample.java)
<!-- example-region:interop-typed:end -->

Use typed `Session` flows such as `session.query(TypedDataSet)` or
`session.query(TypedKey)` so the SDK supplies a `RecordReadContext`. Mapper-backed reads
need that context to resolve referenced objects. The three-argument
`AeroRecordMapper.fromMap(Map, Key, int)` overload intentionally throws and is not a
supported integration path.

Typed SDK object writes use the mapper's `toMap` and `id` bridge, but they do not pass
through `AeroMapper.writeObject`. Use `AeroMapper.save`, `insert`, or `update` when the
mapper should automatically apply its configured TTL and non-zero
`@AerospikeGeneration` check; otherwise configure the corresponding SDK object builder
explicitly.

## Mapper API basics

### Building a mapper

The builder always starts with a `Session`:

```java
AeroMapper mapper = new AeroMapper.Builder(session)
        .preLoadClasses(Person.class, Account.class)
        .build();
```

Builder options include:

- `addConverter(Object)` for custom `@ToAerospike`/`@FromAerospike` conversions.
- `preLoadClass`, `preLoadClasses`, and `preLoadClassesFromPackage` to pay
  introspection cost during startup and register subclasses before polymorphic reads.
- `withConfigurationFile(File|InputStream)` and `withConfiguration(String)` for YAML.
- `withClassConfigurations(ClassConfig...)` for programmatic mapping.

Mapping metadata is cached. Preloading is optional for ordinary classes but useful when
the first operation reads an abstract or interface type whose concrete types have not
otherwise been seen.

### CRUD and batch reads

- `save(object)` calls `Session.replace`: it creates the record if it does not exist,
  otherwise it completely replaces the existing record.
- `insert(object)` calls `Session.insert`: it uses create-only semantics.
- `update(object)` calls `Session.upsert`: it merges written bins into the record.
- `read(Type.class, key)` returns the mapped object or `null`.
- `read(Type.class, Object[] keys)` performs a batch read, preserves key order, and
  leaves missing records as `null` entries.
- `delete(object)` and `delete(Type.class, key)` return whether the delete succeeded.

All three write methods accept optional stored bin names.

<!-- example-region:crud-batch:start -->
```java
mapper.insert(new Person("person-2", "Grace Hopper", 85));

// Batch reads preserve the requested key order. Missing records are represented
// by null entries rather than being removed from the returned array.
Person[] people = mapper.read(
        Person.class, new Object[] {"person-1", "missing", "person-2"});
```

[Region source](examples/src/main/java/com/aerospike/mapper/quickstart/QuickStartExample.java)
<!-- example-region:crud-batch:end -->

> **Partial-write warning:** `save(object, binNames...)` still uses replace semantics.
> It can create a record, or completely replace an existing record, containing only the
> selected bins. Use
> `update(object, binNames...)` when selected bins should be merged with bins already in
> the record.

### Scans and AEL queries

Use `scan` for a full set and `query` for an Aerospike Expression Language filter:

<!-- example-region:scan-query:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/query/ScanAndQueryExample.java)
<!-- example-region:scan-query:end -->

The callback overload stops when the processor returns `false`. Query filters are AEL
strings, and the server must have any secondary index needed by the expression. Legacy
client `Filter`, `QueryPolicy`, and `Exp` mapper overloads are not part of this API.

## Mapping Java objects

### Records, fields, and properties

`@AerospikeRecord` defines class-level mapping such as `namespace`, `set`, `mapAll`,
`sendKey`, `ttl`, `shortName`, `version`, and optional factory information.

With the default `mapAll = true`, fields are mapped regardless of visibility.
`@AerospikeBin(name = "...")` renames a field and
`@AerospikeExclude` removes it. Set `mapAll = false` to persist only fields explicitly
annotated with `@AerospikeBin`.

`@AerospikeBin(useAccessors = true)` accesses an annotated field through its Java
getter and setter. A computed property can instead be represented by matching
`@AerospikeGetter(name = "...")` and `@AerospikeSetter(name = "...")` methods. A
property setter may take a second SDK `Key` or `Value` argument when it needs the
owning-record key during hydration.

### Constructors and factories

If a class has only one constructor, the mapper uses it. With several constructors, an
`@AerospikeConstructor` takes precedence; otherwise the mapper uses a no-argument
constructor. A class with several constructors and neither of those choices cannot be
hydrated.

Constructor arguments are matched to stored bin names. Preserve parameter names with
the compiler's `-parameters` option or annotate each argument:

<!-- example-region:constructor-mapping:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/relationships/RelationshipsExample.java)
<!-- example-region:constructor-mapping:end -->

For generated or externally controlled classes, `factoryClass` and `factoryMethod`
select a static factory. Supported factory signatures take no arguments, `Class`,
`Map<String, Object>`, or `Class` followed by `Map<String, Object>`. Configure both the
factory class and method together.

### Keys

Mark one field or method with `@AerospikeKey`. Supported user-key values follow the SDK
key types (strings, integral values, and byte arrays). A key method takes no arguments
and returns the key. Method-based keys can use a separate method annotated with
`@AerospikeKey(setter = true)` for hydration.

Keys are stored as bins by default. To store the user key in record metadata instead:

<!-- example-region:metadata-key:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/relationships/RelationshipsExample.java)
<!-- example-region:metadata-key:end -->

`storeAsBin = false` requires `sendKey = true`; otherwise the mapper cannot recover the
original user key from the digest.

### Built-in type mappings

The mapper supports primitive types and wrappers, strings, byte arrays, enums, arrays,
lists, maps, mapped object references, and embedded objects. Important non-native
representations are:

- `Date`: epoch milliseconds.
- `Instant`: epoch seconds and nanoseconds combined into one integer nanosecond value.
- `LocalDate`: epoch day.
- `LocalTime`: nanoseconds since start of day.
- `LocalDateTime`: a two-element list containing epoch day and nanoseconds of day.
- `BigInteger` and `BigDecimal`: decimal strings.
- Enums: `toString()` by default.
- Non-byte arrays and Java lists: Aerospike lists; Java maps: Aerospike maps.

Custom converters can replace any built-in representation.

### References, embedding, and batch loading

A mapped object field is a non-lazy ID reference by default. The referenced object must
be saved separately. `@AerospikeReference` makes the choice explicit. This runnable
flow saves referenced accounts first and then reads them with their owner:

<!-- example-region:relationships:start -->
```java
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
```

[Region source](examples/src/main/java/com/aerospike/mapper/relationships/RelationshipsExample.java)
<!-- example-region:relationships:end -->

Non-lazy dependencies are resolved in batches where possible. `lazy = true` creates a
placeholder with its key populated but does not load the rest of the child.
`type = ReferenceType.DIGEST` stores a digest instead of an ID. Digest references cannot
be lazy because the digest cannot be represented in the child's ordinary key field.

Use `@AerospikeEmbed` when the child belongs inside the parent record, as shown by the
`Address` field in the linked complete scenario.

Map embeds are self-describing. List embeds are more compact and use field order, so
schema evolution needs versioning. For a list-backed class, increment
`@AerospikeRecord(version = ...)` and use `@AerospikeVersion(min = ..., max = ...)` on
fields. Versioned lists carry a version marker in stored data.

`@AerospikeOrdinal(1)`, `@AerospikeOrdinal(2)`, and so on override alphabetical list
order. Ordinals must be sequential and cannot be combined with versioned fields. Use
parameterized collection declarations such as `List<Account>` and
`Map<String, Transaction>`; raw collections require extra runtime type metadata and
provide less mapping information.

### Inheritance and interfaces

Mapped superclass fields are included in subclass records. A subclass can inherit its
namespace and set, and `shortName` supplies the compact type discriminator stored when
a value declared as a base type contains a subtype. Short names must be unique within a
mapper's type universe.

`@AerospikeRecord` can also be placed on an interface. Explicit external configuration
has highest precedence, followed by a class annotation, an annotated parent class, and
then an annotated interface. Preload concrete implementations before the first
polymorphic read.

### Placeholders

String annotation values can come from system properties or environment variables.
The placeholder must occupy the complete annotation value:

```java
@AerospikeRecord(
        namespace = "#{AEROSPIKE_NAMESPACE:test}",
        set = "${people.set:people}")
public class Person {
}
```

`${name:default}` reads a system property and `#{NAME:default}` reads an environment
variable. Without a configured value or default, the resolved value is `null`.

## Advanced features

### Custom converters

A converter has one `@ToAerospike` method and one `@FromAerospike` method. Each method
takes one argument, and the parameter and return types must be inverse pairs:

<!-- example-region:custom-converter:start -->
```java
// Aerospike has no native Money type, so this domain value needs a stored form.
public record Money(long cents) {}

public static class MoneyConverter {
    // The @ToAerospike method converts the domain value to a supported stored type.
    @ToAerospike
    public long toAerospike(Money money) {
        return money.cents();
    }

    // @FromAerospike performs the exact inverse conversion during reads.
    @FromAerospike
    public Money fromAerospike(long cents) {
        return new Money(cents);
    }
}

@AerospikeRecord(
        namespace = "${aerospike.examples.namespace:test}",
        set = "sdk_mapper_examples_converter")
public static class Wallet {
    @AerospikeKey public String id;

    // Once the converter is registered, Money fields need no field-level annotation.
    public Money balance;
    public Wallet() {}
    public Wallet(String id, Money balance) { this.id = id; this.balance = balance; }
}

public static AeroMapper moneyMapper(Session session) {
    // Converters are registered on the mapper builder before build().
    return new AeroMapper.Builder(session)
            .addConverter(new MoneyConverter())
            .build();
}
```

[Region source](examples/src/main/java/com/aerospike/mapper/configuration/ConfigurationExample.java)
<!-- example-region:custom-converter:end -->

Converters are global for their Java type and take precedence over built-in mappers.

### YAML and code configuration

Use external configuration when the source class cannot be annotated or mappings vary
by environment:

<!-- example-region:yaml-configuration:start -->
```yaml
---
classes:
  # Use the binary name (with $) for this nested, otherwise unannotated Java class.
  - class: com.aerospike.mapper.configuration.ConfigurationExample$YamlPerson
    # External configuration uses literal namespace and set values.
    namespace: test
    set: sdk_mapper_examples_yaml
    # The key section identifies the field used as the Aerospike user key.
    key:
      field: id
    bins:
      # Store Java field "name" in a differently named Aerospike bin.
      - field: name
        name: fullName
```

[Region source](examples/src/main/resources/mappings/example-mapping.yaml)
<!-- example-region:yaml-configuration:end -->

[Complete runnable configuration scenario](examples/src/main/java/com/aerospike/mapper/configuration/ConfigurationExample.java)

The current YAML model supports class settings including `class`, `namespace`, `set`,
`factoryClass`, `factoryMethod`, `durableDelete`, `mapAll`, `sendKey`, `ttl`,
`shortName`, and `version`; key settings; and bin settings including `field`, `name`,
`getter`, `setter`, `exclude`, `useAccessors`, `ordinal`, `embed`, `reference`, and
`generation`.

Equivalent programmatic configuration uses `ClassConfig`:

<!-- example-region:code-configuration:start -->
```java
// ClassConfig is useful when a class cannot be annotated or when mappings are
// assembled dynamically by application configuration.
ClassConfig personConfig = new ClassConfig.Builder(CodePerson.class)
        .withNamespace(namespace)
        .withSet("sdk_mapper_examples_code")
        // Declare the field used as the Aerospike user key.
        .withKeyField("id")
        // Persist Java field "name" in a bin named "fullName".
        .withFieldNamed("name").mappingToBin("fullName")
        .build();

// Supply one or more complete class configurations before building the mapper.
AeroMapper codeMapper = new AeroMapper.Builder(session)
        .withClassConfigurations(personConfig)
        .build();
```

[Region source](examples/src/main/java/com/aerospike/mapper/configuration/ConfigurationExample.java)
<!-- example-region:code-configuration:end -->

### Generation checks

Mark one `int` or `Integer` field or getter/setter property with
`@AerospikeGeneration`. Reads copy the server record generation into that member. A
write with a non-zero value calls `ensureGenerationIs`, so a stale object fails instead
of silently overwriting a newer generation. The generation is metadata and is not
stored as a bin.

<!-- example-region:generation-check:start -->
```java
// Simulate two callers reading the same version of one record.
Document firstWriter = mapper.read(Document.class, "document-1");
Document staleWriter = mapper.read(Document.class, "document-1");

// The first save() succeeds because its generation still matches the server.
firstWriter.value = "winner";
mapper.save(firstWriter);

// staleWriter still carries generation 1, while the stored record is now at
// generation 2. The mapper asks Aerospike to reject this stale write.
staleWriter.value = "stale";
AerospikeException conflict = null;
try {
    mapper.save(staleWriter);
} catch (AerospikeException expected) {
    conflict = expected;
}
```

[Region source](examples/src/main/java/com/aerospike/mapper/generation/OptimisticLockingExample.java)
<!-- example-region:generation-check:end -->

### Enum field mapping

Enums are stored using `toString()` by default. To store a field declared on the enum,
annotate the containing field:

```java
@AerospikeEnum(enumField = "code")
private Status status;
```

The selected enum field's string value is used for writes and matched when reading.

### Virtual lists

A virtual list applies CDT operations directly to an embedded collection in an
existing database record:

<!-- example-region:virtual-list:start -->
```java
// asBackedList() binds the Cart record and "items" bin to mapped Item values.
VirtualList<Item> items = mapper.asBackedList(cart, "items", Item.class);

// beginMultiOperation() groups both operations into one server request. The range
// is half-open: keys 150 (inclusive) through 350 (exclusive).
List<Item> selected = (List<Item>) items.beginMultiOperation()
        .append(new Item(300, "three hundred"))
        .getByKeyRange(150, 350)
        .end();
```

[Region source](examples/src/main/java/com/aerospike/mapper/virtuallist/VirtualListOperationsExample.java)
<!-- example-region:virtual-list:end -->

Virtual-list operations execute through the mapper's session and can combine several
operations into one record operation. They update database state only; they do not
modify the collection in the in-memory owning POJO. Supported result modes are
`DEFAULT`, `ELEMENTS`, `COUNT`, `INDEX`, and `NONE`.

### Reactive mapper

`ReactiveAeroMapper` is an asynchronous facade over the same `Session`:

<!-- example-region:reactive-mapper:start -->
```java
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

// Scans and queries use the standard java.util.concurrent.Flow API. Subscribe
// promptly: the current publisher starts producing as soon as scan() is called.
Flow.Publisher<Person> publisher = mapper.scan(Person.class);
```

[Region source](examples/src/main/java/com/aerospike/mapper/reactive/ReactiveMapperExample.java)
<!-- example-region:reactive-mapper:end -->

CRUD delegates to the synchronous mapper through `CompletableFuture.runAsync` or
`supplyAsync`. Scans and queries return `Flow.Publisher`. This facade is not a separate
fully non-blocking mapper and does not promise a particular executor or threading
model.

## Logging

Mapper warnings follow the Java SDK client's SLF4J-based logging standard. Applications
using this library should provide their own SLF4J provider and configure output,
levels, and sinks there.

## Reference notes

- This API is based on the SDK `Cluster`, `Session`, typed dataset, and AEL APIs. Legacy
  Java client policy and query APIs are intentionally not exposed.
- Runnable examples live under `examples/src/main/java` and
  `examples/src/test/java`; the latter also contains support tests and migrated legacy
  scenarios. Ordinary Maven test runs compile and execute this catalog, with AEL-based
  live examples skipped on Aerospike Server versions older than 8.2.
