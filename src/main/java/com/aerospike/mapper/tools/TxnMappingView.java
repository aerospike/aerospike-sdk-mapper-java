package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Package-private transactional view of {@link MappingSession}.
 * Routes all engine CRUD calls through the transactional session
 * rather than {@code this}.
 */
final class TxnMappingView extends MappingSession {

    private final Session txnSession;

    TxnMappingView(MappingEngine engine, Session txnSession) {
        super(txnSession.getCluster(), txnSession.getBehavior(), engine);
        this.txnSession = txnSession;
    }

    @Override
    public <T> void save(@NotNull T object, String... binNames) {
        getEngine().save(txnSession, object, binNames);
    }

    @Override
    public <T> void insert(@NotNull T object, String... binNames) {
        getEngine().insert(txnSession, object, binNames);
    }

    @Override
    public <T> void update(@NotNull T object, String... binNames) {
        getEngine().update(txnSession, object, binNames);
    }

    @Override
    public <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return getEngine().read(txnSession, clazz, userKey, true);
    }

    @Override
    public <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey, boolean resolveDependencies) {
        return getEngine().read(txnSession, clazz, userKey, resolveDependencies);
    }

    @Override
    public <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest) {
        return getEngine().readFromDigest(txnSession, clazz, digest, true);
    }

    @Override
    public <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest, boolean resolveDependencies) {
        return getEngine().readFromDigest(txnSession, clazz, digest, resolveDependencies);
    }

    @Override
    public <T> T[] read(@NotNull Class<T> clazz, @NotNull Object[] userKeys) {
        return getEngine().readBatch(txnSession, clazz, userKeys);
    }

    @Override
    public <T> boolean delete(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return getEngine().deleteByKey(txnSession, clazz, userKey);
    }

    @Override
    public boolean delete(@NotNull Object object) {
        return getEngine().deleteByObject(txnSession, object);
    }

    @Override
    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, String aelExpression) {
        getEngine().query(txnSession, clazz, processor, aelExpression);
    }

    @Override
    public <T> List<T> query(@NotNull Class<T> clazz, String aelExpression) {
        return getEngine().query(txnSession, clazz, aelExpression);
    }

    @Override
    public <T> VirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(txnSession, getEngine().getInternalMapper(), object, binName, elementClazz);
    }

    @Override
    public <T> VirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key,
                                           @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(txnSession, getEngine().getInternalMapper(), owningClazz, key, binName, elementClazz);
    }

    @Override
    public MappingSession sessionFor(Behavior behavior) {
        throw new UnsupportedOperationException(
                "Cannot switch behavior inside a mapping transaction.");
    }
}
