package com.aerospike.mapper.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import org.junit.jupiter.api.Test;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import com.aerospike.mapper.tools.configuration.ClassConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ReactiveUsePKColumnInsteadOfBinForKeyTest extends ReactiveAeroMapperBaseTest {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @AerospikeRecord(namespace = "test", set = "testSet", sendKey = true)
    public static class A {
        @AerospikeKey(storeAsBin = false)
        private long key1;
        private String value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class B {
        private String keyStr;
        private String value;
    }

    @Test
    public void runTest() throws Exception {
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        A a = new A(1, "test");
        reactiveMapper.save(a).get();

        A readA = reactiveMapper.read(A.class, 1).get();
        assertNotNull(readA);
        assertEquals(1, readA.key1);

        Key recordKey = reactiveMapper.asMapper().getRecordKey(a);
        Record rawObject = readRawRecord(recordKey);
        assertNotNull(rawObject);
        assertFalse(rawObject.bins.containsKey("key1"));
    }

    @Test
    public void runTestViaYaml() throws Exception {
        String yaml = "---\n" +
                "classes:\n" +
                " - class: com.aerospike.mapper.reactive.ReactiveUsePKColumnInsteadOfBinForKeyTest$B\n" +
                "   namespace: test\n" +
                "   set: testSet\n" +
                "   sendKey: true\n" +
                "   key:\n" +
                "     field: keyStr\n" +
                "     storeAsBin: false\n";
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).withConfiguration(yaml).build();
        B b = new B("key1", "test1");
        reactiveMapper.save(b).get();

        B readB = reactiveMapper.read(B.class, "key1").get();
        assertNotNull(readB);
        assertEquals("key1", readB.keyStr);

        Key recordKey = reactiveMapper.asMapper().getRecordKey(b);
        Record rawObject = readRawRecord(recordKey);
        assertNotNull(rawObject);
        assertFalse(rawObject.bins.containsKey("keyStr"));
    }

    @Test
    public void runTestViaConfig() throws Exception {
        ClassConfig classBConfig = new ClassConfig.Builder(B.class)
                .withNamespace("test")
                .withSet("testSet")
                .withSendKey(true)
                .withKeyFieldAndStoreAsBin("keyStr", false).build();
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).withClassConfigurations(classBConfig).build();
        B b = new B("key2", "test2");
        reactiveMapper.save(b).get();

        B readB = reactiveMapper.read(B.class, "key2").get();
        assertNotNull(readB);
        assertEquals("key2", readB.keyStr);

        Key recordKey = reactiveMapper.asMapper().getRecordKey(b);
        Record rawObject = readRawRecord(recordKey);
        assertNotNull(rawObject);
        assertFalse(rawObject.bins.containsKey("keyStr"));
    }
}
