package com.aerospike.mapper.tools;

/**
 * Package-private interface that provides access to {@link MappingEngine}
 * for internal type mappers (e.g. {@link com.aerospike.mapper.tools.mappers.ObjectReferenceMapper})
 * without exposing the engine on the public {@link IBaseAeroMapper} interface.
 */
interface InternalMapper extends IBaseAeroMapper {
    MappingEngine getEngine();
}
