package com.aerospike.mapper.examples;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.examples.model.Customer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NonJavaMapperApplication extends ApplicationBase {

    private void save(Session session, Customer customer) {
        Key key = new Key("test", "customer", Value.get(customer.getCustomerId()));

        session.upsert(key)
                .bin("id").setTo(customer.getCustomerId())
                .bin("firstName").setTo(customer.getFirstName())
                .bin("lastName").setTo(customer.getLastName())
                .bin("dob").setTo(customer.getDateOfBirth() == null ? null : customer.getDateOfBirth().getTime())
                .bin("phone").setTo(customer.getPhone())
                .bin("joinedBank").setTo(customer.getJoinedBank() == null ? null : customer.getJoinedBank().getTime())
                .bin("vip").setTo(customer.isVip())
                .bin("greet").setTo(customer.getPreferredSalutation())
                .execute()
                .close();
    }

    private Customer read(Session session, String id) {
        Key key = new Key("test", "customer", Value.get(id));
        Record record;
        try (RecordStream stream = session.query(List.of(key)).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            record = (rr != null && rr.isOk()) ? rr.getRecord() : null;
        }

        Customer result = new Customer(record.getString("id"),
                record.getString("firstName"),
                record.getString("lastName"));

        long date = record.getLong("dob");
        result.setDateOfBirth(new Date(date));
        result.setPhone(record.getString("phone"));
        date = record.getLong("joinedBank");
        result.setJoinedBank(new Date(date));
        result.setVip(record.getBoolean("vip"));
        result.setPreferredSalutation(record.getString("greet"));

        return result;
    }

    public void run() throws JsonProcessingException {
        Cluster cluster = new ClusterDefinition("127.0.0.1", 3000).connect();
        Session session = cluster.createSession(Behavior.DEFAULT);

        Customer customer = createAndPopulateCustomer();
        save(session, customer);
        Customer customer2 = null;
        for (int i = 0; i < 100; i++) {
            long now = System.nanoTime();
            customer2 = read(session, customer.getCustomerId());
            System.out.printf("Customer graph read time: %.3fms%n", (System.nanoTime() - now) / 1000000f);
        }

        ObjectWriter objectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
        String readString = objectWriter.writeValueAsString(customer2);
        System.out.println(readString);
        String originalObject = objectWriter.writeValueAsString(customer);
        System.out.println(originalObject);
        assertEquals(originalObject, readString);

        cluster.close();
    }
}
