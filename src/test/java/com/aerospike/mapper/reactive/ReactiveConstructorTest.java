package com.aerospike.mapper.reactive;

import com.aerospike.mapper.annotations.*;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveConstructorTest extends ReactiveAeroMapperBaseTest {

    @AerospikeRecord(namespace = "test", set = "testSet")
    public static class NoArgConstructorClass {
        @AerospikeKey
        public final int id;
        public String name;

        public NoArgConstructorClass() {
            this.name = "";
            this.id = 0;
        }

        public NoArgConstructorClass(int id, String name) {
            super();
            this.id = id;
            this.name = name;
        }
    }

    @AerospikeRecord(namespace = "test", set = "testSet")
    public static class ConstructedClass {
        @AerospikeKey
        public final int id;
        public final int age;
        public final String name;
        public final Date date;

        public ConstructedClass(@ParamFrom("id") int id, @ParamFrom("age") int age, @ParamFrom("name") String name, @ParamFrom("date") Date date) {
            super();
            this.id = id;
            this.age = age;
            this.name = name;
            this.date = date;
        }
    }

    @Test
    public void test() throws Exception {
        ConstructedClass data = new ConstructedClass(1, 19, "jane", new Date());
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(data).get();
        ConstructedClass data2 = reactiveMapper.read(ConstructedClass.class, 1).get();
        assert data2 != null;
        assertEquals(data.id, data2.id);
        assertEquals(data.age, data2.age);
        assertEquals(data.name, data2.name);
        assertEquals(data.date, data2.date);
    }

    @AerospikeRecord(namespace = "test", set = "testSet")
    public static class ConstructedClass2 {
        @AerospikeKey
        public final int id;
        public final int a;
        public int b;
        public int c;

        public ConstructedClass2(@ParamFrom("id") int id, @ParamFrom("a") int a) {
            this.id = id;
            this.a = a;
            System.out.println("Using 2 arg");
        }

        @AerospikeConstructor
        public ConstructedClass2(@ParamFrom("id") int id, @ParamFrom("a") int a, @ParamFrom("b") int b) {
            this.id = id;
            this.a = a;
            this.b = b;
            System.out.println("Using 3 arg");
        }
    }

    @Test
    public void test2() throws Exception {
        ConstructedClass2 data = new ConstructedClass2(1, 2);
        data.b = 3;
        data.c = 4;

        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(data).get();
        System.out.println("reading back");
        ConstructedClass2 data2 = reactiveMapper.read(ConstructedClass2.class, 1).get();
        assert data2 != null;
        assertEquals(data.id, data2.id);
        assertEquals(data.a, data2.a);
        assertEquals(data.b, data2.b);
        assertEquals(data.c, data2.c);
    }

    @Test
    public void test3() throws Exception {
        NoArgConstructorClass data = new NoArgConstructorClass(12, "tim");
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(data).get();
        NoArgConstructorClass data2 = reactiveMapper.read(NoArgConstructorClass.class, data.id).get();
        assert data2 != null;
        assertEquals(data.id, data2.id);
        assertEquals(data.name, data2.name);
    }

    @AerospikeRecord
    public static class ConstructedClass3 {
        @AerospikeKey
        public final String id;
        public final int a;
        public final int b;
        public final int c;

        @AerospikeConstructor
        public ConstructedClass3(@ParamFrom("id") String id, @ParamFrom("a") int a, @ParamFrom("b") int b, @ParamFrom("c") int c) {
            this.id = id;
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @AerospikeRecord(namespace = "test", set = "testSet1")
    public static class ConstructorContainerClass {
        @AerospikeKey
        public int id;
        @AerospikeEmbed(type = AerospikeEmbed.EmbedType.MAP, elementType = AerospikeEmbed.EmbedType.LIST)
        public List<ConstructedClass3> list;
    }

    @Test
    public void test4() throws Exception {
        ConstructorContainerClass ccc = new ConstructorContainerClass();
        ccc.id = 1;
        ccc.list = new ArrayList<>();
        ccc.list.add(new ConstructedClass3("a", 1, 1, 4));
        ccc.list.add(new ConstructedClass3("b", 2, 2, 3));
        ccc.list.add(new ConstructedClass3("c", 3, 4, 2));
        ccc.list.add(new ConstructedClass3("d", 4, 3, 1));

        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        reactiveMapper.save(ccc).get();
        ConstructorContainerClass ccc2 = reactiveMapper.read(ConstructorContainerClass.class, 1).get();
        assert ccc2 != null;
        assertEquals(ccc.id, ccc2.id);
        assertEquals(ccc.list.size(), ccc2.list.size());
        assertEquals(ccc.list.get(0).id, ccc2.list.get(0).id);
        assertEquals(ccc.list.get(0).a, ccc2.list.get(0).a);
        assertEquals(ccc.list.get(0).b, ccc2.list.get(0).b);
        assertEquals(ccc.list.get(0).c, ccc2.list.get(0).c);
    }
}
