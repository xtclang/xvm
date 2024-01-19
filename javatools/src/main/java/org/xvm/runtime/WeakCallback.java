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
        extends WeakReference<ServiceContext>
    {
    public WeakCallback(Frame frame, FunctionHandle hFunction)
        {
        super(frame.f_context);

        f_lCallbackId = frame.f_context.f_container.f_runtime.makeUniqueId();
        frame.f_context.ensureCallbackMap().put(f_lCallbackId, new Callback(frame, hFunction));
        }

    /**
     * @return the underlying function; never null
     */
    public Callback getCallback()
        {
        ServiceContext context = get();
        if (context != null)
            {
            Callback callback = context.getCallbackMap().get(f_lCallbackId);
            if (callback != null)
                {
                return callback;
                }
            }
        throw new IllegalStateException();
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