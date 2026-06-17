package com.aerospike.mapper.tools.converters;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.ClassCacheEntry;
import com.aerospike.mapper.tools.DeferredObjectLoader;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObjectSetter;
import com.aerospike.mapper.tools.IBaseAeroMapper;
import com.aerospike.mapper.tools.LoadedObjectResolver;
import com.aerospike.mapper.tools.ThreadLocalKeySaver;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.utils.MapperUtils;
import com.aerospike.mapper.tools.utils.TypeUtils;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MappingConverter {

    private final IBaseAeroMapper mapper;

    public MappingConverter(IBaseAeroMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Translate a Java object to an Aerospike format object. Note that this could potentially have performance issues as
     * the type information of the passed object must be determined on every call.
     *
     * @param obj A given Java object.
     * @return An Aerospike format object.
     */
    public Object translateToAerospike(Object obj) {
        if (obj == null) {
            return null;
        }
        TypeMapper thisMapper = TypeUtils.getMapper(obj.getClass(), TypeUtils.AnnotatedType.getDefaultAnnotateType(), mapper);
        return thisMapper == null ? obj : thisMapper.toAerospikeFormat(obj);
    }

    /**
     * Translate an Aerospike object to a Java object. Note that this could potentially have performance issues as
     * the type information of the passed object must be determined on every call.
     *
     * @param obj A given Java object.
     * @return An Aerospike format object.
     */
    @SuppressWarnings("unchecked")
    public <T> T translateFromAerospike(@NotNull Object obj, @NotNull Class<T> expectedClazz, Session session) {
        TypeMapper thisMapper = TypeUtils.getMapper(expectedClazz, TypeUtils.AnnotatedType.getDefaultAnnotateType(), mapper);
        T result = (T) (thisMapper == null ? obj : thisMapper.fromAerospikeFormat(obj));
        resolveDependencies(session);
        return result;
    }

    /**
     * Given a record loaded from Aerospike and a class type, attempt to convert the record to
     * an instance of the passed class.
     */
    public <T> T convertToObject(Class<T> clazz, Key key, Record record) {
        return convertToObject(clazz, key, record, null);
    }

    /**
     * Given a record loaded from Aerospike and a class type, attempt to convert the record to
     * an instance of the passed class.
     */
    public <T> T convertToObject(Class<T> clazz, Key key, Record record, ClassCacheEntry<T> entry) {
        return this.convertToObject(clazz, key, record, entry, true, null);
    }

    /**
     * Convert a record to an object, optionally resolving dependencies using the given session.
     */
    public <T> T convertToObject(Class<T> clazz, Key key, Record record, ClassCacheEntry<T> entry,
                                 boolean resolveDependencies, Session session) {
        if (entry == null) {
            entry = ClassCache.getInstance().loadClass(clazz, mapper);
        }
        T result = entry.constructAndHydrate(key, record);
        if (resolveDependencies && session != null) {
            resolveDependencies(session);
        }
        return result;
    }

    /**
     * Given a list of records loaded from Aerospike and a class type, attempt to convert the records to
     * an instance of the passed class.
     */
    public <T> T convertToObject(Class<T> clazz, List<Object> record) {
        return this.convertToObject(clazz, record, true, null);
    }

    /**
     * Convert a list-encoded object, optionally resolving dependencies.
     */
    public <T> T convertToObject(Class<T> clazz, List<Object> record, boolean resolveDependencies, Session session) {
        try {
            ClassCacheEntry<T> entry = ClassCache.getInstance().loadClass(clazz, mapper);
            T result;
            result = clazz.getConstructor().newInstance();
            entry.hydrateFromList(record, result);
            if (resolveDependencies && session != null) {
                resolveDependencies(session);
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new AerospikeException(e);
        }
    }

    /**
     * Given a map of records loaded from Aerospike and a class type, attempt to convert the records to
     * an instance of the passed class.
     */
    public <T> T convertToObject(Class<T> clazz, Map<String, Object> record) {
        ClassCacheEntry<T> entry = ClassCache.getInstance().loadClass(clazz, mapper);
        return entry.constructAndHydrate(record);
    }

    /**
     * Given an instance of a class (of any type), convert its properties to a list
     */
    @SuppressWarnings("unchecked")
    public <T> List<Object> convertToList(@NotNull T instance) {
        ClassCacheEntry<T> entry = (ClassCacheEntry<T>) ClassCache.getInstance().loadClass(instance.getClass(), mapper);
        return entry.getList(instance, false, false);
    }

    /**
     * Given an instance of a class (of any type), convert its properties to a map, properties names will use as the
     * key and properties values will be the values.
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, Object> convertToMap(@NotNull T instance) {
        ClassCacheEntry<T> entry = (ClassCacheEntry<T>) ClassCache.getInstance().loadClass(instance.getClass(), mapper);
        return entry.getMap(instance, false);
    }

    private Key createKey(ClassCacheEntry<?> entry, DeferredObjectLoader.DeferredObject deferredObject) {
        if (deferredObject.isDigest()) {
            return new Key(entry.getNamespace(), (byte[]) deferredObject.getKey(), entry.getSetName(), null);
        } else {
            return new Key(entry.getNamespace(), entry.getSetName(), Value.get(entry.translateKeyToAerospikeKey(deferredObject.getKey())));
        }
    }

    /**
     * Resolve deferred reference dependencies using batch reads via the given Session.
     * <p/>
     * If an object refers to other objects (eg A has a list of B via references), reading the object populates the
     * IDs. These objects are loaded via batch reads and mapped back to their references.
     * <p/>
     * Loaded child objects may themselves have references, so we iterate until the deferred queue is empty.
     */
    @SuppressWarnings("unchecked")
    public void resolveDependencies(Session session) {
        List<DeferredObjectLoader.DeferredObjectSetter> deferredObjects = DeferredObjectLoader.getAndClear();

        if (deferredObjects.isEmpty()) {
            return;
        }

        while (!deferredObjects.isEmpty()) {
            List<Key> keyList = new ArrayList<>();
            List<ClassCacheEntry<?>> classCacheEntryList = new ArrayList<>();

            for (Iterator<DeferredObjectSetter> iterator = deferredObjects.iterator(); iterator.hasNext(); ) {
                DeferredObjectSetter thisObjectSetter = iterator.next();
                DeferredObjectLoader.DeferredObject deferredObject = thisObjectSetter.getObject();
                Class<?> clazz = deferredObject.getType();
                ClassCacheEntry<?> entry = MapperUtils.getEntryAndValidateNamespace(clazz, mapper);

                Key aKey = createKey(entry, deferredObject);
                Object result = LoadedObjectResolver.get(aKey);
                if (result != null) {
                    thisObjectSetter.getSetter().setValue(result);
                    iterator.remove();
                } else {
                    keyList.add(aKey);
                    classCacheEntryList.add(entry);
                }
            }

            int size = keyList.size();
            if (size > 0) {
                Map<Key, Record> recordMap = new HashMap<>();
                try (RecordStream stream = session.query(keyList).execute()) {
                    while (stream.hasNext()) {
                        RecordResult rr = stream.next();
                        if (rr.isOk() && rr.recordOrNull() != null) {
                            recordMap.put(rr.key(), rr.recordOrNull());
                        }
                    }
                }

                for (int i = 0; i < size; i++) {
                    DeferredObjectLoader.DeferredObjectSetter thisObjectSetter = deferredObjects.get(i);
                    Key key = keyList.get(i);
                    Record record = recordMap.get(key);
                    try {
                        ThreadLocalKeySaver.save(key);
                        Object obj = convertToObject(
                                (Class<Object>) thisObjectSetter.getObject().getType(),
                                key,
                                record,
                                (ClassCacheEntry<Object>) classCacheEntryList.get(i),
                                false,
                                session);
                        Object result = record == null ? null : obj;
                        thisObjectSetter.getSetter().setValue(result);
                    } finally {
                        ThreadLocalKeySaver.clear();
                    }
                }
            }
            deferredObjects = DeferredObjectLoader.getAndClear();
        }
    }
}
