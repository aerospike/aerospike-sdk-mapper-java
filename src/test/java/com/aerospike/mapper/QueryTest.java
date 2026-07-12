package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.client.sdk.query.IndexCollectionType;
import com.aerospike.client.sdk.query.IndexType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingSession;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QueryTest extends MapperBaseTest {
    @AerospikeRecord(namespace = "test", set = "testScan")
    public static class A {
        @AerospikeKey
        private final int id;
        private final String name;
        private final int age;

        public A(@ParamFrom("id") int id, @ParamFrom("name") String name, @ParamFrom("age") int age) {
            super();
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return String.format("id:%d, name:%s, age:%d", id, name, age);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            A a = (A) o;
            return id == a.id && age == a.age && Objects.equals(name, a.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, age);
        }
    }

    private final List<A> data = new ArrayList<A>() {{
        add(new A(1, "Tim", 312));
        add(new A(2, "Bob", 44));
        add(new A(3, "Sue", 56));
        add(new A(4, "Rob", 23));
        add(new A(5, "Jim", 32));
        add(new A(6, "Bob", 78));
        add(new A(7, "Fred", 23));
        add(new A(8, "Wilma", 11));
        add(new A(9, "Barney", 54));
        add(new A(10, "Steve", 72));
        add(new A(11, "Bam Bam", 19));
        add(new A(12, "Betty", 34));
        add(new A(13, "Del", 7));
        add(new A(14, "Khon", 98));
        add(new A(15, "Dave", 21));
        add(new A(16, "Mike", 32));
        add(new A(17, "Darren", 14));
        add(new A(18, "Lucy", 45));
        add(new A(19, "Gertrude", 36));
        add(new A(20, "Lucinda", 63));
    }};

    @BeforeAll
    public void createIndexes() {
        try {
            session.createIndex(DataSet.of("test", "testScan"), "age_idx", "age",
                    IndexType.INTEGER, IndexCollectionType.DEFAULT).waitTillComplete();
        } catch (AerospikeException e) {
            if (e.getResultCode() != ResultCode.INDEX_ALREADY_EXISTS) throw e;
        }
    }

    private MappingSession populate() {
        session.truncate(DataSet.of("test", "testScan"));
        MappingSession mapper = createMapper();
        mapper.save(data.toArray());
        return mapper;
    }

    @Test
    public void queryTest() {
        MappingSession mapper = populate();
        AtomicInteger counter = new AtomicInteger(0);
        mapper.query(A.class, (a) -> {
            counter.incrementAndGet();
            return true;
        }, "$.age >= 30 and $.age <= 54");
        assertEquals(7, counter.get());
    }

    @Test
    public void queryTestWithAbort() {
        MappingSession mapper = populate();
        AtomicInteger counter = new AtomicInteger(0);
        mapper.query(A.class, (a) -> {
            counter.incrementAndGet();
            return false;
        }, "$.age >= 30 and $.age <= 54");
        assertEquals(1, counter.get());
    }

    @Test
    public void queryTestReturnsList() {
        MappingSession mapper = populate();

        List<A> result = mapper.query(A.class, "$.age >= 30 and $.age <= 54");

        List<A> expected = data.stream()
                               .filter(d -> d.age >= 30 && d.age <= 54)
                               .sorted(comparing(A::getId))
                               .collect(toList());
        assertEquals(7, result.size());
        assertEquals(expected, result.stream().sorted(comparing(A::getId)).collect(toList()));
    }

    @Test
    public void queryWithExpFilterTestReturnsList() {
        MappingSession mapper = populate();

        List<A> result = mapper.query(A.class, "$.age >= 44 and $.age <= 78 and $.name == \"Bob\"");

        List<A> expected = data.stream()
                               .filter(d -> d.age >= 44 && d.age <= 78 && d.name.equals("Bob"))
                               .sorted(comparing(A::getId))
                               .collect(toList());
        assertEquals(2, result.size());
        assertEquals(expected, result.stream().sorted(comparing(A::getId)).collect(toList()));

        Expression expFilter = Exp.build(Exp.and(
                Exp.ge(Exp.intBin("age"), Exp.val(44)),
                Exp.le(Exp.intBin("age"), Exp.val(78)),
                Exp.eq(Exp.stringBin("name"), Exp.val("Bob"))
        ));
        List<A> resultFromExp = mapper.query(A.class, expFilter);
        assertEquals(2, resultFromExp.size());
        assertEquals(expected, resultFromExp.stream().sorted(comparing(A::getId)).collect(toList()));
    }
}
