package com.aerospike.mapper.tools.virtuallist;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Operation;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.client.sdk.cdt.ListOperation;
import com.aerospike.client.sdk.cdt.ListReturnType;
import com.aerospike.client.sdk.cdt.MapOperation;
import com.aerospike.client.sdk.cdt.MapOrder;
import com.aerospike.client.sdk.cdt.MapPolicy;
import com.aerospike.client.sdk.cdt.MapReturnType;
import com.aerospike.mapper.annotations.AerospikeEmbed;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.ClassCacheEntry;
import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.ValueType;
import com.aerospike.mapper.tools.mappers.ListMapper;
import com.aerospike.mapper.tools.utils.TypeUtils;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseVirtualList<E> {

    protected final ClassCacheEntry<?> owningEntry;
    protected final ClassCacheEntry<?> elementEntry;
    protected final String binName;
    protected final ListMapper listMapper;
    protected final AerospikeEmbed.EmbedType listType;
    protected final MappingEngine engine;
    protected final Session session;
    protected volatile Key key;

    protected BaseVirtualList(Session session, @NotNull MappingEngine engine, Object object, Class<?> owningClazz,
                              Object key, @NotNull String binName, @NotNull Class<E> clazz) {
        if (object != null) {
            owningClazz = object.getClass();
        }
        this.engine = engine;
        this.session = session;
        this.owningEntry = ClassCache.getInstance().loadClass(owningClazz, engine);
        Object aerospikeKey;
        if (key == null) {
            aerospikeKey = owningEntry.getKey(object);
        } else {
            aerospikeKey = owningEntry.translateKeyToAerospikeKey(key);
        }

        this.elementEntry = ClassCache.getInstance().loadClass(clazz, engine);
        this.binName = binName;
        ValueType value = owningEntry.getValueFromBinName(binName);
        if (value == null) {
            throw new AerospikeException(String.format("Class %s has no bin called %s", clazz.getSimpleName(), binName));
        }
        String set = alignedSet();
        this.key = new Key(owningEntry.getNamespace(), set, Value.get(aerospikeKey));

        TypeUtils.AnnotatedType annotatedType = value.getAnnotatedType();
        AerospikeEmbed embed = annotatedType.getAnnotation(AerospikeEmbed.class);
        if (embed == null) {
            throw new AerospikeException(String.format("Bin %s on class %s is not specified as a embedded",
                    binName, clazz.getSimpleName()));
        }

        this.listType = embed.type() == AerospikeEmbed.EmbedType.DEFAULT ?
                AerospikeEmbed.EmbedType.LIST : embed.type();
        Class<?> binClazz = value.getType();
        if (!(binClazz.isArray() || (Map.class.isAssignableFrom(binClazz)) || List.class.isAssignableFrom(binClazz))) {
            throw new AerospikeException(String.format("Bin %s on class %s is not a collection class",
                    binName, clazz.getSimpleName()));
        }

        TypeMapper typeMapper = value.getTypeMapper();
        if (typeMapper instanceof ListMapper) {
            listMapper = ((ListMapper) typeMapper);
        } else {
            throw new AerospikeException(String.format(
                    "Bin %s on class %s is not mapped via a listMapper. This is unexpected", binName, clazz.getSimpleName()));
        }
    }

    /**
     * Copy constructor that replaces the session while sharing all other state.
     * Used by {@code VirtualList.withSession()}.
     */
    protected BaseVirtualList(Session session, MappingEngine engine,
                              ClassCacheEntry<?> owningEntry, ClassCacheEntry<?> elementEntry,
                              String binName, ListMapper listMapper,
                              AerospikeEmbed.EmbedType listType, Key key) {
        this.engine = engine;
        this.session = session;
        this.owningEntry = owningEntry;
        this.elementEntry = elementEntry;
        this.binName = binName;
        this.listMapper = listMapper;
        this.listType = listType;
        this.key = key;
    }

    protected String alignedSet() {
        String set = owningEntry.getSetName();
        if ("".equals(set)) {
            return null;
        }
        return set;
    }

    protected Session getSession() {
        if (session == null) {
            throw new IllegalStateException(
                    "VirtualList requires an explicit Session. " +
                            "Construct it from MappingSession/TxnMappingView or provide a session-backed copy.");
        }
        return session;
    }

    protected Value getValue(Object javaObject, boolean isKey) {
        Object aerospikeObject;
        if (isKey) {
            aerospikeObject = elementEntry.translateKeyToAerospikeKey(javaObject);
        } else {
            aerospikeObject = engine.getMappingConverter().translateToAerospike(javaObject);
        }
        if (aerospikeObject == null) {
            return null;
        }
        return Value.get(aerospikeObject);
    }

    protected List<Value> getValueList(List<Object> values, boolean isKey) {
        return values.stream()
                .map(v -> getValue(v, isKey))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    protected Operation getAppendOperation(Object aerospikeObject) {
        if (aerospikeObject instanceof Map.Entry) {
            Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>) aerospikeObject;
            return MapOperation.put(new MapPolicy(MapOrder.KEY_ORDERED, 0), binName,
                    Value.get(entry.getKey()), Value.get(entry.getValue()));
        } else {
            return ListOperation.append(binName, Value.get(aerospikeObject));
        }
    }

    protected Operation getGetByValueOperation(Object value, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValue(binName, getValue(value, false),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByValue(binName, getValue(value, false),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByValueRangeOperation(Object startValue, Object endValue, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValueRange(binName, getValue(startValue, false), getValue(endValue, false),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByValueRange(binName, getValue(startValue, false), getValue(endValue, false),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByValueListOperation(List<Object> values, ReturnType returnType) {
        List<Value> aerospikeValues = getValueList(values, false);
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValueList(binName, aerospikeValues,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByValueList(binName, aerospikeValues,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByValueRelativeRankRangeOperation(Object value, int rank, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValueRelativeRankRange(binName, getValue(value, false), rank,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByValueRelativeRankRange(binName, getValue(value, false), rank,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByValueRelativeRankRangeOperation(Object value, int rank, int count, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValueRelativeRankRange(binName, getValue(value, false), rank, count,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByValueRelativeRankRange(binName, getValue(value, false), rank, count,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByIndexRangeOperation(int index, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByIndexRange(binName, index,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByIndexRange(binName, index,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByIndexRangeOperation(int index, int count, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByIndexRange(binName, index, count,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByIndexRange(binName, index, count,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByRankOperation(int rank, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByRank(binName, rank,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByRank(binName, rank,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByRankRangeOperation(int rank, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByRankRange(binName, rank,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByRankRange(binName, rank,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByRankRangeOperation(int rank, int count, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByRankRange(binName, rank, count,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByRankRange(binName, rank, count,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByKeyOperation(Object mapKey, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValue(binName, getValue(mapKey, true),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByKey(binName, getValue(mapKey, true),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getGetByKeyRangeOperation(Object startKey, Object endKey, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByValueRange(binName, getValue(startKey, true), getValue(endKey, true),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.getByKeyRange(binName, getValue(startKey, true), getValue(endKey, true),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByKeyOperation(Object mapKey, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValue(binName, getValue(mapKey, true),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByKey(binName, getValue(mapKey, true),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByKeyRangeOperation(Object startKey, Object endKey, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValueRange(binName, getValue(startKey, true), getValue(endKey, true),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByKeyRange(binName, getValue(startKey, true), getValue(endKey, true),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByValueOperation(Object value, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValue(binName, getValue(value, false),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByValue(binName, getValue(value, false),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByValueListOperation(List<Object> values, ReturnType returnType) {
        List<Value> aerospikeValues = getValueList(values, false);
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValueList(binName, aerospikeValues,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByValueList(binName, aerospikeValues,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByValueRangeOperation(Object startValue, Object endValue, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValueRange(binName, getValue(startValue, false), getValue(endValue, false),
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByValueRange(binName, getValue(startValue, false), getValue(endValue, false),
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByValueRelativeRankRangeOperation(Object value, int rank, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValueRelativeRankRange(binName, getValue(value, false), rank,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByValueRelativeRankRange(binName, getValue(value, false), rank,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByValueRelativeRankRangeOperation(Object value, int rank, int count, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByValueRelativeRankRange(binName, getValue(value, false), rank, count,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByValueRelativeRankRange(binName, getValue(value, false), rank, count,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByIndexOperation(int index, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByIndex(binName, index,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByIndex(binName, index,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByIndexRangeOperation(int index, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByIndexRange(binName, index,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByIndexRange(binName, index,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByIndexRangeOperation(int index, int count, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByIndexRange(binName, index, count,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByIndexRange(binName, index, count,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByRankOperation(int rank, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByRank(binName, rank,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByRank(binName, rank,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByRankRangeOperation(int rank, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByRankRange(binName, rank,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByRankRange(binName, rank,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getRemoveByRankRangeOperation(int rank, int count, ReturnType returnType) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.removeByRankRange(binName, rank, count,
                    TypeUtils.returnTypeToListReturnType(returnType));
        } else {
            return MapOperation.removeByRankRange(binName, rank, count,
                    TypeUtils.returnTypeToMapReturnType(returnType));
        }
    }

    protected Operation getByIndexOperation(int index) {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.getByIndex(binName, index, ListReturnType.VALUE);
        } else {
            return MapOperation.getByIndex(binName, index, MapReturnType.KEY_VALUE);
        }
    }

    protected Operation getSizeOperation() {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.size(binName);
        } else {
            return MapOperation.size(binName);
        }
    }

    protected Operation getClearOperation() {
        if (listType == AerospikeEmbed.EmbedType.LIST) {
            return ListOperation.clear(binName);
        } else {
            return MapOperation.clear(binName);
        }
    }

    protected Record executeOperations(Operation... operations) {
        return getSession().upsert(key)
                .appendOperations(operations)
                .execute()
                .getFirstRecord();
    }

    @SuppressWarnings("unchecked")
    protected E unpackSingleResult(Object rawResult, HydrationContext ctx) {
        if (rawResult == null) {
            return null;
        }
        if (listType == AerospikeEmbed.EmbedType.MAP) {
            if (rawResult instanceof List<?> list) {
                rawResult = list.isEmpty() ? null : list.get(0);
            } else if (rawResult instanceof Map<?, ?> map) {
                rawResult = map.isEmpty() ? null : map.entrySet().iterator().next();
            }
        }
        if (rawResult == null) {
            return null;
        }
        return (E) listMapper.fromAerospikeInstanceFormat(rawResult, ctx);
    }

    @SuppressWarnings("unchecked")
    protected List<E> unpackListResult(Object rawResult, HydrationContext ctx) {
        if (rawResult == null) {
            return null;
        }
        if (rawResult instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rawResult;
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort((a, b) -> {
                Object ka = a.getKey(), kb = b.getKey();
                if (ka instanceof Comparable && kb instanceof Comparable) {
                    return ((Comparable<Object>) ka).compareTo(kb);
                }
                return 0;
            });
            List<E> results = new ArrayList<>(entries.size());
            for (Map.Entry<?, ?> entry : entries) {
                results.add((E) listMapper.fromAerospikeInstanceFormat(entry, ctx));
            }
            return results;
        } else if (rawResult instanceof Collection) {
            Collection<Object> source = (Collection<Object>) rawResult;
            List<E> results = new ArrayList<>(source.size());
            for (Object item : source) {
                results.add((E) listMapper.fromAerospikeInstanceFormat(item, ctx));
            }
            return results;
        }
        return null;
    }
}
