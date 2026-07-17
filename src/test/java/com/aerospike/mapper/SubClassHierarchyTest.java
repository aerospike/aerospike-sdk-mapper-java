package com.aerospike.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeConstructor;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;

import static org.junit.jupiter.api.Assertions.*;

public class SubClassHierarchyTest extends MapperBaseTest {
    @AerospikeRecord
    public static class BaseClass {
        private Date lastReadTime;
        private final Date creationTime;

        public BaseClass() {
            this.creationTime = new Date();
        }
    }

    @AerospikeRecord(set = "customer", namespace = "test")
    public static class Customer extends BaseClass {
        @AerospikeKey
        @AerospikeBin(name = "id")
        private final String customerId;
        private final String name;
        @AerospikeBin(name = "date")
        private Date dateJoined;
        @AerospikeReference
        private List<Account> accounts;
        private final Map<String, Account> accountMap;

        private Account primaryAcc;
        private Account secondaryAcc;
        private Savings savingsAcc;
        private Checking checkAcc;
        private Portfolio pfolioAcc;

        public Customer(String customerId, String name) {
            this(customerId, name, new Date());
        }

        @AerospikeConstructor
        public Customer(@ParamFrom("id") String customerId, @ParamFrom("name") String name, @ParamFrom("date") Date dateJoined) {
            this.customerId = customerId;
            this.name = name;
            this.dateJoined = dateJoined;
            this.accounts = new ArrayList<>();
            this.accountMap = new HashMap<>();
        }

        public Date getDateJoined() {
            return dateJoined;
        }

        public void setDateJoined(Date dateJoined) {
            this.dateJoined = dateJoined;
        }

        public List<Account> getAccounts() {
            return accounts;
        }

        public void setAccounts(List<Account> accounts) {
            this.accounts = accounts;
        }

        public String getCustomerId() {
            return customerId;
        }

        public String getName() {
            return name;
        }
    }

    @AerospikeRecord(namespace = "test", set = "subaccs", durableDelete = true, sendKey = true)
    public static class Account extends BaseClass {
        @AerospikeKey
        protected String id;
        protected long balance;
    }

    // Don't need the parameters here, this is just for testing
    @AerospikeRecord(namespace = "test", set = "subaccs", shortName = "SVG")
    public static class Savings extends Account {
        private long numDeposits;
        private float interestRate;
    }

    @AerospikeRecord(shortName = "CHK")
    public static class Checking extends Account {
        private int checksWritten;
    }

    @AerospikeRecord(namespace = "test", set = "pfolio")
    public static class Portfolio extends Account {
        private List<String> subAccounts;
    }

    @AerospikeRecord(namespace = "test", set = "container")
    private static class Container {
        @AerospikeKey
        private long id;
        private Account account;
        private Savings savings;
        private Checking checking;
        @AerospikeEmbed(elementType = EmbedType.LIST)
        private final List<Account> accountList = new ArrayList<>();
        private Account primaryAccount;
    }

    @Test
    public void runTest() {
        MappingEngine.Builder engineBuilder = MappingEngine.builder()
                .preLoadClasses(BaseClass.class, Customer.class, Account.class,
                        Savings.class, Checking.class, Portfolio.class);
        MappingSession mapper = createMapper(engineBuilder);

        Customer customer = new Customer("cust1", "Tim");

        Savings savingsAccount1 = new Savings();
        savingsAccount1.interestRate = 0.03f;
        savingsAccount1.numDeposits = 17;
        savingsAccount1.id = "SVNG1";
        savingsAccount1.balance = 200;
        mapper.save(savingsAccount1);

        Savings savingsAccount2 = new Savings();
        savingsAccount2.interestRate = 0.045f;
        savingsAccount2.numDeposits = 11;
        savingsAccount2.id = "SVNG2";
        savingsAccount2.balance = 99;
        mapper.save(savingsAccount2);

        Checking checkingAccount1 = new Checking();
        checkingAccount1.checksWritten = 4;
        checkingAccount1.id = "CHK1";
        checkingAccount1.balance = 600;
        mapper.save(checkingAccount1);

        Checking checkingAccount2 = new Checking();
        checkingAccount2.checksWritten = 23;
        checkingAccount2.id = "CHK2";
        checkingAccount2.balance = 10902;
        mapper.save(checkingAccount2);

        Account account = new Account();
        account.balance = 927;
        account.id = "Account1";
        mapper.save(account);

        Container container = new Container();
        container.account = account;
        container.checking = checkingAccount1;
        container.savings = savingsAccount1;
        container.primaryAccount = savingsAccount1;
        container.accountList.add(account);
        container.accountList.add(savingsAccount1);
        container.accountList.add(checkingAccount1);
        mapper.save(container);

        customer.accounts.add(savingsAccount1);
        customer.accounts.add(checkingAccount1);

        Portfolio portfolio = new Portfolio();
        portfolio.subAccounts = Arrays.asList("sub-1", "sub-2", "sub-3");
        portfolio.id = "PF1";
        portfolio.balance = 1000;
        customer.accounts.add(portfolio);
        mapper.save(portfolio);

        customer.pfolioAcc = portfolio;
        customer.checkAcc = checkingAccount1;
        customer.savingsAcc = savingsAccount1;
        customer.primaryAcc = checkingAccount1;
        customer.secondaryAcc = portfolio;

        customer.accountMap.put("A", portfolio);
        customer.accountMap.put("B", savingsAccount1);
        customer.accountMap.put("C", checkingAccount1);

        mapper.save(customer);

        Customer customer2 = mapper.read(Customer.class, customer.getCustomerId()).orElse(null);

        assertNotNull(customer2);
        assertEquals("cust1", customer2.getCustomerId());
        assertEquals("Tim", customer2.getName());

        assertNotNull(customer2.getAccounts());
        assertEquals(3, customer2.getAccounts().size());

        assertEquals("SVNG1", customer2.savingsAcc.id);
        assertEquals(200, customer2.savingsAcc.balance);
        assertEquals("CHK1", customer2.checkAcc.id);
        assertEquals(600, customer2.checkAcc.balance);
        assertEquals("PF1", customer2.pfolioAcc.id);
        assertEquals(1000, customer2.pfolioAcc.balance);

        assertEquals("CHK1", customer2.primaryAcc.id);
        assertEquals("PF1", customer2.secondaryAcc.id);

        assertEquals(3, customer2.accountMap.size());
        assertEquals("PF1", customer2.accountMap.get("A").id);
        assertEquals("SVNG1", customer2.accountMap.get("B").id);
        assertEquals("CHK1", customer2.accountMap.get("C").id);
    }
}
