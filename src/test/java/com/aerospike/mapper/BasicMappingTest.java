package com.aerospike.mapper;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeGetter;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.model.Person;
import com.aerospike.mapper.model.PersonDifferentNames;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class BasicMappingTest extends MapperBaseTest {

    private MappingSession mapper;

    @BeforeEach
    public void setup() {
        mapper = createMapper();
        truncateSet(NAMESPACE, "people");
        truncateSet(NAMESPACE, "account");
        truncateSet(NAMESPACE, "product");
    }

    @Test
    public void testSave() {
        Person p = new Person();
        p.setFirstName("John");
        p.setLastName("Doe");
        p.setAge(17);
        p.setBalance(123.10f);
        p.setDateOfBirth(new Date());
        p.setHeight(1.93);
        p.setPhoto("Photo bytes".getBytes());
        p.setSsn("123-456-7890");
        p.setValid(true);

        mapper.save(p);
        Person person2 = mapper.read(Person.class, p.getSsn());
        assertEquals(p.toString(), person2.toString());

        // Check the column names
        Record record = readRawRecord(new Key(NAMESPACE, "people", p.getSsn()));
        assertEquals(record.getString("ssn"), p.getSsn());
        assertEquals(record.getInt("age"), p.getAge());
    }

    @Test
    public void testSaveDifferentNames() {
        PersonDifferentNames p = new PersonDifferentNames();
        p.setFirstName("John");
        p.setLastName("Doe");
        p.setAge(17);
        p.setBalance(123.10f);
        p.setDateOfBirth(new Date());
        p.setHeight(1.93);
        p.setPhoto("Photo bytes".getBytes());
        p.setSsn("123-456-7890");
        p.setValid(true);

        mapper.save(p);
        PersonDifferentNames person2 = mapper.read(PersonDifferentNames.class, p.getSsn());
        assertEquals(p.toString(), person2.toString());

        // Check the column names
        Record record = readRawRecord(new Key(NAMESPACE, "people", p.getSsn()));
        assertEquals(record.getString("s"), p.getSsn());
        assertEquals(record.getInt("a"), p.getAge());
    }

    @Test
    public void testDuplicateName() {
        try {
            createMapper(MappingEngine.builder().preLoadClass(DuplicateKeyClass.class));
            fail();
        } catch (Exception ignore) {
        }
    }

    @Test
    public void testMissingSetter() {
        try {
            createMapper(MappingEngine.builder().preLoadClass(PropertyWithNoSetter.class));
            fail();
        } catch (Exception ignore) {
        }
    }

    @AerospikeRecord(namespace = "test", set = "none", mapAll = false)
    public static class PropertyWithNoSetter {
        @AerospikeBin(name = "dummy")
        private int f1;

        @AerospikeGetter(name = "dummy")
        public int getDummy() {
            return 1;
        }

        public void setDummy(int dummy) {
        }
    }

    @AerospikeRecord(namespace = "test", set = "none", mapAll = false)
    public static class DuplicateKeyClass {
        @AerospikeBin(name = "dummy")
        private int f1;

        @AerospikeGetter(name = "dummy")
        public int getDummy() {
            return 1;
        }

        public void setDummy(int dummy) {
        }
    }
}
