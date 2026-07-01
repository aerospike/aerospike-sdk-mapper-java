package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A {@link Session} subclass that adds annotation-driven CRUD, virtual lists,
 * and transactional mapping support.
 *
 * <p>Created via {@link MappingEngine#sessionExtension()}:</p>
 * <pre>{@code
 * MappingSession session = cluster.createSession(behavior, engine.sessionExtension());
 * session.save(myObject);
 * Customer c = session.read(Customer.class, "cust-1");
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

    // -- CRUD --

    @SafeVarargs
    public final <T> void save(@NotNull T... objects) throws AerospikeException {
        for (T obj : objects) {
            save(obj);
        }
    }

    public <T> void save(@NotNull T object, String... binNames) throws AerospikeException {
        engine.save(this, object, binNames);
    }

    public <T> void insert(@NotNull T object, String... binNames) throws AerospikeException {
        engine.insert(this, object, binNames);
    }

    public <T> void update(@NotNull T object, String... binNames) throws AerospikeException {
        engine.update(this, object, binNames);
    }

    public <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey) throws AerospikeException {
        return read(clazz, userKey, true);
    }

    public <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey, boolean resolveDependencies)
            throws AerospikeException {
        return engine.read(this, clazz, userKey, resolveDependencies);
    }

    public <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest) throws AerospikeException {
        return readFromDigest(clazz, digest, true);
    }

    public <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest, boolean resolveDependencies)
            throws AerospikeException {
        return engine.readFromDigest(this, clazz, digest, resolveDependencies);
    }

    public <T> T[] read(@NotNull Class<T> clazz, @NotNull Object[] userKeys) throws AerospikeException {
        return engine.readBatch(this, clazz, userKeys);
    }

    public <T> boolean delete(@NotNull Class<T> clazz, @NotNull Object userKey) throws AerospikeException {
        return engine.deleteByKey(this, clazz, userKey);
    }

    public boolean delete(@NotNull Object object) throws AerospikeException {
        return engine.deleteByObject(this, object);
    }

    // -- Scan / Query --

    public <T> void scan(@NotNull Class<T> clazz, @NotNull Processor<T> processor) {
        query(clazz, processor, null);
    }

    public <T> List<T> scan(@NotNull Class<T> clazz) {
        return query(clazz, (String) null);
    }

    public <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, String aelExpression) {
        engine.query(this, clazz, processor, aelExpression);
    }

    public <T> List<T> query(@NotNull Class<T> clazz, String aelExpression) {
        return engine.query(this, clazz, aelExpression);
    }

    // -- Virtual lists --

    public <T> VirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(this, engine.getInternalMapper(), object, binName, elementClazz);
    }

    public <T> VirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key,
                                           @NotNull String binName, Class<T> elementClazz) {
        return new VirtualList<>(this, engine.getInternalMapper(), owningClazz, key, binName, elementClazz);
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
        doInTransaction(txn -> {
            MappingSession txnView = new TxnMappingView(engine, txn);
            operation.execute(txnView);
        });
    }

    /**
     * Executes the given operation within a mapping transaction, returning a result.
     * See {@link #doInMappingTransaction(MappingTransactionalVoid)} for restrictions
     * on the transactional view.
     */
    public <T> T doInMappingTransactionReturning(MappingTransactional<T> operation) {
        return doInTransactionReturning(txn -> {
            MappingSession txnView = new TxnMappingView(engine, txn);
            return operation.execute(txnView);
        });
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
