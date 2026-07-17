package com.aerospike.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.aerospike.mapper.annotations.AerospikeBin;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ObjectTransformTest extends MapperBaseTest {
    @AerospikeRecord()
    public static class Transaction {
        public String name;
        public int value;
        @AerospikeKey
        public long time;

        public Transaction() {
        }

        public Transaction(String name, int value, long time) {
            super();
            this.name = name;
            this.value = value;
            this.time = time;
        }
    }

    @AerospikeRecord(namespace = "test", set = "account")
    public static class Account {
        @AerospikeKey
        @AerospikeBin(name = "id")
        public int accountId;

        @AerospikeEmbed(type = EmbedType.MAP, elementType = EmbedType.LIST)
        public List<Transaction> txns;

        @AerospikeEmbed(type = EmbedType.LIST, elementType = EmbedType.LIST)
        public List<Transaction> txns2;

        public Account() {
            this.txns = new ArrayList<>();
            this.txns2 = new ArrayList<>();
        }
    }

    @Test
    public void saveTest() {
        Account account = new Account();
        account.accountId = 1;
        account.txns.add(new Transaction("details1", 100, 101));
        account.txns.add(new Transaction("details2", 200, 99));
        account.txns.add(new Transaction("details3", 300, 1010));

        account.txns2.add(new Transaction("details1", 100, 101));
        account.txns2.add(new Transaction("details2", 200, 99));
        account.txns2.add(new Transaction("details3", 300, 1010));

        MappingSession mapper = createMapper();
        mapper.save(account);

        Account account2 = mapper.read(Account.class, 1).orElse(null);
        assertEquals(account.txns.size(), account2.txns.size());
        assertEquals(account.txns2.size(), account2.txns2.size());

        // txns is MAP-embedded so element order is by key (time), not insertion order.
        // Look up by key instead of comparing by index.
        Map<Long, Transaction> txnsByTime = account2.txns.stream()
                .collect(Collectors.toMap(t -> t.time, Function.identity()));
        for (Transaction orig : account.txns) {
            Transaction read = txnsByTime.get(orig.time);
            assertNotNull(read, "Missing transaction with time=" + orig.time);
            assertEquals(orig.name, read.name);
            assertEquals(orig.value, read.value);
            assertEquals(orig.time, read.time);
        }

        // txns2 is LIST-embedded so insertion order is preserved.
        for (int i = 0; i < account.txns2.size(); i++) {
            assertEquals(account.txns2.get(i).name, account2.txns2.get(i).name);
            assertEquals(account.txns2.get(i).value, account2.txns2.get(i).value);
            assertEquals(account.txns2.get(i).time, account2.txns2.get(i).time);
        }
    }
}
