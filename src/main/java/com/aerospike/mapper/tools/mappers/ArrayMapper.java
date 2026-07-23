package com.aerospike.mapper.tools.mappers;

import java.lang.reflect.Array;
import java.util.List;

import com.aerospike.mapper.tools.HydrationContext;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.utils.TypeUtils;

public class ArrayMapper extends TypeMapper {

    private final Class<?> instanceClass;
    private final boolean supportedWithoutTranslation;
    private final TypeMapper instanceClassMapper;
    private final Boolean allowBatch;

    public ArrayMapper(final Class<?> instanceClass, final TypeMapper instanceClassMapper, final boolean allowBatch) {
        this.instanceClass = instanceClass;
        this.supportedWithoutTranslation = TypeUtils.isByteType(instanceClass);
        this.instanceClassMapper = instanceClassMapper;
        this.allowBatch = allowBatch;
    }

    @Override
    public Object toAerospikeFormat(Object value) {
        if (value == null) {
            return null;
        }
        if (this.supportedWithoutTranslation) {
            return value;
        }

        return CollectionMapperSupport.mapArrayToAerospikeList(value, this.instanceClassMapper::toAerospikeFormat);
    }

    @Override
    public Object fromAerospikeFormat(Object value) {
        return fromAerospikeFormat(value, new HydrationContext(null));
    }

    @Override
    public Object fromAerospikeFormat(Object value, HydrationContext ctx) {
        if (value == null) {
            return null;
        }
        List<?> list = (List<?>) value;
        if (this.supportedWithoutTranslation) {
            return value;
        }

        Object result = Array.newInstance(instanceClass, list.size());
        CollectionMapperSupport.hydrateArray(list, result,
                item -> this.instanceClassMapper.fromAerospikeFormat(item, ctx), ctx, allowBatch);
        return result;
    }
}
