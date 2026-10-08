package com.aerospike.mapper.model;

import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;

/**
 * Issuing branch referenced by a checkbook.
 *
 * <p>The address is embedded because it belongs to the branch snapshot, while the
 * branch itself has a key and can be stored independently.</p>
 */
@AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = Branch.SET)
public class Branch {
    public static final String SET = "sdk_mapper_examples_banking_branch";

    @AerospikeKey
    private final String id;
    @AerospikeBin(name = "addr")
    @AerospikeEmbed
    private final Address address;
    private final String name;

    public Branch(@ParamFrom("id") String id, @ParamFrom("addr") Address address, @ParamFrom("name") String name) {
        super();
        this.id = id;
        this.address = address;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public Address getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }
}
