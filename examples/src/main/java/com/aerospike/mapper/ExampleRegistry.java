package com.aerospike.mapper;

import com.aerospike.mapper.configuration.ConfigurationExample;
import com.aerospike.mapper.generation.OptimisticLockingExample;
import com.aerospike.mapper.interop.SdkInteropExample;
import com.aerospike.mapper.model.Branch;
import com.aerospike.mapper.model.Checkbook;
import com.aerospike.mapper.model.Customer;
import com.aerospike.mapper.model.Property;
import com.aerospike.mapper.model.accounts.Account;
import com.aerospike.mapper.model.accounts.PortfolioAccount;
import com.aerospike.mapper.query.ScanAndQueryExample;
import com.aerospike.mapper.quickstart.QuickStartExample;
import com.aerospike.mapper.reactive.ReactiveMapperExample;
import com.aerospike.mapper.relationships.RelationshipsExample;
import com.aerospike.mapper.virtuallist.VirtualListOperationsExample;

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
                        new ReactiveMapperExample(), ExampleFixture.cleanSets(ReactiveMapperExample.SET)),
                new ExampleDefinition("banking-domain",
                        "Complex banking graph, inheritance, and composite keys",
                        new Application(), ExampleFixture.cleanSets(
                                Customer.SET, Account.SET, PortfolioAccount.SET, Property.SET,
                                Branch.SET, Checkbook.SET)),
                new ExampleDefinition("mapper-customer",
                        "Focused customer round trip through AeroMapper",
                        new JavaMapperApplication(), ExampleFixture.cleanSets(Customer.SET)),
                new ExampleDefinition("manual-sdk-customer",
                        "Equivalent customer round trip with direct SDK bin operations",
                        new NonJavaMapperApplication(), ExampleFixture.cleanSets(Customer.SET)),
                new ExampleDefinition("mapping-features",
                        "Embedding, references, versions, ordinals, and constructors",
                        new AeroMapperDocExamples(), ExampleFixture.cleanSets(
                                AeroMapperDocExamples.PRODUCT_SET,
                                AeroMapperDocExamples.ACCOUNT_SET,
                                AeroMapperDocExamples.PEOPLE_SET,
                                AeroMapperDocExamples.VERSIONED_SET,
                                AeroMapperDocExamples.TRANSACTION_ACCOUNT_SET,
                                AeroMapperDocExamples.TRANSACTION_SET,
                                AeroMapperDocExamples.PARENT_SET,
                                AeroMapperDocExamples.CHILD_SET)),
                new ExampleDefinition("ad-tech-segments",
                        "Value-ordered ad segments with expiration and range reads",
                        new AdTechUseCaseExample(),
                        ExampleFixture.cleanSets(AdTechUseCaseExample.SET)),
                new ExampleDefinition("virtual-list-advanced",
                        "Advanced virtual-list remove, count, and range operations",
                        new VirtualListExample(), ExampleFixture.cleanSets(
                                VirtualListExample.ITEM_SET,
                                VirtualListExample.CONTAINER_SET)));
    }
}
