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
