package org.xvm.runtime.template.annotations;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
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
            TYPE     = getCanonicalType();
            }
        }

    @Override
    public void initDeclared()
        {
        // FutureVar!<RefType> whenComplete(function void (RefType?, Exception?) notify)
        markNativeMethod("whenComplete", new String[] {"Function"}, new String[] {"annotations.FutureVar!<RefType>"});
        markNativeMethod("thenDo", new String[] {"Function"}, new String[] {"annotations.FutureVar!<RefType>"});
        markNativeMethod("passTo", new String[] {"Function"}, new String[] {"annotations.FutureVar!<RefType>"});

        markNativeMethod("get", VOID, new String[] {"RefType"});
        markNativeMethod("set", new String[] {"RefType"}, VOID);

        markNativeMethod("completeExceptionally", new String[] {"Exception"}, VOID);

        xEnum enumCompletion = (xEnum) getChildTemplate("Completion");
        Pending = enumCompletion.getEnumByName("Pending");
        Result = enumCompletion.getEnumByName("Result");
        Error = enumCompletion.getEnumByName("Error");
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

                CompletableFuture cf = hThis.m_future.thenRun(() ->
                    {
                    frame.f_context.callLater(hRun, Utils.OBJECTS_NONE);
                    });

                return frame.assignValue(iReturn, makeHandle(cf), true);
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

                return frame.assignValue(iReturn, makeHandle(cf), true);
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

                return frame.assignValue(iReturn, makeHandle(cf), true);
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
            return frame.raiseException(xException.unassignedReference());
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
                return frame.raiseException(
                    xException.makeHandle("FutureVar has already been assigned"));
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
            return frame.raiseException(
                xException.makeHandle("FutureVar has already been set"));
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
        public ObjectHandle getValue()
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
         * @return a DeferredCallHandle for getting a field from the future object represented by
         *         this handle
         */
        public DeferredCallHandle makeDeferredGetField(Frame frame, String sName)
            {
            assert m_future != null;

            Op[] aopGetProperty = new Op[]
                {
                new Op()
                    {
                    public int process(Frame frame, int iPC)
                        {
                        return frame.call(Utils.createWaitFrame(frame, m_future, A_STACK));
                        }
                    public String toString()
                        {
                        return "waitFutureHandle -> this:stack";
                        }
                    },
                new Op()
                    {
                    public int process(Frame frame, int iPC)
                        {
                        GenericHandle hTarget = (GenericHandle) frame.popStack();
                        return frame.returnValue(hTarget.getField(sName), false);
                        }
                    public String toString()
                        {
                        return "return getField " + sName;
                        }
                    },
                };

            Frame frameGetProperty = frame.createNativeFrame(
                aopGetProperty, Utils.OBJECTS_NONE, Op.A_STACK, null);
            return new DeferredCallHandle(frameGetProperty);
            }

        /**
         * @return a DeferredCallHandle for getting a property the future represented by this handle
         */
        public DeferredCallHandle makeDeferredGetProperty(Frame frame, PropertyConstant idProp)
            {
            assert m_future != null;

            Op[] aopGetProperty = new Op[]
                {
                new Op()
                    {
                    public int process(Frame frame, int iPC)
                        {
                        return frame.call(Utils.createWaitFrame(frame, m_future, A_STACK));
                        }
                    public String toString()
                        {
                        return "waitFutureHandle -> this:stack";
                        }
                    },
                new Op()
                    {
                    public int process(Frame frame, int iPC)
                        {
                        ObjectHandle hTarget = frame.popStack();
                        return hTarget.getTemplate().getPropertyValue(frame, hTarget, idProp, A_STACK);
                        }
                    public String toString()
                        {
                        return "getProperty -> this:stack";
                        }
                    },
                new Op()
                    {
                    public int process(Frame frame, int iPC)
                        {
                        return frame.returnValue(frame.popStack(), false);
                        }
                    public String toString()
                        {
                        return "return this:stack";
                        }
                    },
                };

            Frame frameGetProperty = frame.createNativeFrame(
                aopGetProperty, Utils.OBJECTS_NONE, Op.A_STACK, null);
            return new DeferredCallHandle(frameGetProperty);
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
