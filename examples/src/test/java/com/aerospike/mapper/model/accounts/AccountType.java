package com.aerospike.mapper.model.accounts;

/** Account discriminator stored by the base {@link Account} mapping. */
public enum AccountType {
    SAVINGS,
    CHECKING,
    PORTFOLIO,
    LOAN
}
