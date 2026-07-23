package com.aerospike.mapper.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;

final class ClassCacheEntryHydrationHelper<T> {

    private record ListHydrationTarget(ClassCacheEntry<?> entry, int endIndex) {
    }

    private record VersionMarker(int recordVersion, int endIndex) {
    }

    private final ClassCacheEntry<T> entry;

    ClassCacheEntryHydrationHelper(ClassCacheEntry<T> entry) {
        this.entry = entry;
    }

    @SuppressWarnings("unchecked")
    T constructAndHydrate(Key key, Record record, Map<String, Object> map, HydrationContext ctx)
            throws ReflectiveOperationException {
        Map<String, Object> valueMap = new HashMap<>();
        ClassCacheEntry<?> thisClass = entry;

        String className = map == null ? record.getString(ClassCacheEntry.TYPE_NAME)
                : (String) map.get(ClassCacheEntry.TYPE_NAME);
        if (className != null) {
            thisClass = resolveHydrationClass(className, entry.getRawClassInternal());
        }

        T result = null;
        while (thisClass != null) {
            populateValueMapFromRecordOrMap(key, record, map, ctx, valueMap, thisClass, className);
            if (result == null) {
                result = (T) thisClass.constructAndHydrateFromJavaMapInternal(valueMap, ctx);
            } else {
                applyHydratedValues(thisClass, result, valueMap, ctx);
            }
            valueMap.clear();
            thisClass = thisClass.getSuperClazzInternal();
        }

        if (result != null && entry.getGenerationField() != null && record != null) {
            try {
                entry.getGenerationField().set(result, record.generation);
            } catch (ReflectiveOperationException e) {
                throw new AerospikeException("Failed to set generation field from record generation", e);
            }
        }

        return result;
    }

    void hydrateFromRecordOrMap(Record record, Map<String, Object> map, Object instance, HydrationContext ctx)
            throws ReflectiveOperationException {
        ClassCacheEntry<?> thisClass = entry;
        while (thisClass != null) {
            for (String name : thisClass.getValuesInternal().keySet()) {
                ValueType value = thisClass.getValuesInternal().get(name);
                Object aerospikeValue = record == null ? map.get(name) : record.getValue(name);
                value.set(instance, value.getTypeMapper().fromAerospikeFormat(aerospikeValue, ctx), ctx);
            }
            thisClass = thisClass.getSuperClazzInternal();
        }
    }

    @SuppressWarnings("unchecked")
    T constructAndHydrate(List<Object> list, boolean skipKey, HydrationContext ctx) throws ReflectiveOperationException {
        Map<String, Object> valueMap = new HashMap<>();
        int index = 0;
        ListHydrationTarget hydrationTarget = resolveHydrationTarget(list, entry.getRawClassInternal());
        ClassCacheEntry<?> thisClass = hydrationTarget.entry();
        int endIndex = hydrationTarget.endIndex();

        T result = null;
        while (thisClass != null && index < endIndex) {
            VersionMarker versionMarker = resolveVersionMarker(list, endIndex);
            int recordVersion = versionMarker.recordVersion();
            endIndex = versionMarker.endIndex();

            index = hydrateListValues(thisClass, list, skipKey, ctx, index, recordVersion, null, valueMap);

            if (result == null) {
                result = (T) thisClass.constructAndHydrateFromJavaMapInternal(valueMap, ctx);
            } else {
                applyHydratedValues(thisClass, result, valueMap, ctx);
            }
            valueMap.clear();
            thisClass = thisClass.getSuperClazzInternal();
        }
        return result;
    }

    void hydrateFromList(List<Object> list, Object instance, boolean skipKey, HydrationContext ctx)
            throws ReflectiveOperationException {
        int index = 0;
        int endIndex = list.size();
        ClassCacheEntry<?> thisClass = entry;
        while (thisClass != null && index < endIndex) {
            VersionMarker versionMarker = resolveVersionMarker(list, endIndex);
            int recordVersion = versionMarker.recordVersion();
            endIndex = versionMarker.endIndex();

            index = hydrateListValues(thisClass, list, skipKey, ctx, index, recordVersion, instance, null);
            thisClass = thisClass.getSuperClazzInternal();
        }
    }

    private int hydrateListValues(ClassCacheEntry<?> thisClass, List<Object> list, boolean skipKey,
            HydrationContext ctx, int index, int recordVersion, Object instance, Map<String, Object> valueMap)
            throws ReflectiveOperationException {
        int objectVersion = thisClass.getVersionInternal();
        Map<Integer, String> ordinals = thisClass.getOrdinalsInternal();
        if (ordinals != null) {
            for (int i = 1; i <= ordinals.size(); i++) {
                String name = ordinals.get(i);
                if (!skipKey || !isKeyField(thisClass, name)) {
                    index = setValueByField(thisClass, name, objectVersion, recordVersion, instance, index, list,
                            valueMap, ctx);
                }
            }
        }

        Set<String> fieldsWithOrdinals = thisClass.getFieldsWithOrdinalsInternal();
        for (String name : thisClass.getValuesInternal().keySet()) {
            if ((fieldsWithOrdinals == null || !fieldsWithOrdinals.contains(name))
                    && (!skipKey || !isKeyField(thisClass, name))) {
                index = setValueByField(thisClass, name, objectVersion, recordVersion, instance, index, list,
                        valueMap, ctx);
            }
        }
        return index;
    }

