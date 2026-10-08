package com.aerospike.mapper.examples.model;

import java.time.Instant;

import com.aerospike.mapper.annotations.AerospikeRecord;

/**
 * Small embedded transaction model retained from the original example package.
 *
 * <p>It is intentionally independent of the runnable scenarios so readers can copy it
 * as a minimal POJO mapping starting point.</p>
 */
@AerospikeRecord
public class Transaction {
    private long amount;
    private String description;
    private Instant time;
    private boolean isFraud;

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public boolean isFraud() {
        return isFraud;
    }

    public void setFraud(boolean isFraud) {
        this.isFraud = isFraud;
    }
}
