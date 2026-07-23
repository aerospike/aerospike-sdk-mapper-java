package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aerospike.mapper.annotations.FromAerospike;
import com.aerospike.mapper.annotations.ToAerospike;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.converters.MappingConverter;
import org.junit.jupiter.api.Test;

public class MappingEngineIsolationTest extends MapperBaseTest {

    public static final class EngineScopedType {
        final String value;

        EngineScopedType(String value) {
            this.value = value;
        }
    }

    public static final class PrefixAConverter {
        @ToAerospike
        public String toAerospike(EngineScopedType value) {
            return "A:" + value.value;
        }

        @FromAerospike
        public EngineScopedType fromAerospike(String value) {
            return new EngineScopedType(value.substring(2));
        }
    }

    public static final class PrefixBConverter {
        @ToAerospike
        public String toAerospike(EngineScopedType value) {
            return "B:" + value.value;
        }

        @FromAerospike
        public EngineScopedType fromAerospike(String value) {
            return new EngineScopedType(value.substring(2));
        }
    }

    @Test
    public void convertersShouldBeIsolatedPerEngine() {
        MappingEngine engineA = MappingEngine.builder().addConverter(new PrefixAConverter()).build();
        MappingConverter converterA = engineA.getMappingConverter();

        assertEquals("A:value", converterA.translateToAerospike(new EngineScopedType("value")));

        MappingEngine engineB = MappingEngine.builder().addConverter(new PrefixBConverter()).build();
        MappingConverter converterB = engineB.getMappingConverter();

        assertEquals("B:value", converterB.translateToAerospike(new EngineScopedType("value")));
        assertEquals("A:value", converterA.translateToAerospike(new EngineScopedType("value")));
    }
}