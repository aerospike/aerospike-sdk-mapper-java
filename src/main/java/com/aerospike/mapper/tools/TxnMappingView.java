package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.tools.virtuallist.AsyncVirtualList;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

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
    public <T> void save(@NotNull T object, int ttlSeconds) {
        getEngine().save(txnSession, object, ttlSeconds);
    }

    @Override
    public <T> void save(@NotNull T object, @NotNull Duration ttl) {
        getEngine().save(txnSession, object, ttl);
    }

    @Override
    public <T> void save(@NotNull T object, int ttlSeconds, @NotNull String[] binNames) {
        getEngine().save(txnSession, object, ttlSeconds, binNames);
    }

    @Override
    public <T> void save(@NotNull T object, @NotNull Duration ttl, @NotNull String[] binNames) {
        getEngine().save(txnSession, object, ttl, binNames);
    }

    @Override
    public <T> void insert(@NotNull T object, String... binNames) {
        getEngine().insert(txnSession, object, binNames);
    }

    @Override
    public <T> void insert(@NotNull T object, int ttlSeconds) {
        getEngine().insert(txnSession, object, ttlSeconds);
    }

    @Override
    public <T> void insert(@NotNull T object, @NotNull Duration ttl) {
        getEngine().insert(txnSession, object, ttl);
    }

    @Override
    public <T> void insert(@NotNull T object, int ttlSeconds, @NotNull String[] binNames) {
        getEngine().insert(txnSession, object, ttlSeconds, binNames);
    }

    @Override
    public <T> void insert(@NotNull T object, @NotNull Duration ttl, @NotNull String[] binNames) {
        getEngine().insert(txnSession, object, ttl, binNames);
    }

    @Override
    public <T> void update(@NotNull T object, String... binNames) {
        getEngine().update(txnSession, object, binNames);
    }

    @Override
    public <T> void update(@NotNull T object, int ttlSeconds) {
        getEngine().update(txnSession, object, ttlSeconds);
    }

    @Override
    public <T> void update(@NotNull T object, @NotNull Duration ttl) {
        getEngine().update(txnSession, object, ttl);
    }

    @Override
    public <T> void update(@NotNull T object, int ttlSeconds, @NotNull String[] binNames) {
        getEngine().update(txnSession, object, ttlSeconds, binNames);
    }

    @Override
    public <T> void update(@NotNull T object, @NotNull Duration ttl, @NotNull String[] binNames) {
        getEngine().update(txnSession, object, ttl, binNames);
    }

    @Override
    public <T> Optional<T> read(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return getEngine().read(txnSession, clazz, userKey, true);
    }

    @Override
    public <T> Optional<T> read(@NotNull Class<T> clazz, @NotNull Object userKey, boolean resolveDependencies) {
        return getEngine().read(txnSession, clazz, userKey, resolveDependencies);
    }

    @Override
    public <T> Optional<T> readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest) {
        return getEngine().readFromDigest(txnSession, clazz, digest, true);
    }

    @Override
    public <T> Optional<T> readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest,
                                          boolean resolveDependencies) {
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
    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor) {
        getEngine().query(txnSession, clazz, processor);
    }

    @Override
    public <T> List<T> query(@NotNull Class<T> clazz) {
        return getEngine().query(txnSession, clazz);
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
    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, @NotNull Expression filterExp) {
        getEngine().query(txnSession, clazz, processor, filterExp);
    }

    @Override
    public <T> List<T> query(@NotNull Class<T> clazz, @NotNull Expression filterExp) {
        return getEngine().query(txnSession, clazz, filterExp);
    }

    @Override
    public <T> VirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(txnSession, getEngine(), object, binName, elementClazz);
    }

    @Override
    public <T> VirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key,
                                           @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(txnSession, getEngine(), owningClazz, key, binName, elementClazz);
    }

    @Override
    public MappingSession sessionFor(Behavior behavior) {
        throw new UnsupportedOperationException(
                "Cannot switch behavior inside a mapping transaction.");
    }

    // -- Async methods blocked: futures can outlive the transaction callback --

    private static UnsupportedOperationException asyncNotSupported() {
        return new UnsupportedOperationException(
                "Async operations are not supported inside mapping transactions. " +
                "Use synchronous methods instead.");
    }

    @Override
    public <T> CompletableFuture<Void> saveAsync(T object, String... binNames) { throw asyncNotSupported(); }

    @Override
    public <T> CompletableFuture<Void> insertAsync(T object, String... binNames) { throw asyncNotSupported(); }

    @Override
    public <T> CompletableFuture<Void> updateAsync(T object, String... binNames) { throw asyncNotSupported(); }

    @Override
    public <T> CompletableFuture<Optional<T>> readAsync(Class<T> clazz, Object userKey) {
        throw asyncNotSupported();
    }

    @Override
    public <T> CompletableFuture<Optional<T>> readFromDigestAsync(Class<T> clazz, byte[] digest) {
        throw asyncNotSupported();
    }

    @Override
    public <T> CompletableFuture<List<T>> readAsync(Class<T> clazz, Object[] userKeys) { throw asyncNotSupported(); }

    @Override
    public <T> CompletableFuture<Boolean> deleteAsync(Class<T> clazz, Object userKey) { throw asyncNotSupported(); }

    @Override
    public CompletableFuture<Boolean> deleteAsync(Object object) { throw asyncNotSupported(); }

    @Override
    public <T> Flow.Publisher<T> scanPublisher(Class<T> clazz) { throw asyncNotSupported(); }

    @Override
    public <T> Flow.Publisher<T> queryPublisher(Class<T> clazz, String aelExpression) { throw asyncNotSupported(); }

    @Override
    public <T> Flow.Publisher<T> queryPublisher(Class<T> clazz, Expression filterExp) { throw asyncNotSupported(); }

    @Override
    public <T> AsyncVirtualList<T> asBackedListAsync(Object obj, String bin, Class<T> cls) { throw asyncNotSupported(); }

    @Override
    public <T> AsyncVirtualList<T> asBackedListAsync(Class<?> ownCls, Object key, String bin, Class<T> cls) { throw asyncNotSupported(); }
}
