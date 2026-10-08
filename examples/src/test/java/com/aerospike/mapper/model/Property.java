package com.aerospike.mapper.model;

import java.util.ArrayList;
import java.util.List;

import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;

/**
 * A property is saved independently and may be referenced by portfolio and loan
 * accounts. Its address and valuation history are embedded snapshots.
 */
@AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = Property.SET)
public class Property {
    public static final String SET = "sdk_mapper_examples_banking_property";

    @AerospikeKey
    private final long id;

    // Embedded annotations keep these objects in the same Aerospike record as the
    // property instead of creating Address or Valuation records.
    @AerospikeEmbed
    private final Address address;
    @AerospikeEmbed
    private final List<Valuation> valuations;
    @AerospikeBin(name = "cyear")
    private final int constructionYear;
    @AerospikeBin(name = "cnstrn")
    private String construction;
    private String notes;

    public Property(@ParamFrom("id") long id, @ParamFrom("address") Address address, @ParamFrom("cyear") int constructionYear, @ParamFrom("cnstrn") String construction) {
        super();
        this.id = id;
        this.address = address;
        this.constructionYear = constructionYear;
        this.construction = construction;
        this.valuations = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public List<Valuation> getValuations() {
        return valuations;
    }

    public Property addValuation(Valuation valuation) {
        this.valuations.add(valuation);
        return this;
    }

    public String getConstruction() {
        return construction;
    }

    public void setConstruction(String construction) {
        this.construction = construction;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Address getAddress() {
        return address;
    }

    public int getConstructionYear() {
        return constructionYear;
    }
}
