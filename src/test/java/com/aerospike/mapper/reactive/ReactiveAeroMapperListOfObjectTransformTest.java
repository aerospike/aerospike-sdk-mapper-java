package com.aerospike.mapper.reactive;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReactiveAeroMapperListOfObjectTransformTest extends ReactiveAeroMapperBaseTest {

    @AerospikeRecord(namespace = "test", set = "testSet", sendKey = true)
    public static class OwningClass {
        @AerospikeKey
        private int id;
        @AerospikeEmbed(elementType = AerospikeEmbed.EmbedType.LIST)
        private final List<OwnedClass> children;

        public OwningClass() {
            children = new ArrayList<>();
        }
    }

    @AerospikeRecord()
    public static class OwnedClass {
        public String name;
        public int id;
        @AerospikeKey
        public Date date;

        public OwnedClass(String name, int id, Date date) {
            super();
            this.name = name;
            this.id = id;
            this.date = date;
        }

        public OwnedClass() {
        }
    }

    private ReactiveAeroMapper reactiveMapper;

    @BeforeEach
    public void setup() {
        reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        // TODO: Adapt truncate for SDK - needs cluster/session truncate API
    }

    @Test
    public void test() throws Exception {
        OwningClass owner = new OwningClass();
        owner.id = 1;
        owner.children.add(new OwnedClass("a", 1, new Date()));
        owner.children.add(new OwnedClass("b", 2, new Date(new Date().getTime() - 5000)));
        owner.children.add(new OwnedClass("c", 3, new Date(new Date().getTime() - 10000)));

        reactiveMapper.save(owner).get();
    }
}
