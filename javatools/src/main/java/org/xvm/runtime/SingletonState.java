package org.xvm.runtime;

import java.util.concurrent.CompletableFuture;

import org.xvm.asm.constants.SingletonConstant;

import org.xvm.runtime.ObjectHandle.InitializingHandle;

/**
 * The live value and initialization attempt for a singleton in its owning container.
 *
 * <p>Obtain this state through {@link Container#ensureSingletonState}; the definition's pool alone
 * cannot select its value owner. Unshared containers have independent entries even when they use
 * the very same definition object. Shared modules resolve to their ancestor's entry. Entries live
 * in that owner's constant heap and are never copied with, or retained by, definition constants.
 * They are execution state, not evictable cache entries: removing a live entry could initialize
 * the same singleton again. The table becomes unreachable with its owning heap.
 *
 * <p>Initialization follows {@link Utils#initConstants}: dispatch to the owner's main service,
 * mark the initializing fiber, then publish a value or abort the attempt. Another fiber waits on
 * the attempt's future; recursion in the initializing fiber uses an {@link InitializingHandle}.
 * Only the owner's main service changes this bookkeeping, except for native bootstrap before
 * execution starts. The value is volatile so other services can read a published value. This
 * class does not make arbitrary parallel initialization or mutable definition access safe.
 */
public final class SingletonState {
    /**
     * Create an empty entry. Only the owning heap constructs entries, with an owner-canonical key.
     *
     * @param definition  the singleton definition in the owning container's pool
     */
    SingletonState(SingletonConstant definition) {
        this.definition = definition;
    }

    /**
     * @return the definition describing this value; it holds no execution state
     */
    public SingletonConstant getDefinition() {
        return definition;
    }

    /**
     * Read without starting initialization or evaluating a lazy referent.
     *
     * @return the value, a circular-initialization handle, or null before initialization
     */
    public ObjectHandle getHandle() {
        return handle;
    }

    /**
     * Publish a value and complete this owner's pending waiters.
     *
     * <p>Call on the owner's main service after successful construction, or during native
     * bootstrap. Enum construction can publish a struct before replacing it with its immutable
     * value. Bookkeeping is cleared before completing the future, whose continuations may reenter.
     *
     * @param value  the corresponding non-null handle
     */
    public void setHandle(ObjectHandle value) {
        assert value != null;

        var completion = initialized;
        handle = value;
        initializingFiber = null;
        initialized = null;
        if (completion != null) {
            completion.complete(value);
        }
    }

    /**
     * Claim an initialization attempt on the owner's main service after observing no value.
     *
     * @param fiber  the initializing fiber
     *
     * @return false if an attempt already exists; use {@link #getInitializationWaiter} in that case
     */
    public boolean markInitializing(Fiber fiber) {
        assert fiber != null;
        if (initializingFiber != null) {
            return false;
        }
        initializingFiber = fiber;
        return true;
    }

    /**
     * Join an existing attempt from the owner's main service. All waiting fibers share the same
     * completion future. Only recursion from the initializing fiber creates a circular handle.
     *
     * @param fiber  the requesting fiber
     *
     * @return the attempt's future, or null for recursive initialization
     */
    public CompletableFuture<ObjectHandle> getInitializationWaiter(Fiber fiber) {
        assert fiber != null;
        assert initializingFiber != null;
        if (fiber == initializingFiber) {
            if (!(handle instanceof InitializingHandle)) {
                handle = new InitializingHandle(this);
            }
            return null;
        }
        if (initialized == null) {
            initialized = new CompletableFuture<>();
        }
        return initialized;
    }

    /**
     * Release a failed attempt and fail its waiters. Call on the owner's main service on both
     * synchronous and asynchronous constructor failure. A subsequent access can retry; the old
     * future stays failed and is never reused for the next attempt.
     *
     * @param failure  the reason construction failed
     */
    public void abortInitialization(Throwable failure) {
        var completion = initialized;
        handle = null;
        initializingFiber = null;
        initialized = null;
        if (completion != null) {
            completion.completeExceptionally(failure);
        }
    }

    private final SingletonConstant definition;
    private volatile ObjectHandle handle;
    private Fiber initializingFiber;
    private CompletableFuture<ObjectHandle> initialized;
}
