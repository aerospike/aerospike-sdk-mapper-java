package com.aerospike.mapper.tools;

import java.util.List;
import jakarta.validation.constraints.NotNull;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.RecordMappingFactory;
import com.aerospike.client.sdk.TypedDataSet;
import com.aerospike.mapper.tools.virtuallist.VirtualList;

public interface IAeroMapper extends IBaseAeroMapper {

    /**
     * Save each object in the database. This method will perform a REPLACE on the existing record so any existing
     * data will be overwritten by the data in the passed object.
     *
     * @param objects One or more objects to save.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> void save(@NotNull T... objects);

    /**
     * Save an object in the database. This method will perform a REPLACE on the existing record so any existing
     * data will be overwritten by the data in the passed object.
     *
     * @param object   The object to save.
     * @param binNames Optional bin names to restrict the write to specific bins.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> void save(@NotNull T object, String... binNames);

    /**
     * Insert an object to the database. Uses CREATE_ONLY semantics -- fails if the record already exists.
     *
     * @param object   The object to insert.
     * @param binNames Optional bin names to restrict the write to specific bins.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> void insert(@NotNull T object, String... binNames);

    /**
     * Updates the object in the database, merging the record with the existing record (UPSERT semantics).
     *
     * @param object   The object to update.
     * @param binNames Optional bin names to restrict the write to specific bins.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> void update(@NotNull T object, String... binNames);

    /**
     * Read a record from the repository and map it to an instance of the passed class, by providing a digest.
     *
     * @param clazz  - The type of the record.
     * @param digest - The Aerospike digest (unique server hash value generated from set name and user key).
     * @return The returned mapped record.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest);

    /**
     * This method should not be used externally: it is used by mappers to resolve dependencies.
     */
    <T> T readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest, boolean resolveDependencies);

    /**
     * Read a record from the repository and map it to an instance of the passed class.
     *
     * @param clazz   - The type to be returned.
     * @param userKey - The key of the record. The namespace and set will be derived from the class annotations.
     * @return The returned mapped record.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey);

    /**
     * This method should not be used externally: it is used by mappers to resolve dependencies.
     */
    <T> T read(@NotNull Class<T> clazz, @NotNull Object userKey, boolean resolveDependencies);

    /**
     * Read a batch of records from the repository and map them to instances of the passed class.
     *
     * @param clazz    - The type to be returned.
     * @param userKeys - The keys of the records. The namespace and set will be derived from the class annotations.
     * @return The returned mapped records.
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> T[] read(@NotNull Class<T> clazz, @NotNull Object[] userKeys);

    /**
     * Delete a record by specifying a class and a user key.
     *
     * @param clazz   - The type of the record.
     * @param userKey - The key of the record. The namespace and set will be derived from the class annotations.
     * @return whether record existed on server before deletion
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    <T> boolean delete(@NotNull Class<T> clazz, @NotNull Object userKey);

    /**
     * Delete a record by specifying an object.
     *
     * @param object The object to delete.
     * @return whether record existed on server before deletion
     * @throws AerospikeException an AerospikeException will be thrown in case of an error.
     */
    boolean delete(@NotNull Object object);

    /**
     * Scan every record in the set associated with the passed class. Each record will be converted to the appropriate
     * class then passed to the processor. If the processor returns true, more records will be processed; if it returns
     * false, the scan is aborted.
     *
     * @param clazz     - the class used to determine which set to scan and to convert the returned records to.
     * @param processor - the Processor used to process each record
     */
    <T> void scan(@NotNull Class<T> clazz, @NotNull Processor<T> processor);

    /**
     * Scan every record in the set associated with the passed class
     * and return the list of records converted to the appropriate class.
     *
     * @param clazz - the class used to determine which set to scan and to convert the returned records to.
     * @return List of scanned records
     */
    <T> List<T> scan(@NotNull Class<T> clazz);

    /**
     * Perform a query using an AEL (Aerospike Expression Language) expression. Each record will be converted
     * to the appropriate class then passed to the processor. If the processor returns false the query is aborted.
     *
     * @param clazz         - the class used to determine which set to query and to convert the returned records to.
     * @param processor     - the Processor used to process each record
     * @param aelExpression - the AEL filter expression (e.g. "$.age > 21"). Pass null for a full scan.
     */
    <T> void query(@NotNull Class<T> clazz, @NotNull Processor<T> processor, String aelExpression);

    /**
     * Perform a query using an AEL expression and return the list of matching records.
     *
     * @param clazz         - the class used to determine which set to query and to convert the returned records to.
     * @param aelExpression - the AEL filter expression. Pass null for a full scan.
     * @return List of records converted to the appropriate class
     */
    <T> List<T> query(@NotNull Class<T> clazz, String aelExpression);

    /**
     * Create a virtual list against an attribute on a class.
     *
     * @param <T>          the type of the elements in the list.
     * @param object       The object that will use as a base for the virtual list.
     * @param binName      The Aerospike bin name.
     * @param elementClazz The class of the elements in the list.
     * @return A virtual list.
     */
    <T> VirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz);

    /**
     * Create a virtual list against an attribute on a class, using a specified key rather than an existing object.
     *
     * @param <T>          the type of the elements in the list.
     * @param owningClazz  Used for the definitions of how to map the list elements.
     * @param key          The key to map the object to the database.
     * @param binName      The Aerospike bin name.
     * @param elementClazz The class of the elements in the list.
     * @return A virtual list.
     */
    <T> VirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key, @NotNull String binName, Class<T> elementClazz);

    /**
     * Return a {@link RecordMappingFactory} backed by this mapper's introspection engine.
     * Register it on the cluster via {@code cluster.setRecordMappingFactory(mapper.asMappingFactory())}
     * to enable the SDK's typed mapping features (TypedDataSet, TypedKey, etc.).
     */
    RecordMappingFactory asMappingFactory();

    /**
     * Return a {@link TypedDataSet} for the given annotated class, deriving the namespace and set
     * from the {@code @AerospikeRecord} annotation. This allows users to work directly with
     * the SDK's typed mapping features without manually wiring namespace/set.
     *
     * @param <T>   the entity type
     * @param clazz the annotated entity class
     * @return a TypedDataSet bound to the class's namespace, set, and type
     */
    <T> TypedDataSet<T> getTypedDataSet(Class<T> clazz);

    /**
     * Get the namespace associated with the passed class.
     */
    String getNamespace(Class<?> clazz);

    /**
     * Get the set associated with the passed class.
     */
    String getSet(Class<?> clazz);

    /**
     * Get the primary id associated with the passed object.
     */
    Object getKey(Object obj);

    /**
     * Get the Aerospike Key used to read the record associated with the passed object.
     */
    Key getRecordKey(Object obj);
}
