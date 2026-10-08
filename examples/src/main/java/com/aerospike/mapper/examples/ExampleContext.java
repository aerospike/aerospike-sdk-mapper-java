package com.aerospike.mapper.examples;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.Session;

public record ExampleContext(Cluster cluster, Session session, String namespace) {
    public DataSet dataSet(String set) {
        return DataSet.of(namespace, set);
    }
}
