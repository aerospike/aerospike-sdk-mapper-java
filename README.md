# Aerospike SDK Mapper Java

Java Object Mapper for the [Aerospike Java SDK](https://github.com/aerospike/aerospike-client-java-sdk). 
Maps annotated or externally-configured Java POJOs to Aerospike records and back, providing CRUD, batch read, 
query/scan support.

**Status:** Work in progress.

**SDK dependency:** Requires the `stage` branch of the Aerospike Java SDK (`com.aerospike:aerospike-client-sdk`), 
installed to the local Maven repository.

## Logging

Mapper warnings follow the Java SDK client's SLF4J-based logging standard.
Applications using this library should provide their own SLF4J provider and
configure output, levels, and sinks there.

## Mapping Metadata

`AeroMapper` exposes record metadata such as namespace, set, key, and bin names. Use
`getBinNames(Class<?>)` to resolve Java field names to their stored Aerospike bin names
when mapper-managed objects are used with ordinary SDK operations.

Load the map once after the mapper exists, then keep the bin names as constants on hot
paths:

```java
public final class CustomerStore {
    private final TypedDataSet<Customer> customers;
    private final String salutationBin;
    private final String loyaltyPointsBin;

    public CustomerStore(AeroMapper mapper) {
        Map<String, String> bins = mapper.getBinNames(Customer.class);
        this.customers = mapper.getTypedDataSet(Customer.class);
        this.salutationBin = bins.get("preferredSalutation");
        this.loyaltyPointsBin = bins.get("loyaltyPoints");
    }

    public void setLoyaltyPoints(Session session, String customerId, int points) {
        session.update(customers.id(customerId))
                .bin(loyaltyPointsBin)
                .setTo(points)
                .execute();
    }
}
```

The returned map is immutable, includes mapped superclass fields, and respects
`@AerospikeBin` and YAML/config bin names. Fields excluded from persistence, generation
fields, method-only `@AerospikeGetter` bins, and keys with `storeAsBin = false` are not
included.
