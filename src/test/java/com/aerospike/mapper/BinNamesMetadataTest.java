package com.aerospike.mapper;

import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeExclude;
import com.aerospike.mapper.annotations.AerospikeGeneration;
import com.aerospike.mapper.annotations.AerospikeGetter;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeSetter;
import com.aerospike.mapper.tools.AeroMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BinNamesMetadataTest extends AeroMapperBaseTest {

    @BeforeEach
    public void setup() {
        truncateSet(NAMESPACE, "binNamesMetadata");
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "binNamesMetadata", sendKey = true)
    public static class AnnotationMappedBase {
        @AerospikeKey(storeAsBin = false)
        protected String externalId;
        protected String inheritedName;
        @AerospikeBin(name = "base-clash")
        protected String clash;
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "binNamesMetadata", sendKey = true)
    public static class AnnotationMappedChild extends AnnotationMappedBase {
        @AerospikeBin(name = "renamed")
        private String explicit;
        private int defaultName;
        @AerospikeBin(useAccessors = true)
        private String accessorBacked;
        @AerospikeExclude
        private String ignored;
        @AerospikeGeneration
        private int generation;
        @AerospikeBin(name = "child-clash")
        private String clash;

        public AnnotationMappedChild() {
        }

        public AnnotationMappedChild(String externalId) {
            this.externalId = externalId;
            this.inheritedName = "inherited";
            super.clash = "base";
            this.explicit = "explicit";
            this.defaultName = 17;
            this.accessorBacked = "accessor";
            this.ignored = "ignored";
            this.generation = 0;
            this.clash = "child";
        }

        public String getAccessorBacked() {
            return accessorBacked;
        }

        public void setAccessorBacked(String accessorBacked) {
            this.accessorBacked = accessorBacked;
        }

        @AerospikeGetter(name = "computed")
        public String getComputed() {
            return "computed";
        }

        @AerospikeSetter(name = "computed")
        public void setComputed(String computed) {
        }
    }

    public static class ConfigMapped {
        private String id;
        private String accessorConfigured;
        private String ordinary;

        public String getAccessorConfigured() {
            return accessorConfigured;
        }

        public void setAccessorConfigured(String accessorConfigured) {
            this.accessorConfigured = accessorConfigured;
        }
    }

    @Test
    public void getBinNamesReflectsAnnotationMappingRules() {
        AeroMapper mapper = new AeroMapper.Builder(session).build();

        Map<String, String> binNames = mapper.getBinNames(AnnotationMappedChild.class);

        assertEquals("renamed", binNames.get("explicit"));
        assertEquals("defaultName", binNames.get("defaultName"));
        assertEquals("accessorBacked", binNames.get("accessorBacked"));
        assertEquals("inheritedName", binNames.get("inheritedName"));
        assertEquals("child-clash", binNames.get("clash"));
        assertFalse(binNames.containsKey("externalId"));
        assertFalse(binNames.containsKey("ignored"));
        assertFalse(binNames.containsKey("generation"));
        assertFalse(binNames.containsKey("computed"));
    }

    @Test
    public void getBinNamesUsesYamlConfigurationRules() {
        String yaml = "---\n" +
                "classes:\n" +
                "  - class: " + ConfigMapped.class.getName() + "\n" +
                "    namespace: test\n" +
                "    set: binNamesConfig\n" +
                "    sendKey: true\n" +
                "    key:\n" +
                "      field: id\n" +
                "      storeAsBin: false\n" +
                "    bins:\n" +
                "      - field: accessorConfigured\n" +
                "        name: cfgAccessor\n" +
                "        useAccessors: true\n" +
                "      - field: ordinary\n" +
                "        name: cfgOrdinary\n";
        AeroMapper mapper = new AeroMapper.Builder(session).withConfiguration(yaml).build();

        Map<String, String> binNames = mapper.getBinNames(ConfigMapped.class);

        assertEquals("cfgAccessor", binNames.get("accessorConfigured"));
        assertEquals("cfgOrdinary", binNames.get("ordinary"));
        assertFalse(binNames.containsKey("id"));
    }

    @Test
    public void getBinNamesReturnsImmutableMapWithoutRequiringIdentityStability() {
        AeroMapper mapper = new AeroMapper.Builder(session).build();

        Map<String, String> binNames = mapper.getBinNames(AnnotationMappedChild.class);
        Map<String, String> copy = Map.copyOf(binNames);

        assertThrows(UnsupportedOperationException.class, () -> binNames.put("explicit", "mutated"));
        assertEquals(copy, mapper.getBinNames(AnnotationMappedChild.class));
        assertNull(mapper.getBinNames(String.class));
    }

    @Test
    public void getBinNamesMatchesSavedRecordBins() {
        AeroMapper mapper = new AeroMapper.Builder(session).build();
        AnnotationMappedChild object = new AnnotationMappedChild("bin-names-key-1");
        mapper.save(object);

        Map<String, String> binNames = mapper.getBinNames(AnnotationMappedChild.class);
        Record rawRecord = readRawRecord(mapper.getRecordKey(object));

        assertEquals("explicit", rawRecord.getString(binNames.get("explicit")));
        assertEquals(17, rawRecord.getInt(binNames.get("defaultName")));
        assertEquals("accessor", rawRecord.getString(binNames.get("accessorBacked")));
        assertEquals("inherited", rawRecord.getString(binNames.get("inheritedName")));
        assertEquals("child", rawRecord.getString(binNames.get("clash")));
        assertFalse(rawRecord.bins.containsKey("externalId"));
    }
}
