package com.aerospike.mapper;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.model.Customer;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.util.Date;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Manual SDK counterpart to {@link JavaMapperApplication}.
 *
 * <p>The two scenarios intentionally write the same set and bin schema. This version
 * exposes the repetitive serialization and reconstruction code that {@code AeroMapper}
 * derives from annotations.</p>
 */
public class NonJavaMapperApplication extends ApplicationBase implements RunnableExample {

    private void save(Session session, String namespace, Customer customer) {
        Key key = new Key(namespace, Customer.SET, Value.get(customer.getCustomerId()));

        // Each Java property is converted into an Aerospike bin explicitly. Dates use
        // epoch milliseconds so this representation matches the mapper's date codec.
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

    private Customer read(Session session, String namespace, String id) {
        Key key = new Key(namespace, Customer.SET, Value.get(id));

        // getFirstRecord() is the terminal point-query operation and closes its result
        // stream automatically. This example then reconstructs the domain object by hand.
        Record record = session.query(key)
                .execute()
                .getFirstRecord();

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

    @Override
    public void run(ExampleContext context) {
        Session session = context.session();
        Customer customer = createAndPopulateCustomer();
        save(session, context.namespace(), customer);

        // Retain the original repeated reads for a direct visual comparison with the
        // mapper scenario. These timings are illustrative, not a benchmark.
        Customer customer2 = null;
        for (int i = 0; i < 100; i++) {
            long now = System.nanoTime();
            customer2 = read(session, context.namespace(), customer.getCustomerId());
            System.out.printf("Customer graph read time: %.3fms%n", (System.nanoTime() - now) / 1000000f);
        }

        ObjectWriter objectWriter = JsonMapper.builder().build().writerWithDefaultPrettyPrinter();
        String readString = objectWriter.writeValueAsString(customer2);
        System.out.println(readString);
        String originalObject = objectWriter.writeValueAsString(customer);
        System.out.println(originalObject);
        equal(originalObject, readString, "manual SDK customer should round-trip");
    }
}
