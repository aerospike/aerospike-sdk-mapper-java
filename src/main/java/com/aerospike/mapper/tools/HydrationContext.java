package com.aerospike.mapper.tools;

import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Session;
import com.aerospike.mapper.tools.DeferredObjectLoader.DeferredObjectSetter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit context object for hydration operations.
 *
 * <p>An instance is created at the boundary of each hydration operation and
 * passed explicitly through the call chain. All state (key stack, object cache,
 * deferred queue) is instance-based with no static/ThreadLocal state.</p>
 */
public class HydrationContext {

    private final List<DeferredObjectSetter> deferred = new ArrayList<>();
    private final Map<Key, Object> loadedObjects = new HashMap<>();
    private final Deque<Key> keyStack = new ArrayDeque<>();
    private final Session session;

    public HydrationContext(Session session) {
        this.session = session;
    }

    // -- Key stack --

    public void pushKey(Key key) {
        keyStack.addLast(key);
    }

    public Key currentKey() {
        return keyStack.peekLast();
    }

    public void popKey() {
        keyStack.removeLast();
    }

    // -- Object cache --

    public void cacheCurrentObject(Object obj) {
        Key key = currentKey();
        if (key != null) {
            loadedObjects.put(key, obj);
        }
    }

    public Object getCached(Key key) {
        return loadedObjects.get(key);
    }

    // -- Deferred list --

    public void addDeferred(DeferredObjectSetter setter) {
        deferred.add(setter);
    }

    public List<DeferredObjectSetter> getAndClearDeferred() {
        List<DeferredObjectSetter> result = new ArrayList<>(deferred);
        deferred.clear();
        return result;
    }

    // -- Session access --

    public Session session() {
        return session;
    }

}
