package org.xvm.runtime.template.annotations;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.Enum;
import org.xvm.runtime.template.Enum.EnumHandle;
import org.xvm.runtime.template.Function.FunctionHandle;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.Ref;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xFutureRef
        extends Ref
    {
    public static xFutureRef INSTANCE;
    public static EnumHandle Pending;
    public static EnumHandle Result;
    public static EnumHandle Error;

    public xFutureRef(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // FutureRef!<RefType> whenComplete(function Void (RefType?, Exception?) notify)
        markNativeMethod("whenComplete", new String[] {"Function"}, new String[] {"annotations.FutureRef!<RefType>"});
        markNativeMethod("thenDo", new String[] {"Function"}, new String[] {"annotations.FutureRef!<RefType>"});
        markNativeMethod("passTo", new String[] {"Function"}, new String[] {"annotations.FutureRef!<RefType>"});

        markNativeMethod("get", VOID, new String[]{"RefType"});
        markNativeMethod("set", new String[]{"RefType"}, VOID);

        Enum enumCompletion = (Enum) f_types.getTemplate("annotations.FutureRef.Completion");
        Pending = enumCompletion.getEnumByName("Pending");
        Result = enumCompletion.getEnumByName("Result");
        Error = enumCompletion.getEnumByName("Error");
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;
        CompletableFuture cf = hThis.m_future;

        switch (property.getName())
            {
            case "assignable":
                return frame.assignValue(iReturn, xBoolean.makeHandle(cf == null || !cf.isDone()));

            case "assigned":
                return frame.assignValue(iReturn, xBoolean.makeHandle(cf != null && cf.isDone()));

            case "failure":
                {
                if (cf != null && cf.isCompletedExceptionally())
                    {
                    try
                        {
                        cf.get();
                        throw new IllegalStateException(); // cannot happen
                        }
                    catch (Exception e)
                        {
                        Throwable eOrig = e.getCause();
                        if (eOrig instanceof ExceptionHandle.WrapperException)
                            {
                            ExceptionHandle hException =
                                    ((ExceptionHandle.WrapperException) eOrig).getExceptionHandle();
                            return frame.assignValue(iReturn, hException);
                            }
                        throw new UnsupportedOperationException("Unexpected exception", e);
                        }
                    }
                return frame.assignValue(iReturn, xNullable.NULL);
                }

            case "completion":
                {
                EnumHandle hValue =
                    cf == null || !cf.isDone() ?
                        Pending : // REVIEW: shouldn't we throw if unassigned?
                    cf.isCompletedExceptionally() ?
                        Error :
                        Result;
                return frame.assignValue(iReturn, hValue);
                }

            case "notify":
                // currently unused
                return frame.assignValue(iReturn, xNullable.NULL);

            }
        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.getName())
            {
            case "thenDo":
                {
                FunctionHandle hRun = (FunctionHandle) hArg;

                CompletableFuture cf = hThis.m_future.thenRun(() ->
                    {
                    frame.f_context.callLater(hRun, Utils.OBJECTS_NONE);
                    });

                return frame.assignValue(iReturn, makeHandle(cf));
                }

            case "passTo":
                {
                FunctionHandle hConsume = (FunctionHandle) hArg;

                CompletableFuture cf = hThis.m_future.thenAccept(r ->
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[1];
                    ahArg[0] = r;
                    frame.f_context.callLater(hConsume, ahArg);
                    });

                return frame.assignValue(iReturn, makeHandle(cf));
                }

            case "whenComplete":
                {
                FunctionHandle hNotify = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future.whenComplete((r, x) ->
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[2];
                    ahArg[0] = r;
                    ahArg[1] = x == null ? xNullable.NULL :
                                ((ExceptionHandle.WrapperException) x).getExceptionHandle();

                    frame.f_context.callLater(hNotify, ahArg);
                    });

                return frame.assignValue(iReturn, makeHandle(cf));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return new FutureHandle(clazz, sName, null);
        }

    public static class FutureHandle
            extends RefHandle
        {
        public CompletableFuture<ObjectHandle> m_future;

        protected FutureHandle(TypeComposition clazz, String sName, CompletableFuture<ObjectHandle> future)
            {
            super(clazz, sName);

            m_future = future;
            }

        public boolean isDone()
            {
            return m_future != null && m_future.isDone();
            }

        @Override
        protected ObjectHandle getInternal()
                throws ExceptionHandle.WrapperException
            {
            CompletableFuture<ObjectHandle> cf = m_future;
            if (cf == null)
                {
                throw xException.makeHandle("Unassigned reference").getException();
                }

            if (cf.isDone())
                {
                try
                    {
                    return cf.get();
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
            else
                {
                // wait for the completion;
                // the service is responsible for timing out
                return null;
                }
            }

        @Override
        protected ExceptionHandle setInternal(ObjectHandle handle)
            {
            if (handle instanceof FutureHandle)
                {
                // this is only possible if this "handle" is a "dynamic ref" and the passed in
                // "handle" is a synthetic or dynamic one (see Frame.assignValue)
                if (m_future != null)
                    {
                    return xException.makeHandle("FutureRef has already been assigned");
                    }

                FutureHandle that = (FutureHandle) handle;
                this.m_future = that.m_future;
                return null;
                }

            if (m_future == null)
                {
                m_future = CompletableFuture.completedFuture(handle);
                return null;
                }

            if (m_future.isDone())
                {
                return xException.makeHandle("FutureRef has already been set");
                }

            m_future.complete(handle);
            return null;
            }

        @Override
        public String toString()
            {
            return "(" + f_clazz + ") " + (
                    m_future == null ?  "Unassigned" :
                    m_future.isDone() ? "Completed: "  + toSafeString():
                                        "Not completed"
                    );
            }

        private String toSafeString()
            {
            try
                {
                return String.valueOf(m_future.get());
                }
            catch (Throwable e)
                {
                if (e instanceof ExecutionException)
                    {
                    e = e.getCause();
                    }
                return e.toString();
                }
            }
        }

    public static FutureHandle makeHandle(CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(INSTANCE.f_clazzCanonical, null, future);
        }
    }
