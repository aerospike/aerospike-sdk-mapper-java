package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.RecordResult;
import com.aerospike.mapper.tools.converters.MappingConverter;

import java.util.concurrent.Flow;

/**
 * A {@link Flow.Subscriber} adapter that transforms {@link RecordResult} items
 * into mapped Java objects and forwards them to a downstream subscriber.
 *
 * <p>Runs on the SDK's publisher virtual thread. Since mapping and
 * {@code resolveDependencies} may perform blocking batch reads, this is safe
 * because virtual threads park cheaply on blocking I/O.</p>
 */
final class MappingSubscriber<T> implements Flow.Subscriber<RecordResult> {

    private final Flow.Subscriber<? super T> downstream;
    private final Class<T> clazz;
    private final ClassCacheEntry<T> entry;
    private final MappingConverter converter;
    private final com.aerospike.client.sdk.Session session;
    private Flow.Subscription subscription;
    private volatile boolean done;

    MappingSubscriber(Flow.Subscriber<? super T> downstream, Class<T> clazz,
                      ClassCacheEntry<T> entry, com.aerospike.client.sdk.Session session,
                      MappingConverter converter) {
        this.downstream = downstream;
        this.clazz = clazz;
        this.entry = entry;
        this.session = session;
        this.converter = converter;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        downstream.onSubscribe(subscription);
    }

    @Override
    public void onNext(RecordResult rr) {
        if (done) {
            return;
        }
        if (!rr.isOk() || rr.recordOrNull() == null) {
            return;
        }
        HydrationContext ctx = new HydrationContext(session);
        try {
            ctx.pushKey(rr.key());
            try {
                T object = converter.convertToObject(clazz, rr.key(), rr.recordOrNull(), entry, true, ctx);
                downstream.onNext(object);
            } finally {
                ctx.popKey();
            }
        } catch (Throwable t) {
            done = true;
            subscription.cancel();
            downstream.onError(t);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        downstream.onError(throwable);
    }

    @Override
    public void onComplete() {
        downstream.onComplete();
    }
}
