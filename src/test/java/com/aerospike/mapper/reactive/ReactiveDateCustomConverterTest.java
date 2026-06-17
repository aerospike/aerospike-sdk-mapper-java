package com.aerospike.mapper.reactive;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.annotations.FromAerospike;
import com.aerospike.mapper.annotations.ToAerospike;
import com.aerospike.mapper.tools.ReactiveAeroMapper;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactiveDateCustomConverterTest extends ReactiveAeroMapperBaseTest {
    public static class DateConverter {
        private static final ThreadLocal<SimpleDateFormat> dateFormatter = ThreadLocal.withInitial(() -> new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS zzzZ"));

        @ToAerospike
        public String toAerospike(Date date) {
            if (date == null) {
                return null;
            }
            return dateFormatter.get().format(date);
        }

        @FromAerospike
        public Date fromAerospike(String dateStr) throws ParseException {
            if (dateStr == null) {
                return null;
            }
            return dateFormatter.get().parse(dateStr);
        }
    }

    @AerospikeRecord(namespace = "test", set = "dateFormat")
    public static class DateContainer {
        @AerospikeKey
        public long key;
        public Date date;
    }

    @Test
    public void testSave() throws Exception {
        ReactiveAeroMapper reactiveMapper = new ReactiveAeroMapper.Builder(session).addConverter(new DateConverter()).build();

        Date date = new Date();

        DateContainer container = new DateContainer();
        container.key = 1;
        container.date = date;

        reactiveMapper.save(container).get();
        DateContainer container2 = reactiveMapper.read(DateContainer.class, container.key).get();
        compare(container, container2, true);

        Record record = readRawRecord(new Key("test", "dateFormat", 1));
        assert record != null;
        String dateStr = record.getString("date");
        Date date2 = DateConverter.dateFormatter.get().parse(dateStr);
        System.out.println("Expected: " + date + ", received " + date2);
        assertEquals(date, date2);
    }
}
