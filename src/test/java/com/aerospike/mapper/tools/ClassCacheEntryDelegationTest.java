package com.aerospike.mapper.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aerospike.mapper.MapperBaseTest;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.AerospikeReference;
import com.aerospike.mapper.annotations.ParamFrom;

class ClassCacheEntryDelegationTest extends MapperBaseTest {

    @AerospikeRecord(namespace = NAMESPACE, set = "delegation-child")
    static class ReferencedChild {
        @AerospikeKey
        final int id;
        final String name;

        ReferencedChild(@ParamFrom("id") int id, @ParamFrom("name") String name) {
            this.id = id;
            this.name = name;
        }
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "delegation-parent")
    static class ConstructorParent {
        @AerospikeKey
        final int id;

        @AerospikeReference
        final ReferencedChild child;

        final List<String> tags;

        ConstructorParent(@ParamFrom("id") int id, @ParamFrom("child") ReferencedChild child,
                @ParamFrom("tags") List<String> tags) {
            this.id = id;
            this.child = child;
            this.tags = tags;
        }
    }

    @AerospikeRecord(namespace = NAMESPACE, set = "delegation-account")
    static class BaseAccount {
        @AerospikeKey
        final int id;
        final String owner;

        BaseAccount(@ParamFrom("id") int id, @ParamFrom("owner") String owner) {
            this.id = id;
            this.owner = owner;
        }
    }

    @AerospikeRecord(shortName = "SAVG")
    static class SavingsAccount extends BaseAccount {
        final int balance;
        final List<String> labels;

        SavingsAccount(@ParamFrom("id") int id, @ParamFrom("owner") String owner,
                @ParamFrom("balance") int balance, @ParamFrom("labels") List<String> labels) {
            super(id, owner);
            this.balance = balance;
            this.labels = labels;
        }
    }

    @Test
    void constructAndHydrateFromMapResolvesDeferredConstructorReference() {
        MappingSession mapper = createMapper();
        MappingEngine engine = mapper.getEngine();

        ReferencedChild child = new ReferencedChild(501, "child-501");
        mapper.save(child);

        ConstructorParent original = new ConstructorParent(42, child, List.of("alpha", "beta"));
        ClassCacheEntry<ConstructorParent> entry = engine.loadClass(ConstructorParent.class);

        Map<String, Object> aerospikeMap = entry.getMap(original, false, null);
        ConstructorParent hydrated = entry.constructAndHydrate(aerospikeMap, new HydrationContext(session));

        assertEquals(original.id, hydrated.id);
        assertNotNull(hydrated.child);
        assertEquals(child.id, hydrated.child.id);
        assertEquals(child.name, hydrated.child.name);
        assertEquals(original.tags, hydrated.tags);
    }

    @Test
    void constructAndHydrateFromListResolvesDeferredConstructorReference() {
        MappingSession mapper = createMapper();
        MappingEngine engine = mapper.getEngine();

        ReferencedChild child = new ReferencedChild(777, "child-777");
        mapper.save(child);

        ConstructorParent original = new ConstructorParent(84, child, List.of("left", "right"));
        ClassCacheEntry<ConstructorParent> entry = engine.loadClass(ConstructorParent.class);

        List<Object> aerospikeList = entry.getList(original, false, false);
        ConstructorParent hydrated = entry.constructAndHydrate(aerospikeList, false, new HydrationContext(session));

        assertEquals(original.id, hydrated.id);
        assertNotNull(hydrated.child);
        assertEquals(child.id, hydrated.child.id);
        assertEquals(child.name, hydrated.child.name);
        assertEquals(original.tags, hydrated.tags);
    }

    @Test
    void baseEntryHydrationPreservesSubtypeMarkersForMapAndList() {
        MappingSession mapper = createMapper();
        MappingEngine engine = mapper.getEngine();

        SavingsAccount original = new SavingsAccount(912, "Ada", 300, List.of("vip", "rewards"));
        ClassCacheEntry<BaseAccount> baseEntry = engine.loadClass(BaseAccount.class);
        ClassCacheEntry<SavingsAccount> subtypeEntry = engine.loadClass(SavingsAccount.class);

        Map<String, Object> aerospikeMap = subtypeEntry.getMap(original, true);
        BaseAccount hydratedFromMap = baseEntry.constructAndHydrate(aerospikeMap, new HydrationContext(session));
        SavingsAccount mappedSavings = assertInstanceOf(SavingsAccount.class, hydratedFromMap);
        assertEquals(original.id, mappedSavings.id);
        assertEquals(original.owner, mappedSavings.owner);
        assertEquals(original.balance, mappedSavings.balance);
        assertEquals(original.labels, mappedSavings.labels);

        List<Object> aerospikeList = subtypeEntry.getList(original, false, true);
        BaseAccount hydratedFromList = baseEntry.constructAndHydrate(aerospikeList, false, new HydrationContext(session));
        SavingsAccount listedSavings = assertInstanceOf(SavingsAccount.class, hydratedFromList);
        assertEquals(original.id, listedSavings.id);
        assertEquals(original.owner, listedSavings.owner);
        assertEquals(original.balance, listedSavings.balance);
        assertEquals(original.labels, listedSavings.labels);
    }
}
