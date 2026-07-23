package com.aerospike.mapper;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.client.sdk.TypedRecordStream;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SdkTypedMappingTest extends MapperBaseTest {

    @AerospikeRecord(namespace = "test", set = "sdkTyped")
    public static class Widget {
        @AerospikeKey
        public int id;
        public String label;

        public Widget() {}

        public Widget(@ParamFrom("id") int id, @ParamFrom("label") String label) {
            this.id = id;
            this.label = label;
        }
    }

    @Test
    void testInstallOnRegistersFactory() {
        MappingEngine engine = MappingEngine.builder().build();
        engine.installOn(cluster);
        truncateSet("test", "sdkTyped");

        MappingSession mapper = cluster.createSession(Behavior.DEFAULT, engine.sessionExtension());
        mapper.save(new Widget(1, "bolt"));

        TypedDataSet<Widget> ds = engine.typedDataSet(Widget.class);
        Session plainSession = cluster.createSession(Behavior.DEFAULT);
        try (TypedRecordStream<Widget> stream = plainSession.query(ds.id(1)).execute()) {
            List<Widget> results = stream.toObjectList();
            assertEquals(1, results.size());
            assertEquals("bolt", results.get(0).label);
            assertEquals(1, results.get(0).id);
        }
    }

    @Test
    void testTypedDataSetDerivesNamespaceAndSet() {
        MappingEngine engine = MappingEngine.builder().build();

        TypedDataSet<Widget> ds = engine.typedDataSet(Widget.class);
        assertEquals("test", ds.getNamespace());
        assertEquals("sdkTyped", ds.getSet());
        assertEquals(Widget.class, ds.getClazz());
    }

    @Test
    void testGetKeyExtractsAnnotatedKey() {
        MappingEngine engine = MappingEngine.builder().build();

        Widget widget = new Widget(42, "nut");
        Object key = engine.getKey(widget);
        assertEquals(42, key);
    }

    @Test
    void testGetRecordKeyIncludesNamespaceSetAndKey() {
        MappingEngine engine = MappingEngine.builder().build();

        Widget widget = new Widget(7, "washer");
        Key recordKey = engine.getRecordKey(widget);
        assertNotNull(recordKey);
        assertEquals("test", recordKey.namespace);
        assertEquals("sdkTyped", recordKey.setName);
        assertNotNull(recordKey.digest);
    }
}
