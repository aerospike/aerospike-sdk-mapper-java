package com.aerospike.mapper.examples;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.examples.model.Customer;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaMapperApplication extends ApplicationBase {

    public void run() throws JsonProcessingException {
        Cluster cluster = new ClusterDefinition("127.0.0.1", 3000).connect();

        MappingEngine engine = MappingEngine.builder().build();
        MappingSession mapper = cluster.createSession(Behavior.DEFAULT, engine.sessionExtension());
        Customer customer = createAndPopulateCustomer();

        mapper.save(customer);
        Customer customer2 = null;
        for (int i = 0; i < 100; i++) {
            long now = System.nanoTime();
            customer2 = mapper.read(Customer.class, customer.getCustomerId()).orElse(null);
            System.out.printf("Customer graph read time: %.3fms%n", (System.nanoTime() - now) / 1000000f);
        }

        ObjectWriter objectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
        String readString = objectWriter.writeValueAsString(customer2);
        System.out.println(readString);
        String originalObject = objectWriter.writeValueAsString(customer);
        assertEquals(originalObject, readString);

        cluster.close();
    }
}
