package org.xvm.runtime.template.annotations;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction.FunctionHandle;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xVar;


/**
 * TODO:
 */
public class xFutureVar
        extends xVar
    {
    public static xFutureVar INSTANCE;
    public static TypeConstant TYPE;
    public static EnumHandle Pending;
    public static EnumHandle Result;
    public static EnumHandle Error;

    public xFutureVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            TYPE = getCanonicalType();
            }
        }

    @Override
    public void initDeclared()
        {
        // FutureVar!<RefType> whenComplete(function Void (RefType?, Exception?) notify)
        markNativeMethod("whenComplete", new String[] {"Function"}, new String[] {"annotations.FutureVar!<RefType>"});
        markNativeMethod("thenDo", new String[] {"Function"}, new String[] {"annotations.FutureVar!<RefType>"});
        markNativeMethod("passTo", new String[] {"Function"}, new String[] {"annotations.FutureVar!<RefType>"});

        xEnum enumCompletion = (xEnum) getChildTemplate("Completion");
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

    @Override
    protected int getInternal(Frame frame, RefHandle hTarget, int iReturn)
        {
        FutureHandle hFuture = (FutureHandle) hTarget;

        CompletableFuture<ObjectHandle> cf = hFuture.m_future;
        if (cf == null)
            {
            return frame.raiseException(xException.makeHandle("Unassigned reference"));
            }

        if (cf.isDone())
            {
            try
                {
                return frame.assignValue(iReturn, cf.get());
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
                    return frame.raiseException((ExceptionHandle.WrapperException) eOrig);
                    }
                throw new UnsupportedOperationException("Unexpected exception", eOrig);
                }
            }
        else
            {
            // wait for the completion; the service is responsible for timing out
            return Op.R_BLOCK;
            }
        }

    @Override
    protected int setInternal(Frame frame, RefHandle hTarget, ObjectHandle handle)
        {
        FutureHandle hFuture = (FutureHandle) hTarget;

        assert handle != null;

        if (handle instanceof FutureHandle)
            {
            // this is only possible if this "handle" is a "dynamic ref" and the passed in
            // "handle" is a synthetic or dynamic one (see Frame.assignValue)
            if (hFuture.m_future != null)
                {
                return frame.raiseException(
                    xException.makeHandle("FutureVar has already been assigned"));
                }

            FutureHandle that = (FutureHandle) handle;
            hFuture.m_future = that.m_future;
            return Op.R_NEXT;
            }

        CompletableFuture cf = hFuture.m_future;
        if (cf == null)
            {
            hFuture.m_future = CompletableFuture.completedFuture(handle);
            return Op.R_NEXT;
            }

        if (cf.isDone())
            {
            return frame.raiseException(
                xException.makeHandle("FutureVar has already been set"));
            }

        cf.complete(handle);
        return Op.R_NEXT;
        }


    // ----- the handle -----

    public static class FutureHandle
            extends RefHandle
        {
        public CompletableFuture<ObjectHandle> m_future;

        protected FutureHandle(TypeComposition clazz, String sName, CompletableFuture<ObjectHandle> future)
            {
            super(clazz, sName);

            m_future = future;
            }

        @Override
        public boolean isAssigned(Frame frame)
            {
            return m_future != null && m_future.isDone();
            }

        @Override
        public String toString()
            {
            return "(" + m_clazz + ") " + (
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
        return new FutureHandle(INSTANCE.ensureCanonicalClass(), null, future);
        }
    }
