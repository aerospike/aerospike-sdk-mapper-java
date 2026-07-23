package com.aerospike.mapper.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.MapperBaseTest;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.MappingSession;

/**
 * Example-oriented tests that document which generic declarations the mapper supports
 */
public class GenericExamplesTest extends MapperBaseTest {

    private MappingSession mapper;

    @BeforeEach
    public void setup() {
        mapper = createMapper();
        truncateSet(NAMESPACE, "generic-example-items");
        truncateSet(NAMESPACE, "generic-example-containers");
    }

    @AerospikeRecord(namespace = "test", set = "generic-example-items")
    public static class PaymentMethod {
        @AerospikeKey
        public int id;
        public String label;
    }

    @AerospikeRecord(namespace = "test", set = "generic-example-items", shortName = "CARD")
    public static class CardPayment extends PaymentMethod {
        public String lastFour;
    }

    @AerospikeRecord(namespace = "test", set = "generic-example-items", shortName = "BANK")
    public static class BankTransfer extends PaymentMethod {
        public String ibanSuffix;
    }

    @AerospikeRecord(namespace = "test", set = "generic-example-containers")
    public static class SupportedUpperBoundedContainer {
        @AerospikeKey
        public int id;

        // The mapper reduces `? extends PaymentMethod` to the upper bound `PaymentMethod`
        // when choosing a mapper. Concrete subtype markers are still written, so the
        // hydrated collection retains CardPayment/BankTransfer instances.
        @AerospikeEmbed(type = EmbedType.LIST, elementType = EmbedType.MAP)
        public List<? extends PaymentMethod> methods;

        // Same idea for map values: the wildcard is reduced to the upper bound for mapper
        // selection, while the stored subtype metadata preserves the concrete type.
        @AerospikeEmbed(type = EmbedType.MAP, elementType = EmbedType.MAP)
        public Map<String, ? extends PaymentMethod> methodsByName;
    }

    @AerospikeRecord(namespace = "test", set = "generic-example-containers")
    public static class UnsupportedWildcardContainer {
        @AerospikeKey
        public int id;

        // `? extends Object` is effectively unbounded from the mapper's point of view, so
        // it is rejected. The mapper requires a concrete element type, or an upper bound
        // narrower than Object that it can reduce to.
        @AerospikeEmbed(type = EmbedType.LIST, elementType = EmbedType.MAP)
        public List<? extends Object> values;
    }

    @Test
    public void upperBoundedWildcardCollectionsRoundTripAsTheirUpperBound() {
        SupportedUpperBoundedContainer original = new SupportedUpperBoundedContainer();
        original.id = 1;

        CardPayment card = new CardPayment();
        card.id = 101;
        card.label = "primary-card";
        card.lastFour = "4242";

        BankTransfer bank = new BankTransfer();
        bank.id = 202;
        bank.label = "salary-account";
        bank.ibanSuffix = "9988";

        original.methods = List.of(card, bank);
        Map<String, PaymentMethod> methodsByName = new LinkedHashMap<>();
        methodsByName.put("card", card);
        methodsByName.put("bank", bank);
        original.methodsByName = methodsByName;

        mapper.save(original);

        SupportedUpperBoundedContainer read = mapper.read(SupportedUpperBoundedContainer.class, 1).orElse(null);
        assertNotNull(read);
        assertNotNull(read.methods);
        assertNotNull(read.methodsByName);
        assertEquals(2, read.methods.size());
        assertEquals(2, read.methodsByName.size());

        CardPayment readCard = assertInstanceOf(CardPayment.class, read.methods.get(0));
        BankTransfer readBank = assertInstanceOf(BankTransfer.class, read.methods.get(1));
        assertEquals("4242", readCard.lastFour);
        assertEquals("9988", readBank.ibanSuffix);

        CardPayment readMappedCard = assertInstanceOf(CardPayment.class, read.methodsByName.get("card"));
        BankTransfer readMappedBank = assertInstanceOf(BankTransfer.class, read.methodsByName.get("bank"));
        assertEquals("primary-card", readMappedCard.label);
        assertEquals("salary-account", readMappedBank.label);
    }

    @Test
    public void extendsObjectWildcardIsRejectedBecauseItDoesNotResolveToASpecificType() {
        UnsupportedWildcardContainer original = new UnsupportedWildcardContainer();
        original.id = 2;
        original.values = new ArrayList<>(List.of("text", 123));

        AerospikeException exception = assertThrows(AerospikeException.class, () -> mapper.save(original));
        assertTrue(exception.getMessage().contains("Unsupported generic declaration"));
        assertTrue(exception.getMessage().contains("? extends java.lang.Object")
                || exception.getMessage().contains("? extends Object")
                || exception.getMessage().contains("unbounded wildcards"));
    }
}