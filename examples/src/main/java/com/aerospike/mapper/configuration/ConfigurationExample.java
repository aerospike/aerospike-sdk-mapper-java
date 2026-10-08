package com.aerospike.mapper.configuration;

import com.aerospike.client.sdk.Session;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.FromAerospike;
import com.aerospike.mapper.annotations.ToAerospike;
import com.aerospike.mapper.ExampleContext;
import com.aerospike.mapper.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.configuration.ClassConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.aerospike.mapper.ExampleAssertions.equal;
import static com.aerospike.mapper.ExampleAssertions.require;

/**
 * Demonstrates the three ways to customize mapping without changing mapper internals:
 * YAML metadata, programmatic {@link ClassConfig}, and value converters.
 */
public final class ConfigurationExample implements RunnableExample {
    public static final String YAML_SET = "sdk_mapper_examples_yaml";
    public static final String CODE_SET = "sdk_mapper_examples_code";
    public static final String CONVERTER_SET = "sdk_mapper_examples_converter";

    // This class intentionally has no mapper annotations. Its complete mapping comes
    // from mappings/example-mapping.yaml.
    public static class YamlPerson {
        public String id;
        public String name;
        public YamlPerson() {}
        public YamlPerson(String id, String name) { this.id = id; this.name = name; }
    }

    // This second unannotated class is configured entirely through ClassConfig.
    public static class CodePerson {
        public String id;
        public String name;
        public CodePerson() {}
        public CodePerson(String id, String name) { this.id = id; this.name = name; }
    }

    // @start region="custom-converter"
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
    // @end region="custom-converter"

    @Override
    public void run(ExampleContext context) throws IOException {
        String namespace = context.namespace();
        Session session = context.session();

        // Load the checked-in YAML through the classpath, then build a mapper that uses
        // those external class definitions.
        byte[] yaml = configuredYaml(namespace);
        AeroMapper yamlMapper = new AeroMapper.Builder(session)
                .withConfigurationFile(new ByteArrayInputStream(yaml))
                .build();
        YamlPerson yamlPerson = new YamlPerson("yaml-1", "Ada Lovelace");
        yamlMapper.save(yamlPerson);
        YamlPerson yamlLoaded = yamlMapper.read(YamlPerson.class, "yaml-1");
        equal("Ada Lovelace", yamlLoaded.name, "YAML round trip");
        equal("fullName", yamlMapper.getBinNames(YamlPerson.class).get("name"), "YAML bin name");

        // @start region="code-configuration"
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
        // @end region="code-configuration"

        codeMapper.save(new CodePerson("code-1", "Grace Hopper"));
        CodePerson codeLoaded = codeMapper.read(CodePerson.class, "code-1");
        equal("Grace Hopper", codeLoaded.name, "code configuration round trip");
        equal("fullName", codeMapper.getBinNames(CodePerson.class).get("name"), "code bin name");

        // The converter-backed mapper round-trips Money through an Aerospike integer.
        AeroMapper converterMapper = moneyMapper(session);
        converterMapper.save(new Wallet("wallet-1", new Money(12345)));
        Wallet wallet = converterMapper.read(Wallet.class, "wallet-1");
        equal(new Money(12345), wallet.balance, "converter round trip");
    }

    private byte[] configuredYaml(String namespace) throws IOException {
        ClassLoader loader = ConfigurationExample.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream("mappings/example-mapping.yaml")) {
            require(input != null, "Missing mappings/example-mapping.yaml");
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            // External configuration values do not use annotation placeholder
            // expansion. Replace the single checked-in default namespace explicitly
            // when the runner targets another namespace.
            String marker = "namespace: test";
            int first = yaml.indexOf(marker);
            require(first >= 0 && first == yaml.lastIndexOf(marker),
                    "YAML must contain exactly one default namespace scalar");
            if (!"test".equals(namespace)) {
                yaml = yaml.replace(marker, "namespace: " + namespace);
            }
            return yaml.getBytes(StandardCharsets.UTF_8);
        }
    }
}
