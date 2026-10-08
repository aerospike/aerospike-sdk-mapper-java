package com.aerospike.mapper.examples.model.accounts;

/** Account discriminator stored by the base {@link Account} mapping. */
public enum AccountType {
    SAVINGS,
    CHECKING,
    PORTFOLIO,
    LOAN
}
