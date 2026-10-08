package com.aerospike.mapper.examples.model.accounts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.ParamFrom;
import com.aerospike.mapper.examples.model.Property;

/**
 * Specialized account with a property portfolio and per-property interest rates.
 *
 * <p>The explicit record annotation gives this subtype its own set while retaining the
 * fields mapped by {@link Account}.</p>
 */
@AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = PortfolioAccount.SET)
public class PortfolioAccount extends Account {
    public static final String SET = "sdk_mapper_examples_banking_portfolio";

    @AerospikeBin(name = "props")
    private final List<Property> properties;
    @AerospikeBin(name = "excls")
    private int[] contractClausesExcluded;
    private final Map<Long, Float> interestRates;

    public PortfolioAccount(@ParamFrom("id") String accountId, @ParamFrom("custId") String customerId, @ParamFrom("title") String title, @ParamFrom("type") AccountType type) {
        super(accountId, customerId, title, type);
        this.properties = new ArrayList<>();
        this.contractClausesExcluded = new int[0];
        this.interestRates = new HashMap<>();
    }

    public void setContractClausesExcluded(int... clauseIds) {
        this.contractClausesExcluded = clauseIds;
    }

    public void addPropertyToPortfolio(Property property, float interestRate) {
        this.properties.add(property);
        this.interestRates.put(property.getId(), interestRate);
    }

    public List<Property> getProperties() {
        return properties;
    }

    public int[] getContractClausesExcluded() {
        return contractClausesExcluded;
    }

    public Map<Long, Float> getInterestRates() {
        return interestRates;
    }
}
