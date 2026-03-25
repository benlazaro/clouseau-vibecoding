package com.clouseau.core;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

import java.util.concurrent.Executors;

/**
 * Application-wide event bus.
 *
 * SYNC  — UI state changes, plugin lifecycle events.
 * ASYNC — High-throughput log ingestion (keeps EDT free).
 */
public final class ClouseauEventBus {

    private static final EventBus SYNC  = new EventBus("clouseau-sync");

    // Use virtual threads on Java 21+; swap for newCachedThreadPool on Java 17
    private static final EventBus ASYNC = new AsyncEventBus(
        "clouseau-async",
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "clouseau-async");
            t.setDaemon(true);
            return t;
        })
    );

    private ClouseauEventBus() {}

    public static void register(Object subscriber)      { SYNC.register(subscriber);   }
    public static void registerAsync(Object subscriber) { ASYNC.register(subscriber);  }
    public static void unregister(Object subscriber)    { SYNC.unregister(subscriber); }

    public static void post(Object event)               { SYNC.post(event);   }
    public static void postAsync(Object event)          { ASYNC.post(event);  }
}
