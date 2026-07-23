package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.tools.virtuallist.AsyncVirtualList;

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
    protected Session routingSession() {
        return txnSession;
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