    private ListHydrationTarget resolveHydrationTarget(List<Object> list, Class<?> anchorClass) throws ClassNotFoundException {
        int endIndex = list.size();
        ClassCacheEntry<?> hydrationClass = entry;
        if (!list.isEmpty() && list.get(endIndex - 1) instanceof String classMarker
                && classMarker.startsWith(ClassCacheEntry.TYPE_PREFIX)) {
            String className = classMarker.substring(ClassCacheEntry.TYPE_PREFIX.length());
            hydrationClass = resolveHydrationClass(className, anchorClass);
            endIndex--;
        }
        return new ListHydrationTarget(hydrationClass, endIndex);
    }

    private VersionMarker resolveVersionMarker(List<Object> list, int endIndex) {
        if (endIndex > 0 && list.get(endIndex - 1) instanceof String versionMarker
                && versionMarker.startsWith(ClassCacheEntry.VERSION_PREFIX)) {
            return new VersionMarker(Integer.parseInt(versionMarker.substring(2)), endIndex - 1);
        }
        return new VersionMarker(1, endIndex);
    }

    private ClassCacheEntry<?> resolveHydrationClass(String className, Class<?> anchorClass) throws ClassNotFoundException {
        ClassCacheEntry<?> resolved = ClassCache.getInstance().resolveTypeIdentifier(className, entry.getEngineInternal(),
                anchorClass);
        if (resolved != null) {
            return resolved;
        }
        Class<?> typeClazz = Class.forName(className);
        return ClassCache.getInstance().loadClass(typeClazz, entry.getEngineInternal());
    }

    private void populateValueMapFromRecordOrMap(Key key, Record record, Map<String, Object> map, HydrationContext ctx,
            Map<String, Object> valueMap, ClassCacheEntry<?> thisClass, String className) throws ReflectiveOperationException {
        for (String name : thisClass.getValuesInternal().keySet()) {
            ValueType value = thisClass.getValuesInternal().get(name);
            Object aerospikeValue;
            if (record == null) {
                aerospikeValue = map.get(name);
            } else if (name.equals(thisClass.getKeyNameInternal()) && !thisClass.isKeyAsBinInternal()) {
                if (key.userKey != null) {
                    aerospikeValue = key.userKey.getObject();
                } else {
                    throw new AerospikeException(String.format(
                            "Key field on class %s was <null> for key %s. Was the record saved passing 'sendKey = true'? ",
                            className, key));
                }
            } else {
                aerospikeValue = record.getValue(name);
            }
            valueMap.put(name, value.getTypeMapper().fromAerospikeFormat(aerospikeValue, ctx));
        }
    }

    private void applyHydratedValues(ClassCacheEntry<?> thisClass, Object instance, Map<String, Object> valueMap,
            HydrationContext ctx) throws ReflectiveOperationException {
        for (String field : valueMap.keySet()) {
            ValueType value = thisClass.getValuesInternal().get(field);
            value.set(instance, valueMap.get(field), ctx);
        }
    }

    private boolean isKeyField(ClassCacheEntry<?> thisClass, String name) {
        String keyName = thisClass.getKeyNameInternal();
        return keyName != null && keyName.equals(name);
    }

    private int setValueByField(ClassCacheEntry<?> thisClass, String name, int objectVersion, int recordVersion,
            Object instance, int index, List<Object> list, Map<String, Object> map, HydrationContext ctx)
            throws ReflectiveOperationException {
        ValueType value = thisClass.getValuesInternal().get(name);
        TypeMapper typeMapper = value.getTypeMapper();
        if (!(value.getMinimumVersion() <= objectVersion && objectVersion <= value.getMaximumVersion())) {
            if (value.getMinimumVersion() <= recordVersion && recordVersion <= value.getMaximumVersion()) {
                index++;
            }
            return index;
        }
        if (value.getMinimumVersion() <= recordVersion && recordVersion <= value.getMaximumVersion()
                && index < list.size()) {
            Object aerospikeValue = list.get(index++);
            Object javaValue = aerospikeValue == null ? null : typeMapper.fromAerospikeFormat(aerospikeValue, ctx);
            if (instance == null) {
                map.put(name, javaValue);
            } else {
                value.set(instance, javaValue, ctx);
            }
        }
        return index;
    }
}