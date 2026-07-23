package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Node;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.command.Info;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.client.sdk.util.Version;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.MappingEngine;
import com.aerospike.mapper.tools.MappingSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class MapperBaseTest {

    public static final String NAMESPACE = "test";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    protected static Cluster cluster;
    protected static Session session;
    protected static boolean enterprise;

    @BeforeAll
    public static void setupClass() {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.parseInt(System.getProperty("test.port", "3000"));
        cluster = new ClusterDefinition(host, port).connect();
        session = cluster.createSession(Behavior.DEFAULT);
        enterprise = detectEnterprise();
    }

    private static boolean detectEnterprise() {
        Node node = cluster.getNodes()[0];
        String infoKey = node.getVersion().isGreaterOrEqual(Version.SERVER_VERSION_8_1)
                ? "release" : "edition";
        String value = Info.request(node, infoKey);
        return value != null && value.contains("Enterprise");
    }

    @AfterAll
    public static void cleanupClass() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @BeforeEach
    public void clearCache() {
        ClassCache.getInstance().clear();
    }

    /**
     * Creates a {@link MappingSession} with a default (empty) engine.
     */
    protected static MappingSession createMapper() {
        return createMapper(MappingEngine.builder());
    }

    /**
     * Creates a {@link MappingSession} from a customized engine builder.
     */
    protected static MappingSession createMapper(MappingEngine.Builder builder) {
        MappingEngine engine = builder.build();
        return cluster.createSession(Behavior.DEFAULT, engine.sessionExtension());
    }

    protected static void truncateSet(String namespace, String setName) {
        session.truncate(DataSet.of(namespace, setName));
    }

    protected Record readRawRecord(Key key) {
        try (RecordStream stream = session.query(List.of(key)).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return (rr != null && rr.isOk()) ? rr.recordOrNull() : null;
        }
    }

    public void compare(Object original, Object read) {
        this.compare(original, read, false);
    }

    public void compare(Object original, Object read, boolean showObjects) {
        ObjectWriter objectWriter = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
        JsonNode originalTree = OBJECT_MAPPER.valueToTree(original);
        JsonNode readTree = OBJECT_MAPPER.valueToTree(read);
        try {
            String readString = objectWriter.writeValueAsString(read);
            if (showObjects) {
                System.out.println("------ Read Data -----\n" + readString);
            }
            String originalObject = objectWriter.writeValueAsString(original);
            if (showObjects) {
                System.out.println("------ Original Data -----\n" + originalObject);
            }
            assertEquals(originalTree, readTree);
        } catch (Exception jpe) {
            throw new RuntimeException(jpe);
        }
    }
}
