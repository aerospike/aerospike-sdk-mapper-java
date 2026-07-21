package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Session;

public abstract class TypeMapper {
    public abstract Object toAerospikeFormat(Object value);

    public abstract Object fromAerospikeFormat(Object value);

    /**
     * Session-aware deserialization convenience entry point for top-level callers.
     * Internally this routes through {@link HydrationContext} so nested hydration,
     * deferred resolution, and key tracking share one context model.
     */
    public Object fromAerospikeFormat(Object value, Session session) {
        return fromAerospikeFormat(value, new HydrationContext(session));
    }

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
