package org.xvm.runtime.template.annotations;


import java.util.concurrent.CompletableFuture;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Future native implementation.
 */
public class xFuture
        extends xVar {
    public static xFuture INSTANCE;
    public static TypeConstant TYPE;
    public static xEnum COMPLETION;

    public xFuture(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        ConstantPool  pool     = pool();
        ClassConstant idMixin  = (ClassConstant) f_struct.getIdentityConstant();
        Annotation    anno     = pool.ensureAnnotation(idMixin);
        TypeConstant  typeVar  = xVar.INSTANCE.getCanonicalType();

        TYPE       = pool.ensureAnnotatedTypeConstant(typeVar, anno);
        COMPLETION = (xEnum) f_container.getTemplate("annotations.Future.Completion");

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

        markNativeMethod("complete", null, VOID);
        markNativeMethod("completeExceptionally", null, VOID);

        markNativeProperty("assigned");
        markNativeProperty("completion");

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return TYPE;
    }

    @Override
    public TypeComposition ensureClass(Container container, TypeConstant typeActual) {
        if (!typeActual.isAnnotated()) {
            ConstantPool pool = typeActual.getConstantPool();

            // turn Future<T> into @Future Var<T>
            assert typeActual.getDefiningConstant().equals(pool.clzFuture());

            typeActual = pool.ensureFuture(typeActual.getParamType(0));
        }

        return super.ensureClass(container, typeActual);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (sPropName) {
        case "assigned":
            return frame.assignValue(iReturn, xBoolean.makeHandle(hThis.isAssigned()));

        case "failure":
            return frame.assignValue(iReturn, hThis.getException());

        case "completion": {
            EnumHandle hValue =
                hThis.isAssigned() ?
                    hThis.getFuture().isCompletedExceptionally() ?
                        COMPLETION.getEnumByName("Error")  :
                        COMPLETION.getEnumByName("Result") :
                    COMPLETION.getEnumByName("Pending");

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
                             ObjectHandle hArg, int iReturn) {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.getName()) {
        case "complete":
            return hThis.complete(hArg, null);

        case "completeExceptionally":
            return hThis.complete(null, (ExceptionHandle) hArg);

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
                             ObjectHandle[] ahArg, int iReturn) {
        FutureHandle hThis = (FutureHandle) hTarget;

        switch (method.getName()) {
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

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        FutureHandle hVar1 = (FutureHandle) hValue1;
        FutureHandle hVar2 = (FutureHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(hVar1.getFuture() == hVar2.getFuture()));
    }


    // ----- native method implementations ---------------------------------------------------------

    /**
     * Implementation of "Future! thenDo(function void () run)"
     */
    protected int invokeThenDo(Frame frame, FutureHandle hThis, FunctionHandle hRun, int iReturn) {
        CompletableFuture<ObjectHandle> cfThis = hThis.getFuture();

        if (cfThis.isDone()) {
            ObjectHandle[] ahR = extractResult(frame, cfThis);
            if (ahR[1] != xNullable.NULL) {
                // the future terminated exceptionally; re-throw it
                return frame.raiseException((ExceptionHandle) ahR[1]);
            }

            switch (hRun.call1(frame, null, Utils.OBJECTS_NONE, Op.A_IGNORE)) {
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
        } else {
            FutureHandle hThen = makeHandle(hThis.getComposition(), new CompletableFuture<>());

            cfThis.whenComplete((hR, ex) -> {
                if (ex == null) {
                    CompletableFuture<ObjectHandle> cfThen =
                            frame.f_context.postRequest(frame, hRun, Utils.OBJECTS_NONE, 0);

                    cfThen.whenComplete((hVoid, exThen) ->
                            hThen.complete(hR, Utils.translate(exThen)));
                } else {
                    hThen.complete(null, Utils.translate(ex));
                }
            });
            return frame.assignValue(iReturn, hThen);
        }
    }

    /**
     * Implementation of "Future! passTo(function void (Referent) consume)"
     */
    protected int invokePassTo(Frame frame, FutureHandle hThis, FunctionHandle hConsume, int iReturn) {
        CompletableFuture<ObjectHandle> cfThis = hThis.getFuture();

        if (cfThis.isDone()) {
            ObjectHandle[] ahR = extractResult(frame, cfThis);
            if (ahR[1] != xNullable.NULL) {
                // the future terminated exceptionally; re-throw it
                return frame.raiseException((ExceptionHandle) ahR[1]);
            }

            ahR[1] = null; // the consumer doesn't need it

            switch (hConsume.call1(frame, null, ahR, Op.A_IGNORE)) {
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
        } else {
            FutureHandle hPass = makeHandle(hThis.getComposition(), new CompletableFuture<>());

            cfThis.whenComplete((hR, ex) -> {
                if (ex == null) {
                    CompletableFuture<ObjectHandle> cfPass =
                            frame.f_context.postRequest(frame, hConsume, new ObjectHandle[] {hR}, 0);

                    cfPass.whenComplete((hVoid, exPass) ->
                            hPass.complete(hR, Utils.translate(exPass)));
                } else {
                    hPass.complete(null, Utils.translate(ex));
                }
            });
            return frame.assignValue(iReturn, hPass);
        }
    }

    /**
     * Implementation of "<NewType> Future!<NewType> transform(function NewType (Referent) convert)"
     */
    protected int invokeTransform(Frame frame, FutureHandle hThis, TypeHandle hNewType,
                                  FunctionHandle hConvert, int iReturn) {
        var             cfThis   = hThis.getFuture();
        TypeComposition clzTrans = ensureComposition(frame.f_context.f_container, hNewType.getDataType());

        if (cfThis.isDone()) {
            ObjectHandle[] ahR = extractResult(frame, cfThis);
            if (ahR[1] != xNullable.NULL) {
                // the future terminated exceptionally; re-throw it
                return frame.raiseException((ExceptionHandle) ahR[1]);
            }

            ahR[1] = null; // the converter doesn't need it

            switch (hConvert.call1(frame, null, ahR, Op.A_STACK)) {
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
        } else {
            FutureHandle hTrans = makeHandle(clzTrans, new CompletableFuture<>());

            cfThis.whenComplete((hR, ex) -> {
                if (ex == null) {
                    CompletableFuture<ObjectHandle> cfTrans =
                            frame.f_context.postRequest(frame, hConvert, new ObjectHandle[] {hR}, 1);

                    cfTrans.whenComplete((hNew, exTrans) ->
                            hTrans.complete(hNew, Utils.translate(exTrans)));
                } else {
                    hTrans.complete(null, Utils.translate(ex));
                }
            });
            return frame.assignValue(iReturn, hTrans);
        }
    }

    /**
     * Implementation of "Future! handle(function Referent (Exception) convert)"
     */
    protected int invokeHandle(Frame frame, FutureHandle hThis, FunctionHandle hConvert, int iReturn) {
        CompletableFuture<ObjectHandle> cfThis    = hThis.getFuture();
        TypeComposition                 clzHandle = hThis.getComposition();

        if (cfThis.isDone()) {
            if (!cfThis.isCompletedExceptionally()) {
                return frame.assignValue(iReturn, hThis);
            }

            ObjectHandle[] ahR = extractResult(frame, cfThis);
            assert ahR[0] == xNullable.NULL;

            ahR[0] = ahR[1]; // hException
            ahR[1] = null;

            switch (hConvert.call1(frame, null, ahR, Op.A_STACK)) {
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
        } else {
            FutureHandle hHandle = makeHandle(clzHandle, new CompletableFuture<>());

            cfThis.whenComplete((hR, ex) -> {
                if (ex == null) {
                    hHandle.complete(hR, null);
                } else {
                    ExceptionHandle hEx = Utils.translate(ex);
                    CompletableFuture<ObjectHandle> cfTrans =
                            frame.f_context.postRequest(frame, hConvert, new ObjectHandle[] {hEx}, 1);

                    cfTrans.whenComplete((hNew, exTrans) ->
                            hHandle.complete(hNew, Utils.translate(exTrans)));
                }
            });
            return frame.assignValue(iReturn, hHandle);
        }
    }

    /**
     * Implementation of "<NewType> Future!<NewType>
     *                      transformOrHandle(function NewType (Referent?, Exception?) convert)"
     */
    protected int invokeTransformOrHandle(Frame frame, FutureHandle hThis, TypeHandle hNewType,
                                          FunctionHandle hConvert, int iReturn) {
        var             cfThis   = hThis.getFuture();
        TypeComposition clzTrans = ensureComposition(frame.f_context.f_container, hNewType.getDataType());

        if (cfThis.isDone()) {
            ObjectHandle[] ahR = extractResult(frame, cfThis);

            switch (hConvert.call1(frame, null, ahR, Op.A_STACK)) {
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
        } else {
            FutureHandle hTrans = makeHandle(clzTrans, new CompletableFuture<>());

            cfThis.whenComplete((hR, ex) -> {
                CompletableFuture<ObjectHandle> cfTrans =
                        frame.f_context.postRequest(frame, hConvert, combineResult(hR, ex), 1);

                cfTrans.whenComplete((hNew, exTrans) ->
                        hTrans.complete(hNew, Utils.translate(exTrans)));
            });
            return frame.assignValue(iReturn, hTrans);
        }
    }

    /**
     * Implementation of "<OtherType, NewType> Future!<NewType> and(Future!<OtherType> other,
     *                      function NewType (Referent, OtherType) combine)"
     */
    protected int invokeAndFuture(Frame frame, FutureHandle hThis,
                                  TypeHandle hOtherType, TypeHandle hNewType,
                                  FutureHandle hThat, FunctionHandle hCombine, int iReturn) {
        CompletableFuture<ObjectHandle> cfThis = hThis.getFuture();
        CompletableFuture<ObjectHandle> cfThat = hThat.getFuture();
        TypeComposition                 clzAnd = ensureComposition(frame.f_context.f_container, hNewType.getDataType());

        if (cfThis.isDone() && cfThat.isDone()) {
            ObjectHandle[] ahRThis = extractResult(frame, cfThis);
            ObjectHandle[] ahRThat = extractResult(frame, cfThis);

            if (ahRThis[1] != xNullable.NULL) {
                return frame.raiseException((ExceptionHandle) ahRThis[1]);
            }
            if (ahRThat[1] != xNullable.NULL) {
                return frame.raiseException((ExceptionHandle) ahRThat[1]);
            }

            ObjectHandle[] ahArg = new ObjectHandle[] {ahRThis[0], ahRThat[0]};

            switch (hCombine.call1(frame, null, ahArg, Op.A_STACK)) {
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
        } else {
            FutureHandle hAnd = makeHandle(clzAnd, new CompletableFuture<>());

            CompletableFuture.allOf(cfThis, cfThat).whenComplete((_null, ex) -> {
                if (ex == null) {
                    ObjectHandle[] ahArg = new ObjectHandle[2];
                    try {
                        ahArg[0] = cfThis.get();
                        ahArg[1] = cfThat.get();
                    } catch (Throwable e) {
                        // must not happen
                        assert false;
                    }

                    CompletableFuture<ObjectHandle> cfAnd =
                            frame.f_context.postRequest(frame, hCombine, ahArg, 1);

                    cfAnd.whenComplete((hNew, exTrans) ->
                            hAnd.complete(hNew, Utils.translate(exTrans)));
                } else {
                    hAnd.complete(null, Utils.translate(ex));
                }
            });
            return frame.assignValue(iReturn, hAnd);
        }
    }

    /**
     * Implementation of "Future!<Referent> or(Future!<Referent> other)"
     */
    protected int invokeOrFuture(Frame frame, FutureHandle hThis, FutureHandle hThat, int iReturn) {
        CompletableFuture<ObjectHandle> cfThis = hThis.getFuture();
        CompletableFuture<ObjectHandle> cfThat = hThat.getFuture();

        if (cfThis.isDone()) {
            return frame.assignValue(iReturn, hThis);
        }

        if (cfThat.isDone()) {
            return frame.assignValue(iReturn, hThat);
        }

        CompletableFuture<Object> cfAny = CompletableFuture.anyOf(cfThis, cfThat);

        @SuppressWarnings("unchecked") // anyOf returns Object, but we know it's ObjectHandle
        CompletableFuture<ObjectHandle> cfResult = (CompletableFuture<ObjectHandle>) (CompletableFuture<?>) cfAny;
        return frame.assignValue(iReturn, makeHandle(hThis.getComposition(), cfResult));
    }

    /**
     * Implementation of "Future!<Referent> whenComplete(function void (Referent?, Exception?) notify)"
     */
    protected int invokeWhenComplete(Frame frame, FutureHandle hThis, FunctionHandle hNotify, int iReturn) {
        CompletableFuture<ObjectHandle> cfThis = hThis.getFuture();

        if (cfThis.isDone()) {
            ObjectHandle[] ahR = extractResult(frame, cfThis);

            switch (hNotify.call1(frame, null, ahR, Op.A_IGNORE)) {
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
        } else {
            FutureHandle hWhen = makeHandle(hThis.getComposition(), new CompletableFuture<>());

            cfThis.whenComplete((hR, ex) -> {
                CompletableFuture<ObjectHandle> cfWhen =
                        frame.f_context.postRequest(frame, hNotify, combineResult(hR, ex), 0);

                cfWhen.whenComplete((hVoid, exWhen) -> {
                    if (exWhen == null && ex == null) {
                        hWhen.complete(hR, null);
                    } else {
                        hWhen.complete(null, Utils.translate(exWhen == null ? ex : exWhen));
                    }
                });
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
    protected ObjectHandle[] extractResult(Frame frame, CompletableFuture<ObjectHandle> cf) {
        ObjectHandle[] ahR = new ObjectHandle[2];
        try {
            ahR[0] = cf.get();
            ahR[1] = xNullable.NULL;
        } catch (Throwable e) {
            ahR[0] = xNullable.NULL;
            ahR[1] = Utils.translate(e);
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
    protected ObjectHandle[] combineResult(ObjectHandle hResult, Throwable exception) {
        ObjectHandle[] ahArg = new ObjectHandle[2];
        ahArg[0] = hResult == null
                ? xNullable.NULL
                : hResult;
        ahArg[1] = exception == null
                ? xNullable.NULL
                : Utils.translate(exception);
        return ahArg;
    }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName) {
        return new FutureHandle(clazz.ensureAccess(Access.PUBLIC), sName,
                new CompletableFuture<>());
    }

    @Override
    protected int invokeNativeGetReferent(Frame frame, RefHandle hRef, int iReturn) {
        return invokeGetReferent(frame, hRef, iReturn);
    }

    @Override
    protected int invokeGetReferent(Frame frame, RefHandle hRef, int iReturn) {
        return getReferentImpl(frame, hRef, true, iReturn);
    }

    @Override
    protected int getReferentImpl(Frame frame, RefHandle hRef, boolean fNative, int iReturn) {
        if (hRef.isProperty()) {
            ObjectHandle hHolder = hRef.getReferentHolder();
            if (hHolder.isService()) {
                ServiceHandle hService = hHolder.getService();
                if (frame.f_context != hService.f_context) {
                    return hService.f_context.sendProperty01Request(frame, hRef, null, iReturn,
                        (frameCaller, hTarget, idProp_, iRet) ->
                            getReferentImpl(frameCaller, (RefHandle) hTarget, true, iRet));
                }
            }
        }

        return ((FutureHandle) hRef).waitAndAssign(frame, iReturn);
    }

    @Override
    protected int invokeSetReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue) {
        return setReferentImpl(frame, hTarget, true, hValue);
    }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hRef, boolean fNative, ObjectHandle hValue) {
        if (hRef.isProperty()) {
            ObjectHandle hHolder = hRef.getReferentHolder();
            if (hHolder.isService()) {
                ServiceHandle hService = hHolder.getService();
                if (frame.f_context != hService.f_context) {
                    return hService.f_context.sendProperty10Request(frame, hRef, null, hValue,
                        (frameCaller, hTarget, null_, hVal) ->
                            setReferentImpl(frameCaller, (RefHandle) hTarget, true, hVal));
                }
            }
        }

        CompletableFuture<ObjectHandle> cfThis = ((FutureHandle) hRef).getFuture();

        assert hValue != null;

        if (hValue instanceof FutureHandle hFuture) {
            // this is only possible if this "handle" is a "dynamic ref" and the passed in
            // "handle" is a synthetic or dynamic one (see Frame.assignValue)

            CompletableFuture<ObjectHandle> cfThat = hFuture.getFuture();

            // "connect" the futures
            cfThat.whenComplete((r, e) -> {
                if (e == null) {
                    cfThis.complete(r);
                } else {
                    cfThis.completeExceptionally(e);
                }
            });
            return Op.R_NEXT;
        }

        if (cfThis.isDone()) {
            return frame.raiseException("Future has already been set");
        }

        cfThis.complete(hValue);
        return Op.R_NEXT;
    }

    /**
     * @return a TypeComposition for a Future of a given referent type
     */
    private TypeComposition ensureComposition(Container container, TypeConstant typeReferent) {
        return ensureClass(container, typeReferent.getConstantPool().ensureFuture(typeReferent));
    }

    /**
     * Helper method to assign a result of the completed future.
     */
    public static int assignCompleted(Frame frame, CompletableFuture<ObjectHandle> cf, int iReturn) {
        try {
            // services may replace "null" elements of a negative conditional return
            // with the DEFAULT values (see ServiceContext.sendResponse)
            ObjectHandle hValue = cf.get();
            return hValue == ObjectHandle.DEFAULT
                ? Op.R_NEXT
                : frame.assignValue(iReturn, hValue);
        } catch (Throwable e) {
            return frame.raiseException(Utils.translate(e));
        }
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static FutureHandle makeHandle(CompletableFuture<ObjectHandle> future) {
        return makeHandle(INSTANCE.getCanonicalClass(), future);
    }

    public static FutureHandle makeHandle(TypeComposition clz, CompletableFuture<ObjectHandle> future) {
        return new FutureHandle(clz, null, future);
    }

    /**
     * Represents a future ObjectHandle.
     */
    public static class FutureHandle
            extends RefHandle {
        private final CompletableFuture<ObjectHandle> f_future;

        protected FutureHandle(TypeComposition clazz, String sName, CompletableFuture<ObjectHandle> future) {
            super(clazz, sName);

            f_future = future;
        }

        @Override
        public boolean isAssigned() {
            return getFuture().isDone();
        }

        @Override
        public ObjectHandle getReferent() {
            // can only be called by the debugger
            CompletableFuture<ObjectHandle> future = getFuture();
            if (future != null && future.isDone()) {
                try {
                    return future.get();
                } catch (Exception ignore) {}
            }

            return null;
        }

        public CompletableFuture<ObjectHandle> getFuture() {
            return f_future;
        }

        /**
         * @return an exception object if the future represented by this handle completed
         *         exceptionally; null otherwise
         */
        public ExceptionHandle getException() {
            CompletableFuture<ObjectHandle> future = getFuture();
            if (future.isCompletedExceptionally()) {
                try {
                    future.get();
                    throw new IllegalStateException(); // cannot happen
                } catch (Exception e) {
                    return Utils.translate(e);
                }
            }
            return null;
        }

        /**
         * Complete the underlying future.
         *
         * @param hValue      the value (null if hException is not null)
         * @param hException  the exception (null if hValue is not null)
         *
         * @return one of R_NEXT or R_EXCEPTION values
         */
        public int complete(ObjectHandle hValue, ExceptionHandle hException) {
            CompletableFuture<ObjectHandle> cf = getFuture();

            if (!cf.isDone()) {
                if (hException == null) {
                    assert hValue != null;
                    cf.complete(hValue);
                } else {
                    cf.completeExceptionally(hException.getException());
                }
            }
            return Op.R_NEXT;
        }

        /**
         * Wait for the future completion and assign the specified register to the result.
         *
         * @param frame    the current frame
         * @param iReturn  the register id
         *
         * @return R_NEXT, R_CALL, R_EXCEPTION
         */
        public int waitAndAssign(Frame frame, int iReturn) {
            // if the future is not assigned yet, the service is responsible for timing out
            return isAssigned()
                    ? assign(frame, iReturn)
                    : frame.call(frame.createWaitFrame(this, iReturn));
        }

        /**
         * Assign a value of a completed future to a frame's register.
         *
         * @param frame    the current frame
         * @param iReturn  the register id to place the result to
         *
         * @return one of R_NEXT, R_CALL or R_EXCEPTION
         */
        protected int assign(Frame frame, int iReturn) {
            CompletableFuture<ObjectHandle> cf = getFuture();
            assert cf.isDone();

            return assignCompleted(frame, cf, iReturn);
        }

        @Override
        public String toString() {
            return "(" + m_clazz + ") " + (
                    getFuture().isDone() ? "Completed: " + toSafeString(): "Not completed"
                    );
        }

        protected String toSafeString() {
            try {
                return String.valueOf(getFuture().get());
            } catch (Throwable e) {
                return Utils.translate(e).toString();
            }
        }
    }

    /**
     * Represents a future TupleHandle.
     */
    public static class FutureTupleHandle
            extends FutureHandle {
        private final ObjectHandle[] f_ahValue;

        public FutureTupleHandle(TypeComposition clazz, ObjectHandle[] ahValue) {
            super(clazz, null, null);

            f_ahValue = ahValue;
        }

        @Override
        public CompletableFuture<ObjectHandle> getFuture() {
            CompletableFuture<ObjectHandle> cfLast = null;
            for (ObjectHandle hValue : f_ahValue) {
                if (hValue instanceof FutureHandle hFuture) {
                    cfLast = hFuture.getFuture();
                    if (!cfLast.isDone()) {
                        break;
                    }
                }
            }
            return cfLast;
        }

        @Override
        protected int assign(Frame frame, int iReturn) {
            for (int i = 0, c = f_ahValue.length; i < c; i++) {
                ObjectHandle hValue = f_ahValue[i];

                if (hValue instanceof FutureHandle hFuture) {
                    assert hFuture.isAssigned();

                    try {
                        f_ahValue[i] = hFuture.getFuture().get();
                    } catch (Throwable e) {
                        return frame.raiseException(Utils.translate(e));
                    }
                }
            }

            TypeComposition clzTuple = getType().getParamType(0).ensureClass(frame);
            return frame.assignValue(iReturn, xTuple.makeHandle(clzTuple, f_ahValue));
        }
    }
}