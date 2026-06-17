package com.aerospike.mapper.reactive;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.model.Person;
import com.aerospike.mapper.model.PersonDifferentNames;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ReactiveAeroMapperTest extends ReactiveAeroMapperBaseTest {

    private ReactiveAeroMapper reactiveMapper;

    @BeforeEach
    public void setup() {
        reactiveMapper = new ReactiveAeroMapper.Builder(session).build();
        // TODO: Adapt truncate for SDK - needs cluster/session truncate API
    }

    @Test
    public void testSaveRead() throws Exception {
        Person writePerson = new Person();
        writePerson.setFirstName("John");
        writePerson.setLastName("Doe");
        writePerson.setAge(17);
        writePerson.setBalance(123.10f);
        writePerson.setDateOfBirth(new Date());
        writePerson.setHeight(1.93);
        writePerson.setPhoto("Photo bytes".getBytes());
        writePerson.setSsn("123-456-7890");
        writePerson.setValid(true);

        reactiveMapper.save(writePerson).get();

        Person readPerson = reactiveMapper.read(Person.class, writePerson.getSsn()).get();

        assertNotNull(readPerson);
        assertEquals(writePerson.getSsn(), readPerson.getSsn());
        assertEquals(writePerson.toString(), readPerson.toString());

        Record record = readRawRecord(new Key(NAMESPACE, "people", writePerson.getSsn()));
        assertNotNull(record);
        assertEquals(record.getString("ssn"), writePerson.getSsn());
        assertEquals(record.getInt("age"), writePerson.getAge());
    }

    @Test
    public void testSaveDifferentNames() throws Exception {
        PersonDifferentNames writePerson = new PersonDifferentNames();
        writePerson.setFirstName("John");
        writePerson.setLastName("Doe");
        writePerson.setAge(17);
        writePerson.setBalance(123.10f);
        writePerson.setDateOfBirth(new Date());
        writePerson.setHeight(1.93);
        writePerson.setPhoto("Photo bytes".getBytes());
        writePerson.setSsn("123-456-7890");
        writePerson.setValid(true);

        reactiveMapper.save(writePerson).get();

        PersonDifferentNames readPerson = reactiveMapper.read(PersonDifferentNames.class, writePerson.getSsn()).get();

        assertNotNull(readPerson);
        assertEquals(writePerson.toString(), readPerson.toString());

        Record record = readRawRecord(new Key(NAMESPACE, "people", writePerson.getSsn()));
        assertNotNull(record);
        assertEquals(record.getString("s"), writePerson.getSsn());
        assertEquals(record.getInt("a"), writePerson.getAge());
    }
}
