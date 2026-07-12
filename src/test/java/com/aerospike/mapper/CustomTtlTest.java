package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;


/**
 * This test class requires positive nsup-period configured
 */
public class CustomTtlTest extends MapperBaseTest {
	
	@AerospikeRecord(namespace = "test", set = "classWithTtl", ttl=300)
	public static class ClassWithTtl {
		@AerospikeKey
		public int id;
		public String name;
	}

	@AerospikeRecord(namespace = "test", set = "classWithTtlExplicit")
	public static class ClassWithExplicitTtl {
		@AerospikeKey
		public int id;
		public String name;
	}
	
	@Test
	public void testTtl() {
		MappingSession mapper = createMapper();
		
		ClassWithTtl myRecord = new ClassWithTtl();
		myRecord.id = 1;
		myRecord.name = "not overridden ttl";
		mapper.save(myRecord);
		
		myRecord.id = 2;
		myRecord.name = "overridden ttl";
		mapper.save(myRecord, 100);
		
		Record readClient1 = readRawRecord(new Key("test", "classWithTtl", 1));
		Record readClient2 = readRawRecord(new Key("test", "classWithTtl", 2));
		assertTrue(readClient1.getTimeToLive() > 290 && readClient1.getTimeToLive() <= 300);
		assertTrue(readClient2.getTimeToLive() > 90 && readClient2.getTimeToLive() <= 100);
	}
	
	@Test
	public void testExplicitTtlNoAnnotation() {
		MappingSession mapper = createMapper();
		
		ClassWithExplicitTtl myRecord = new ClassWithExplicitTtl();
		myRecord.id = 1;
		myRecord.name = "explicit ttl 300";
		mapper.save(myRecord, 300);
		
		myRecord.id = 2;
		myRecord.name = "explicit ttl 100";
		mapper.save(myRecord, 100);
		
		Record readClient1 = readRawRecord(new Key("test", "classWithTtlExplicit", 1));
		Record readClient2 = readRawRecord(new Key("test", "classWithTtlExplicit", 2));
		assertTrue(readClient1.getTimeToLive() > 290 && readClient1.getTimeToLive() <= 300);
		assertTrue(readClient2.getTimeToLive() > 90 && readClient2.getTimeToLive() <= 100);
	}

	@Test
	public void testInsertWithIntTtl() {
		MappingSession mapper = createMapper();

		ClassWithExplicitTtl myRecord = new ClassWithExplicitTtl();
		myRecord.id = 10;
		myRecord.name = "insert ttl";
		mapper.delete(ClassWithExplicitTtl.class, 10);
		mapper.insert(myRecord, 200);

		Record rec = readRawRecord(new Key("test", "classWithTtlExplicit", 10));
		assertNotNull(rec);
		assertTrue(rec.getTimeToLive() > 190 && rec.getTimeToLive() <= 200);
	}

	@Test
	public void testUpdateWithIntTtl() {
		MappingSession mapper = createMapper();

		ClassWithExplicitTtl myRecord = new ClassWithExplicitTtl();
		myRecord.id = 11;
		myRecord.name = "before update";
		mapper.save(myRecord);

		myRecord.name = "after update";
		mapper.update(myRecord, 150);

		Record rec = readRawRecord(new Key("test", "classWithTtlExplicit", 11));
		assertNotNull(rec);
		assertTrue(rec.getTimeToLive() > 140 && rec.getTimeToLive() <= 150);
	}

	@Test
	public void testSaveWithDurationTtl() {
		MappingSession mapper = createMapper();

		ClassWithExplicitTtl myRecord = new ClassWithExplicitTtl();
		myRecord.id = 12;
		myRecord.name = "duration ttl";
		mapper.save(myRecord, Duration.ofSeconds(250));

		Record rec = readRawRecord(new Key("test", "classWithTtlExplicit", 12));
		assertNotNull(rec);
		assertTrue(rec.getTimeToLive() > 240 && rec.getTimeToLive() <= 250);
	}

	@Test
	public void testSaveWithTtlAndBinNames() {
		MappingSession mapper = createMapper();

		ClassWithExplicitTtl myRecord = new ClassWithExplicitTtl();
		myRecord.id = 13;
		myRecord.name = "partial bins";
		mapper.save(myRecord);

		myRecord.name = "updated name";
		mapper.save(myRecord, 100, new String[]{"name"});

		Record rec = readRawRecord(new Key("test", "classWithTtlExplicit", 13));
		assertNotNull(rec);
		assertTrue(rec.getTimeToLive() > 90 && rec.getTimeToLive() <= 100);
		assertEquals("updated name", rec.getString("name"));
	}
}
