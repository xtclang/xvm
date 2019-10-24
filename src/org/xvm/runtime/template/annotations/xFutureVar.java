package org.xvm.runtime.template.annotations;


import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle.WrapperException;
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
            TYPE     = getCanonicalType();
            }
        }

    @Override
    public void initDeclared()
        {
        xEnum enumCompletion = (xEnum) getChildTemplate("Completion");
        Pending = enumCompletion.getEnumByName("Pending");
        Result  = enumCompletion.getEnumByName("Result");
        Error   = enumCompletion.getEnumByName("Error");

        markNativeMethod("whenComplete", new String[] {"Function"}, new String[] {"annotations.FutureVar!<Referent>"});
        markNativeMethod("thenDo", new String[] {"Function"}, new String[] {"annotations.FutureVar!<Referent>"});
        markNativeMethod("passTo", new String[] {"Function"}, new String[] {"annotations.FutureVar!<Referent>"});

        markNativeMethod("get", VOID, new String[] {"Referent"});
        markNativeMethod("set", new String[] {"Referent"}, VOID);

        markNativeMethod("completeExceptionally", new String[] {"Exception"}, VOID);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;
        CompletableFuture cf = hThis.m_future;

        switch (sPropName)
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
                        if (eOrig instanceof WrapperException)
                            {
                            ExceptionHandle hException =
                                    ((WrapperException) eOrig).getExceptionHandle();
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
                        Pending :
                    cf.isCompletedExceptionally() ?
                        Error :
                        Result;
                return frame.assignValue(iReturn, hValue);
                }

            case "notify":
                // currently unused
                return frame.assignValue(iReturn, xNullable.NULL);

            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.getName())
            {
            case "completeExceptionally":
                {
                ExceptionHandle hException = (ExceptionHandle) hArg;

                hThis.m_future.completeExceptionally(hException.getException());

                return Op.R_NEXT;
                }

            case "thenDo":
                {
                FunctionHandle hRun = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future;
                if (cf.isDone())
                    {
                    ObjectHandle[] ahArg = extractResult(frame, cf);
                    if (ahArg[1] != xNullable.NULL)
                        {
                        // the future terminated exceptionally; re-throw it
                        return frame.raiseException((ExceptionHandle) ahArg[1]);
                        }

                    switch (hRun.call1(frame, null, Utils.OBJECTS_NONE, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            // we can return the same handle since it's completed
                            return frame.assignValue(iReturn, hThis);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, hThis));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    CompletableFuture cf0 = cf.thenRun(() ->
                        frame.f_context.callLater(hRun, Utils.OBJECTS_NONE));
                    return frame.assignValue(iReturn, makeHandle(cf0));
                    }
                }

            case "passTo":
                {
                FunctionHandle hConsume = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future;
                if (cf.isDone())
                    {
                    ObjectHandle[] ahArg = extractResult(frame, cf);
                    if (ahArg[1] != xNullable.NULL)
                        {
                        // the future terminated exceptionally; re-throw it
                        return frame.raiseException((ExceptionHandle) ahArg[1]);
                        }

                    ahArg[1] = null; // the consumer doesn't need it

                    switch (hConsume.call1(frame, null, ahArg, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            // we can return the same handle since it's completed
                            return frame.assignValue(iReturn, hThis);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, hThis));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    CompletableFuture cf0 = cf.thenAccept(r ->
                        {
                        ObjectHandle[] ahArg = new ObjectHandle[1];
                        ahArg[0] = r;
                        frame.f_context.callLater(hConsume, ahArg);
                        });

                    return frame.assignValue(iReturn, makeHandle(cf0));
                    }
                }

            case "whenComplete":
                {
                FunctionHandle hNotify = (FunctionHandle) hArg;

                CompletableFuture<ObjectHandle> cf = hThis.m_future;
                if (cf.isDone())
                    {
                    ObjectHandle[] ahArg = extractResult(frame, cf);

                    switch (hNotify.call1(frame, null, ahArg, Op.A_IGNORE))
                        {
                        case Op.R_NEXT:
                            // we can return the same handle since it's completed
                            return frame.assignValue(iReturn, hThis);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                frameCaller.assignValue(iReturn, hThis));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    cf = cf.whenComplete((r, x) ->
                        frame.f_context.callLater(hNotify, combineResult(r, x)));

                    return frame.assignValue(iReturn, makeHandle(cf));
                    }
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    /**
     * Extract the result of the completed future into an ObjectHandle array.
     *
     *
     * @param frame  the current frame
     * @param cf     the future
     *
     * @return an ObjectHandle array containing the result and the exception handles
     */
    protected ObjectHandle[] extractResult(Frame frame, CompletableFuture<ObjectHandle> cf)
        {
        ObjectHandle[] ahArg = new ObjectHandle[2];
        try
            {
            ahArg[0] = cf.get();
            ahArg[1] = xNullable.NULL;
            }
        catch (Throwable e)
            {
            ExceptionHandle hException;
            if (e instanceof ExecutionException)
                {
                hException = ((WrapperException) e.getCause()).getExceptionHandle();
                }
            else if (e instanceof CancellationException)
                {
                hException = xException.makeHandle(frame, "cancelled");
                }
            else
                {
                hException = xException.makeHandle(frame, "interrupted");
                }
            ahArg[0] = xNullable.NULL;
            ahArg[1] = hException;
            }
        return ahArg;
        }

    /**
     * Combine the result of the completed future phase into an ObjectHandle array.
     *
     * @param hResult  the execution result
     * @param exception        an exception
     *
     * @return an ObjectHandle array containing the result and the exception handles
     */
    protected ObjectHandle[] combineResult(ObjectHandle hResult, Throwable exception)
        {
        ObjectHandle[] ahArg = new ObjectHandle[2];
        ahArg[0] = hResult;
        ahArg[1] = exception == null
                ? xNullable.NULL
                : ((WrapperException) exception).getExceptionHandle();
        return ahArg;
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
            return frame.raiseException(xException.unassignedReference(frame));
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
                if (eOrig instanceof WrapperException)
                    {
                    return frame.raiseException((WrapperException) eOrig);
                    }
                throw new UnsupportedOperationException("Unexpected exception", eOrig);
                }
            }

        // wait for the assignment/completion; the service is responsible for timing out
        return Op.R_BLOCK;
        }

    @Override
    protected int setInternal(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        FutureHandle hFuture = (FutureHandle) hTarget;

        assert hValue != null;

        if (hValue instanceof FutureHandle)
            {
            // this is only possible if this "handle" is a "dynamic ref" and the passed in
            // "handle" is a synthetic or dynamic one (see Frame.assignValue)
            if (hFuture.m_future != null)
                {
                return frame.raiseException("FutureVar has already been assigned");
                }

            FutureHandle that = (FutureHandle) hValue;
            hFuture.m_future = that.m_future;
            return Op.R_NEXT;
            }

        CompletableFuture cf = hFuture.m_future;
        if (cf == null)
            {
            hFuture.m_future = CompletableFuture.completedFuture(hValue);
            return Op.R_NEXT;
            }

        if (cf.isDone())
            {
            return frame.raiseException("FutureVar has already been set");
            }

        cf.complete(hValue);
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
        public boolean isAssigned()
            {
            return m_future != null && m_future.isDone();
            }

        @Override
        public ObjectHandle getReferent()
            {
            // it's a responsibility of the caller to only use this when the future is known to have
            // completed normally
            try
                {
                return m_future.get();
                }
            catch (Throwable e)
                {
                return null;
                }
            }

        /**
         * @return true iff the future represented by this handle completed normally
         */
        public boolean isCompletedNormally()
            {
            return m_future != null && m_future.isDone() && !m_future.isCompletedExceptionally();
            }

        /**
         * @return a DeferredCallHandle for the future represented by this handle
         */
        public DeferredCallHandle makeDeferredHandle(Frame frame)
            {
            assert m_future != null;
            return new DeferredCallHandle(Utils.createWaitFrame(frame, m_future, Op.A_STACK));
            }

        /**
         * Wait for the future completion and assign the specified register to the result.
         *
         * @param frame    the current frame
         * @param iResult  the register id
         *
         * @return R_NEXT, R_CALL, R_EXCEPTION
         */
        public int waitAndAssign(Frame frame, int iResult)
            {
            if (isAssigned())
                {
                return frame.assignValue(iResult, getReferent());
                }

            // add a notification and wait for the assignment/completion;
            // the service is responsible for timing out
            CompletableFuture<ObjectHandle> cf = m_future;
            if (cf == null)
                {
                // since this ref can only be changed by this service,
                // we can safely add a completable future now
                cf = m_future = new CompletableFuture();
                }
            cf.whenComplete
                (
                (r, x) -> frame.f_fiber.m_fResponded = true
                );
            return frame.call(Utils.createWaitFrame(frame, cf, iResult));
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
        return new FutureHandle(INSTANCE.getCanonicalClass(), null, future);
        }
    }
