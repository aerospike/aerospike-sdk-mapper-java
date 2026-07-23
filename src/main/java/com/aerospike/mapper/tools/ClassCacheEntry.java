package com.aerospike.mapper.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.exceptions.NotAnnotatedClass;
import com.aerospike.mapper.tools.configuration.ClassConfig;
import com.aerospike.mapper.tools.utils.ParserUtils;

public class ClassCacheEntry<T> {

    public static final String VERSION_PREFIX = "@V";
    public static final String TYPE_PREFIX = "@T:";
    public static final String TYPE_NAME = ".type";

    private String namespace;
    private String setName;
    private Integer ttl = null;
    private boolean mapAll = true;
    private Boolean sendKey = null;
    private Boolean durableDelete = null;
    private int version = 1;

    private final Class<T> clazz;
    private ValueType key;
    private String keyName = null;
    private boolean keyAsBin = true;
    private ValueType generationField = null;
    private final TreeMap<String, ValueType> values = new TreeMap<>();
    private ClassCacheEntry<?> superClazz;
    private int binCount;
    private final MappingEngine engine;
    private Map<Integer, String> ordinals = null;
    private Set<String> fieldsWithOrdinals = null;
    private final ClassConfig classConfig;
    private final ClassConfig config;

    private String factoryMethod;
    private String factoryClass;
    private final ClassCacheEntryMetadataResolver<T> metadataResolver;
    private final ClassCacheEntryConstructorResolver<T> constructorResolver;
    private final ClassCacheEntryHydrationHelper<T> hydrationHelper;

    /**
     * When there are subclasses, we need to store the type information to be able
     * to re-create an instance of the same type. As the class name can be verbose,
     * we provide the ability to set a string representing the class name. This
     * string must be unique for all classes.
     */
    private String shortenedClassName;
    private boolean isChildClass = false;

    private volatile boolean constructed;

    // package visibility only.
    ClassCacheEntry(@NotNull Class<T> clazz, MappingEngine engine, ClassConfig config, boolean requireRecord) {
        this.clazz = clazz;
        this.engine = engine;
        this.classConfig = config;
        this.metadataResolver = new ClassCacheEntryMetadataResolver<>(this);
        this.constructorResolver = new ClassCacheEntryConstructorResolver<>(this);
        this.hydrationHelper = new ClassCacheEntryHydrationHelper<>(this);

        AerospikeRecord recordDescription = clazz.getAnnotation(AerospikeRecord.class);
        if (requireRecord && recordDescription == null && config == null) {
            throw new NotAnnotatedClass(
                    String.format("Class %s is not augmented by the @AerospikeRecord annotation", clazz.getName()));
        } else if (recordDescription != null) {
            this.setPropertiesFromAerospikeRecord(recordDescription);
        }
        this.config = config;
    }

    public ClassCacheEntry<T> construct() {
        if (config != null) {
            config.validate();
            this.overrideSettings(config);
        }

        if (this.namespace == null || this.namespace.isEmpty()) {
            List<AerospikeRecord> aerospikeInterfaceRecords = this.loadAerospikeRecordsFromInterfaces(this.clazz);
            for (int i = 0; (this.namespace == null || this.namespace.isEmpty())
                    && i < aerospikeInterfaceRecords.size(); i++) {
                this.setPropertiesFromAerospikeRecord(aerospikeInterfaceRecords.get(i));
            }
        }
        this.metadataResolver.resolve();
        this.superClazz = ClassCache.getInstance().loadClass(this.clazz.getSuperclass(), this.engine, !this.mapAll);
        this.binCount = this.values.size() + (superClazz != null ? superClazz.binCount : 0);
        this.constructorResolver.resolve();
        if (StringUtils.isBlank(this.shortenedClassName)) {
            this.shortenedClassName = clazz.getSimpleName();
        }
        ClassCache.getInstance().setStoredName(this, this.shortenedClassName);

        this.checkRecordSettingsAgainstSuperClasses();
        constructed = true;
        return this;
    }

    public boolean isNotConstructed() {
        return !constructed;
    }

