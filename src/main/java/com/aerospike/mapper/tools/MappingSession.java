package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.tools.virtuallist.AsyncVirtualList;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * A {@link Session} subclass that adds annotation-driven CRUD, virtual lists,
 * and transactional mapping support.
 *
 * <p>Created via {@link MappingEngine#sessionExtension()}:</p>
 * <pre>{@code
 * MappingSession session = cluster.createSession(behavior, engine.sessionExtension());
 * session.save(myObject);
 * Optional<Customer> customer = session.read(Customer.class, "cust-1");
 * }</pre>
 *
 * <p>Not {@code final} — the package-private {@code TxnMappingView} extends it
 * to route operations through a transactional session. External subclassing is
 * prevented by the package-private constructor.</p>
 */
public class MappingSession extends Session {

    private final MappingEngine engine;

    MappingSession(Cluster cluster, Behavior behavior, MappingEngine engine) {
        super(cluster, behavior);
        this.engine = engine;
    }

    /**
     * Returns the SDK session that mapper-level operations should route through.
     * The default mapping session routes through itself; transactional views
     * override this to target the underlying transactional session instead.
     */
    protected Session routingSession() {
        return this;
    }

    // -- CRUD --

    @SafeVarargs
    public final <T> void save(@NotNull T... objects) throws AerospikeException {
        for (T obj : objects) {
            save(obj);
        }
    }

    public <T> void save(@NotNull T object, String... binNames) throws AerospikeException {
        engine.save(routingSession(), object, binNames);
    }

    public <T> void save(@NotNull T object, int ttlSeconds) throws AerospikeException {
        engine.save(routingSession(), object, ttlSeconds);
    }

    public <T> void save(@NotNull T object, @NotNull Duration ttl) throws AerospikeException {
        engine.save(routingSession(), object, ttl);
    }

    public <T> void save(@NotNull T object, int ttlSeconds, @NotNull String[] binNames) throws AerospikeException {
        engine.save(routingSession(), object, ttlSeconds, binNames);
    }

    public <T> void save(@NotNull T object, @NotNull Duration ttl, @NotNull String[] binNames)
            throws AerospikeException {
        engine.save(routingSession(), object, ttl, binNames);
    }

    public <T> void insert(@NotNull T object, String... binNames) throws AerospikeException {
        engine.insert(routingSession(), object, binNames);
    }

    public <T> void insert(@NotNull T object, int ttlSeconds) throws AerospikeException {
        engine.insert(routingSession(), object, ttlSeconds);
    }

    public <T> void insert(@NotNull T object, @NotNull Duration ttl) throws AerospikeException {
        engine.insert(routingSession(), object, ttl);
    }

    public <T> void insert(@NotNull T object, int ttlSeconds, @NotNull String[] binNames) throws AerospikeException {
        engine.insert(routingSession(), object, ttlSeconds, binNames);
    }

    public <T> void insert(@NotNull T object, @NotNull Duration ttl, @NotNull String[] binNames)
            throws AerospikeException {
        engine.insert(routingSession(), object, ttl, binNames);
    }

    public <T> void update(@NotNull T object, String... binNames) throws AerospikeException {
        engine.update(routingSession(), object, binNames);
    }

    public <T> void update(@NotNull T object, int ttlSeconds) throws AerospikeException {
        engine.update(routingSession(), object, ttlSeconds);
    }

    public <T> void update(@NotNull T object, @NotNull Duration ttl) throws AerospikeException {
        engine.update(routingSession(), object, ttl);
    }

    public <T> void update(@NotNull T object, int ttlSeconds, @NotNull String[] binNames) throws AerospikeException {
        engine.update(routingSession(), object, ttlSeconds, binNames);
    }

    public <T> void update(@NotNull T object, @NotNull Duration ttl, @NotNull String[] binNames)
            throws AerospikeException {
        engine.update(routingSession(), object, ttl, binNames);
    }

    public <T> Optional<T> read(@NotNull Class<T> clazz, @NotNull Object userKey) throws AerospikeException {
        return read(clazz, userKey, true);
    }

    public <T> Optional<T> read(@NotNull Class<T> clazz, @NotNull Object userKey, boolean resolveDependencies)
            throws AerospikeException {
        return engine.read(routingSession(), clazz, userKey, resolveDependencies);
    }

    public <T> Optional<T> readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest) throws AerospikeException {
        return readFromDigest(clazz, digest, true);
    }

    public <T> Optional<T> readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest, boolean resolveDependencies)
            throws AerospikeException {
        return engine.readFromDigest(routingSession(), clazz, digest, resolveDependencies);
    }

    public <T> T[] read(@NotNull Class<T> clazz, @NotNull Object[] userKeys) throws AerospikeException {
        return engine.readBatch(routingSession(), clazz, userKeys);
    }

    public <T> boolean delete(@NotNull Class<T> clazz, @NotNull Object userKey) throws AerospikeException {
        return engine.deleteByKey(routingSession(), clazz, userKey);
    }

    public boolean delete(@NotNull Object object) throws AerospikeException {
        return engine.deleteByObject(routingSession(), object);
    }

    // -- Scan / Query --

    public <T> void scan(@NotNull Class<T> clazz, @NotNull Processor<T> processor) {
        query(clazz, processor);
    }

    public <T> List<T> scan(@NotNull Class<T> clazz) {
        return query(clazz);
    }

    public <T> void scan(@NotNull Class<T> clazz, @NotNull Processor<T> processor, @NotNull String aelExpression) {
        query(clazz, processor, aelExpression);
    }

    public <T> List<T> scan(@NotNull Class<T> clazz, @NotNull String aelExpression) {
        return query(clazz, aelExpression);
    }

    public <T> void scan(@NotNull Class<T> clazz, @NotNull Processor<T> processor, @NotNull Expression filterExp) {
        query(clazz, processor, filterExp);
    }

    public <T> List<T> scan(@NotNull Class<T> clazz, @NotNull Expression filterExp) {
        return query(clazz, filterExp);
    }

    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor) {
        engine.query(routingSession(), clazz, processor);
    }

    public <T> List<T> query(@NotNull Class<T> clazz) {
        return engine.query(routingSession(), clazz);
    }

    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, String aelExpression) {
        engine.query(routingSession(), clazz, processor, aelExpression);
    }

    public <T> List<T> query(@NotNull Class<T> clazz, String aelExpression) {
        return engine.query(routingSession(), clazz, aelExpression);
    }

    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, @NotNull Expression filterExp) {
        engine.query(routingSession(), clazz, processor, filterExp);
    }

    public <T> List<T> query(@NotNull Class<T> clazz, @NotNull Expression filterExp) {
        return engine.query(routingSession(), clazz, filterExp);
    }

    // -- Virtual lists --

    public <T> VirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(routingSession(), engine, object, binName, elementClazz);
    }

    public <T> VirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key,
                                           @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(routingSession(), engine, owningClazz, key, binName, elementClazz);
    }

    // -- Async convenience API --
    // Uses SDK's native executeAsync for true non-blocking operations.

    public <T> CompletableFuture<Void> saveAsync(@NotNull T object, String... binNames) {
        return engine.writeObjectAsync(this, object, MappingEngine.WriteOp.REPLACE, java.util.OptionalInt.empty(), binNames);
    }

    public <T> CompletableFuture<Void> insertAsync(@NotNull T object, String... binNames) {
        return engine.writeObjectAsync(this, object, MappingEngine.WriteOp.INSERT, java.util.OptionalInt.empty(), binNames);
    }

    public <T> CompletableFuture<Void> updateAsync(@NotNull T object, String... binNames) {
        return engine.writeObjectAsync(this, object, MappingEngine.WriteOp.UPSERT, java.util.OptionalInt.empty(), binNames);
    }

    public <T> CompletableFuture<Optional<T>> readAsync(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return engine.readSingleAsync(this, clazz, userKey);
    }

    public <T> CompletableFuture<Optional<T>> readFromDigestAsync(@NotNull Class<T> clazz, @NotNull byte[] digest) {
        return engine.readFromDigestAsync(this, clazz, digest);
    }

    public <T> CompletableFuture<List<T>> readAsync(@NotNull Class<T> clazz, @NotNull Object[] userKeys) {
        return CompletableFuture.supplyAsync(() -> {
            T[] results = read(clazz, userKeys);
            return new ArrayList<>(Arrays.asList(results));
        }, engine.asyncExecutor());
    }

    public <T> CompletableFuture<Boolean> deleteAsync(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return engine.deleteByKeyAsync(this, clazz, userKey);
    }

    public CompletableFuture<Boolean> deleteAsync(@NotNull Object object) {
        return engine.deleteByObjectAsync(this, object);
    }

    public <T> Flow.Publisher<T> scanPublisher(@NotNull Class<T> clazz) {
        return queryPublisher(clazz, (String) null);
    }

    public <T> Flow.Publisher<T> queryPublisher(@NotNull Class<T> clazz, String aelExpression) {
        return subscriber -> {
            ClassCacheEntry<T> entry = engine.loadClass(clazz);
            RecordStream stream = engine.buildQueryStream(this, clazz, aelExpression, null);
            try {
                Flow.Publisher<RecordResult> raw = stream.asPublisher();
                raw.subscribe(new MappingSubscriber<>(subscriber, clazz, entry, this, engine.getMappingConverter()));
            } catch (Throwable t) {
                stream.close();
                throw t;
            }
        };
    }

    public <T> Flow.Publisher<T> queryPublisher(@NotNull Class<T> clazz, @NotNull Expression filterExp) {
        return subscriber -> {
            ClassCacheEntry<T> entry = engine.loadClass(clazz);
            RecordStream stream = engine.buildQueryStream(this, clazz, null, filterExp);
            try {
                Flow.Publisher<RecordResult> raw = stream.asPublisher();
                raw.subscribe(new MappingSubscriber<>(subscriber, clazz, entry, this, engine.getMappingConverter()));
            } catch (Throwable t) {
                stream.close();
                throw t;
            }
        };
    }

    public <T> AsyncVirtualList<T> asBackedListAsync(@NotNull Object object,
            @NotNull String binName, Class<T> elementClazz) {
        return new AsyncVirtualList<>(this, engine, object, binName, elementClazz);
    }

    public <T> AsyncVirtualList<T> asBackedListAsync(@NotNull Class<?> owningClazz,
            @NotNull Object key, @NotNull String binName, Class<T> elementClazz) {
        return new AsyncVirtualList<>(this, engine, owningClazz, key, binName, elementClazz);
    }

    // -- Transactions --

    /**
     * Executes the given operation within a mapping transaction.
     * The callback receives a transactional {@code MappingSession} view
     * where all mapper-level CRUD methods ({@code save}, {@code read},
     * {@code delete}, {@code query}, {@code scan}, {@code asBackedList})
     * run under transactional semantics.
     *
     * <p><strong>Restrictions on the transactional view:</strong></p>
     * <ul>
     *   <li>{@link #sessionFor(Behavior)} throws {@link UnsupportedOperationException}
     *       — behavior switching is not supported mid-transaction.</li>
     *   <li>Raw {@link com.aerospike.client.sdk.Session Session} methods
     *       inherited from the superclass (e.g. {@code replace()}, {@code upsert()})
     *       do <em>not</em> participate in the transaction. Only use
     *       the mapper-level methods listed above.</li>
     * </ul>
     */
    public void doInMappingTransaction(MappingTransactionalVoid operation) {
        RuntimeException[] userException = {null};
        try {
            doInTransaction(txn -> {
                MappingSession txnView = new TxnMappingView(engine, txn);
                try {
                    operation.execute(txnView);
                } catch (RuntimeException e) {
                    userException[0] = e;
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            if (userException[0] != null) {
                throw userException[0];
            }
            throw e;
        }
    }

    /**
     * Executes the given operation within a mapping transaction, returning a result.
     * See {@link #doInMappingTransaction(MappingTransactionalVoid)} for restrictions
     * on the transactional view.
     */
    public <T> T doInMappingTransactionReturning(MappingTransactional<T> operation) {
        RuntimeException[] userException = {null};
        try {
            return doInTransactionReturning(txn -> {
                MappingSession txnView = new TxnMappingView(engine, txn);
                try {
                    return operation.execute(txnView);
                } catch (RuntimeException e) {
                    userException[0] = e;
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            if (userException[0] != null) {
                throw userException[0];
            }
            throw e;
        }
    }

    // -- Behavior switching --

    @Override
    public MappingSession sessionFor(Behavior behavior) {
        return getCluster().createSession(behavior, engine.sessionExtension());
    }

    // -- Engine access --

    public MappingEngine getEngine() {
        return engine;
    }
}
