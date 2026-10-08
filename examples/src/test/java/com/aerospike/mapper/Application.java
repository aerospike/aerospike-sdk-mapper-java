package com.aerospike.mapper;

import com.aerospike.mapper.model.*;
import com.aerospike.mapper.model.accounts.Account;
import com.aerospike.mapper.model.accounts.LoanAccount;
import com.aerospike.mapper.model.accounts.PortfolioAccount;
import com.aerospike.mapper.tools.AeroMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Full banking-domain graph from the original examples package.
 *
 * <p>This deliberately exercises more than a minimal CRUD path: subtype mappings,
 * embedded addresses and valuations, referenced records, a composite method key, maps,
 * lists, arrays, enums, and immutable constructor arguments all participate in one
 * customer round trip.</p>
 */
public class Application extends ApplicationBase implements RunnableExample {

    @Override
    public void run(ExampleContext context) {
        // The catalog owns the Cluster and Session. Copying this body into an
        // application only requires supplying its own Session to the mapper builder.
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();

        // Addresses are embedded values. Reusing the same Java instance here does not
        // create a separate address record.
        Address mailingAddress = new Address("773 Elm St", "Apt 2", "Grand Junction", "CO", "83451");
        Address billingAddress = new Address("123 Main St", null, "Denver", "CO", "80001");
        Address alternateAddress = new Address("1 Park Road", null, "Miami", "FL", "98531");

        Customer customer = createAndPopulateCustomer();
        customer.setMailingAddress(mailingAddress);

        /* Create a checking account for this customer */
        Account checkingAccount = createAndPopulateChecking(customer.getCustomerId());
        checkingAccount.setBillingAddress(billingAddress);
        checkingAccount.getAlternateAddresses().add(mailingAddress);
        checkingAccount.getAlternateAddresses().add(alternateAddress);

        // Checkbooks demonstrate a composite @AerospikeKey assembled by an accessor.
        // Their issuer is a reference, so the branches are also saved independently.
        Branch issuingBranch = new Branch("Br123", new Address("77 Park Road", null, "Miami", "FL", "98531"), "Miami Central");
        Checkbook checkbook = createAndPopulateCheckbook1(checkingAccount.getAccountId(), issuingBranch);
        checkingAccount.getCheckbooks().put(1, checkbook);

        Branch otherIssuingBranch = new Branch("Br567", new Address("129 Bump Drive", null, "New York", "NY", "77777"), "New York City Office");
        Checkbook checkbook2 = createAndPopulateCheckbook2(checkingAccount.getAccountId(), otherIssuingBranch);
        checkingAccount.getCheckbooks().put(2, checkbook2);

        mapper.save(checkingAccount);
        customer.getAccounts().add(checkingAccount);

        /* Create a savings account for this customer */
        Account savingsAccount = createAndPopulateSavingsAccount(customer.getCustomerId());
        savingsAccount.setBillingAddress(billingAddress);
        savingsAccount.getAlternateAddresses().add(mailingAddress);

        mapper.save(savingsAccount);
        customer.getAccounts().add(savingsAccount);

        /*
         * A portfolio is a mapped Account subtype. Properties are independently
         * addressable records and are also represented in the portfolio's domain data.
         */
        Property property1 = createAndPopulateProperty1();
        Property property2 = createAndPopulateProperty2();
        Property property3 = createAndPopulateProperty3();
        mapper.save(property1);
        mapper.save(property2);
        mapper.save(property3);

        PortfolioAccount portfolioAccount = createAndPopulatePortfolioAccount(customer.getCustomerId());
        portfolioAccount.setBillingAddress(billingAddress);
        portfolioAccount.getAlternateAddresses().add(mailingAddress);
        portfolioAccount.addPropertyToPortfolio(property1, 0.0239f);
        portfolioAccount.addPropertyToPortfolio(property2, 0.0299f);
        portfolioAccount.addPropertyToPortfolio(property3, 0.0319f);

        mapper.save(portfolioAccount);
        customer.getAccounts().add(portfolioAccount);

        // LoanAccount rolls up into the base account set and carries its security
        // property plus loan-specific dates, rate, and interest type.
        LoanAccount loanAccount = createAndPopulateLoanAccount(customer.getCustomerId());
        loanAccount.setBillingAddress(billingAddress);
        loanAccount.getAlternateAddresses().add(mailingAddress);
        customer.getAccounts().add(loanAccount);

        Property securityProperty = createAndPopulateProperty4();
        loanAccount.setSecurityProperty(securityProperty);

        mapper.save(securityProperty);
        mapper.save(loanAccount);

        mapper.save(issuingBranch);
        mapper.save(otherIssuingBranch);
        mapper.save(customer);
        mapper.save(checkbook);
        mapper.save(checkbook2);

        /*
         * Preserve the original repeated-read loop. It makes mapper cache warm-up and
         * end-to-end graph hydration visible, but it is illustrative timing rather than
         * a statistically rigorous benchmark.
         */
        Customer readCustomer = null;
        for (int i = 0; i < 100; i++) {
            long now = System.nanoTime();
            readCustomer = mapper.read(Customer.class, customer.getCustomerId());
            System.out.printf("Customer graph read time: %.3fms%n", (System.nanoTime() - now) / 1000000f);
        }
        ObjectWriter objectWriter = JsonMapper.builder().build().writerWithDefaultPrettyPrinter();
        String readString = objectWriter.writeValueAsString(readCustomer);
        System.out.println(readString);
        String originalObject = objectWriter.writeValueAsString(customer);
        equal(originalObject, readString, "complete banking graph should round-trip");
    }
}
