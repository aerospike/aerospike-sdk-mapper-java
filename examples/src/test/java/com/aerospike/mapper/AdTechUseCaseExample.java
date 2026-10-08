package com.aerospike.mapper;

import com.aerospike.mapper.annotations.*;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.virtuallist.ReturnType;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aerospike.mapper.ExampleAssertions.equal;

/**
 * Advertising-segment use case for value-ordered virtual lists.
 *
 * <p>The list lives inside a user record, but updates and range reads execute on the
 * server without first loading and rewriting the complete user object.</p>
 */
public class AdTechUseCaseExample implements RunnableExample {
    public static final String SET = "sdk_mapper_examples_adtech_users";

    @AerospikeRecord(namespace = "${aerospike.examples.namespace:test}", set = SET)
    public static class User {
        @AerospikeKey
        private final String id;
        private final Date created;

        // MAP/LIST means the outer collection is a key-addressable map while each
        // Segment value is encoded positionally as a list.
        @AerospikeEmbed(type = EmbedType.MAP, elementType = EmbedType.LIST)
        private final List<Segment> segments;

        public User(@ParamFrom("id") String id, @ParamFrom("created") Date created, @ParamFrom("segments") List<Segment> segments) {
            super();
            this.id = id;
            this.created = created;
            this.segments = segments;
        }

        public String getId() {
            return id;
        }

        public Date getCreated() {
            return created;
        }

        public List<Segment> getSegments() {
            return segments;
        }
    }

    @AerospikeRecord
    public static class Segment {
        @AerospikeKey
        private String name;

        // VirtualList value-range operations use this ordinal field to order and select
        // segments by recency.
        @AerospikeOrdinal
        private Date lastSeen;
        @AerospikeBin(name = "partId")
        private long partnerId;
        private long flags;

        public Segment(@ParamFrom("name") String name, @ParamFrom("lastSeen") Date lastSeen, @ParamFrom("partId") long partnerId, @ParamFrom("flags") long flags) {
            super();
            this.name = name;
            this.lastSeen = lastSeen;
            this.partnerId = partnerId;
            this.flags = flags;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Date getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(Date lastSeen) {
            this.lastSeen = lastSeen;
        }

        public long getPartnerId() {
            return partnerId;
        }

        public void setPartnerId(long partnerId) {
            this.partnerId = partnerId;
        }

        public long getFlags() {
            return flags;
        }

        public void setFlags(long flags) {
            this.flags = flags;
        }

        @Override
        public String toString() {
            return String.format("{name=%s, lastSeen=%s, partnerId=%d, flags=%d}\n", name, lastSeen.toString(), partnerId, flags);
        }
    }

    @Override
    public void run(ExampleContext context) {
        AeroMapper mapper = new AeroMapper.Builder(context.session()).build();

        User user = new User("User1", new Date(), new ArrayList<>());
        mapper.save(user);

        // Bind directly to the persisted segments bin. The Java list in user remains a
        // normal in-memory list and is not updated by these server-side operations.
        VirtualList<Segment> segments = mapper.asBackedList(user, "segments", Segment.class);

        // Capture one clock value so every sample has a deterministic relative age.
        long timeInMs = new Date().getTime();

        Date cutoff = new Date(timeInMs - TimeUnit.MILLISECONDS.convert(30, TimeUnit.DAYS));

        /*
         * The operation chain is sent as one command: append all observations, then
         * remove values whose ordinal date is older than the 30-day cutoff.
         */
        segments.beginMultiOperation()
                .append(new Segment("Sports", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS)), 1234, 1L))
                .append(new Segment("Computers", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(36, TimeUnit.HOURS)), 9999, 2L))
                .append(new Segment("Elecronics", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(80, TimeUnit.HOURS)), 858, 3L))
                .append(new Segment("Fishing", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(96, TimeUnit.HOURS)), 888, 4L))
                .append(new Segment("Elecronics", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(80, TimeUnit.HOURS)), 858, 5L))
                .append(new Segment("Drinking", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(120, TimeUnit.HOURS)), 222, 6L))
                .append(new Segment("Sleeping", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(160, TimeUnit.HOURS)), 111, 7L))
                .append(new Segment("Eating", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(170, TimeUnit.HOURS)), 858, 8L))
                .append(new Segment("Hiking", new Date(timeInMs - TimeUnit.MILLISECONDS.convert(2400, TimeUnit.HOURS)), 77777, 9L))
                .removeByValueRange(null, new Date[]{cutoff})
                .end();

        // Query the remaining collection for segments seen during the last seven days.
        Date readCutoff = new Date(timeInMs - TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS));
        List<Segment> segmentList = segments.getByValueRange(new Date[]{readCutoff}, null, ReturnType.ELEMENTS);
        System.out.println(segmentList);

        // Segment.name is the embedded-map key, so the second "Elecronics" append
        // replaces the first. The old Hiking entry is removed, and the 170-hour Eating
        // entry falls just outside the seven-day read window, leaving six results.
        equal(6, segmentList.size(), "recent segment count");
    }
}
