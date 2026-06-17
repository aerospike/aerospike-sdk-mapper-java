package com.aerospike.mapper.reactive;

import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import com.aerospike.mapper.tools.virtuallist.ReactiveVirtualList;
import com.aerospike.mapper.tools.virtuallist.ReturnType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveVirtualListReferenceTest extends ReactiveAeroMapperBaseTest {
    @AerospikeRecord(namespace = "test", set = "A")
    public static class A {
        @AerospikeKey
        public int id;
        public String name;
        @AerospikeEmbed(type = AerospikeEmbed.EmbedType.MAP)
        public List<B> refs;
    }

    @AerospikeRecord(namespace = "test", set = "B")
    public static class B {
        @AerospikeKey
        public int id;
        public String name;
        public C c;
        public List<C> cs;
    }

    @AerospikeRecord(namespace = "test", set = "C")
    public static class C {
        @AerospikeKey
        public int id;
        public String name;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSingleOpList() throws Exception {
        C c1 = new C();
        c1.id = 1000;
        c1.name = "1000";

        C c2 = new C();
        c2.id = 1001;
        c2.name = "name";

        B b = new B();
        b.id = 10;
        b.name = "blah";

        b.c = c1;
        b.cs = new ArrayList<>();
        b.cs.add(c1);
        b.cs.add(c2);

        A a = new A();
        a.id = 1;
        a.name = "fred";
        a.refs = new ArrayList<>();
        a.refs.add(b);

        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(a).get();
        reactiveMapper.save(b).get();
        reactiveMapper.save(c1).get();
        reactiveMapper.save(c2).get();
        ReactiveVirtualList<B> list = reactiveMapper.asBackedList(a, "refs", B.class);
        List<B> results = (List<B>) list.getByKeyRange(b.id, b.id + 1, ReturnType.ELEMENTS).get();
        assert results != null;
        assertEquals(a.refs.get(0).id, results.get(0).id);
        assertEquals(a.refs.get(0).name, results.get(0).name);
        assertEquals(a.refs.get(0).c.id, results.get(0).c.id);
        assertEquals(a.refs.get(0).c.name, results.get(0).c.name);
        assertEquals(a.refs.get(0).cs.get(0).id, results.get(0).cs.get(0).id);
        assertEquals(a.refs.get(0).cs.get(0).name, results.get(0).cs.get(0).name);
        assertEquals(a.refs.get(0).cs.get(1).id, results.get(0).cs.get(1).id);
        assertEquals(a.refs.get(0).cs.get(1).name, results.get(0).cs.get(1).name);

        List<B> results2 = (List<B>) list.beginMultiOperation().getByKeyRange(b.id, b.id + 1).end().get();
        assert results2 != null;
        assertEquals(a.refs.get(0).id, results2.get(0).id);
        assertEquals(a.refs.get(0).name, results2.get(0).name);
        assertEquals(a.refs.get(0).c.id, results2.get(0).c.id);
        assertEquals(a.refs.get(0).c.name, results2.get(0).c.name);
        assertEquals(a.refs.get(0).cs.get(0).id, results2.get(0).cs.get(0).id);
        assertEquals(a.refs.get(0).cs.get(0).name, results2.get(0).cs.get(0).name);
        assertEquals(a.refs.get(0).cs.get(1).id, results2.get(0).cs.get(1).id);
        assertEquals(a.refs.get(0).cs.get(1).name, results2.get(0).cs.get(1).name);
    }
}
