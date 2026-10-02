# Aerospike SDK Mapper Java

The Aerospike Java SDK Object Mapper maps annotated or externally configured Java POJOs to
Aerospike records and back. It is built on the
[Aerospike Java SDK](https://github.com/aerospike/aerospike-client-java-sdk), integrates through Session API
and provides object CRUD, batch reads, scans, AEL queries, typed SDK interoperability,
references, embeds, and database-backed virtual lists.

## Status and requirements

This project is a work in progress. The current mapper version is `1.0.0-SNAPSHOT` and
requires:

- Java 21
- The latest state of the Java SDK `stage` branch installed locally as `com.aerospike:aerospike-client-sdk:0.9.0-alpha.2`
- An Aerospike server for integration tests and database operations

The mapper and its SDK dependency are pre-release artifacts. It is not assumed that either
artifact is available from Maven Central.

## Installation

Until the required artifacts are published, install a matching checkout of the Java SDK
and then this project into your local Maven repository:

```shell
cd ~/Repos/aerospike-client-java-sdk
mvn install

cd ~/Repos/aerospike-sdk-mapper-java
mvn install
```

Applications can then use the locally installed mapper:

```xml
<dependency>
    <groupId>com.aerospike</groupId>
    <artifactId>aerospike-sdk-mapper-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Table of contents

1. [Quick start](#quick-start)
2. [Using Session and SDK APIs](#using-session-and-sdk-apis)
3. [Mapper API basics](#mapper-api-basics)
4. [Mapping Java objects](#mapping-java-objects)
5. [Advanced features](#advanced-features)
6. [Logging](#logging)
7. [Reference notes](#reference-notes)

## Quick start

Create the SDK `Cluster` and `Session`, pass the session to the mapper, and annotate the
class to describe its namespace, set, key, and bins:

```java
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.AeroMapper;

public final class MapperQuickStart {
    @AerospikeRecord(namespace = "test", set = "people")
    public static class Person {
        @AerospikeKey
        public String id;

        @AerospikeBin(name = "fullName")
        public String name;

        public int age;

        public Person() {
        }

        public Person(String id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }
    }

    public static void main(String[] args) {
        Cluster cluster = new ClusterDefinition("127.0.0.1", 3000).connect();
        try {
            Session session = cluster.createSession(Behavior.DEFAULT);
            AeroMapper mapper = new AeroMapper.Builder(session).build();

            Person person = new Person("person-1", "Ada Lovelace", 36);
            mapper.insert(person);                       // create only

            Person loaded = mapper.read(Person.class, "person-1");
            loaded.age = 37;
            mapper.update(loaded, "age");                // merge this bin
            mapper.save(loaded);                         // create or completely replace

            mapper.delete(loaded);
        } finally {
            cluster.close();
        }
    }
}
```

The mapper retains and uses the caller-provided `Session`; it does not own the
`Cluster`. The same session can be used for mapper calls and ordinary SDK operations.
Close the owning cluster when the application is finished with it.

## Using Session and SDK APIs

### Mapping metadata

`AeroMapper` exposes `getNamespace`, `getSet`, `getKey`, `getRecordKey`, and
`getBinNames`. Use `getBinNames(Class<?>)` to resolve Java field names to stored bin
names before issuing ordinary SDK operations:

```java
public final class PersonStore {
    private final TypedDataSet<Person> people;
    private final String ageBin;

    public PersonStore(AeroMapper mapper) {
        Map<String, String> bins = mapper.getBinNames(Person.class);
        this.people = mapper.getTypedDataSet(Person.class);
        this.ageBin = bins.get("age");
    }

    public void setAge(Session session, String id, int age) {
        session.update(people.id(id))
                .bin(ageBin)
                .setTo(age)
                .execute();
    }
}
```

The returned bin-name map is immutable, includes mapped superclass fields, and respects
annotation, YAML, and code-configured names. It excludes non-persistent fields,
generation fields, method-only `@AerospikeGetter` bins, and keys with
`storeAsBin = false`. Resolve and retain these names rather than repeatedly looking them
up on hot paths.

### Typed SDK mapping

Register the mapper's `RecordMappingFactory` on the cluster before using SDK typed
reads or object writes:

```java
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.TypedRecordStream;

AeroMapper mapper = new AeroMapper.Builder(session).build();
cluster.setRecordMappingFactory(mapper.asMappingFactory());

TypedDataSet<Person> people = mapper.getTypedDataSet(Person.class);
Person person = new Person("person-2", "Grace Hopper", 40);
session.insert(people).object(person).execute();

try (TypedRecordStream<Person> records =
         session.query(people.id("person-2")).execute()) {
    Person loaded = records.getFirstObject().orElse(null);
}
```

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

> **Partial-write warning:** `save(object, binNames...)` still uses replace semantics.
> It can create a record, or completely replace an existing record, containing only the
> selected bins. Use
> `update(object, binNames...)` when selected bins should be merged with bins already in
> the record.

### Scans and AEL queries

Use `scan` for a full set and `query` for an Aerospike Expression Language filter:

```java
List<Person> everyone = mapper.scan(Person.class);
List<Person> workingAge =
        mapper.query(Person.class, "$.age >= 30 and $.age <= 54");

mapper.query(Person.class, person -> {
    process(person);
    return shouldContinue();
}, "$.age >= 30");
```

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

```java
@AerospikeConstructor
public Person(@ParamFrom("id") String id,
              @ParamFrom("fullName") String name,
              @ParamFrom("age") int age) {
    this.id = id;
    this.name = name;
    this.age = age;
}
```

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

```java
@AerospikeRecord(namespace = "test", set = "people", sendKey = true)
public class Person {
    @AerospikeKey(storeAsBin = false)
    private String id;
}
```

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
be saved separately. `@AerospikeReference` makes the choice explicit:

```java
@AerospikeReference(lazy = false, batchLoad = true)
private Account primaryAccount;
```

Non-lazy dependencies are resolved in batches where possible. `lazy = true` creates a
placeholder with its key populated but does not load the rest of the child.
`type = ReferenceType.DIGEST` stores a digest instead of an ID. Digest references cannot
be lazy because the digest cannot be represented in the child's ordinary key field.

Use `@AerospikeEmbed` when the child belongs inside the parent record:

```java
@AerospikeEmbed(type = EmbedType.MAP)
private Address address;

@AerospikeEmbed(type = EmbedType.MAP,
                elementType = EmbedType.LIST,
                saveKey = false)
private List<Transaction> transactions;
```

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

```java
public final class MoneyConverter {
    @ToAerospike
    public String write(Money money) {
        return money == null ? null : money.toString();
    }

    @FromAerospike
    public Money read(String value) {
        return value == null ? null : Money.parse(value);
    }
}

AeroMapper mapper = new AeroMapper.Builder(session)
        .addConverter(new MoneyConverter())
        .build();
```

Converters are global for their Java type and take precedence over built-in mappers.

### YAML and code configuration

Use external configuration when the source class cannot be annotated or mappings vary
by environment:

```yaml
---
classes:
  - class: com.example.Person
    namespace: test
    set: people
    sendKey: true
    key:
      field: id
      storeAsBin: false
    bins:
      - field: name
        name: fullName
      - field: address
        embed:
          type: MAP
```

The current YAML model supports class settings including `class`, `namespace`, `set`,
`factoryClass`, `factoryMethod`, `durableDelete`, `mapAll`, `sendKey`, `ttl`,
`shortName`, and `version`; key settings; and bin settings including `field`, `name`,
`getter`, `setter`, `exclude`, `useAccessors`, `ordinal`, `embed`, `reference`, and
`generation`.

Equivalent programmatic configuration uses `ClassConfig`:

```java
ClassConfig addressConfig = new ClassConfig.Builder(Address.class)
        .withKeyField("id")
        .build();
ClassConfig personConfig = new ClassConfig.Builder(Person.class)
        .withFieldNamed("address")
        .beingEmbeddedAs(AerospikeEmbed.EmbedType.MAP)
        .build();

AeroMapper mapper = new AeroMapper.Builder(session)
        .withClassConfigurations(personConfig, addressConfig)
        .build();
```

### Generation checks

Mark one `int` or `Integer` field or getter/setter property with
`@AerospikeGeneration`. Reads copy the server record generation into that member. A
write with a non-zero value calls `ensureGenerationIs`, so a stale object fails instead
of silently overwriting a newer generation. The generation is metadata and is not
stored as a bin.

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

```java
VirtualList<Item> items =
        mapper.asBackedList(container, "items", Item.class);

items.append(new Item(500, new Date(), "new item"));

long removed = (long) items.beginMultiOperation()
        .removeByKeyRange(100, 200)
        .asResultOfType(ReturnType.COUNT)
        .end();
```

Virtual-list operations execute through the mapper's session and can combine several
operations into one record operation. They update database state only; they do not
modify the collection in the in-memory owning POJO. Supported result modes are
`DEFAULT`, `ELEMENTS`, `COUNT`, `INDEX`, and `NONE`.

### Reactive mapper

`ReactiveAeroMapper` is an asynchronous facade over the same `Session`:

```java
ReactiveAeroMapper reactive =
        new ReactiveAeroMapper.Builder(session).build();

CompletableFuture<Person> person =
        reactive.read(Person.class, "person-1");
Flow.Publisher<Person> adults =
        reactive.query(Person.class, "$.age >= 18");
```

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
- The mapper is still pre-release; verify mappings and stored representations against
  the version used by your application before deploying schema changes.
- Executable examples and behavior coverage live under `src/test/java`.
