package org.xvm.runtime;


import java.lang.ref.WeakReference;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Weak reference for the function that is to be called on the specified context.
 */
public class WeakCallback
        extends WeakReference<ServiceContext>
    {
    public WeakCallback(ServiceContext context, FunctionHandle hFunction)
        {
        super(context);

        f_lFunctionId = context.f_container.f_runtime.makeUniqueId();
        context.ensureCallbackMap().put(f_lFunctionId, hFunction);
        }

    /**
     * @return the underlying function; never null
     */
    public FunctionHandle getFunction()
        {
        ServiceContext context = get();
        if (context != null)
            {
            FunctionHandle hFunction = context.getCallbackMap().get(f_lFunctionId);
            if (hFunction != null)
                {
                return hFunction;
                }
            }
        throw new IllegalStateException();
        }

    private final long f_lFunctionId;
    }