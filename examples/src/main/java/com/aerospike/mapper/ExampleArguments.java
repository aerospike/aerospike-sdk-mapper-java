package com.aerospike.mapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ExampleArguments(
        boolean list,
        List<String> names,
        String host,
        int port,
        String namespace,
        boolean allowNonTestNamespace,
        boolean failFast) {

    public ExampleArguments {
        names = List.copyOf(names);
    }

    public static ExampleArguments parse(String[] args, List<ExampleDefinition> definitions) {
        Set<String> known = new HashSet<>();
        definitions.forEach(definition -> known.add(definition.name()));
        String host = "localhost";
        int port = 3000;
        String namespace = "test";
        boolean allow = false;
        boolean failFast = false;
        boolean list = false;
        boolean all = false;
        List<String> names = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--host" -> host = value(args, ++i, arg);
                case "--port" -> {
                    String value = value(args, ++i, arg);
                    try {
                        port = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid port: " + value);
                    }
                }
                case "--namespace" -> namespace = value(args, ++i, arg);
                case "--allow-non-test-namespace" -> allow = true;
                case "--fail-fast" -> failFast = true;
                case "list" -> list = true;
                case "all" -> all = true;
                default -> {
                    if (arg.startsWith("-")) throw new IllegalArgumentException("Unknown option: " + arg);
                    names.add(arg);
                }
            }
        }

        if (host.isBlank()) throw new IllegalArgumentException("Host must not be blank");
        if (namespace.isBlank()) throw new IllegalArgumentException("Namespace must not be blank");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
        if (list && (all || !names.isEmpty())) throw new IllegalArgumentException("list cannot be combined with examples");
        if (all && !names.isEmpty()) throw new IllegalArgumentException("all cannot be combined with named examples");
        if (list && failFast) throw new IllegalArgumentException("--fail-fast is only valid for live execution");
        if (args.length == 0) list = true;

        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!known.contains(name)) throw new IllegalArgumentException("Unknown example: " + name);
            if (!seen.add(name)) throw new IllegalArgumentException("Duplicate example: " + name);
        }
        if (all) names = definitions.stream().map(ExampleDefinition::name).toList();
        if (!list && names.isEmpty()) throw new IllegalArgumentException("Choose list, all, or one or more example names");
        if (!list && !"test".equals(namespace) && !allow) {
            throw new IllegalArgumentException(
                    "Refusing destructive cleanup in namespace '" + namespace
                            + "'; pass --allow-non-test-namespace to continue");
        }
        return new ExampleArguments(list, names, host, port, namespace, allow, failFast);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }
}
