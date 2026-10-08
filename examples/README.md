# Runnable mapper examples

This catalog exercises the mapper's public API against a live Aerospike server.
Example sources are part of the normal Maven test source roots so IDE navigation,
refactoring, and compile checks work out of the box. Published artifacts do not
contain them.

## Prerequisites

- Java 21
- The matching `com.aerospike:aerospike-client-sdk` artifact installed locally
- A live Aerospike server (defaults: `localhost:3000`, namespace `test`)

## Catalog

The registry order is authoritative:

1. `quick-start` — CRUD and order-preserving batch reads
2. `sdk-interop` — mapper metadata and typed SDK operations
3. `relationships` — constructors, embedding, and references
4. `configuration` — YAML, code, and custom converters
5. `scan-query` — scans, callbacks, and indexed AEL queries
6. `optimistic-locking` — generation checks
7. `virtual-list` — server-backed collection operations
8. `reactive` — `CompletableFuture` and `Flow` APIs
9. `banking-domain` — complex graph, inherited mappings, and composite keys
10. `mapper-customer` — focused mapper customer round trip
11. `manual-sdk-customer` — the same customer schema written with direct SDK calls
12. `mapping-features` — legacy embedding, references, versions, and ordinals
13. `ad-tech-segments` — value-ordered segment expiration and range reads
14. `virtual-list-advanced` — remove, count, and range multi-operations

The final six entries preserve the original examples formerly stored in the ordinary
test source tree. Their source remains under `examples/src/test/java` because they are
illustrative programs reused by the standalone runner, not production sources.

## Default Maven test run

Examples run by default as part of the repository's normal Maven verification:

```shell
mvn test
```

That command compiles example sources, runs the non-live support tests, and runs the
live example catalog through a JUnit smoke test. The live smoke test skips on
Aerospike Server versions older than 8.2 because the `scan-query` example uses AEL.

## Separate example runs

Run examples from the repository root with the wrapper script:

```shell
./examples/run_examples list
./examples/run_examples quick-start
./examples/run_examples all --fail-fast
```

Alternatively, invoke the runner through Maven:

```shell
mvn -Pexamples -DskipTests test-compile exec:java \
  -Dexec.arguments=all,--fail-fast
```

Options are `--host`, `--port`, `--namespace`, `--allow-non-test-namespace`, and
`--fail-fast`. For Maven, pass these as comma-separated `exec.arguments`, for example
`all,--host,192.168.1.20,--port,3010,--fail-fast`. Multiple scenario names can be
supplied in the desired order.

## Safety and lifecycle

The runner owns every fixed `sdk_mapper_examples_*` set and index. It truncates owned
sets before and after each scenario, manages the query index, and clears mapper metadata
between scenarios. Destructive work outside namespace `test` requires the explicit
`--allow-non-test-namespace` flag. Concurrent runs in the same namespace are not
supported.

One cluster and session are shared by a selected run. Scenario code receives those
objects but never creates or closes them. Fixtures own setup and cleanup, keeping the
copyable application code focused on public mapper and SDK calls. Every migrated
top-level record uses an `sdk_mapper_examples_*` set and the runtime namespace
placeholder, so custom namespaces follow the same explicit safety gate.

## Source regions and README synchronization

Selected Java and YAML blocks use JDK-style named markers:

```java
// @start region="region-name"
// public mapper or SDK calls
// @end region="region-name"
```

Update or verify generated root README spans with the dependency-free Java 21 tool:

```shell
./examples/sync_readme --write
./examples/sync_readme --check
java examples/tools/ReadmeSnippetSync.java --check
```

The root README generates only selected snippets. Connection bootstrap and explanatory
prose remain hand-maintained; each generated block links to its complete scenario.

## Verification

Run the repository tests and examples:

```shell
mvn test
```

Run only the non-live support tests:

```shell
mvn -Dtest='ExampleArgumentsTests,ExampleRegistryTests,ExampleRunnerTests,MapperExamplesTests,ReadmeSnippetSyncTests' \
  test
```

Run every live scenario manually:

```shell
./examples/run_examples all --fail-fast
```

Support tests validate CLI, registry, lifecycle, and snippet synchronization behavior.
Live examples validate database behavior.
