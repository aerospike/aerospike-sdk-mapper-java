package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.utils.TypeUtils;
import com.aerospike.mapper.tools.utils.TypeUtils.AnnotatedType;

public class GenericTypeResolutionTest {

    private MappingEngine engine;

    @BeforeEach
    public void setup() {
        ClassCache.getInstance().clear();
        TypeUtils.clear();
        engine = MappingEngine.builder().build();
    }

    @AfterEach
    public void cleanup() {
        if (engine != null) {
            engine.close();
        }
    }

    @AerospikeRecord(namespace = "test", set = "generic-nested")
    public static class NestedStringContainer {
        @AerospikeKey
        public int id;
        public List<List<String>> nestedNames;
        public Map<String, List<String>> groups;
    }

    @AerospikeRecord(namespace = "test", set = "generic-object-base")
    public static class BaseItem {
        @AerospikeKey
        public int id;
        public String name;
    }

    @AerospikeRecord(namespace = "test", set = "generic-object-base", shortName = "GCA")
    public static class ChildItemA extends BaseItem {
        public List<String> tags = new ArrayList<>();
    }

    @AerospikeRecord(namespace = "test", set = "generic-object-base", shortName = "GCB")
    public static class ChildItemB extends BaseItem {
        public int score;
    }

    @AerospikeRecord(namespace = "test", set = "generic-nested-objects")
    public static class NestedObjectContainer {
        @AerospikeKey
        public int id;

        @AerospikeEmbed(type = EmbedType.LIST, elementType = EmbedType.LIST)
        public List<List<BaseItem>> nestedItems;
    }

    @AerospikeRecord(namespace = "test", set = "generic-wildcard-list")
    public static class WildcardListContainer {
        @AerospikeKey
        public int id;

        @AerospikeEmbed(type = EmbedType.LIST, elementType = EmbedType.LIST)
        public List<? extends BaseItem> items;
    }

    @AerospikeRecord(namespace = "test", set = "generic-wildcard-map")
    public static class WildcardMapContainer {
        @AerospikeKey
        public int id;

        @AerospikeEmbed(type = EmbedType.LIST, elementType = EmbedType.LIST)
        public Map<String, ? extends BaseItem> items;
    }

    public static class UnsupportedListDeclarations {
        public List<? super BaseItem> lowerBounded;
        public List<?> unbounded;
        public List<? extends Object> objectUpperBound;
    }

    public static class UnsupportedMapDeclarations {
        public Map<? extends String, BaseItem> wildcardKey;
    }

    public static class GenericHolder<T> {
        public List<T> variableList;
    }

    @Test
    public void testNestedStringCollectionsRoundTrip() {
        NestedStringContainer original = new NestedStringContainer();
        original.id = 10;
        original.nestedNames = List.of(List.of("alice", "bob"), List.of("carol"));
        original.groups = new LinkedHashMap<>();
        original.groups.put("team-a", List.of("x", "y"));
        original.groups.put("team-b", List.of("z"));

        Map<String, Object> record = engine.getMappingConverter().convertToMap(original);
        NestedStringContainer hydrated = engine.getMappingConverter().convertToObject(NestedStringContainer.class, record);

        assertEquals(original.id, hydrated.id);
        assertEquals(original.nestedNames, hydrated.nestedNames);
        assertEquals(original.groups, hydrated.groups);
    }

    @Test
    public void testNestedEmbeddedObjectCollectionsRoundTrip() {
        NestedObjectContainer original = new NestedObjectContainer();
        original.id = 20;
        original.nestedItems = new ArrayList<>();

        ChildItemA first = new ChildItemA();
        first.id = 1;
        first.name = "first";
        first.tags.add("t1");
        first.tags.add("t2");

        ChildItemB second = new ChildItemB();
        second.id = 2;
        second.name = "second";
        second.score = 99;

        original.nestedItems.add(List.of(first));
        original.nestedItems.add(List.of(second));

        Map<String, Object> record = engine.getMappingConverter().convertToMap(original);
        NestedObjectContainer hydrated = engine.getMappingConverter().convertToObject(NestedObjectContainer.class, record);

        assertNotNull(hydrated.nestedItems);
        assertEquals(2, hydrated.nestedItems.size());

        BaseItem hydratedFirst = hydrated.nestedItems.get(0).get(0);
        BaseItem hydratedSecond = hydrated.nestedItems.get(1).get(0);

        ChildItemA hydratedChildA = assertInstanceOf(ChildItemA.class, hydratedFirst);
        assertEquals(first.id, hydratedChildA.id);
        assertEquals(first.name, hydratedChildA.name);
        assertEquals(first.tags, hydratedChildA.tags);

        ChildItemB hydratedChildB = assertInstanceOf(ChildItemB.class, hydratedSecond);
        assertEquals(second.id, hydratedChildB.id);
        assertEquals(second.name, hydratedChildB.name);
        assertEquals(second.score, hydratedChildB.score);
    }

