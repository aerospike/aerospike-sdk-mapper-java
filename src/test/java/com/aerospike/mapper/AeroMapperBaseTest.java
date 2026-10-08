package com.aerospike.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.client.sdk.query.IndexCollectionType;
import com.aerospike.client.sdk.query.IndexType;
import com.aerospike.mapper.tools.ClassCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class AeroMapperBaseTest {

    public static final String NAMESPACE = "test";
    protected static Cluster cluster;
    protected static Session session;

    @BeforeAll
    public static void setupClass() {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.parseInt(System.getProperty("test.port", "3000"));
        cluster = new ClusterDefinition(host, port).connect();
        session = cluster.createSession(Behavior.DEFAULT);
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

    protected static void truncateSet(String namespace, String setName) {
        session.truncate(DataSet.of(namespace, setName));
    }

    protected static void recreateIndex(
            DataSet dataSet, String indexName, String binName, IndexType indexType) {
        dropIndexIfPresent(dataSet, indexName);
        session.createIndex(dataSet, indexName, binName, indexType, IndexCollectionType.DEFAULT)
                .waitTillComplete();
    }

    protected static void dropIndexIfPresent(DataSet dataSet, String indexName) {
        try {
            session.dropIndex(dataSet, indexName).waitTillComplete();
        } catch (AerospikeException e) {
            if (e.getResultCode() != ResultCode.INDEX_NOTFOUND) {
                throw e;
            }
        }
    }

    protected static void assumeAelSupported() {
        assumeTrue(cluster.supportsAel(), () ->
                "AEL requires Aerospike Server 8.2.0+; connected server version is "
                        + cluster.getServerVersion());
    }

    protected Record readRawRecord(Key key) {
        try (RecordStream stream = session.query(List.of(key)).execute()) {
            RecordResult rr = stream.hasNext() ? stream.next() : null;
            return (rr != null && rr.isOk()) ? rr.getRecord() : null;
        }
    }

    public void compare(Object original, Object read) {
        this.compare(original, read, false);
    }

    public void compare(Object original, Object read, boolean showObjects) {
        try {
            ObjectWriter objectWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
            String readString = objectWriter.writeValueAsString(read);
            if (showObjects) {
                System.out.println("------ Read Data -----\n" + readString);
            }
            String originalObject = objectWriter.writeValueAsString(original);
            if (showObjects) {
                System.out.println("------ Original Data -----\n" + originalObject);
            }
            assertEquals(originalObject, readString);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }
}
