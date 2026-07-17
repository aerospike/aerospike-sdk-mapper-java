package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.FromAerospike;
import com.aerospike.mapper.annotations.ToAerospike;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;


public class DateCustomConverterTest extends MapperBaseTest {

    public static class DateConverter {
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

        @ToAerospike
        public String toAerospike(Date date) {
            if (date == null) {
                return null;
            }
            return DATE_FORMATTER.format(date.toInstant());
        }

        @FromAerospike
        public Date fromAerospike(String dateStr) {
            if (dateStr == null) {
                return null;
            }
            return Date.from(Instant.from(DATE_FORMATTER.parse(dateStr)));
        }
    }

    @AerospikeRecord(namespace = "test", set = "dateFormat")
    public static class DateContainer {
        @AerospikeKey
        public long key;
        public Date date;
    }

    @Test
    public void testSave() {
        MappingSession convertingMapper = createMapper(MappingEngine.builder().addConverter(new DateConverter()));

        Date date = new Date();

        DateContainer container = new DateContainer();
        container.key = 1;
        container.date = date;

        convertingMapper.save(container);
        DateContainer container2 = convertingMapper.read(DateContainer.class, container.key).orElse(null);
        compare(container, container2, true);

        Record record = readRawRecord(new Key("test", "dateFormat", 1));
        String dateStr = record.getString("date");
        Date date2 = Date.from(Instant.from(DateConverter.DATE_FORMATTER.parse(dateStr)));
        assertEquals(date, date2);
    }
}