    @Test
    public void testUpperBoundedWildcardListRoundTripUsesUpperBoundMapper() {
        TypeMapper mapper = mapperForField(WildcardListContainer.class, "items");
        assertNotNull(mapper);

        WildcardListContainer original = new WildcardListContainer();
        original.id = 30;

        ChildItemA first = new ChildItemA();
        first.id = 11;
        first.name = "wild-a";
        first.tags.add("a");

        ChildItemB second = new ChildItemB();
        second.id = 12;
        second.name = "wild-b";
        second.score = 7;

        original.items = List.of(first, second);

        Map<String, Object> record = engine.getMappingConverter().convertToMap(original);
        WildcardListContainer hydrated = engine.getMappingConverter().convertToObject(WildcardListContainer.class, record);

        assertEquals(2, hydrated.items.size());
        ChildItemA hydratedFirst = assertInstanceOf(ChildItemA.class, hydrated.items.get(0));
        ChildItemB hydratedSecond = assertInstanceOf(ChildItemB.class, hydrated.items.get(1));
        assertEquals(first.tags, hydratedFirst.tags);
        assertEquals(second.score, hydratedSecond.score);
    }

    @Test
    public void testUpperBoundedWildcardMapValueRoundTripUsesUpperBoundMapper() {
        TypeMapper mapper = mapperForField(WildcardMapContainer.class, "items");
        assertNotNull(mapper);

        WildcardMapContainer original = new WildcardMapContainer();
        original.id = 40;
        Map<String, BaseItem> items = new LinkedHashMap<>();

        ChildItemA first = new ChildItemA();
        first.id = 21;
        first.name = "map-a";
        first.tags.add("left");

        ChildItemB second = new ChildItemB();
        second.id = 22;
        second.name = "map-b";
        second.score = 123;

        items.put("one", first);
        items.put("two", second);
        original.items = items;

        Map<String, Object> record = engine.getMappingConverter().convertToMap(original);
        WildcardMapContainer hydrated = engine.getMappingConverter().convertToObject(WildcardMapContainer.class, record);

        assertEquals(2, hydrated.items.size());
        ChildItemA hydratedFirst = assertInstanceOf(ChildItemA.class, hydrated.items.get("one"));
        ChildItemB hydratedSecond = assertInstanceOf(ChildItemB.class, hydrated.items.get("two"));
        assertEquals(first.tags, hydratedFirst.tags);
        assertEquals(second.score, hydratedSecond.score);
    }

    @Test
    public void testLowerBoundedWildcardRejected() {
        AerospikeException exception = assertThrows(AerospikeException.class,
                () -> mapperForField(UnsupportedListDeclarations.class, "lowerBounded"));
        assertTrue(exception.getMessage().contains("Unsupported generic declaration"));
        assertTrue(exception.getMessage().contains("lower-bounded wildcards are not supported"));
    }

    @Test
    public void testUnboundedWildcardRejected() {
        AerospikeException exception = assertThrows(AerospikeException.class,
                () -> mapperForField(UnsupportedListDeclarations.class, "unbounded"));
        assertTrue(exception.getMessage().contains("Unsupported generic declaration"));
        assertTrue(exception.getMessage().contains("?"));
    }

    @Test
    public void testExtendsObjectWildcardRejected() {
        AerospikeException exception = assertThrows(AerospikeException.class,
                () -> mapperForField(UnsupportedListDeclarations.class, "objectUpperBound"));
        assertTrue(exception.getMessage().contains("Unsupported generic declaration"));
        assertTrue(exception.getMessage().contains("'? extends java.lang.Object'")
                || exception.getMessage().contains("'?'"));
        assertTrue(exception.getMessage().contains("'? extends Object'")
                || exception.getMessage().contains("'? extends java.lang.Object'")
                || exception.getMessage().contains("unbounded wildcards and '? extends Object' are not supported"));
    }

    @Test
    public void testTypeVariableRejected() {
        AerospikeException exception = assertThrows(AerospikeException.class,
                () -> mapperForField(GenericHolder.class, "variableList"));
        assertTrue(exception.getMessage().contains("Unsupported generic declaration"));
        assertTrue(exception.getMessage().contains("unresolved type variables are not supported"));
    }

    @Test
    public void testWildcardMapKeyRejected() {
        AerospikeException exception = assertThrows(AerospikeException.class,
                () -> mapperForField(UnsupportedMapDeclarations.class, "wildcardKey"));
        assertTrue(exception.getMessage().contains("Unsupported generic declaration"));
        assertTrue(exception.getMessage().contains("map key"));
    }

    private TypeMapper mapperForField(Class<?> declaringClass, String fieldName) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            return TypeUtils.getMapper(field.getType(), new AnnotatedType(null, field), engine);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }
}