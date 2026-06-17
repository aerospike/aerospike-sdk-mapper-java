package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.Value;
import com.aerospike.mapper.tools.converters.MappingConverter;
import com.aerospike.mapper.tools.utils.MapperUtils;
import com.aerospike.mapper.tools.virtuallist.ReactiveVirtualList;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public class ReactiveAeroMapper implements IReactiveAeroMapper {

    private final Session session;
    private final MappingConverter mappingConverter;
    private volatile AeroMapper syncMapper;

    private ReactiveAeroMapper(@NotNull Session session) {
        this.session = session;
        this.mappingConverter = new MappingConverter(this);
    }

    void initSyncMapper() {
        this.syncMapper = new AeroMapper.Builder(session).build();
    }

    public static class Builder extends AbstractBuilder<ReactiveAeroMapper> {
        public Builder(Session session) {
            super(new ReactiveAeroMapper(session));
        }

        @Override
        public ReactiveAeroMapper build() {
            ReactiveAeroMapper mapper = super.build();
            mapper.initSyncMapper();
            return mapper;
        }
    }

    @Override
    public <T> CompletableFuture<Void> save(@NotNull T object, String... binNames) {
        return CompletableFuture.runAsync(() -> syncMapper.save(object, binNames));
    }

    @Override
    public <T> CompletableFuture<Void> insert(@NotNull T object, String... binNames) {
        return CompletableFuture.runAsync(() -> syncMapper.insert(object, binNames));
    }

    @Override
    public <T> CompletableFuture<Void> update(@NotNull T object, String... binNames) {
        return CompletableFuture.runAsync(() -> syncMapper.update(object, binNames));
    }

    @Override
    public <T> CompletableFuture<T> readFromDigest(@NotNull Class<T> clazz, @NotNull byte[] digest) {
        return CompletableFuture.supplyAsync(() -> syncMapper.readFromDigest(clazz, digest));
    }

    @Override
    public <T> CompletableFuture<T> read(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return CompletableFuture.supplyAsync(() -> syncMapper.read(clazz, userKey));
    }

    @Override
    public <T> CompletableFuture<List<T>> read(@NotNull Class<T> clazz, @NotNull Object[] userKeys) {
        return CompletableFuture.supplyAsync(() -> {
            T[] results = syncMapper.read(clazz, userKeys);
            return new java.util.ArrayList<>(java.util.Arrays.asList(results));
        });
    }

    @Override
    public <T> CompletableFuture<Boolean> delete(@NotNull Class<T> clazz, @NotNull Object userKey) {
        return CompletableFuture.supplyAsync(() -> syncMapper.delete(clazz, userKey));
    }

    @Override
    public CompletableFuture<Boolean> delete(@NotNull Object object) {
        return CompletableFuture.supplyAsync(() -> syncMapper.delete(object));
    }

    @Override
    public <T> Flow.Publisher<T> scan(@NotNull Class<T> clazz) {
        return query(clazz, null);
    }

    @Override
    public <T> Flow.Publisher<T> query(@NotNull Class<T> clazz, String aelExpression) {
        SubmissionPublisher<T> publisher = new SubmissionPublisher<>();

        CompletableFuture.runAsync(() -> {
            try {
                ClassCacheEntry<T> entry = MapperUtils.getEntryAndValidateNamespace(clazz, this);
                var dataSet = com.aerospike.client.sdk.DataSet.of(entry.getNamespace(), entry.getSetName());
                var queryBuilder = session.query(dataSet);
                if (aelExpression != null && !aelExpression.isBlank()) {
                    queryBuilder.where(aelExpression);
                }

                try (RecordStream stream = queryBuilder.execute()) {
                    while (stream.hasNext()) {
                        RecordResult rr = stream.next();
                        if (rr.isOk() && rr.recordOrNull() != null) {
                            T object = mappingConverter.convertToObject(clazz, rr.key(), rr.recordOrNull(),
                                    entry, true, session);
                            publisher.submit(object);
                        }
                    }
                }
                publisher.close();
            } catch (Throwable t) {
                publisher.closeExceptionally(t);
            }
        });

        return publisher;
    }

    @Override
    public <T> ReactiveVirtualList<T> asBackedList(@NotNull Object object, @NotNull String binName, Class<T> elementClazz) {
        return new ReactiveVirtualList<>(this, object, binName, elementClazz);
    }

    @Override
    public <T> ReactiveVirtualList<T> asBackedList(@NotNull Class<?> owningClazz, @NotNull Object key, @NotNull String binName,
                                                   Class<T> elementClazz) {
        return new ReactiveVirtualList<>(this, owningClazz, key, binName, elementClazz);
    }

    @Override
    public Session getSession() {
        return this.session;
    }

    @Override
    public MappingConverter getMappingConverter() {
        return this.mappingConverter;
    }

    @Override
    public IAeroMapper asMapper() {
        return this.syncMapper;
    }
}
