package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.SessionExtension;
import com.aerospike.client.sdk.policy.Behavior;

/**
 * {@link SessionExtension} that creates {@link MappingSession} instances
 * backed by a {@link MappingEngine}.
 *
 * <p>Obtained via {@link MappingEngine#sessionExtension()}:</p>
 * <pre>{@code
 * MappingSession session = cluster.createSession(behavior, engine.sessionExtension());
 * }</pre>
 */
final class MappingSessionExtension implements SessionExtension<MappingSession> {

    private final MappingEngine engine;

    MappingSessionExtension(MappingEngine engine) {
        this.engine = engine;
    }

    @Override
    public MappingSession create(Cluster cluster, Behavior behavior) {
        return new MappingSession(cluster, behavior, engine);
    }
}
