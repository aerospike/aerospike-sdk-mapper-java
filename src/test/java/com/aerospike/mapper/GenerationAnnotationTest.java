package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.annotations.AerospikeGeneration;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;

import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;
import com.aerospike.mapper.tools.configuration.ClassConfig;

public class GenerationAnnotationTest extends MapperBaseTest {

    @BeforeEach
    public void truncateGenerationSets() {
        truncateSet(NAMESPACE, "generationTest");
        truncateSet(NAMESPACE, "generationTestInteger");
        truncateSet(NAMESPACE, "generationTestMethods");
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "generationTest")
    public static class GenerationEntity {
        @AerospikeKey
        private int id;
        private String name;
        @AerospikeGeneration
        private int generation;

        public GenerationEntity() {}

        public GenerationEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getGeneration() { return generation; }
        public void setGeneration(int generation) { this.generation = generation; }
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "generationTestInteger")
    public static class GenerationEntityWithInteger {
        @AerospikeKey
        private int id;
        private String name;
        @AerospikeGeneration
        private Integer generation;

        public GenerationEntityWithInteger() {}

        public GenerationEntityWithInteger(int id, String name) {
            this.id = id;
            this.name = name;
            this.generation = 0;
        }

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getGeneration() { return generation; }
        public void setGeneration(Integer generation) { this.generation = generation; }
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "generationTestMethods")
    public static class GenerationEntityWithMethods {
        @AerospikeKey
        private int id;
        private String name;
        private int generation;

        public GenerationEntityWithMethods() {}

        public GenerationEntityWithMethods(int id, String name) {
            this.id = id;
            this.name = name;
            this.generation = 0;
        }

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        @AerospikeGeneration
        public int getGeneration() { return generation; }
        
        @AerospikeGeneration
        public void setGeneration(int generation) { this.generation = generation; }
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "invalidGenerationTest")
    public static class InvalidGenerationEntity {
        @AerospikeKey
        private int id;
        @AerospikeGeneration
        private String generation; // Invalid type - should cause error

        public InvalidGenerationEntity() {}
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "multipleGenerationTest")
    public static class MultipleGenerationEntity {
        @AerospikeKey
        private int id;
        @AerospikeGeneration
        private int generation1;
        @AerospikeGeneration
        private int generation2; // Multiple @Generation fields - should cause error

        public MultipleGenerationEntity() {}
    }

    @Test
    public void testGenerationFieldMapping() {
        MappingSession mapper = createMapper();
        
        // Start with a clean slate
        mapper.delete(GenerationEntity.class, 1);

        // Create and save entity
        GenerationEntity entity = new GenerationEntity(1, "Test Entity");
        mapper.save(entity);
        
        // Read back the entity
        GenerationEntity readEntity = mapper.read(GenerationEntity.class, 1);
        
        assertNotNull(readEntity);
        assertEquals(1, readEntity.getId());
        assertEquals("Test Entity", readEntity.getName());
        assertEquals(1, readEntity.getGeneration()); // Should be 1 after first save
    }

    @Test
    public void testGenerationFieldMappingWithInteger() {
        MappingSession mapper = createMapper();
        // Start with a clean slate
        mapper.delete(GenerationEntityWithInteger.class, 2);
        
        // Create and save entity
        GenerationEntityWithInteger entity = new GenerationEntityWithInteger(2, "Test Entity Integer");
        mapper.save(entity);
        
        // Read back the entity
        GenerationEntityWithInteger readEntity = mapper.read(GenerationEntityWithInteger.class, 2);
        
        assertNotNull(readEntity);
        assertEquals(2, readEntity.getId());
        assertEquals("Test Entity Integer", readEntity.getName());
        assertEquals(Integer.valueOf(1), readEntity.getGeneration()); // Should be 1 after first save
    }

