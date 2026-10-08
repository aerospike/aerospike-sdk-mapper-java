package com.aerospike.mapper;

import com.aerospike.client.sdk.Cluster;

@FunctionalInterface
interface ExampleConnectionFactory {
    Cluster connect(String host, int port);
}
