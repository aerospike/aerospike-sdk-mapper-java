package com.aerospike.mapper.tools;

import jakarta.validation.constraints.NotNull;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.mapper.tools.virtuallist.ReactiveVirtualList;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public interface IReactiveAeroMapper extends IBaseAeroMapper {

    /**
     * Save an object in the database asynchronously. REPLACE semantics.
     */
    <T> CompletableFuture<Void> save(@NotNull T object, String... binNames);

    /**
     * Insert an object to the database asynchronously. CREATE_ONLY semantics.
     */
    <T> CompletableFuture<Void> insert(@NotNull T object, String... binNames);

    /**
     * Updates the object in the database asynchronously. UPSERT semantics.
     */
    <T> CompletableFuture<Void> update(@NotNull T object, String... binNames);

    /**
     * Read a record from the repository asynchronously by digest.
     */
    <T> CompletableFuture<T> readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest);

    /**
     * Read a record from the repository asynchronously.
     */
    <T> CompletableFuture<T> read(@NotNull Class<T> clazz, @NotNull Object userKey);

    /**
     * Read a batch of records from the repository asynchronously.
     */
    <T> CompletableFuture<List<T>> read(@NotNull Class<T> clazz, @NotNull Object[] userKeys);

    /**
     * Delete a record by specifying a class and a user key, asynchronously.
     */
    <T> CompletableFuture<Boolean> delete(@NotNull Class<T> clazz, @NotNull Object userKey);

    /**
     * Delete a record by specifying an object, asynchronously.
     */
    CompletableFuture<Boolean> delete(@NotNull Object object);

    /**
     * Scan every record in the set associated with the passed class, returned as a Publisher.
     */
    <T> Flow.Publisher<T> scan(@NotNull Class<T> clazz);

    /**
     * Perform a query using an AEL expression, returned as a Publisher.
     */
    <T> Flow.Publisher<T> query(@NotNull Class<T> clazz, String aelExpression);

    /**
     * Create a reactive virtual list against an attribute on a class.
     */
    <T> ReactiveVirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz);

    /**
     * Create a reactive virtual list using a specified key rather than an existing object.
     */
    <T> ReactiveVirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key, @NotNull String binName, Class<T> elementClazz);

}
