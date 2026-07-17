package com.aerospike.mapper;

import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YamlConfigurationTest extends MapperBaseTest {

    private MappingSession mapper;

    @BeforeEach
    public void setup() {
        mapper = createMapper();
        truncateSet(NAMESPACE, "testSet");
    }

    @Test
    public void testConvenienceMethods() throws Exception {

        String yaml =
                "---\n" +
                        "classes:\n" +
                        "  - class: com.aerospike.mapper.YamlConfigurationTest$DataClass\n" +
                        "    namespace: test\n" +
                        "    set: dataClass\n" +
                        "    key:\n" +
                        "      field: id\n" +
                        "    bins:\n" +
                        "      - field: date\n" +
                        "        name: d1\n" +
                        "  - class: com.aerospike.mapper.YamlConfigurationTest$ContainerClass\n" +
                        "    namespace: test\n" +
                        "    set: containers\n" +
                        "    key:\n" +
                        "      field: id\n" +
                        "    bins:\n" +
                        "      - field: dataClasses\n" +
                        "        embed:\n" +
                        "          type: MAP\n" +
                        "          elementType: LIST\n" +
                        "        name: data\n";

        MappingSession mapper = createMapper(MappingEngine.builder().withConfiguration(yaml));

        ContainerClass container = new ContainerClass();
        container.id = 123;
        container.dataClasses.add(new DataClass(1, 123, "string", new Date()));
        container.dataClasses.add(new DataClass(2, 234, "str", new Date()));
        mapper.save(container);

        // These objects are not embedded so they must be explicitly saved.
        mapper.save(container.dataClasses.get(0));
        mapper.save(container.dataClasses.get(1));

        ContainerClass container2 = mapper.read(ContainerClass.class, container.id).orElse(null);
        assertEquals(container, container2);
    }

    public static class DataClass {
        private int id;
        private int integer;
        private String string;
        private Date date;

        public DataClass() {
        }

        public DataClass(int id, int integer, String string, Date date) {
            super();
            this.id = id;
            this.integer = integer;
            this.string = string;
            this.date = date;
        }

        public int getId() {
            return id;
        }

        public int getInteger() {
            return integer;
        }

        public String getString() {
            return string;
        }

        public Date getDate() {
            return date;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            DataClass object = (DataClass) obj;
            return (object.id == this.id && object.integer == this.integer
                    && object.string.equals(this.string) && object.date.equals(this.date));
        }

        @Override
        public String toString() {
            return String.format("id:%d, integer:%d, string:%s, date:%s", id, integer, string, date);
        }
    }

    public static class ContainerClass {
        public int id;
        public List<DataClass> dataClasses;

        public ContainerClass() {
            this.dataClasses = new ArrayList<>();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ContainerClass container = (ContainerClass) obj;
            if (this.id != container.id) {
                return false;
            }
            if ((this.dataClasses == null && container.dataClasses != null)
                    || this.dataClasses != null && container.dataClasses == null) {
                return false;
            }
            if (this.dataClasses != null) {
                return this.dataClasses.equals(container.dataClasses);
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format("id:%d, elements:%s", this.id, this.dataClasses.toString());
        }
    }
}