    public Class<?> getUnderlyingClass() {
        return this.clazz;
    }

    public ClassConfig getClassConfig() {
        return this.classConfig;
    }

    public String getShortenedClassName() {
        return this.shortenedClassName;
    }

    private void overrideSettings(ClassConfig config) {
        if (!StringUtils.isBlank(config.getNamespace())) {
            this.namespace = config.getNamespace();
        }
        if (!StringUtils.isBlank(config.getSet())) {
            this.setName = config.getSet();
        }
        if (config.getTtl() != null) {
            this.ttl = config.getTtl();
        }
        if (config.getVersion() != null) {
            this.version = config.getVersion();
        }
        if (config.getDurableDelete() != null) {
            this.durableDelete = config.getDurableDelete();
        }
        if (config.getMapAll() != null) {
            this.mapAll = config.getMapAll();
        }
        if (config.getSendKey() != null) {
            this.sendKey = config.getSendKey();
        }
        if (config.getShortName() != null) {
            this.shortenedClassName = config.getShortName();
        }
        if (config.getFactoryMethod() != null) {
            this.factoryMethod = config.getFactoryMethod();
        }
        if (config.getFactoryClass() != null) {
            this.factoryClass = config.getFactoryClass();
        }
    }

    public boolean isChildClass() {
        return isChildClass;
    }

