package com.aerospike.mapper.examples;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExampleRegistryTests {
    @Test
    void registryHasStableOwnedCatalog() {
        List<ExampleDefinition> examples = ExampleRegistry.examples();
        assertEquals(List.of(
                "quick-start", "sdk-interop", "relationships", "configuration",
                "scan-query", "optimistic-locking", "virtual-list", "reactive",
                "banking-domain", "mapper-customer", "manual-sdk-customer",
                "mapping-features", "ad-tech-segments", "virtual-list-advanced"),
                examples.stream().map(ExampleDefinition::name).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> examples.add(examples.getFirst()));

        Set<String> names = new HashSet<>();
        for (ExampleDefinition definition : examples) {
            assertTrue(names.add(definition.name()));
            assertFalse(definition.description().isBlank());
            assertNotNull(definition.example());
            assertNotNull(definition.fixture());
            definition.fixture().ownedSets().forEach(
                    name -> assertTrue(name.startsWith("sdk_mapper_examples_"), name));
            definition.fixture().ownedIndexes().forEach(
                    name -> assertTrue(name.startsWith("sdk_mapper_examples_"), name));
        }
    }
}
