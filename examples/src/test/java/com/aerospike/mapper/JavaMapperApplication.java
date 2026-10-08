package com.aerospike.mapper;

import com.aerospike.mapper.model.Customer;
import com.aerospike.mapper.tools.AeroMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Focused mapper version of the customer round trip.
 *
 * <p>Run this next to {@link NonJavaMapperApplication} to compare declarative mapper
 * persistence with the equivalent hand-written SDK bin operations.</p>
 */
public class JavaMapperApplication extends ApplicationBase implements RunnableExample {

    @Override
    public void run(ExampleContext context) {
        // Cluster/session lifecycle belongs to MapperExamples; a scenario only consumes
        // the shared session and must not close it.
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();
        Customer customer = createAndPopulateCustomer();

        mapper.save(customer);

        // Keep the original repeated reads so the example still displays warm-cache
        // graph hydration timings.
        Customer customer2 = null;
        for (int i = 0; i < 100; i++) {
            long now = System.nanoTime();
            customer2 = mapper.read(Customer.class, customer.getCustomerId());
            System.out.printf("Customer graph read time: %.3fms%n", (System.nanoTime() - now) / 1000000f);
        }

        ObjectWriter objectWriter = JsonMapper.builder().build().writerWithDefaultPrettyPrinter();
        String readString = objectWriter.writeValueAsString(customer2);
        System.out.println(readString);
        String originalObject = objectWriter.writeValueAsString(customer);
        equal(originalObject, readString, "mapper customer should round-trip");
    }
}
