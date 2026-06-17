package com.aerospike.mapper.reactive;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.Test;

public class ReactiveInheritanceTest extends ReactiveAeroMapperBaseTest {
    @AerospikeRecord(namespace = "test", set = "A")
    public static class A {
        @AerospikeKey
        public int id;
        public String name;
        @AerospikeEmbed
        public B topB;
        @AerospikeEmbed
        public B cAsAb;
        @AerospikeEmbed
        public B dAsAb;
    }

    @AerospikeRecord(namespace = "test", set = "B")
    public static class B {
        @AerospikeKey
        public int id;
        public String name;
    }

    @AerospikeRecord
    public static class C extends B {
        public String otherName;
    }

    @AerospikeRecord(namespace = "test", set = "D")
    public static class D extends B {
        public int age;
    }

    @Test
    public void run() throws Exception {
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        A a = new A();
        a.id = 1;
        a.name = "test";

        B b = new B();
        b.id = 2;
        b.name = "b";

        C c = new C();
        c.id = 3;
        c.name = "c";
        c.otherName = "masquerading as a B";

        D d = new D();
        d.id = 4;
        d.name = "d";
        d.age = 312;
        a.topB = b;
        a.cAsAb = c;
        a.dAsAb = d;

        reactiveMapper.save(a).get();
        reactiveMapper.save(b).get();
        reactiveMapper.save(c).get();
        reactiveMapper.save(d).get();
        A a2 = reactiveMapper.read(A.class, a.id).get();
        compare(a, a2);
    }
}
