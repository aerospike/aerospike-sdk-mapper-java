package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;


/**
 * This test class requires positive nsup-period configured
 */
public class TestCustomTtl extends AeroMapperBaseTest {
	
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
}
