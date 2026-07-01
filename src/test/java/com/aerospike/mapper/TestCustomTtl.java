package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.Key;
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

	@AerospikeRecord(namespace = "test", set = "classWithTtl")	
	public static class ClassWithTtlViaPolicy {
		@AerospikeKey
		public int id;
		public String name;
	}
	
	@Test
	public void testTtl() {
        // This test requires positive nsup-period configured
		MappingSession mapper = createMapper();
		
		ClassWithTtl myRecord = new ClassWithTtl();
		myRecord.id = 1;
		myRecord.name = "not overridden ttl";
		mapper.save(myRecord);
		
		// TODO: Adapt for SDK - custom write policy with TTL override
		// WritePolicy customWritePolicy = new WritePolicy(mapper.getWritePolicy(ClassWithTtl.class));
		// customWritePolicy.expiration = 100;
		myRecord.id = 2;
		myRecord.name = "overridden ttl";
		mapper.save(myRecord);
		
		// TODO: Adapt for SDK - raw record read via session to validate TTL
		// Record readClient1 = client.get(null, new Key("test", "classWithTtl", 1));
		// Record readClient2 = client.get(null, new Key("test", "classWithTtl", 2));
		// assertTrue(readClient1.getTimeToLive() > 290 && readClient1.getTimeToLive() <= 300);
		// assertTrue(readClient2.getTimeToLive() > 90 && readClient2.getTimeToLive() <= 100);
	}
	
	@Test
	public void testTtlViaPolicy() {
        // This test requires positive nsup-period configured
		// TODO: Adapt for SDK - write policy with expiration configured via builder
		MappingSession mapper = createMapper();
		
		ClassWithTtlViaPolicy myRecord = new ClassWithTtlViaPolicy();
		myRecord.id = 1;
		myRecord.name = "not overridden ttl";
		mapper.save(myRecord);
		
		// TODO: Adapt for SDK - custom write policy with TTL override
		// WritePolicy customWritePolicy = new WritePolicy(mapper.getWritePolicy(ClassWithTtlViaPolicy.class));
		// customWritePolicy.expiration = 100;
		myRecord.id = 2;
		myRecord.name = "overridden ttl";
		mapper.save(myRecord);
		
		// TODO: Adapt for SDK - raw record read via session to validate TTL
		// Record readClient1 = client.get(null, new Key("test", "classWithTtl", 1));
		// Record readClient2 = client.get(null, new Key("test", "classWithTtl", 2));
		// assertTrue(readClient1.getTimeToLive() > 290 && readClient1.getTimeToLive() <= 300);
		// assertTrue(readClient2.getTimeToLive() > 90 && readClient2.getTimeToLive() <= 100);
	}
}
