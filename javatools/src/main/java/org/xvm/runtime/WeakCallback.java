package org.xvm.runtime;


import java.lang.ref.WeakReference;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Weak reference for the function that is to be called at some point in the future on the context
 * of the specified frame if the corresponding service is still running.
 * <p/>
 * The idea behind the WeakCallback is that it can retrieve all necessary information to create a
 * {@link ServiceContext.CallLaterRequest} using a unique id, but does not itself hold that data,
 * therefore not preventing the underlying service from being stopped and GC'd.
 */
public class WeakCallback
        extends WeakReference<ServiceContext> {
    public WeakCallback(Frame frame, FunctionHandle hFunction) {
        super(frame.f_context);

        f_lCallbackId = frame.f_context.f_container.f_runtime.makeUniqueId();
        frame.f_context.getCallbackMap().put(f_lCallbackId, new Callback(frame, hFunction));
    }

    /**
     * Extract the callback data, removing it from the owning service's registry.
     * <p/>
     * This runs on the shared native timer thread. A missing callback is a normal outcome - the
     * service may have been collected, or the alarm may have been discarded by a racing cancel -
     * and must never throw here: an exception escaping a {@link java.util.TimerTask} kills the
     * shared static {@link java.util.Timer} and silently disables every alarm in every container.
     *
     * @return the callback data, or null if the service is gone or the callback was already
     *         extracted or discarded
     */
    public Callback extractCallback() {
        ServiceContext context = get();
        return context == null
                ? null
                : context.getCallbackMap().remove(f_lCallbackId);
    }

    /**
     * Discard the callback data without running it. Called when an alarm is canceled, so that the
     * registry does not leak the captured frame and function for the lifetime of the service.
     */
    public void discard() {
        ServiceContext context = get();
        if (context != null) {
            context.getCallbackMap().remove(f_lCallbackId);
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
}
