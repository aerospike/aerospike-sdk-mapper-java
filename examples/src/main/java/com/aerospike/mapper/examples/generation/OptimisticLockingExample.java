package com.aerospike.mapper.examples.generation;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.mapper.annotations.AerospikeGeneration;
import com.aerospike.mapper.annotations.AerospikeKey;
import com.aerospike.mapper.annotations.AerospikeRecord;
import com.aerospike.mapper.examples.ExampleContext;
import com.aerospike.mapper.examples.RunnableExample;
import com.aerospike.mapper.tools.AeroMapper;

import static com.aerospike.mapper.examples.ExampleAssertions.equal;
import static com.aerospike.mapper.examples.ExampleAssertions.require;

/**
 * Uses Aerospike record generations for optimistic concurrency control so a stale
 * application object cannot silently overwrite a newer write.
 */
public final class OptimisticLockingExample implements RunnableExample {
    public static final String SET = "sdk_mapper_examples_generation";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = SET)
    public static class Document {
        @AerospikeKey public String id;
        public String value;

        // This field maps Aerospike record metadata, not an ordinary stored bin.
        // read() populates it and write methods enforce it when the value is non-zero.
        @AerospikeGeneration public int generation;
        public Document() {}
        public Document(String id, String value) { this.id = id; this.value = value; }
    }

    @Override
    public void run(ExampleContext context) {
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();

        // A record created by insert() starts at server generation 1.
        mapper.insert(new Document("document-1", "initial"));
        Document inserted = mapper.read(Document.class, "document-1");
        equal(1, inserted.generation, "insert generation");

        // @start region="generation-check"
        // Simulate two callers reading the same version of one record.
        Document firstWriter = mapper.read(Document.class, "document-1");
        Document staleWriter = mapper.read(Document.class, "document-1");

        // The first save() succeeds because its generation still matches the server.
        firstWriter.value = "winner";
        mapper.save(firstWriter);

        // staleWriter still carries generation 1, while the stored record is now at
        // generation 2. The mapper asks Aerospike to reject this stale write.
        staleWriter.value = "stale";
        AerospikeException conflict = null;
        try {
            mapper.save(staleWriter);
        } catch (AerospikeException expected) {
            conflict = expected;
        }
        // @end region="generation-check"

        require(conflict != null, "a stale generation must be rejected");
        equal(ResultCode.GENERATION_ERROR, conflict.getResultCode(), "conflict result code");
        Document winner = mapper.read(Document.class, "document-1");
        equal("winner", winner.value, "winning value");
        equal(2, winner.generation, "winning generation");
    }
}
