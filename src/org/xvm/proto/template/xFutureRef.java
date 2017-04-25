package org.xvm.proto.template;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xFutureRef
        extends xRef
    {
    public static xFutureRef INSTANCE;

    public xFutureRef(TypeSet types)
        {
        super(types, "x:FutureRef<RefType>", "x:Ref", Shape.Mixin);

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        //    enum Completion {Pending, Result, Error};
        //    public/private Completion completion = Pending;
        //    private Boolean assignable = false;
        //    private Exception? failure = null;
        //    typedef function Void (Completion, RefType?, Exception?) NotifyDependent;
        //    private NotifyDependent? notify = null;

        // FutureRef.Type<RefType> whenComplete(function Void (RefType?, Exception?) notify)
        MethodTemplate mtWC = ensureMethodTemplate("whenComplete", new String[] {"x:Function"}, THIS);
        mtWC.markNative();
        }

    @Override
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget,
                                        MethodTemplate method, ObjectHandle hArg, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.f_sName)
            {
            case "whenComplete":
                FunctionHandle hNotify = (FunctionHandle) hArg;
                CompletableFuture<ObjectHandle> cf = hThis.m_future.whenComplete((r, x) ->
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[2];
                    ahArg[0] = r;
                    ahArg[1] = x == null ? xNullable.NULL :
                                ((ExceptionHandle.WrapperException) x).getExceptionHandle();

                    ExceptionHandle hException = hNotify.call1(frame, ahArg, -1);

                    if (hException != null)
                        {
                        // TODO: call the "Unhandled exception" handler
                        Utils.log("Unhandled exception: " + hException);
                        }
                    });
                return iReturn >= 0 ? frame.assignValue(iReturn, makeHandle(cf)) : null;

            }
        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new FutureHandle(clazz, null, false);
        }

    public static class FutureHandle
            extends xRef.RefHandle
        {
        public final boolean f_fSynthetic;
        protected CompletableFuture<ObjectHandle> m_future;

        protected FutureHandle(TypeComposition clazz, CompletableFuture<ObjectHandle> future, boolean fSynthetic)
            {
            super(clazz, null);

            f_fSynthetic = fSynthetic;
            m_future = future;
            }

        @Override
        public ObjectHandle get()
                throws ExceptionHandle.WrapperException
            {
            try
                {
                // TODO: use the timeout defined on the service
                while (!m_future.isDone())
                    {
                    ServiceContext.getCurrentContext().yield();
                    }
                return m_future.get();
                }
            catch (InterruptedException e)
                {
                throw new UnsupportedOperationException("TODO");
                }
            catch (ExecutionException e)
                {
                Throwable eOrig = e.getCause();
                if (eOrig instanceof ExceptionHandle.WrapperException)
                    {
                    throw (ExceptionHandle.WrapperException) eOrig;
                    }
                throw new UnsupportedOperationException("Unexpected exception", eOrig);
                }
            }

        @Override
        public ExceptionHandle set(ObjectHandle handle)
            {
            if (handle instanceof FutureHandle)
                {
                if (m_future != null)
                    {
                    return xException.makeHandle("Future has already been assigned");
                    }
                m_future = ((FutureHandle) handle).m_future;
                }
            else if (m_future.isDone())
                {
                return xException.makeHandle("Future has already been set");
                }
            else
                {
                m_future.complete(handle);
                }
            return null;
            }

        @Override
        public String toString()
            {
            return "(" + f_clazz + ") " + (
                    m_future == null ?  "Unassigned" :
                    m_future.isDone() ? "Completed" :
                                        "Not completed"
                    );
            }
        }

    public static FutureHandle makeHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, future, false);
        }

    public static FutureHandle makeSyntheticHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, future, true);
        }
    }
