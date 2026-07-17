package com.aerospike.mapper.async;

import com.aerospike.mapper.annotations.*;
import com.aerospike.mapper.tools.MappingSession;
import com.aerospike.mapper.tools.virtuallist.AsyncVirtualList;
import com.aerospike.mapper.tools.virtuallist.ReturnType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncVirtualListTest extends AsyncBaseTest {

    @AerospikeRecord(namespace = "test", set = "C")
    public static class C {
        @AerospikeKey
        public int a;
        public String b;

        public C(@ParamFrom("a") int a, @ParamFrom("b") String b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public String toString() {
            return String.format("{%d,\"%s\"}", a, b);
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && a == ((C) obj).a && b.equals(((C) obj).b);
        }

        @Override
        public int hashCode() {
            return 17 * a + (b == null ? 0 : b.hashCode());
        }
    }

    @AerospikeRecord
    public static class B {
        @AerospikeKey
        public int id;
        public String name;
        public long date;
        public C thisC;
        public List<C> Cs;
        public List<C> otherCs;
        public C anonC;

        @AerospikeConstructor
        public B(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("date") long date) {
            super();
            this.id = id;
            this.name = name;
            this.date = date;
            this.Cs = new ArrayList<>();
            this.otherCs = new ArrayList<>();
        }

        public B(int id, String name, long date, C thisC, C... listCs) {
            this(id, name, date);
            this.thisC = thisC;
            this.anonC = thisC;
            for (C aC : listCs) {
                this.Cs.add(aC);
                this.otherCs.add(aC);
            }
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public long getDate() {
            return date;
        }

        public List<C> getCs() {
            return Cs;
        }

        public C getThisC() {
            return thisC;
        }

        public void setThisC(C thisC) {
            this.thisC = thisC;
        }

        @Override
        public String toString() {
            return String.format("{id=%d, name=%s, date=%d, thisC=%s, listC=%s}", id, name, date, thisC, Cs);
        }

        private boolean compare(Object a, Object b) {
            if (a == null && b == null) {
                return true;
            }
            if ((a != null && b == null) || (a == null && b != null)) {
                return false;
            }
            return a.equals(b);
        }

        @Override
        public boolean equals(Object obj) {
            if ((!(obj instanceof B))) {
                return false;
            }
            B b2 = (B) obj;
            if (id != b2.id || date != b2.date) {
                return false;
            }
            return compare(name, b2.name) && compare(thisC, b2.thisC) && compare(Cs, b2.Cs) && compare(otherCs, b2.otherCs) && compare(anonC, b2.anonC);
        }
    }

    @AerospikeRecord(namespace = "test", set = "A")
    public static class A {
        @AerospikeEmbed(type = AerospikeEmbed.EmbedType.MAP, elementType = AerospikeEmbed.EmbedType.MAP)
        public List<B> elements;

        @AerospikeKey
        public int id;

        public A() {
            elements = new ArrayList<>();
        }
    }

    @AerospikeRecord(namespace = "test", set = "D")
    public static class D {
        @AerospikeEmbed(type = AerospikeEmbed.EmbedType.LIST)
        public List<Long> elements2;

        @AerospikeKey
        public int id;

        public D() {
            elements2 = new ArrayList<>();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testVirtualList() throws Exception {
        C a = new C(1, "a");
        C b = new C(2, "b");
        C c = new C(3, "c");
        C d = new C(4, "d");
        C e = new C(5, "e");
        C f = new C(6, "f");
        C g = new C(7, "g");
        C h = new C(8, "h");
        C i = new C(9, "i");
        C j = new C(10, "j");

        A collection = new A();
        collection.id = 1;

        collection.elements.add(new B(102, "bob", 12345, a, b, c));
        collection.elements.add(new B(101, "joe", 23456, b, d, e, f));
        collection.elements.add(new B(100, "sue", 34567, c));

        MappingSession mapper = createMapper();
        mapper.save(collection);
        mapper.save(a);
        mapper.save(b);
        mapper.save(c);
        mapper.save(d);
        mapper.save(e);
        mapper.save(f);
        mapper.save(g);
        mapper.save(h);
        mapper.save(i);
        mapper.save(j);

        AsyncVirtualList<B> list = mapper.asBackedListAsync(collection, "elements", B.class);
        List<B> results = (List<B>) list.beginMultiOperation()
                .append(new B(104, "tim", 22222, i, e, f))
                .append(new B(103, "tom", 45678, h, g, g))
                .append(new B(105, "sam", 33333, j, a, b))
                .append(new B(106, "rob", 44444, j, g))
                .getByKeyRange(101, 105)
                .end().get();

        assert results != null;
        assertEquals(4, results.size());
        assertEquals(101, results.get(0).id);
        assertEquals("joe", results.get(0).name);
        assertEquals(23456, results.get(0).date);
        assertEquals(b, results.get(0).thisC);
        assertEquals(d, results.get(0).Cs.get(0));
        assertEquals(e, results.get(0).Cs.get(1));
        assertEquals(f, results.get(0).Cs.get(2));

        assertEquals(102, results.get(1).id);
        assertEquals(103, results.get(2).id);
        assertEquals(104, results.get(3).id);

        A result = mapper.read(A.class, collection.id).orElse(null);
        assert result != null;
        assertEquals(collection.id, result.id);
        assertEquals(7, result.elements.size());

        for (int x = 0; x < collection.elements.size(); x++) {
            boolean found = false;
            for (int y = 0; y < result.elements.size(); y++) {
                if (collection.elements.get(x).id == result.elements.get(y).id) {
                    assertEquals(collection.elements.get(x), result.elements.get(y));
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }

        list.clear().get();
        assertEquals(0, (long) list.size().get());
        mapper.save(collection);
        list = mapper.asBackedListAsync(collection, "elements", B.class);

        results = (List<B>) list.beginMultiOperation()
                .removeByIndex(0)
                .removeByKey(102)
                .getByIndexRange(0)
                .end().get();

        assert results != null;
        assertEquals(1, results.size());
        assertEquals(101, results.get(0).id);

        list.clear().get();
        assertEquals(0, (long) list.size().get());
        mapper.save(collection);
        list = mapper.asBackedListAsync(collection, "elements", B.class);

        results = (List<B>) list.getByKey(102, ReturnType.DEFAULT).get();
        assertEquals(3, (long) list.size().get());
        assert results != null;
        assertEquals(1, results.size());
        assertEquals(12345, results.get(0).getDate());
        assertEquals("bob", results.get(0).getName());

        list.clear().get();
        assertEquals(0, (long) list.size().get());
        mapper.save(collection);
        list = mapper.asBackedListAsync(collection, "elements", B.class);

        results = (List<B>) list.beginMultiOperation()
                .removeByRank(0)
                .getByRankRange(1)
                .end().get();

        assertEquals(2, (long) list.size().get());
        assert results != null;
        assertEquals(1, results.size());
        assertEquals(101, results.get(0).getId());
        assertEquals("joe", results.get(0).getName());

        D collection2 = new D();
        collection2.id = 1;

        collection2.elements2.add(12345L);
        collection2.elements2.add(23456L);
        collection2.elements2.add(34567L);
        collection2.elements2.add(44444L);
        collection2.elements2.add(55555L);

        list.clear().get();
        assertEquals(0, (long) list.size().get());

        mapper.save(collection2);
        AsyncVirtualList<Long> list2 = mapper.asBackedListAsync(collection2, "elements2", Long.class);

        List<Object> valueList = new ArrayList<>();
        valueList.add(12345L);
        valueList.add(34567L);
        valueList.add(55555L);

        List<Long> results2 = (List<Long>) list2.beginMultiOperation()
                .removeByValue(23456L)
                .getByValueList(valueList)
                .end().get();

        assertEquals(4, (long) list2.size().get());
        assert results2 != null;
        assertEquals(3, results2.size());
        assertTrue(results2.containsAll(valueList));
        assertFalse(results2.contains(44444L));
    }

    @Test
    public void testAsBackedListAsyncByClassAndKey() throws Exception {
        MappingSession mapper = createMapper();
        A collection = new A();
        collection.id = 99;
        collection.elements.add(new B(200, "test", 11111, new C(1, "x")));
        mapper.save(collection);
        mapper.save(new C(1, "x"));

        AsyncVirtualList<B> list = mapper.asBackedListAsync(A.class, 99, "elements", B.class);
        assertEquals(1, (long) list.size().get());
    }

    @Test
    public void testAppendAndGetByIndex() throws Exception {
        C c1 = new C(1, "x");
        C c2 = new C(2, "y");
        C c3 = new C(3, "z");

        MappingSession mapper = createMapper();
        mapper.save(c1);
        mapper.save(c2);
        mapper.save(c3);

        A owner = new A();
        owner.id = 200;
        mapper.save(owner);

        AsyncVirtualList<B> list = mapper.asBackedListAsync(owner, "elements", B.class);
        list.clear().get();

        list.append(new B(300, "first", 11111, c1)).get();
        list.append(new B(301, "second", 22222, c2)).get();
        list.append(new B(302, "third", 33333, c3)).get();

        assertEquals(3, (long) list.size().get());

        B first = list.get(0).get();
        assertNotNull(first);
        assertEquals(300, first.id);
        assertEquals("first", first.name);

        B last = list.get(2).get();
        assertNotNull(last);
        assertEquals(302, last.id);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetByRank() throws Exception {
        C c1 = new C(1, "a");

        MappingSession mapper = createMapper();
        mapper.save(c1);

        A owner = new A();
        owner.id = 201;
        owner.elements.add(new B(102, "bob", 30000, c1));
        owner.elements.add(new B(100, "sue", 10000, c1));
        owner.elements.add(new B(101, "joe", 20000, c1));
        mapper.save(owner);

        AsyncVirtualList<B> list = mapper.asBackedListAsync(owner, "elements", B.class);

        List<B> lowest = (List<B>) list.getByRank(0, ReturnType.DEFAULT).get();
        assertNotNull(lowest);
        assertEquals(1, lowest.size());
        assertEquals(100, lowest.get(0).id);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRemoveByIndex() throws Exception {
        C c1 = new C(1, "a");

        MappingSession mapper = createMapper();
        mapper.save(c1);

        A owner = new A();
        owner.id = 202;
        owner.elements.add(new B(100, "first", 11111, c1));
        owner.elements.add(new B(101, "second", 22222, c1));
        owner.elements.add(new B(102, "third", 33333, c1));
        mapper.save(owner);

        AsyncVirtualList<B> list = mapper.asBackedListAsync(owner, "elements", B.class);
        assertEquals(3, (long) list.size().get());

        List<B> removed = (List<B>) list.removeByIndex(0, ReturnType.DEFAULT).get();
        assertNotNull(removed);
        assertEquals(1, removed.size());
        assertEquals(100, removed.get(0).id);
        assertEquals(2, (long) list.size().get());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetByValueRange() throws Exception {
        MappingSession mapper = createMapper();

        D owner = new D();
        owner.id = 203;
        owner.elements2.clear();
        owner.elements2.add(10L);
        owner.elements2.add(20L);
        owner.elements2.add(30L);
        owner.elements2.add(40L);
        owner.elements2.add(50L);
        mapper.save(owner);

        AsyncVirtualList<Long> list = mapper.asBackedListAsync(owner, "elements2", Long.class);

        List<Long> range = (List<Long>) list.getByValueRange(20L, 40L, ReturnType.DEFAULT).get();
        assertNotNull(range);
        assertEquals(2, range.size());
        assertTrue(range.contains(20L));
        assertTrue(range.contains(30L));
    }

    @Test
    public void testWithKeyTargetsDifferentRecord() throws Exception {
        C c1 = new C(1, "x");
        MappingSession mapper = createMapper();
        mapper.save(c1);

        A owner1 = new A();
        owner1.id = 204;
        owner1.elements.add(new B(100, "a", 11111, c1));
        owner1.elements.add(new B(101, "b", 22222, c1));
        mapper.save(owner1);

        A owner2 = new A();
        owner2.id = 205;
        owner2.elements.add(new B(200, "c", 33333, c1));
        owner2.elements.add(new B(201, "d", 44444, c1));
        owner2.elements.add(new B(202, "e", 55555, c1));
        mapper.save(owner2);

        AsyncVirtualList<B> list1 = mapper.asBackedListAsync(owner1, "elements", B.class);
        assertEquals(2, (long) list1.size().get());

        AsyncVirtualList<B> list2 = list1.withKey(205);
        assertEquals(3, (long) list2.size().get());

        assertEquals(2, (long) list1.size().get());
    }
}
