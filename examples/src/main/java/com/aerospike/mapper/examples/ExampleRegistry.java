package com.aerospike.mapper.examples;

import com.aerospike.mapper.examples.configuration.ConfigurationExample;
import com.aerospike.mapper.examples.generation.OptimisticLockingExample;
import com.aerospike.mapper.examples.interop.SdkInteropExample;
import com.aerospike.mapper.examples.query.ScanAndQueryExample;
import com.aerospike.mapper.examples.quickstart.QuickStartExample;
import com.aerospike.mapper.examples.reactive.ReactiveMapperExample;
import com.aerospike.mapper.examples.relationships.RelationshipsExample;
import com.aerospike.mapper.examples.virtuallist.VirtualListOperationsExample;

import java.util.List;

public final class ExampleRegistry {
    private ExampleRegistry() {}

    public static List<ExampleDefinition> examples() {
        return List.of(
                new ExampleDefinition("quick-start", "CRUD and order-preserving batch reads",
                        new QuickStartExample(), ExampleFixture.cleanSets(QuickStartExample.SET)),
                new ExampleDefinition("sdk-interop", "Mapper metadata and typed SDK operations",
                        new SdkInteropExample(), ExampleFixture.cleanSets(SdkInteropExample.SET)),
                new ExampleDefinition("relationships", "Constructors, embedding, and references",
                        new RelationshipsExample(), ExampleFixture.cleanSets(
                                RelationshipsExample.PEOPLE_SET, RelationshipsExample.ACCOUNTS_SET)),
                new ExampleDefinition("configuration", "YAML, code, and converter configuration",
                        new ConfigurationExample(), ExampleFixture.cleanSets(
                                ConfigurationExample.YAML_SET,
                                ConfigurationExample.CODE_SET,
                                ConfigurationExample.CONVERTER_SET)),
                new ExampleDefinition("scan-query", "Scans, callbacks, and AEL queries",
                        new ScanAndQueryExample(), ExampleFixture.cleanSetWithIndex(
                                ScanAndQueryExample.SET, ScanAndQueryExample.INDEX, "age")),
                new ExampleDefinition("optimistic-locking", "Generation-based optimistic locking",
                        new OptimisticLockingExample(),
                        ExampleFixture.cleanSets(OptimisticLockingExample.SET)),
                new ExampleDefinition("virtual-list", "Server-backed virtual list operations",
                        new VirtualListOperationsExample(),
                        ExampleFixture.cleanSets(VirtualListOperationsExample.SET)),
                new ExampleDefinition("reactive", "CompletableFuture and Flow APIs",
                        new ReactiveMapperExample(), ExampleFixture.cleanSets(ReactiveMapperExample.SET)));
    }
}
