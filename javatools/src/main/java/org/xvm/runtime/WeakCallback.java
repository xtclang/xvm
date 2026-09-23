package org.xvm.runtime;

import java.lang.ref.WeakReference;

import java.util.concurrent.CompletableFuture;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

/**
 * Weak reference for the function that is to be called at some point in the future on the context
 * of the specified frame if the corresponding service is still running.
 *
 * <p>The idea behind the WeakCallback is that it can retrieve all necessary information to create a
 * {@link ServiceContext.CallLaterRequest} using a unique id, but does not itself hold that data,
 * therefore not preventing the underlying service from being stopped and GC'd.
 */
public class WeakCallback
        extends WeakReference<ServiceContext> {
    public WeakCallback(Frame frame, FunctionHandle hFunction) {
        super(frame.f_context);

        f_lCallbackId = frame.f_context.f_container.f_runtime.makeUniqueId();
        ServiceContext context = frame.f_context;
        registration = frame.acquireResource(() -> {
            context.ensureCallbackMap().put(f_lCallbackId, new Callback(frame, hFunction));
            return f_lCallbackId;
        }, _ -> {
            cancelAlarm();
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * @return the underlying callback, or null if cancellation or termination already removed it
     */
    public Callback extractCallback() {
        Callback callback;
        synchronized (this) {
            ServiceContext context = get();
            callback = context == null ? null : context.getCallbackMap().remove(f_lCallbackId);
            if (callback != null) {
                // Execution now owns the alarm's keep-alive release, after posting the callback.
                cancellation = null;
                discarded = true;
            }
        }
        discard();
        return callback;
    }

    /**
     * Release a cancelled callback without invoking it. Safe to race extraction or repeat.
     */
    public void discard() {
        registration.closeAsync();
    }

    /**
     * Attach alarm disposal before scheduling, including disposal of a paused alarm on termination.
     *
     * @param action  idempotent cancellation of the alarm
     */
    public void onDiscard(Runnable action) {
        boolean run;
        synchronized (this) {
            run = discarded;
            if (!run) {
                cancellation = action;
            }
        }
        if (run) {
            action.run();
        }
    }

    private void cancelAlarm() {
        Runnable action;
        synchronized (this) {
            ServiceContext context = get();
            if (context != null) {
                context.getCallbackMap().remove(f_lCallbackId);
            }
            discarded = true;
            action = cancellation;
            cancellation = null;
        }
        if (action != null) {
            action.run();
        }
    }

    @Override
    public String toString() {
        ServiceContext context = get();
        if (context != null) {
            Callback callback = context.getCallbackMap().get(f_lCallbackId);
            if (callback != null) {
                return callback.functionHandle().toString();
            }
        }
        return "Empty";
    }

    /**
     * The callback data.
     */
    public record Callback(Frame frame, FunctionHandle functionHandle) {}

    /**
     * The callback data id.
     */
    private final long f_lCallbackId;
    private final OwnedResource<Long> registration;
    private Runnable cancellation;
    private boolean discarded;
}
