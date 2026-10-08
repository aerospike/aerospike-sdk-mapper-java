package com.aerospike.mapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapperLiveExamplesTests extends AeroMapperBaseTest {
    @Test
    void allExamplesPassAgainstLiveServer() {
        assumeAelSupported();

        int code = new MapperExamples().run(
                new String[] {"all", "--fail-fast"}, System.out, System.err);

        assertEquals(0, code);
    }
}
