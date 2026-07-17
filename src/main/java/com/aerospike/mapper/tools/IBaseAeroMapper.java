package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Session;
import com.aerospike.mapper.tools.converters.MappingConverter;

public interface IBaseAeroMapper {

    MappingConverter getMappingConverter();

    Session getSession();
}
