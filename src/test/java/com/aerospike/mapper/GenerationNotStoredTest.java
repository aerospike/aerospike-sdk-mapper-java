package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeGeneration;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.ClassCacheEntry;
import com.aerospike.mapper.tools.utils.MapperUtils;

public class GenerationNotStoredTest extends AeroMapperBaseTest {

    @AerospikeRecord(namespace = NAMESPACE, set = "generationNotStoredTest")
    public static class EntityWithGeneration {
        @AerospikeKey
        private int id;
        private String name;
        @AerospikeGeneration
        private Integer generation = 0;

        public EntityWithGeneration() {}

        public EntityWithGeneration(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getGeneration() { return generation; }
        public void setGeneration(Integer generation) { this.generation = generation; }
    }

    @Test
    public void testGenerationFieldNotStoredAsBin() {
        AeroMapper mapper = new AeroMapper.Builder(session).build();
        EntityWithGeneration entity = new EntityWithGeneration(1, "test");
        entity.setGeneration(5);

        ClassCacheEntry<EntityWithGeneration> entry = MapperUtils.getEntryAndValidateNamespace(EntityWithGeneration.class, mapper);
        Map<String, Object> binMap = entry.getMap(entity, false, null);

        assertTrue(binMap.containsKey("name"), "Should find 'name' bin");
        assertEquals("test", binMap.get("name"));
        assertFalse(binMap.containsKey("generation"), "Should NOT find 'generation' bin - it should not be stored");
    }
}
