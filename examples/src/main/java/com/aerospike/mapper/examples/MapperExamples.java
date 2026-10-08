package com.aerospike.mapper.examples;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.policy.Behavior;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MapperExamples {
    private static final String NAMESPACE_PROPERTY = "aerospike.examples.namespace";
    private final ExampleConnectionFactory connectionFactory;

    public MapperExamples() {
        this((host, port) -> new ClusterDefinition(host, port).connect());
    }

    MapperExamples(ExampleConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public static void main(String[] args) {
        System.exit(new MapperExamples().run(args, System.out, System.err));
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        String priorNamespace = System.getProperty(NAMESPACE_PROPERTY);
        try {
            List<ExampleDefinition> registry = ExampleRegistry.examples();
            ExampleArguments arguments;
            try {
                arguments = ExampleArguments.parse(args, registry);
            } catch (IllegalArgumentException e) {
                err.println("CLI error: " + e.getMessage());
                err.println("Usage: list | all | <name>... [--host HOST] [--port PORT]"
                        + " [--namespace NS] [--allow-non-test-namespace] [--fail-fast]");
                return 2;
            }

            if (arguments.list()) {
                registry.forEach(definition ->
                        out.printf("%-20s %s%n", definition.name(), definition.description()));
                return 0;
            }

            System.setProperty(NAMESPACE_PROPERTY, arguments.namespace());
            Map<String, ExampleDefinition> byName = registry.stream().collect(
                    Collectors.toMap(ExampleDefinition::name, Function.identity()));
            List<ExampleDefinition> selected =
                    arguments.names().stream().map(byName::get).toList();

            try (Cluster cluster = connectionFactory.connect(arguments.host(), arguments.port())) {
                Session session = cluster.createSession(Behavior.DEFAULT);
                List<ExampleResult> results = new ExampleRunner().run(
                        selected, new ExampleContext(cluster, session, arguments.namespace()),
                        arguments.failFast());
                long passed = results.stream()
                        .filter(result -> result.status() == ExampleStatus.PASSED).count();
                long failed = results.size() - passed;
                for (ExampleResult result : results) {
                    out.printf("%-20s %s (%d ms)%n",
                            result.name(), result.status(), result.duration().toMillis());
                    if (result.cause() != null) {
                        err.println(result.name() + ": " + result.cause());
                        for (Throwable suppressed : result.cause().getSuppressed()) {
                            err.println("  suppressed cleanup failure: " + suppressed);
                        }
                    }
                }
                out.printf("Summary: %d passed, %d failed%n", passed, failed);
                return failed == 0 ? 0 : 1;
            } catch (Throwable bootstrapFailure) {
                err.println("Runner failure: " + bootstrapFailure);
                return 1;
            }
        } finally {
            if (priorNamespace == null) System.clearProperty(NAMESPACE_PROPERTY);
            else System.setProperty(NAMESPACE_PROPERTY, priorNamespace);
        }
    }
}