    @Test
    public void testGenerationFieldMappingWithMethods() {
        MappingSession mapper = createMapper();
        
        // Start with a clean slate
        mapper.delete(GenerationEntityWithMethods.class, 3);

        // Create and save entity
        GenerationEntityWithMethods entity = new GenerationEntityWithMethods(3, "Test Entity Methods");
        mapper.save(entity);
        
        // Read back the entity
        GenerationEntityWithMethods readEntity = mapper.read(GenerationEntityWithMethods.class, 3);
        
        assertNotNull(readEntity);
        assertEquals(3, readEntity.getId());
        assertEquals("Test Entity Methods", readEntity.getName());
        assertEquals(1, readEntity.getGeneration()); // Should be 1 after first save
    }

    @Test
    public void testOptimisticConcurrencyControl() {
        MappingSession mapper = createMapper();
        
        // Start with a clean slate
        mapper.delete(GenerationEntity.class, 4);

        // Create and save entity
        GenerationEntity entity = new GenerationEntity(4, "Concurrency Test");
        mapper.save(entity);
        
        // Read the entity to get the current generation
        GenerationEntity readEntity1 = mapper.read(GenerationEntity.class, 4);
        GenerationEntity readEntity2 = mapper.read(GenerationEntity.class, 4);
        
        // Both should have the same generation
        assertEquals(readEntity1.getGeneration(), readEntity2.getGeneration());
        
        // Update first entity
        readEntity1.setName("Updated by first");
        mapper.save(readEntity1);
        
        // Try to update second entity with stale generation - should fail
        readEntity2.setName("Updated by second");
        assertThrows(AerospikeException.class, () -> {
            mapper.save(readEntity2);
        });
    }

    @Test
    public void testGenerationIncrementOnUpdate() {
        MappingSession mapper = createMapper();
        
        // Start with a clean slate
        mapper.delete(GenerationEntity.class, 5);

        // Create and save entity
        GenerationEntity entity = new GenerationEntity(5, "Generation Increment Test");
        mapper.insert(entity);
        
        // Read and verify initial generation
        GenerationEntity readEntity = mapper.read(GenerationEntity.class, 5);
        assertEquals(1, readEntity.getGeneration());
        
        // Update and save
        readEntity.setName("Updated Name");
        mapper.save(readEntity);
        
        // Read again and verify generation incremented
        GenerationEntity updatedEntity = mapper.read(GenerationEntity.class, 5);
        assertEquals(2, updatedEntity.getGeneration());
        assertEquals("Updated Name", updatedEntity.getName());
    }

    @Test
    public void testInvalidGenerationFieldType() {
        assertThrows(AerospikeException.class, () -> {
            MappingSession mapper = createMapper();
            InvalidGenerationEntity entity = new InvalidGenerationEntity();
            entity.id = 6;
            mapper.save(entity);
        });
    }

    @Test
    public void testMultipleGenerationFields() {
        assertThrows(AerospikeException.class, () -> {
            MappingSession mapper = createMapper();
            MultipleGenerationEntity entity = new MultipleGenerationEntity();
            entity.id = 7;
            mapper.save(entity);
        });
    }

    @Test
    public void testGenerationThroughConfiguration() {
        // Test generation field configuration through code
        ClassConfig config = new ClassConfig.Builder(GenerationEntity.class)
                .withFieldNamed("generation").asGenerationField()
                .build();
        
        MappingSession mapper = createMapper(MappingEngine.builder().withClassConfigurations(config));
        
        // Start with a clean slate
        mapper.delete(GenerationEntity.class, 8);

        GenerationEntity entity = new GenerationEntity(8, "Config Test");
        mapper.save(entity);
        
        GenerationEntity readEntity = mapper.read(GenerationEntity.class, 8);
        assertEquals(1, readEntity.getGeneration());
    }

    @Test
    public void testZeroGenerationDoesNotSetGeneration() {
        MappingSession mapper = createMapper();
        
        // Start with a clean slate
        mapper.delete(GenerationEntity.class, 10);

        // Create entity with generation 0 (should not set generation policy)
        GenerationEntity entity = new GenerationEntity(10, "Zero Generation Test");
        entity.setGeneration(0);
        
        // This should work fine as no generation policy is set
        mapper.save(entity);
        
        GenerationEntity readEntity = mapper.read(GenerationEntity.class, 10);
        assertEquals(1, readEntity.getGeneration()); // Should be 1 after save
    }
}