    private List<AerospikeRecord> loadAerospikeRecordsFromInterfaces(Class<?> clazz) {
        List<AerospikeRecord> results = new ArrayList<>();
        Class<?>[] interfaces = clazz.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            Class<?> thisInterface = interfaces[i];
            AerospikeRecord[] aerospikeRecords = thisInterface.getAnnotationsByType(AerospikeRecord.class);
            for (int j = 0; j < aerospikeRecords.length; j++) {
                results.add(aerospikeRecords[j]);
            }
            results.addAll(loadAerospikeRecordsFromInterfaces(thisInterface));
        }
        return results;
    }

    private void setPropertiesFromAerospikeRecord(AerospikeRecord recordDescription) {
        this.namespace = ParserUtils.getInstance().get(recordDescription.namespace());
        this.setName = ParserUtils.getInstance().get(recordDescription.set());
        this.ttl = recordDescription.ttl();
        this.mapAll = recordDescription.mapAll();
        this.version = recordDescription.version();
        this.sendKey = recordDescription.sendKey();
        this.durableDelete = recordDescription.durableDelete();
        this.shortenedClassName = recordDescription.shortName();
        this.factoryClass = recordDescription.factoryClass();
        this.factoryMethod = recordDescription.factoryMethod();
    }

    private void checkRecordSettingsAgainstSuperClasses() {
        if (!StringUtils.isBlank(this.namespace) && !StringUtils.isBlank(this.setName)) {
            // This class defines its own namespace + set, it is only a child class if its
            // closest named superclass is the same as ours.
            this.isChildClass = false;
            ClassCacheEntry<?> thisEntry = this.superClazz;
            while (thisEntry != null) {
                if ((!StringUtils.isBlank(thisEntry.getNamespace()))
                        && (!StringUtils.isBlank(thisEntry.getSetName()))) {
                    if (this.namespace.equals(thisEntry.getNamespace())
                            && this.setName.equals(thisEntry.getSetName())) {
                        this.isChildClass = true;
                    }
                    break;
                }
                thisEntry = thisEntry.superClazz;
            }
        } else {
            // Otherwise this is a child class, find the set and namespace from the closest
            // highest class
            this.isChildClass = true;
            ClassCacheEntry<?> thisEntry = this.superClazz;
            while (thisEntry != null) {
                if ((!StringUtils.isBlank(thisEntry.getNamespace()))
                        && (!StringUtils.isBlank(thisEntry.getSetName()))) {
                    this.namespace = thisEntry.getNamespace();
                    this.setName = thisEntry.getSetName();
                    break;
                }
                thisEntry = thisEntry.superClazz;
            }
        }
        ClassCacheEntry<?> thisEntry = this.superClazz;
        while (thisEntry != null) {
            if (this.durableDelete == null && thisEntry.getDurableDelete() != null) {
                this.durableDelete = thisEntry.getDurableDelete();
            }
            if (this.ttl == null && thisEntry.getTtl() != null) {
                this.ttl = thisEntry.getTtl();
            }
            if (this.sendKey == null && thisEntry.getSendKey() != null) {
                this.sendKey = thisEntry.getSendKey();
            }
            if (this.key == null && thisEntry.key != null) {
                this.key = thisEntry.key;
            }
            thisEntry = thisEntry.superClazz;
        }
    }

    public Object translateKeyToAerospikeKey(Object key) {
        return this.key.getTypeMapper().toAerospikeFormat(key);
    }

    private Object _getKey(Object object) throws ReflectiveOperationException {
        if (this.key != null) {
            return this.translateKeyToAerospikeKey(this.key.get(object));
        } else if (superClazz != null) {
            return this.superClazz._getKey(object);
        }
        return null;
    }

    public Object getKey(Object object) {
        try {
            Object key = this._getKey(object);
            if (key == null) {
                throw new AerospikeException(String.format(
                        "Null key from annotated object of class %s. Did you forget an @AerospikeKey annotation?",
                        this.clazz.getSimpleName()));
            }
            return key;
        } catch (ReflectiveOperationException re) {
            throw new AerospikeException(re);
        }
    }

    private void _setKey(Object object, Object value) throws ReflectiveOperationException {
        _setKey(object, value, new HydrationContext(null));
    }

    private void _setKey(Object object, Object value, HydrationContext ctx) throws ReflectiveOperationException {
        if (this.key != null) {
            this.key.set(object, this.key.getTypeMapper().fromAerospikeFormat(value, ctx), ctx);
        } else if (superClazz != null) {
            this.superClazz._setKey(object, value, ctx);
        }
    }

    public void setKey(Object object, Object value) {
        try {
            this._setKey(object, value);
        } catch (ReflectiveOperationException re) {
            throw new AerospikeException(re);
        }
    }

    public void setKey(Object object, Object value, HydrationContext ctx) {
        try {
            this._setKey(object, value, ctx);
        } catch (ReflectiveOperationException re) {
            throw new AerospikeException(re);
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public String getSetName() {
        return setName;
    }

    public Integer getTtl() {
        if (ttl == null || ttl == Integer.MIN_VALUE) {
            return null;
        }
        return ttl;
    }

    public Boolean getSendKey() {
        return sendKey;
    }

    public Boolean getDurableDelete() {
        return durableDelete;
    }

    public ValueType getGenerationField() {
        return generationField;
    }

    public Integer getGenerationValue(Object instance) {
        if (generationField == null) {
            return null;
        }
        try {
            Object value = generationField.get(instance);
            return switch (value) {
                case Integer integer -> integer;
                case Number number -> number.intValue();
                case null, default -> null;
            };
        } catch (ReflectiveOperationException e) {
            throw new AerospikeException("Failed to get generation field value", e);
        }
    }

    private boolean contains(String[] names, String thisName) {
        if (names == null || names.length == 0) {
            return true;
        }
        if (thisName == null) {
            return false;
        }
        return Arrays.stream(names).anyMatch(thisName::equals);
    }

    public Map<String, Object> getMap(Object instance, boolean needsType) {
        return getMap(instance, needsType, false, null);
    }

    /**
     * Convert the object to a Map suitable for the SDK's RecordMapper.toMap() contract.
     *
     * @param instance      the object instance
     * @param allowNullBins if true, include null-valued bins; if false, skip them
     * @param binNames      optional bin name filter; null or empty means all bins
     * @return Map of bin-name to Aerospike-format value
     */
    public Map<String, Object> getMap(Object instance, boolean allowNullBins, String[] binNames) {
        return getMap(instance, false, allowNullBins, binNames);
    }

    private Map<String, Object> getMap(Object instance, boolean needsType, boolean allowNullBins, String[] binNames) {
        try {
            Map<String, Object> results = new HashMap<>();
            ClassCacheEntry<?> thisClass = this;
            if (needsType) {
                results.put(TYPE_NAME, this.getShortenedClassName());
            }
            while (thisClass != null) {
                for (String name : thisClass.values.keySet()) {
                    if (name.equals(thisClass.keyName) && !thisClass.keyAsBin) {
                        continue;
                    }
                    if (contains(binNames, name)) {
                        ValueType value = thisClass.values.get(name);
                        Object javaValue = value.get(instance);
                        Object aerospikeValue = value.getTypeMapper().toAerospikeFormat(javaValue);
                        if (aerospikeValue != null || allowNullBins) {
                            results.put(name, aerospikeValue);
                        }
                    }
                }
                thisClass = thisClass.superClazz;
            }
            return results;
        } catch (ReflectiveOperationException ref) {
            throw new AerospikeException(ref);
        }
    }

    private void addDataFromValueName(String name, Object instance, ClassCacheEntry<?> thisClass, List<Object> results)
            throws ReflectiveOperationException {
        ValueType value = thisClass.values.get(name);
        if (value.getMinimumVersion() <= thisClass.version && thisClass.version <= value.getMaximumVersion()) {
            Object javaValue = value.get(instance);
            Object aerospikeValue = value.getTypeMapper().toAerospikeFormat(javaValue);
            results.add(aerospikeValue);
        }
    }

    private boolean isKeyField(String name) {
        return keyName != null && keyName.equals(name);
    }

    public List<Object> getList(Object instance, boolean skipKey, boolean needsType) {
        try {
            List<Object> results = new ArrayList<>();
            List<Object> versionsToAdd = new ArrayList<>();
            ClassCacheEntry<?> thisClass = this;
            while (thisClass != null) {
                if (thisClass.version > 1) {
                    versionsToAdd.add(0, VERSION_PREFIX + thisClass.version);
                }
                if (thisClass.ordinals != null) {
                    for (int i = 1; i <= thisClass.ordinals.size(); i++) {
                        String name = thisClass.ordinals.get(i);
                        if (!skipKey || !isKeyField(name)) {
                            addDataFromValueName(name, instance, thisClass, results);
                        }
                    }
                }
                for (String name : thisClass.values.keySet()) {
                    if (thisClass.fieldsWithOrdinals == null || !thisClass.fieldsWithOrdinals.contains(name)) {
                        if (!skipKey || !isKeyField(name)) {
                            addDataFromValueName(name, instance, thisClass, results);
                        }
                    }
                }
                thisClass = thisClass.superClazz;
            }
            results.addAll(versionsToAdd);
            if (needsType) {
                results.add(TYPE_PREFIX + this.getShortenedClassName());
            }
            return results;
        } catch (ReflectiveOperationException ref) {
            throw new AerospikeException(ref);
        }
    }

    public T constructAndHydrate(Map<String, Object> map) {
        return constructAndHydrate(map, new HydrationContext(null));
    }

    public T constructAndHydrate(Map<String, Object> map, HydrationContext ctx) {
        return constructAndHydrate(null, null, map, ctx);
    }

    public T constructAndHydrate(Key key, Record record) {
        return constructAndHydrate(key, record, new HydrationContext(null));
    }

    public T constructAndHydrate(Key key, Record record, HydrationContext ctx) {
        return constructAndHydrate(key, record, (Map<String, Object>) null, ctx);
    }

    @SuppressWarnings("unchecked")
    private T constructAndHydrate(Key key, Record record, Map<String, Object> map, HydrationContext ctx) {
        try {
            return this.hydrationHelper.constructAndHydrate(key, record, map, ctx);
        } catch (ReflectiveOperationException ref) {
            throw new AerospikeException(ref);
        }
    }

    public void hydrateFromRecord(Record record, Object instance) {
        hydrateFromRecordOrMap(record, null, instance, new HydrationContext(null));
    }

    private void hydrateFromRecordOrMap(Record record, Map<String, Object> map, Object instance, HydrationContext ctx) {
        try {
            this.hydrationHelper.hydrateFromRecordOrMap(record, map, instance, ctx);
        } catch (ReflectiveOperationException ref) {
            throw new AerospikeException(ref);
        }
    }

    public void hydrateFromList(List<Object> list, Object instance) {
        this.hydrateFromList(list, instance, false);
    }

    @SuppressWarnings("unchecked")
    private T constructAndHydrateFromJavaMap(Map<String, Object> javaValuesMap, HydrationContext ctx) throws ReflectiveOperationException {
        return this.constructorResolver.instantiate(javaValuesMap, ctx);
    }

    T constructAndHydrateFromJavaMapInternal(Map<String, Object> javaValuesMap, HydrationContext ctx)
            throws ReflectiveOperationException {
        return this.constructAndHydrateFromJavaMap(javaValuesMap, ctx);
    }

    Class<T> getRawClassInternal() {
        return this.clazz;
    }

    MappingEngine getEngineInternal() {
        return this.engine;
    }

    ClassCacheEntry<?> getSuperClazzInternal() {
        return this.superClazz;
    }

    Map<String, ValueType> getValuesInternal() {
        return this.values;
    }

    ClassConfig getConfigInternal() {
        return this.config;
    }

    boolean isMapAllInternal() {
        return this.mapAll;
    }

    ValueType getKeyInternal() {
        return this.key;
    }

    void setKeyInternal(ValueType key) {
        this.key = key;
    }

    ValueType getGenerationFieldInternal() {
        return this.generationField;
    }

    void setGenerationFieldInternal(ValueType generationField) {
        this.generationField = generationField;
    }

    String getFactoryMethodNameInternal() {
        return this.factoryMethod;
    }

    String getFactoryClassNameInternal() {
        return this.factoryClass;
    }

    int getVersionInternal() {
        return this.version;
    }

    String getKeyNameInternal() {
        return this.keyName;
    }

    void setKeyNameInternal(String keyName) {
        this.keyName = keyName;
    }

    boolean isKeyAsBinInternal() {
        return this.keyAsBin;
    }

    void setKeyAsBinInternal(boolean keyAsBin) {
        this.keyAsBin = keyAsBin;
    }

    Map<Integer, String> getOrdinalsInternal() {
        return this.ordinals;
    }

    void setOrdinalsInternal(Map<Integer, String> ordinals) {
        this.ordinals = ordinals;
    }

    Set<String> getFieldsWithOrdinalsInternal() {
        return this.fieldsWithOrdinals;
    }

    void setFieldsWithOrdinalsInternal(Set<String> fieldsWithOrdinals) {
        this.fieldsWithOrdinals = fieldsWithOrdinals;
    }

    public T constructAndHydrate(List<Object> list, boolean skipKey) {
        return constructAndHydrate(list, skipKey, new HydrationContext(null));
    }

    @SuppressWarnings("unchecked")
    public T constructAndHydrate(List<Object> list, boolean skipKey, HydrationContext ctx) {
        try {
            return this.hydrationHelper.constructAndHydrate(list, skipKey, ctx);
        } catch (ReflectiveOperationException ref) {
            throw new AerospikeException(ref);
        }
    }

    public void hydrateFromList(List<Object> list, Object instance, boolean skipKey) {
        hydrateFromList(list, instance, skipKey, new HydrationContext(null));
    }

    public void hydrateFromList(List<Object> list, Object instance, boolean skipKey, HydrationContext ctx) {
        try {
            this.hydrationHelper.hydrateFromList(list, instance, skipKey, ctx);
        } catch (ReflectiveOperationException ref) {
            throw new AerospikeException(ref);
        }
    }

    public ValueType getValueFromBinName(String name) {
        return this.values.get(name);
    }

    @Override
    public String toString() {
        return String.format("ClassCacheEntry<%s> (ns=%s,set=%s,subclass=%b,shortName=%s)",
                this.getUnderlyingClass().getSimpleName(), this.namespace, this.setName, this.isChildClass,
                this.shortenedClassName);
    }
}
