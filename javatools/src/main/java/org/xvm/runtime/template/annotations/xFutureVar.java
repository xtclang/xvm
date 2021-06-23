package org.xvm.runtime.template.annotations;


import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
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
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * FutureVar native implementation.
 */
public class xFutureVar
        extends xVar
    {
    public static xFutureVar INSTANCE;
    public static TypeConstant TYPE;
    public static xEnum COMPLETION;

    public xFutureVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ConstantPool  pool     = pool();
        ClassConstant idMixin  = (ClassConstant) f_struct.getIdentityConstant();
        Annotation    anno     = pool.ensureAnnotation(idMixin);
        TypeConstant  typeVar  = xVar.INSTANCE.getCanonicalType();

        TYPE       = pool.ensureAnnotatedTypeConstant(typeVar, anno);
        COMPLETION = (xEnum) f_templates.getTemplate("annotations.FutureVar.Completion");

        markNativeMethod("thenDo", null, null);
        markNativeMethod("passTo", null, null);
        markNativeMethod("transform", null, null);
        markNativeMethod("handle", null, null);
        markNativeMethod("transformOrHandle", null, null);
        markNativeMethod("whenComplete", null, null);

        markNativeMethod("and", null, null);
        markNativeMethod("or", null, null);

        markNativeMethod("get", VOID, null);
        markNativeMethod("set", null, VOID);

        markNativeMethod("completeExceptionally", null, VOID);

        markNativeProperty("assigned");
        markNativeProperty("completion");

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return TYPE;
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        FutureHandle                    hThis = (FutureHandle) hTarget;
        CompletableFuture<ObjectHandle> cf    = hThis.m_future;

        switch (sPropName)
            {
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
                            return frame.assignValue(iReturn,
                                    ((WrapperException) eOrig).getExceptionHandle());
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
                        COMPLETION.getEnumByName("Pending") :
                    cf.isCompletedExceptionally() ?
                        COMPLETION.getEnumByName("Error") :
                        COMPLETION.getEnumByName("Result");
                return Utils.assignInitializedEnum(frame, hValue, iReturn);
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
                hThis.m_future.completeExceptionally(((ExceptionHandle) hArg).getException());
                return Op.R_NEXT;

            case "thenDo":
                return invokeThenDo(frame, hThis, (FunctionHandle) hArg, iReturn);

            case "passTo":
                return invokePassTo(frame, hThis, (FunctionHandle) hArg, iReturn);

            case "handle":
                return invokeHandle(frame, hThis, (FunctionHandle) hArg, iReturn);

            case "or":
                return invokeOrFuture(frame, hThis, (FutureHandle) hArg, iReturn);

            case "whenComplete":
                return invokeWhenComplete(frame, hThis, (FunctionHandle) hArg, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.getName())
            {
            case "transform":
                return invokeTransform(frame, hThis, (TypeHandle) ahArg[0],
                        (FunctionHandle) ahArg[1], iReturn);

            case "transformOrHandle":
                return invokeTransformOrHandle(frame, hThis, (TypeHandle) ahArg[0],
                        (FunctionHandle) ahArg[1], iReturn);

            case "and":
                return invokeAndFuture(frame, hThis, (TypeHandle) ahArg[0], (TypeHandle) ahArg[1],
                        (FutureHandle) ahArg[2], (FunctionHandle) ahArg[3], iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Implementation of "FutureVar! thenDo(function void () run)"
     */
    protected int invokeThenDo(Frame frame, FutureHandle hThis, FunctionHandle hRun, int iReturn)
        {
        CompletableFuture<ObjectHandle> cf = hThis.m_future;

        if (cf.isDone())
            {
            ObjectHandle[] ahR = extractResult(frame, cf);
            if (ahR[1] != xNullable.NULL)
                {
                // the future terminated exceptionally; re-throw it
                return frame.raiseException((ExceptionHandle) ahR[1]);
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
            FutureHandle hThen = makeHandle(hThis.getComposition(), new CompletableFuture<>());

            cf.whenComplete((hR, ex) ->
                {
                if (ex == null)
                    {
                    CompletableFuture<ObjectHandle> cfThen =
                            frame.f_context.postRequest(frame, hRun, Utils.OBJECTS_NONE, 0);

                    cfThen.whenComplete((hVoid, exThen) ->
                            hThen.assign(hR, (WrapperException) exThen));
                    }
                else
                    {
                    hThen.assign(null, (WrapperException) ex);
                    }
                });
            return frame.assignValue(iReturn, hThen);
            }
        }

    /**
     * Implementation of "FutureVar! passTo(function void (Referent) consume)"
     */
    protected int invokePassTo(Frame frame, FutureHandle hThis, FunctionHandle hConsume, int iReturn)
        {
        CompletableFuture<ObjectHandle> cf = hThis.m_future;

        if (cf.isDone())
            {
            ObjectHandle[] ahR = extractResult(frame, cf);
            if (ahR[1] != xNullable.NULL)
                {
                // the future terminated exceptionally; re-throw it
                return frame.raiseException((ExceptionHandle) ahR[1]);
                }

            ahR[1] = null; // the consumer doesn't need it

            switch (hConsume.call1(frame, null, ahR, Op.A_IGNORE))
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
            FutureHandle hPass = makeHandle(hThis.getComposition(), new CompletableFuture<>());

            cf.whenComplete((hR, ex) ->
                {
                if (ex == null)
                    {
                    CompletableFuture<ObjectHandle> cfPass =
                            frame.f_context.postRequest(frame, hConsume, new ObjectHandle[] {hR}, 0);

                    cfPass.whenComplete((hVoid, exPass) ->
                            hPass.assign(hR, (WrapperException) exPass));
                    }
                else
                    {
                    hPass.assign(null, (WrapperException) ex);
                    }
                });
            return frame.assignValue(iReturn, hPass);
            }
        }

    /**
     * Implementation of "<NewType> FutureVar!<NewType> transform(function NewType (Referent) convert)"
     */
    protected int invokeTransform(Frame frame, FutureHandle hThis, TypeHandle hNewType,
                                  FunctionHandle hConvert, int iReturn)
        {
        CompletableFuture<ObjectHandle> cf       = hThis.m_future;
        TypeComposition                 clzTrans = frame.ensureClass(hNewType.getDataType());

        if (cf.isDone())
            {
            ObjectHandle[] ahR = extractResult(frame, cf);
            if (ahR[1] != xNullable.NULL)
                {
                // the future terminated exceptionally; re-throw it
                return frame.raiseException((ExceptionHandle) ahR[1]);
                }

            ahR[1] = null; // the converter doesn't need it

            switch (hConvert.call1(frame, null, ahR, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return frame.assignValue(iReturn, makeHandle(clzTrans,
                            CompletableFuture.completedFuture(frame.popStack())));

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn, makeHandle(clzTrans,
                            CompletableFuture.completedFuture(frameCaller.popStack()))));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        else
            {
            FutureHandle hTrans = makeHandle(clzTrans, new CompletableFuture<>());

            cf.whenComplete((hR, ex) ->
                {
                if (ex == null)
                    {
                    CompletableFuture<ObjectHandle> cfTrans =
                            frame.f_context.postRequest(frame, hConvert, new ObjectHandle[] {hR}, 1);

                    cfTrans.whenComplete((hNew, exTrans) ->
                            hTrans.assign(hNew, (WrapperException) exTrans));
                    }
                else
                    {
                    hTrans.assign(null, (WrapperException) ex);
                    }
                });
            return frame.assignValue(iReturn, hTrans);
            }
        }

    /**
     * Implementation of "FutureVar! handle(function Referent (Exception) convert)"
     */
    protected int invokeHandle(Frame frame, FutureHandle hThis, FunctionHandle hConvert, int iReturn)
        {
        CompletableFuture<ObjectHandle> cf        = hThis.m_future;
        TypeComposition                 clzHandle = hThis.getComposition();

        if (cf.isDone())
            {
            if (!cf.isCompletedExceptionally())
                {
                return frame.assignValue(iReturn, hThis);
                }

            ObjectHandle[] ahR = extractResult(frame, cf);
            assert ahR[0] == xNullable.NULL;

            ahR[0] = ahR[1]; // hException
            ahR[1] = null;

            switch (hConvert.call1(frame, null, ahR, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return frame.assignValue(iReturn, makeHandle(clzHandle,
                        CompletableFuture.completedFuture(frame.popStack())));

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn, makeHandle(clzHandle,
                            CompletableFuture.completedFuture(frameCaller.popStack()))));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        else
            {
            FutureHandle hHandle = makeHandle(clzHandle, new CompletableFuture<>());

            cf.whenComplete((hR, ex) ->
                {
                if (ex == null)
                    {
                    hHandle.assign(hR, null);
                    }
                else
                    {
                    ExceptionHandle hEx = ((WrapperException) ex).getExceptionHandle();
                    CompletableFuture<ObjectHandle> cfTrans =
                            frame.f_context.postRequest(frame, hConvert, new ObjectHandle[] {hEx}, 1);

                    cfTrans.whenComplete((hNew, exTrans) ->
                            hHandle.assign(hNew, (WrapperException) exTrans));
                    }
                });
            return frame.assignValue(iReturn, hHandle);
            }
        }

    /**
     * Implementation of "<NewType> FutureVar!<NewType>
     *                      transformOrHandle(function NewType (Referent?, Exception?) convert)"
     */
    protected int invokeTransformOrHandle(Frame frame, FutureHandle hThis, TypeHandle hNewType,
                                          FunctionHandle hConvert, int iReturn)
        {
        CompletableFuture<ObjectHandle> cf       = hThis.m_future;
        TypeComposition                 clzTrans = frame.ensureClass(hNewType.getDataType());

        if (cf.isDone())
            {
            ObjectHandle[] ahR = extractResult(frame, cf);

            switch (hConvert.call1(frame, null, ahR, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return frame.assignValue(iReturn, makeHandle(clzTrans,
                            CompletableFuture.completedFuture(frame.popStack())));

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn, makeHandle(clzTrans,
                            CompletableFuture.completedFuture(frameCaller.popStack()))));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        else
            {
            FutureHandle hTrans = makeHandle(clzTrans, new CompletableFuture<>());

            cf.whenComplete((hR, ex) ->
                {
                CompletableFuture<ObjectHandle> cfTrans =
                        frame.f_context.postRequest(frame, hConvert, combineResult(hR, ex), 1);

                cfTrans.whenComplete((hNew, exTrans) ->
                        hTrans.assign(hNew, (WrapperException) exTrans));
                });
            return frame.assignValue(iReturn, hTrans);
            }
        }

    /**
     * Implementation of "<OtherType, NewType> FutureVar!<NewType> and(FutureVar!<OtherType> other,
     *                      function NewType (Referent, OtherType) combine)"
     */
    protected int invokeAndFuture(Frame frame, FutureHandle hThis,
                                  TypeHandle hOtherType, TypeHandle hNewType,
                                  FutureHandle hThat, FunctionHandle hCombine, int iReturn)
        {
        CompletableFuture<ObjectHandle> cfThis = hThis.m_future;
        CompletableFuture<ObjectHandle> cfThat = hThat.m_future;
        TypeComposition                 clzAnd = frame.ensureClass(hNewType.getDataType());

        if (cfThis.isDone() && cfThat.isDone())
            {
            ObjectHandle[] ahRThis = extractResult(frame, cfThis);
            ObjectHandle[] ahRThat = extractResult(frame, cfThis);

            if (ahRThis[1] != xNullable.NULL)
                {
                return frame.raiseException((ExceptionHandle) ahRThis[1]);
                }
            if (ahRThat[1] != xNullable.NULL)
                {
                return frame.raiseException((ExceptionHandle) ahRThat[1]);
                }

            ObjectHandle[] ahArg = new ObjectHandle[] {ahRThis[0], ahRThat[0]};

            switch (hCombine.call1(frame, null, ahArg, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return frame.assignValue(iReturn, makeHandle(clzAnd,
                            CompletableFuture.completedFuture(frame.popStack())));

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn, makeHandle(clzAnd,
                            CompletableFuture.completedFuture(frameCaller.popStack()))));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        else
            {
            FutureHandle hAnd = makeHandle(clzAnd, new CompletableFuture<>());

            CompletableFuture.allOf(cfThis, cfThat).whenComplete((_null, ex) ->
                {
                if (ex == null)
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[2];
                    try
                        {
                        ahArg[0] = cfThis.get();
                        ahArg[1] = cfThat.get();
                        }
                    catch (Throwable e)
                        {
                        // must not happen
                        }

                    CompletableFuture<ObjectHandle> cfAnd =
                            frame.f_context.postRequest(frame, hCombine, ahArg, 1);

                    cfAnd.whenComplete((hNew, exTrans) ->
                            hAnd.assign(hNew, (WrapperException) exTrans));
                    }
                else
                    {
                    hAnd.assign(null, (WrapperException) ex);
                    }
                });
            return frame.assignValue(iReturn, hAnd);
            }
        }

    /**
     * Implementation of "FutureVar!<Referent> or(FutureVar!<Referent> other)"
     */
    protected int invokeOrFuture(Frame frame, FutureHandle hThis, FutureHandle hThat, int iReturn)
        {
        CompletableFuture<ObjectHandle> cfThis = hThis.m_future;
        CompletableFuture<ObjectHandle> cfThat = hThat.m_future;

        if (cfThis.isDone())
            {
            return frame.assignValue(iReturn, hThis);
            }

        if (cfThat.isDone())
            {
            return frame.assignValue(iReturn, hThat);
            }

        CompletableFuture cfAny = CompletableFuture.anyOf(cfThis, cfThat);

        return frame.assignValue(iReturn, makeHandle(hThis.getComposition(), cfAny));
        }

    /**
     * Implementation of "FutureVar!<Referent> whenComplete(function void (Referent?, Exception?) notify)"
     */
    protected int invokeWhenComplete(Frame frame, FutureHandle hThis, FunctionHandle hNotify, int iReturn)
        {
        CompletableFuture<ObjectHandle> cf = hThis.m_future;

        if (cf.isDone())
            {
            ObjectHandle[] ahR = extractResult(frame, cf);

            switch (hNotify.call1(frame, null, ahR, Op.A_IGNORE))
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
            FutureHandle hWhen = makeHandle(hThis.getComposition(), new CompletableFuture<>());

            cf.whenComplete((hR, ex) ->
                {
                CompletableFuture<ObjectHandle> cfWhen =
                        frame.f_context.postRequest(frame, hNotify, combineResult(hR, ex), 0);

                cfWhen.whenComplete((hVoid, exWhen) ->
                        hWhen.assign(hR, (WrapperException) exWhen));
                });
            return frame.assignValue(iReturn, hWhen);
            }
        }

    /**
     * Extract the result of the completed future into an ObjectHandle array.
     *
     * @param frame  the current frame
     * @param cf     the future
     *
     * @return an ObjectHandle array containing the result and the exception handles
     */
    protected ObjectHandle[] extractResult(Frame frame, CompletableFuture<ObjectHandle> cf)
        {
        ObjectHandle[] ahR = new ObjectHandle[2];
        try
            {
            ahR[0] = cf.get();
            ahR[1] = xNullable.NULL;
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
            ahR[0] = xNullable.NULL;
            ahR[1] = hException;
            }
        return ahR;
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
        ahArg[0] = hResult == null
                ? xNullable.NULL
                : hResult;
        ahArg[1] = exception == null
                ? xNullable.NULL
                : ((WrapperException) exception).getExceptionHandle();
        return ahArg;
        }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        // native handle; the future for a property needs to be initialized
        return new FutureHandle(clazz.ensureAccess(Access.PUBLIC), sName,
            frame == null ? new CompletableFuture<>() : null);
        }

    @Override
    protected int invokeNativeGetReferent(Frame frame, RefHandle hRef, int iReturn)
        {
        return invokeGetReferent(frame, hRef, iReturn);
        }

    @Override
    protected int invokeGetReferent(Frame frame, RefHandle hRef, int iReturn)
        {
        return getReferentImpl(frame, hRef, true, iReturn);
        }

    @Override
    protected int getReferentImpl(Frame frame, RefHandle hRef, boolean fNative, int iReturn)
        {
        if (hRef.isProperty())
            {
            ObjectHandle hHolder = hRef.getReferentHolder();
            if (hHolder.isService())
                {
                ServiceHandle hService = hHolder.getService();
                if (frame.f_context != hService.f_context)
                    {
                    return hService.f_context.sendProperty01Request(frame, hRef, null, iReturn,
                        (frameCaller, hTarget, idProp_, iRet) ->
                            getReferentImpl(frameCaller, (RefHandle) hTarget, true, iRet));
                    }
                }
            }

        FutureHandle hFuture = (FutureHandle) hRef;

        CompletableFuture<ObjectHandle> cf = hFuture.m_future;
        if (cf == null)
            {
            return frame.raiseException(xException.unassignedReference(frame));
            }

        if (cf.isDone())
            {
            return assignDone(frame, cf, iReturn);
            }

        // wait for the assignment/completion; the service is responsible for timing out
        return frame.call(Utils.createWaitFrame(frame, cf, iReturn));
        }

    @Override
    protected int invokeSetReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        return setReferentImpl(frame, hTarget, true, hValue);
        }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hRef, boolean fNative, ObjectHandle hValue)
        {
        if (hRef.isProperty())
            {
            ObjectHandle hHolder = hRef.getReferentHolder();
            if (hHolder.isService())
                {
                ServiceHandle hService = hHolder.getService();
                if (frame.f_context != hService.f_context)
                    {
                    return hService.f_context.sendProperty10Request(frame, hRef, null, hValue,
                        (frameCaller, hTarget, idProp_, hVal) ->
                            setReferentImpl(frameCaller, (RefHandle) hTarget, true, hVal));
                    }
                }
            }

        FutureHandle hFuture = (FutureHandle) hRef;

        assert hValue != null;

        if (hValue instanceof FutureHandle)
            {
            // this is only possible if this "handle" is a "dynamic ref" and the passed in
            // "handle" is a synthetic or dynamic one (see Frame.assignValue)

            FutureHandle that = (FutureHandle) hValue;
            if (that.m_future == null)
                {
                return frame.raiseException(xException.unassignedReference(frame));
                }

            if (hFuture.m_future == null)
                {
                hFuture.m_future = that.m_future;
                }
            else
                {
                // "connect" the futures
                hFuture.m_future.whenComplete((r, e) ->
                    {
                    if (e == null)
                        {
                        that.m_future.complete(r);
                        }
                    else
                        {
                        that.m_future.completeExceptionally(e);
                        }
                    });
                }
            return Op.R_NEXT;
            }

        CompletableFuture<ObjectHandle> cf = hFuture.m_future;
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

    /**
     * Helper method to assign a value of a completed future to a frame's register.
     *
     * @param frame    the current frame
     * @param cf       a future
     * @param iReturn  the register id to place the result to
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    public static int assignDone(Frame frame, CompletableFuture<ObjectHandle> cf, int iReturn)
        {
        assert cf.isDone();

        try
            {
            // services may replace "null" elements of a negative conditional return
            // with the DEFAULT values (see ServiceContext.sendResponse)
            ObjectHandle hValue = cf.get();
            return hValue == ObjectHandle.DEFAULT
                ? Op.R_NEXT
                : frame.assignValue(iReturn, hValue);
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


    // ----- ObjectHandle --------------------------------------------------------------------------

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
            // never called
            throw new IllegalStateException();
            }

        /**
         * @return true iff the future represented by this handle completed normally
         */
        public boolean isCompletedNormally()
            {
            return m_future != null && m_future.isDone() && !m_future.isCompletedExceptionally();
            }

        public int assign(ObjectHandle hValue, WrapperException ex)
            {
            CompletableFuture<ObjectHandle> cf = m_future;
            if (cf == null)
                {
                m_future = new CompletableFuture<>();
                }

            if (cf.isDone())
                {
                return Op.R_NEXT;
                }

            if (ex == null)
                {
                cf.complete(hValue);
                return Op.R_NEXT;
                }
            else
                {
                cf.completeExceptionally(ex);
                return Op.R_EXCEPTION;
                }
            }

        /**
         * Wait for the future completion and assign the specified register to the result.
         *
         * @param frame    the current frame
         * @param iReturn  the register id
         *
         * @return R_NEXT, R_CALL, R_EXCEPTION
         */
        public int waitAndAssign(Frame frame, int iReturn)
            {
            CompletableFuture<ObjectHandle> cf = m_future;
            if (cf == null)
                {
                // since this ref can only be changed by this service,
                // we can safely add a completable future now
                cf = m_future = new CompletableFuture<>();
                }
            else if (cf.isDone())
                {
                return assignDone(frame, cf, iReturn);
                }

            // add a notification and wait for the assignment/completion;
            // the service is responsible for timing out
            cf.whenComplete(
                (r, x) -> frame.f_fiber.onResponse());

            return frame.call(Utils.createWaitFrame(frame, cf, iReturn));
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
        return makeHandle(INSTANCE.getCanonicalClass(), future);
        }

    public static FutureHandle makeHandle(TypeComposition clz, CompletableFuture<ObjectHandle> future)
        {
        return new FutureHandle(clz, null, future);
        }
    }
