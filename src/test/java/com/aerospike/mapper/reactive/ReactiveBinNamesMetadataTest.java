package com.aerospike.mapper.reactive;

import com.aerospike.mapper.BinNamesMetadataTest;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ReactiveBinNamesMetadataTest extends ReactiveAeroMapperBaseTest {

    @Test
    public void getBinNamesDelegatesToSyncMapperMetadata() {
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).build();

        Map<String, String> binNames = reactiveMapper.getBinNames(BinNamesMetadataTest.AnnotationMappedChild.class);

        assertEquals("renamed", binNames.get("explicit"));
        assertEquals("accessorBacked", binNames.get("accessorBacked"));
        assertFalse(binNames.containsKey("externalId"));
    }
}
