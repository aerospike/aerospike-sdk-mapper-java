package com.aerospike.mapper.examples.model;

import java.util.Date;

import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;

/**
 * Demonstrates a composite key exposed through annotated accessor methods.
 */
@AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = Checkbook.SET)
public class Checkbook {
    public static final String SET = "sdk_mapper_examples_banking_checkbook";

    private String acctId;
    private long first;
    private long last;
    private final Date issued;
    private boolean recalled;
    private Branch issuer;

    public Checkbook(@ParamFrom("acctId") String acctId, @ParamFrom("first") long first, @ParamFrom("last") long last, @ParamFrom("issued") Date issued) {
        super();
        this.acctId = acctId;
        this.first = first;
        this.last = last;
        this.issued = issued;
    }

    public long getFirst() {
        return first;
    }

    public long getLast() {
        return last;
    }

    public Date getIssued() {
        return issued;
    }

    public boolean isRecalled() {
        return recalled;
    }

    public void setRecalled(boolean recalled) {
        this.recalled = recalled;
    }

    public Branch getIssuer() {
        return issuer;
    }

    public void setIssuer(Branch issuer) {
        this.issuer = issuer;
    }

    /*
     * A checkbook does not have one natural scalar identifier. The mapper persists this
     * stable account/range representation as its key.
     */
    @AerospikeKey
    public String getKey() {
        return String.format("%s-%d-%d", acctId, first, last);
    }

    /*
     * Mapper hydration calls the key setter. Parse from the right because account IDs
     * such as "ACC-1234" legitimately contain dashes of their own.
     */
    @AerospikeKey(setter = true)
    public void setKey(String key) {
        int index = key.lastIndexOf('-');
        this.last = Long.parseLong(key.substring(index + 1));
        int firstIndex = key.lastIndexOf('-', index - 1);
        this.first = Long.parseLong(key.substring(firstIndex + 1, index));
        this.acctId = key.substring(0, firstIndex);
    }
}
