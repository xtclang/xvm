package org.xvm.runtime;

import java.util.Objects;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import java.util.function.Function;

/**
 * A native resource reserved with its container before acquisition begins. Explicit close and
 * container termination share one cleanup action and its completion.
 *
 * <p>Cleanup starts without holding either the resource or container monitor. It must not block;
 * asynchronous cleanup returns a stage that completes when the resource has actually stopped.
 * The container's owner applies its existing shutdown deadline while awaiting that completion.
 *
 * @param <T>  the resource type
 */
public final class OwnedResource<T> {
    OwnedResource(Container owner, Function<? super T, ? extends CompletionStage<Void>> cleanup) {
        this.owner   = owner;
        this.cleanup = Objects.requireNonNull(cleanup);
    }

    /**
     * Obtain the resource while it is open. The caller must still tolerate concurrent shutdown
     * during use, as it would with an explicitly closed channel or socket.
     *
     * @return the acquired resource
     *
     * @throws IllegalStateException if acquisition has not finished or closing has begun
     */
    public synchronized T get() {
        if (!acquired || closing) {
            throw new IllegalStateException("Resource is not open");
        }
        return value;
    }

    /**
     * Initiate cleanup once, or wait for an in-progress acquisition to supply the resource to
     * clean up. Successful cleanup removes the registration; failures also reach owner shutdown.
     *
     * @return a view of cleanup completion; cancelling or completing this view cannot bypass cleanup
     */
    public CompletableFuture<Void> closeAsync() {
        synchronized (this) {
            closing = true;
        }
        startCleanup();
        return completion.copy();
    }

    /**
     * Supply the acquired value, cleaning it up immediately if shutdown won the race.
     *
     * @return true if the value can be handed to the caller
     */
    boolean acquired(T value) {
        boolean accepted;
        synchronized (this) {
            this.value = value;
            acquired   = true;
            accepted   = !closing;
        }
        if (!accepted) {
            startCleanup();
        }
        return accepted;
    }

    /**
     * Release a reservation whose factory failed without returning a resource.
     */
    void acquisitionFailed() {
        synchronized (this) {
            closing = true;
        }
        finish(null);
    }

    private void startCleanup() {
        T resource;
        Function<? super T, ? extends CompletionStage<Void>> action;
        synchronized (this) {
            if (!acquired || cleanupStarted) {
                return;
            }
            cleanupStarted = true;
            resource       = value;
            action         = cleanup;
        }
        try {
            Objects.requireNonNull(action.apply(resource), "Cleanup returned no completion")
                    .whenComplete((_, failure) -> finish(failure));
        } catch (RuntimeException | Error failure) {
            finish(failure);
        }
    }

    private void finish(Throwable failure) {
        Container previousOwner;
        synchronized (this) {
            previousOwner = owner;
            if (previousOwner == null) {
                return;
            }
            owner   = null;
            value   = null;
            cleanup = null;
        }
        previousOwner.releaseResource(this, failure);
        if (failure == null) {
            completion.complete(null);
        } else {
            completion.completeExceptionally(failure);
        }
    }

    /**
     * Acquire a resource, closing any partial allocation before throwing if acquisition fails.
     *
     * @param <T>  the resource type
     * @param <E>  the acquisition exception type
     */
    @FunctionalInterface
    public interface Factory<T, E extends Exception> {
        /**
         * @return the newly acquired resource; never null
         *
         * @throws E if acquisition fails
         */
        T open() throws E;
    }

    private Container owner;
    private T value;
    private Function<? super T, ? extends CompletionStage<Void>> cleanup;
    private boolean acquired;
    private boolean closing;
    private boolean cleanupStarted;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
}
