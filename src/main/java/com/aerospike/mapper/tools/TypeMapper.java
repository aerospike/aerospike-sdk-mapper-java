package com.aerospike.mapper.tools;

public abstract class TypeMapper {
    public abstract Object toAerospikeFormat(Object value);

    public abstract Object fromAerospikeFormat(Object value);

    /**
     * Context-aware deserialization. Mappers that interact with deferred loading
     * (ListMapper, ArrayMapper, MapMapper) override this to route deferred objects
     * through the explicit context.
     */
    public Object fromAerospikeFormat(Object value, HydrationContext ctx) {
        return fromAerospikeFormat(value);
    }

    /**
     * Some types need to know if they're mapped to the correct class. If they do, they can override this method to glean that information
     */
    public Object toAerospikeFormat(Object value, boolean isUnknownType, boolean isSubclassOfKnownType) {
        return toAerospikeFormat(value);
    }
}
