package org.xvm.runtime;


import java.sql.Timestamp;

import java.util.List;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import java.util.function.Predicate;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext.Synchronicity;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.reflect.xClass.ClassHandle;
import org.xvm.runtime.template.reflect.xModule;
import org.xvm.runtime.template.reflect.xPackage;
import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;


/**
 * Various helpers.
 */
public abstract class Utils
    {
    /**
     * Ensure that the specified array of arguments is of the specified size.
     *
     * @param ahArg  the array of arguments
     * @param cVars  the desired array size
     *
     * @return the array of no less than the desired size containing all the arguments
     */
    public static ObjectHandle[] ensureSize(ObjectHandle[] ahArg, int cVars)
        {
        int cArgs = ahArg.length;
        if (cArgs < cVars)
            {
            ObjectHandle[] ahVar = new ObjectHandle[cVars];
            System.arraycopy(ahArg, 0, ahVar, 0, cArgs);
            return ahVar;
            }

        return ahArg;
        }

    /**
     * Create a FullyBoundHandle representing a finalizer of the specified constructor.
     *
     * @param frame        the current frame
     * @param constructor  the constructor
     * @param ahArg        the arguments to bind
     *
     * @return a FullyBoundHandle representing the finalizer or null if there is no finalizer
     */
    public static FullyBoundHandle makeFinalizer(Frame frame, MethodStructure constructor, ObjectHandle[] ahArg)
        {
        MethodStructure methodFinally = constructor.getConstructFinally();

        return methodFinally == null
            ? null
            : xRTFunction.makeInternalHandle(frame, methodFinally).bindArguments(ahArg);
        }

    /**
     * Helper method for the "toString()" method invocation that pushes the result onto the frame's
     * stack. Any exception thrown by "toString()" will be re-thrown to the caller.
     *
     * @param frame   the current frame
     * @param hValue  the value to get a string value for
     *
     * @return one of R_EXCEPTION, R_NEXT or R_CALL values
     */
    public static int callToString(Frame frame, ObjectHandle hValue)
        {
        TypeComposition clz   = hValue.getComposition();
        CallChain       chain = clz.getMethodCallChain(clz.getConstantPool().sigToString());
        return chain.isNative()
            ? hValue.getTemplate().buildStringValue(frame, hValue, Op.A_STACK)
            : chain.invoke(frame, hValue, OBJECTS_NONE, Op.A_STACK);
        }
    private static ClassTemplate     ANNOTATION_TEMPLATE_TEMPLATE;
    private static ClassTemplate     ARGUMENT_TEMPLATE;

    /**
     * An adapter method that assigns the result of a natural execution to a calling frame
     * that expects a conditional return.
     *
     * @param frame     the frame that expects a conditional return value
     * @param iResult   the result of the previous execution
     * @param aiReturn  the return indexes for the conditional return
     *
     * @return one of R_EXCEPTION, R_NEXT or R_CALL values
     */
    public static int assignConditionalResult(Frame frame, int iResult, int[] aiReturn)
        {
        switch (iResult)
            {
            case Op.R_NEXT:
                return frame.assignValues(aiReturn, xBoolean.TRUE, frame.popStack());

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    frameCaller.assignValues(aiReturn, xBoolean.TRUE, frameCaller.popStack()));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * A helper method for native code that needs to assign EnumHandle values retrieved
     * via {@link xEnum#getEnumByName} or {@link xEnum#getEnumByOrdinal}.
     *
     * @param frame  the current frame
     * @param hEnum  the Enum handle
     *
     * @return the initialized (public) enum handle or a deferred handle
     */
    public static ObjectHandle ensureInitializedEnum(Frame frame, EnumHandle hEnum)
        {
        if (hEnum.isStruct())
            {
            // turn the Enum struct into a "public" value
            IdentityConstant idValue = (IdentityConstant) hEnum.getType().getDefiningConstant();
            return frame.getConstHandle(
                    frame.poolContext().ensureSingletonConstConstant(idValue));
            }
        return hEnum;
        }

    /**
     * Call "getResource" method on the specified injector instance.
     *
     * @return the handle representing the resource (can be deferred)
     */
    public static ObjectHandle callGetResource(Frame frame, ObjectHandle hInjector,
                                               TypeConstant type, String sName)
        {
        int iResult;
        if (Op.isDeferred(hInjector))
            {
            iResult = hInjector.proceed(frame, frameCaller ->
                {
                ObjectHandle hResource = Utils.callGetResource(frameCaller,
                        frameCaller.popStack(), type, sName);
                return hResource instanceof DeferredCallHandle hDeferred
                        ? hDeferred.proceed(frameCaller, null)
                        : frameCaller.pushStack(hResource);
                });
            }
        else
            {
            TypeComposition clazz = hInjector.getComposition();
            CallChain       chain = clazz.getMethodCallChain(SIG_GET_RESOURCE);

            if (chain.isEmpty())
                {
                return new DeferredCallHandle(xException.makeHandle(frame,
                    "Missing method \"" + SIG_GET_RESOURCE.getValueString() +
                    "\" on " + hInjector.getType().getValueString()));
                }

            ObjectHandle[] ahArg = new ObjectHandle[chain.getMaxVars()];
            ahArg[0] = type.ensureTypeHandle(frame.f_context.f_container);
            ahArg[1] = xString.makeHandle(sName);

            iResult = chain.invoke(frame, hInjector, ahArg, Op.A_STACK);
            }

        switch (iResult)
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                return new DeferredCallHandle(frame.m_frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.clearException());

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * A helper method for native code that needs to assign EnumHandle values retrieved
     * via {@link xEnum#getEnumByName} or {@link xEnum#getEnumByOrdinal}.
     *
     * @param frame    the current frame
     * @param hEnum    the Enum handle
     * @param iReturn  the register to assign the value into
     *
     * @return one of R_EXCEPTION, R_NEXT or R_CALL values
     */
    public static int assignInitializedEnum(Frame frame, EnumHandle hEnum, int iReturn)
        {
        return frame.assignDeferredValue(iReturn, ensureInitializedEnum(frame, hEnum));
        }

    /**
     * Log a given message for a given frame to System.out.
     */
    public static void log(Frame frame, String sMsg)
        {
        if (sMsg.charAt(0) == '\n')
            {
            System.out.println();
            sMsg = sMsg.substring(1);
            }

        ServiceContext context;
        long           lFiberId;

        if (frame == null)
            {
            context  = ServiceContext.getCurrentContext();
            lFiberId = -1;
            }
        else
            {
            context  = frame.f_context;
            lFiberId = frame.f_fiber.getId();
            }

        System.out.println(new Timestamp(context.f_container.currentTimeMillis())
            + " " + context + ", fiber " + lFiberId + ": " + sMsg);
        }


    // ----- "local property or DeferredCallHandle as an argument" support -------------------------

    public static class GetArguments
                implements Frame.Continuation
        {
        public GetArguments(ObjectHandle[] ahArg, Frame.Continuation continuation)
            {
            this.ahArg = ahArg;
            this.continuation = continuation;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            // replace a property handle with the value
            ahArg[index] = frameCaller.popStack();
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < ahArg.length)
                {
                ObjectHandle hArg = ahArg[index];
                if (hArg == null)
                    {
                    // nulls can only be at the tail of the array
                    break;
                    }

                if (hArg instanceof DeferredCallHandle)
                    {
                    return hArg.proceed(frameCaller, this);
                    }
                }
            return continuation.proceed(frameCaller);
            }

        private final ObjectHandle[] ahArg;
        private final Frame.Continuation continuation;
        private int index = -1;
        }

    public static class AssignValues
            implements Frame.Continuation
        {
        public AssignValues(int[] aiReturn, ObjectHandle[] ahValue)
            {
            this.aiReturn  = aiReturn;
            this.ahValue   = ahValue;
            }

        public int proceed(Frame frameCaller)
            {
            while (++index < aiReturn.length)
                {
                ObjectHandle hValue = ahValue[index];
                if (hValue instanceof DeferredCallHandle hDeferred)
                    {
                    hDeferred.addContinuation(this::updateDeferredValue);
                    return hDeferred.proceed(frameCaller, this);
                    }

                switch (frameCaller.assignValue(aiReturn[index], ahValue[index]))
                    {
                    case Op.R_NEXT:
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            return Op.R_NEXT;
            }

        protected int updateDeferredValue(Frame frameCaller)
            {
            ahValue[index--] = frameCaller.popStack();
            return Op.R_NEXT;
            }

        private final int[] aiReturn;
        private final ObjectHandle[] ahValue;

        private int index = -1;
        }

    public static class ReturnValues
            implements Frame.Continuation
        {
        public ReturnValues(int[] aiReturn, ObjectHandle[] ahValue, boolean[] afDynamic)
            {
            this.aiReturn  = aiReturn;
            this.ahValue   = ahValue;
            this.afDynamic = afDynamic;
            }

        public int proceed(Frame frameCaller)
            {
            while (++index < aiReturn.length)
                {
                ObjectHandle hValue = ahValue[index];
                if (hValue instanceof DeferredCallHandle hDeferred)
                    {
                    hDeferred.addContinuation(this::updateDeferredValue);
                    return hDeferred.proceed(frameCaller, this);
                    }

                switch (frameCaller.returnValue(aiReturn[index], ahValue[index],
                        afDynamic != null && afDynamic[index]))
                    {
                    case Op.R_RETURN:
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_RETURN_EXCEPTION:
                        return Op.R_RETURN_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            return Op.R_RETURN;
            }

        protected int updateDeferredValue(Frame frameCaller)
            {
            ahValue[index--] = frameCaller.popStack();
            return Op.R_NEXT;
            }

        private final int[] aiReturn;
        private final ObjectHandle[] ahValue;
        private final boolean[] afDynamic;

        private int index = -1;
        }


    // ----- comparison support --------------------------------------------------------------------

    /**
     * Perform sequential equality check on two values of specified types.
     */
    public static int callEqualsSequence(Frame frame, TypeConstant type1, TypeConstant type2,
                                         ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }

        switch (type1.callEquals(frame, hValue1, hValue2, Op.A_STACK))
            {
            case Op.R_NEXT:
                return completeEquals(frame, type2, hValue1, hValue2, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeEquals(frameCaller, type2, hValue1, hValue2, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Completion of the callEquals implementation.
     */
    protected static int completeEquals(Frame frame, TypeConstant type2,
                                        ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle hResult = frame.popStack();
        return hResult == xBoolean.FALSE
            ? frame.assignValue(iReturn, hResult)
            : type2.callEquals(frame, hValue1, hValue2, iReturn);
        }

    /**
     * Perform sequential comparison check on two values of specified types.
     */
    public static int callCompareSequence(Frame frame, TypeConstant type1, TypeConstant type2,
                                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xOrdered.EQUAL);
            }

        switch (type1.callCompare(frame, hValue1, hValue2, Op.A_STACK))
            {
            case Op.R_NEXT:
                return completeCompare(frame, type2, hValue1, hValue2, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeCompare(frameCaller, type2, hValue1, hValue2, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Completion of the callCompare implementation.
     */
    protected static int completeCompare(Frame frame, TypeConstant type2,
                                        ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle hResult = frame.popStack();
        return hResult != xOrdered.EQUAL
            ? frame.assignValue(iReturn, hResult)
            : type2.callCompare(frame, hValue1, hValue2, iReturn);
        }


    // ----- various run-time support --------------------------------------------------------------

    /**
     * Translate a Throwable thrown by {@link CompletableFuture#get} to an ExceptionHandle.
     */
    public static ExceptionHandle translate(Throwable e)
        {
        if (e == null)
            {
            return null;
            }
        if (e instanceof ExceptionHandle.WrapperException we)
            {
            return we.getExceptionHandle();
            }
        if (e instanceof ExecutionException ||
            e instanceof CompletionException)
            {
            return translate(e.getCause());
            }
        if (e instanceof CancellationException)
            {
            return xException.makeHandle(null, "cancelled");
            }
        if (e instanceof InterruptedException)
            {
            return xException.makeHandle(null, "interrupted");
            }

        return xException.makeHandle(null, "Unexpected native exception: " + e.getMessage());
        }

    /**
     * Ensure that all SingletonConstants in the specified list are initialized and proceed
     * with the specified continuation.
     *
     * @param frame           the caller's frame
     * @param listSingletons  the list of singleton constants
     * @param continuation    the continuation to proceed with after initialization completes
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public static int initConstants(Frame frame, List<SingletonConstant> listSingletons,
                                    Frame.Continuation continuation)
        {
        boolean fMainContext = false;

        for (SingletonConstant constSingleton : listSingletons)
            {
            ObjectHandle hValue = constSingleton.getHandle();
            if (hValue != null)
                {
                continue;
                }

            ServiceContext ctxCurr = frame.f_context;
            if (!fMainContext)
                {
                ServiceContext ctxMain = ctxCurr.getMainContext();

                if (ctxCurr == ctxMain)
                    {
                    fMainContext = true;
                    }
                else
                    {
                    assert continuation != null;

                    // we have at least one non-initialized singleton;
                    // call the main service to initialize them all
                    CompletableFuture<ObjectHandle> cfResult =
                        ctxMain.sendConstantRequest(frame, listSingletons);

                    if (ctxCurr.getSynchronicity() == Synchronicity.Concurrent)
                        {
                        // create a pseudo frame to deal with the wait, but don't allow any other fiber
                        // to interleave until a response comes back (as in "forbidden" reentrancy)
                        ctxCurr.setSynchronicity(frame.f_fiber, Synchronicity.Critical);
                        cfResult.whenComplete((r, e) ->
                            ctxCurr.setSynchronicity(null, Synchronicity.Concurrent));
                        }

                    return frame.wait(cfResult, Op.A_IGNORE, continuation);
                    }
                }

            // we are on the main context and can actually perform the initialization
            if (!constSingleton.markInitializing())
                {
                // this can only happen if we are called recursively; the value is INITIALIZING
                return continuation.proceed(frame);
                }

            Container containerThis = ctxCurr.f_container;
            Container containerOrig = containerThis.getOriginContainer(constSingleton);

            int iResult;
            if (containerOrig == containerThis)
                {
                iResult = constructSingletonHandle(frame, constSingleton);
                }
            else
                {
                Op opConstruct = new Op()
                    {
                    public int process(Frame frame, int iPC)
                        {
                        switch (constructSingletonHandle(frame, constSingleton))
                            {
                            case Op.R_NEXT:
                                return frame.assignValue(0, frame.popStack());

                            case Op.R_CALL:
                                Frame.Continuation stepNext = frameCaller ->
                                    frameCaller.assignValue(0, frameCaller.popStack());
                                frame.m_frameNext.addContinuation(stepNext);
                                return Op.R_CALL;

                            case Op.R_EXCEPTION:
                                return Op.R_EXCEPTION;

                            default:
                                throw new IllegalStateException();
                            }
                        }

                    public String toString()
                        {
                        return "ConstructSingleton: " + constSingleton.getClassConstant();
                        }
                    };

                iResult = containerOrig.getServiceContext().sendOp1Request(frame, opConstruct, Op.A_STACK);
                }

            switch (iResult)
                {
                case Op.R_NEXT:
                    constSingleton.setHandle(frame.popStack());
                    break; // next constant

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        {
                        constSingleton.setHandle(frameCaller.popStack());
                        return initConstants(frameCaller, listSingletons, continuation);
                        });
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        return continuation.proceed(frame);
        }

    private static int constructSingletonHandle(Frame frame, SingletonConstant constSingleton)
        {
        IdentityConstant constValue = constSingleton.getClassConstant();

        switch (constValue.getFormat())
            {
            case Module:
                return xModule.INSTANCE.createConstHandle(frame, constValue);

            case Package:
                return xPackage.INSTANCE.createConstHandle(frame, constValue);

            case Property:
                return callPropertyInitializer(frame, (PropertyConstant) constValue);

            case Class:
                {
                ClassConstant idClz = (ClassConstant) constValue;
                ClassStructure clz  = (ClassStructure) idClz.getComponent();

                assert clz.isSingleton();

                Container     container = frame.f_context.f_container;
                ClassTemplate template  = container.getTemplate(idClz);
                if (template.getStructure().getFormat() == Format.ENUMVALUE)
                    {
                    // this can happen if the constant's handle was not initialized or
                    // assigned on a different constant pool
                    return template.createConstHandle(frame, constSingleton);
                    }

                // the class must have a no-params constructor to call
                MethodStructure constructor = clz.findConstructor(TypeConstant.NO_TYPES);
                return template.construct(frame, constructor,
                        template.getCanonicalClass(container), null, OBJECTS_NONE, Op.A_STACK);
                }

            default:
                throw new IllegalStateException("unexpected defining constant: " + constValue);
            }
        }

    /**
     * Call the static property initializer.
     *
     * @param frame   the caller's frame
     * @param idProp  the property id
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    private static int callPropertyInitializer(Frame frame, PropertyConstant idProp)
        {
        PropertyStructure prop = (PropertyStructure) idProp.getComponent();

        if (prop.isInjected())
            {
            TypeConstant typeRef = frame.poolContext().ensureAnnotatedTypeConstant(
                    idProp.getRefType(null), prop.getRefAnnotations());

            TypeComposition clzRef  = typeRef.ensureClass(frame);
            VarSupport      support = (VarSupport) clzRef.getSupport();

            switch (support.introduceRef(frame, clzRef, idProp.getName(), Op.A_STACK))
                {
                case Op.R_NEXT:
                    return support.getReferent(frame, (RefHandle) frame.popStack(), Op.A_STACK);

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        support.getReferent(frameCaller, (RefHandle) frameCaller.popStack(), Op.A_STACK));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        Constant constVal = prop.getInitialValue();
        if (constVal == null)
            {
            MethodStructure methodInit = prop.getInitializer();
            if (methodInit == null)
                {
                // should not happen; must be caught by the compiler
                return frame.raiseException("Initializer is missing for " +
                        prop.getIdentityConstant().getPathString());
                }

            ObjectHandle[] ahVar = ensureSize(OBJECTS_NONE, methodInit.getMaxVars());
            return frame.call1(methodInit, null, ahVar, Op.A_STACK);
            }

        return frame.pushDeferredValue(frame.getConstHandle(constVal));
        }

    /**
     * An abstract base for in-place operation support.
     */
    public abstract static class AbstractInPlace
            implements Frame.Continuation
        {
        protected ObjectHandle hValueOld;
        protected ObjectHandle hValueNew;
        protected int ixStep = -1;

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            switch (ixStep)
                {
                case 0: // get
                    hValueOld = frameCaller.popStack();
                    break;

                case 1: // action
                    hValueNew = frameCaller.popStack();
                    break;

                case 2: // set
                    break;

                default:
                    throw new IllegalStateException();
                }
            }

        public abstract int doNext(Frame frameCaller);
        }

    /**
     * In place property unary operation support.
     */
    public static class InPlacePropertyUnary
            extends AbstractInPlace
        {
        private final UnaryAction action;
        private final ClassTemplate template;
        private final ObjectHandle hTarget;
        private final PropertyConstant idProp;
        private final boolean fPost;
        private final int iReturn;

        protected InPlacePropertyUnary(UnaryAction action, ClassTemplate template,
                                       ObjectHandle hTarget, PropertyConstant idProp, boolean fPost,
                                       int iReturn)
            {
            this.action = action;
            this.template = template;
            this.hTarget = hTarget;
            this.idProp = idProp;
            this.fPost = fPost;
            this.iReturn = iReturn;
            }

        public int doNext(Frame frameCaller)
            {
            while (true)
                {
                int iResult;
                switch (++ixStep)
                    {
                    case 0:
                        iResult = template.
                            getPropertyValue(frameCaller, hTarget, idProp, Op.A_STACK);
                        break;

                    case 1:
                        iResult = action.invoke(frameCaller, hValueOld);
                        break;

                    case 2:
                        iResult = template.
                            setPropertyValue(frameCaller, hTarget, idProp, hValueNew);
                        break;

                    case 3:
                        return frameCaller.assignValue(iReturn, fPost ? hValueOld : hValueNew);

                    default:
                        throw new IllegalStateException();
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }
        }

    /**
     * In place property binary operation support.
     */
    public static class InPlacePropertyBinary
            extends AbstractInPlace
        {
        private final BinaryAction action;
        private final ClassTemplate template;
        private final ObjectHandle hTarget;
        private final PropertyConstant idProp;
        private final ObjectHandle hArg;

        protected InPlacePropertyBinary(BinaryAction action, ClassTemplate template,
                                        ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hArg)
            {
            this.action = action;
            this.template = template;
            this.hTarget = hTarget;
            this.idProp = idProp;
            this.hArg = hArg;
            }

        public int doNext(Frame frameCaller)
            {
            while (true)
                {
                int iResult;
                switch (++ixStep)
                    {
                    case 0:
                        iResult = template.getPropertyValue(frameCaller, hTarget, idProp, Op.A_STACK);
                        break;

                    case 1:
                        iResult = action.invoke(frameCaller, hValueOld, hArg);
                        break;

                    case 2:
                        return template.setPropertyValue(frameCaller, hTarget, idProp, hValueNew);

                    default:
                        throw new IllegalStateException();
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }
        }

    /**
     * In place Var unary operation support.
     */
    public static class InPlaceVarUnary
            extends AbstractInPlace
        {
        private final UnaryAction action;
        private final RefHandle hTarget;
        private final boolean fPost;
        private final int iReturn;

        /**
         * @param action   the action
         * @param hTarget  the target Var
         * @param fPost    if true, the operation is performed after the current value is returned
 *                 (e.g. i--); otherwise - before that (e.g. --i)
         * @param iReturn  the register to place the result of the operation into
         */
        public InPlaceVarUnary(UnaryAction action, RefHandle hTarget, boolean fPost, int iReturn)
            {
            this.action = action;
            this.hTarget = hTarget;
            this.fPost = fPost;
            this.iReturn = iReturn;
            }

        public int doNext(Frame frameCaller)
            {
            while (true)
                {
                int nStep = ++ixStep;

                int iResult;
                switch (nStep)
                    {
                    case 0:
                        iResult = hTarget.getVarSupport().getReferent(frameCaller, hTarget, Op.A_STACK);
                        break;

                    case 1:
                        iResult = action.invoke(frameCaller, hValueOld);
                        break;

                    case 2:
                        iResult = hTarget.getVarSupport().setReferent(frameCaller, hTarget, hValueNew);
                        break;

                    case 3:
                        return frameCaller.assignValue(iReturn, fPost ? hValueOld : hValueNew);

                    default:
                        throw new IllegalStateException();
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }
        }

    /**
     * In place Var binary operation support.
     */
    public static class InPlaceVarBinary
            extends AbstractInPlace
        {
        private final BinaryAction action;
        private final RefHandle hTarget;
        private final ObjectHandle hArg;

        public InPlaceVarBinary(BinaryAction action, RefHandle hTarget, ObjectHandle hArg)
            {
            this.action = action;
            this.hTarget = hTarget;
            this.hArg = hArg;
            }

        public int doNext(Frame frameCaller)
            {
            while (true)
                {
                int iResult;
                switch (++ixStep)
                    {
                    case 0:
                        iResult = hTarget.getVarSupport().getReferent(frameCaller, hTarget, Op.A_STACK);
                        break;

                    case 1:
                        iResult = action.invoke(frameCaller, hValueOld, hArg);
                        break;

                    case 2:
                        return hTarget.getVarSupport().setReferent(frameCaller, hTarget, hValueNew);

                    default:
                        throw new IllegalStateException();
                    }

                switch (iResult)
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }
        }

    // the lambda for unary actions
    @FunctionalInterface
    public interface UnaryAction
        {
        // invoke and place the result into A_LOCAL
        int invoke(Frame frame, ObjectHandle hTarget);

        // ----- action constants ------------------------------------------------------------------

        UnaryAction INC = (frameCaller, hValue) ->
            hValue.getOpSupport().invokeNext(frameCaller, hValue, Op.A_STACK);

        UnaryAction DEC = (frameCaller, hValue) ->
            hValue.getOpSupport().invokePrev(frameCaller, hValue, Op.A_STACK);
        }

    // the lambda for binary actions
    @FunctionalInterface
    public interface BinaryAction
        {
        // invoke and place the result into A_LOCAL
        int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg);

        // ----- action constants ------------------------------------------------------------------

        BinaryAction ADD = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeAdd(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction SUB = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeSub(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction MUL = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeMul(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction DIV = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeDiv(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction MOD = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeMod(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction SHL = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeShl(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction SHR = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeShr(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction USHR = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeShrAll(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction AND = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeAnd(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction OR = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeOr(frameCaller, hValue, hArg, Op.A_STACK);

        BinaryAction XOR = (frameCaller, hValue, hArg) ->
            hValue.getOpSupport().invokeXor(frameCaller, hValue, hArg, Op.A_STACK);
        }

    /**
     * Helper class for collecting the annotations.
     */
    public static class CreateAnnos
            implements Frame.Continuation
        {
        public CreateAnnos(Annotation[] aAnno, int iReturn)
            {
            this.aAnno   = aAnno;
            this.ahAnno  = new ObjectHandle[aAnno.length];
            this.iReturn = iReturn;
            stageNext    = Stage.Mixin;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            switch (stageNext)
                {
                case Mixin:
                    assert iAnno >= 0;
                    ahAnno[iAnno] = frameCaller.popStack();
                    break;

                case ArgumentArray:
                    hMixin = frameCaller.popStack();
                    break;

                case Argument:
                    hValue = frameCaller.popStack();
                    break;

                case Value:
                    assert iArg >= 0;
                    ahAnnoArg[iArg] = frameCaller.popStack();
                    break;

                default:
                    throw new IllegalStateException();
                }

            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            NextStep:
            while (true)
                {
                switch (stageNext)
                    {
                    case Mixin:
                        {
                        // start working on a next Annotation
                        assert hMixin    == null;
                        assert ahAnnoArg == null;

                        if (++iAnno == aAnno.length)
                            {
                            // we are done
                            break NextStep;
                            }

                        Annotation    anno   = aAnno[iAnno];
                        ClassConstant idAnno = (ClassConstant) anno.getAnnotationClass();

                        hMixin    = frameCaller.getConstHandle(idAnno);
                        stageNext = Stage.ArgumentArray;

                        if (Op.isDeferred(hMixin))
                            {
                            return hMixin.proceed(frameCaller, this);
                            }
                        // fall through;
                        }

                    case ArgumentArray:
                        {
                        assert hMixin    != null;
                        assert ahAnnoArg == null;

                        // start working on the Annotation arguments
                        Annotation anno  = aAnno[iAnno];
                        int        cArgs = anno.getParams().length;

                        ClassConstant  idAnno      = (ClassConstant) anno.getAnnotationClass();
                        ClassStructure structMixin = (ClassStructure) idAnno.getComponent();

                        // should be one and only one constructor
                        constructMixin = structMixin.findMethod("construct", m -> true);
                        if (constructMixin == null || cArgs > constructMixin.getParamCount())
                            {
                            return frameCaller.raiseException("Unknown annotation: " + idAnno
                                + " with " + cArgs + " parameters");
                            }

                        int cParamsAll      = constructMixin.getParamCount();
                        int cParamsRequired = constructMixin.getRequiredParamCount();
                        if (cParamsRequired > cArgs)
                            {
                            return frameCaller.raiseException("Missing arguments for: " + idAnno
                                + "; required=" + cParamsRequired + "; actual=" + cArgs);
                            }
                        if (cArgs > cParamsAll)
                            {
                            return frameCaller.raiseException("Unknown arguments for: " + idAnno
                                + "; required=" + cParamsAll + "; actual=" + cArgs);
                            }

                        if (cParamsAll == 0)
                            {
                            ahAnnoArg = OBJECTS_NONE;
                            stageNext = Stage.Annotation;
                            break;
                            }

                        ahAnnoArg = new ObjectHandle[cParamsAll];
                        iArg      = -1;
                        stageNext = Stage.Value;
                        // break through
                        }

                    case Value:
                        {
                        assert ahAnnoArg != null;

                        if (++iArg == constructMixin.getParamCount())
                            {
                            // all arguments are collected; construct the annotation
                            stageNext = Stage.Annotation;
                            continue; // NextStep;
                            }

                        Constant[] aconstArg = aAnno[iAnno].getParams();
                        Constant   constArg  = iArg < aconstArg.length
                                ? aconstArg[iArg]
                                : null;

                        if (constArg == null ||
                                constArg instanceof RegisterConstant constReg &&
                                constReg.getRegisterIndex() == Op.A_DEFAULT)
                            {
                            constArg = constructMixin.getParam(iArg).getDefaultValue();
                            }

                        hValue    = frameCaller.getConstHandle(constArg);
                        stageNext = Stage.Argument;

                        if (Op.isDeferred(hValue))
                            {
                            return hValue.proceed(frameCaller, this);
                            }
                        // fall through
                        }

                    case Argument:
                        {
                        assert ahAnnoArg      != null;
                        assert constructMixin != null;
                        assert hValue         != null;

                        // constructing Argument<Referent extends immutable Const>
                        //                  (Referent value, String? name = Null)
                        Parameter    param  = constructMixin.getParam(iArg);
                        TypeConstant type   = param.getType().
                            resolveGenerics(frameCaller.poolContext(),
                                frameCaller.getGenericsResolver(false));

                        int iResult = constructArgument(frameCaller, type, hValue, param.getName());
                        if (iResult == Op.R_CALL)
                            {
                            frameCaller.m_frameNext.addContinuation(this);

                            stageNext = CreateAnnos.Stage.Value;
                            }
                        else
                            {
                            assert iResult == Op.R_EXCEPTION;
                            }
                        return iResult;
                        }

                    case Annotation:
                        {
                        assert hMixin    != null;
                        assert ahAnnoArg != null;

                        int iResult = constructAnnotation(frameCaller, (ClassHandle) hMixin, ahAnnoArg, Op.A_STACK);
                        if (iResult == Op.R_CALL)
                            {
                            frameCaller.m_frameNext.addContinuation(this);

                            // when constructed, proceed() will insert the Annotation instance
                            // at iAnno index and continue to the next one
                            hMixin         = null;
                            ahAnnoArg      = null;
                            constructMixin = null;
                            stageNext      = Stage.Mixin;
                            }
                        else
                            {
                            assert iResult == Op.R_EXCEPTION;
                            }
                        return iResult;
                        }
                    }
                }
            return frameCaller.assignValue(iReturn,
                    makeAnnoArrayHandle(frameCaller.f_context.f_container, ahAnno));
            }

        enum Stage {Mixin, ArgumentArray, Value, Argument, Annotation}
        private Stage stageNext;

        private final Annotation[]   aAnno;
        private final int            iReturn;
        private final ObjectHandle[] ahAnno;

        private int             iAnno = -1;
        private ObjectHandle    hMixin;
        private MethodStructure constructMixin;
        private ObjectHandle[]  ahAnnoArg;
        private ObjectHandle    hValue;
        private int             iArg  = -1;
        }

    /**
     * @return a constant Annotation array handle
     */
    public static ArrayHandle makeAnnoArrayHandle(Container container, ObjectHandle[] ahAnno)
        {
        return xArray.makeArrayHandle(
                container.ensureClassComposition(ANNOTATION_ARRAY_TYPE, xArray.INSTANCE),
                ahAnno.length, ahAnno, Mutability.Constant);
        }

    /**
     * @return a constant Argument array handle
     */
    public static ArrayHandle makeArgumentArrayHandle(Container container, ObjectHandle[] ahArg)
        {
        return xArray.makeArrayHandle(
                container.ensureClassComposition(ARGUMENT_ARRAY_TYPE, xArray.INSTANCE),
                ahArg.length, ahArg, Mutability.Constant);
        }

    /**
     * Helper class for constructing Parameters.
     */
    public static class CreateParameters
                implements Frame.Continuation
        {
        public CreateParameters(Parameter[] aParam, ObjectHandle[] ahParam,
                                Frame.Continuation continuation)
            {
            this.aParam       = aParam;
            this.ahParam      = ahParam;
            this.continuation = continuation;
            typeRTParameter   = RT_PARAMETER_TEMPLATE.getClassConstant().getType();
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            // replace a property handle with the value
            ahParam[index] = frameCaller.popStack();
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < aParam.length)
                {
                Parameter    param        = aParam[index];
                TypeConstant type         = param.getType();
                String       sName        = param.getName();
                boolean      fFormal      = param.isTypeParameter();
                Constant     constDefault = param.getDefaultValue();

                ConstantPool    pool      = frameCaller.poolContext();
                ClassTemplate   template  = RT_PARAMETER_TEMPLATE;
                TypeConstant    typeParam = pool.ensureParameterizedTypeConstant(typeRTParameter, type);
                TypeComposition clzParam  = frameCaller.f_context.f_container.ensureClassComposition(typeParam, template);

                MethodStructure  construct = RT_PARAMETER_CONSTRUCT;
                ObjectHandle[]   ahArg     = new ObjectHandle[construct.getMaxVars()];
                ahArg[0] = xInt64.makeHandle(index); // ordinal
                ahArg[1] = sName == null ? xNullable.NULL : xString.makeHandle(sName);
                ahArg[2] = xBoolean.makeHandle(fFormal);
                if (constDefault == null)
                    {
                    ahArg[3] = xBoolean.FALSE;
                    ahArg[4] = xNullable.NULL;
                    }
                else
                    {
                    ahArg[3] = xBoolean.TRUE;
                    ahArg[4] = frameCaller.getConstHandle(constDefault);
                    }

                int iResult = template.construct(frameCaller, construct, clzParam, null, ahArg, Op.A_STACK);
                if (iResult != Op.R_EXCEPTION)
                    {
                    assert iResult == Op.R_CALL;
                    frameCaller.m_frameNext.addContinuation(this);
                    return iResult;
                    }
                }

            return continuation.proceed(frameCaller);
            }

        private final Parameter[]    aParam;
        private final ObjectHandle[] ahParam;
        private final Frame.Continuation continuation;
        private final TypeConstant  typeRTParameter;
        private int index = -1;
        }

    /**
     * Construct a {@code collections.ListMap} based on the arrays of keys and values.
     *
     * @param frame     the current frame
     * @param clzMap    the ListMap class
     * @param haKeys    the array of keys
     * @param haValues  the array of values
     * @param iReturn   the register to place the ListMap handle into
     *
     * @return R_CALL or R_EXCEPTION
     */
    public static int constructListMap(Frame frame, TypeComposition clzMap,
                                       ObjectHandle haKeys, ObjectHandle haValues, int iReturn)
        {
        MethodStructure constructor = LIST_MAP_CONSTRUCT;
        ObjectHandle[]  ahArg       = new ObjectHandle[constructor.getMaxVars()];
        ahArg[0] = haKeys;
        ahArg[1] = haValues;

        return clzMap.getTemplate().construct(frame, constructor, clzMap, null, ahArg, iReturn);
        }

    /**
     * Construct a {@code reflect.Argument} constant and place it on the stack.
     *
     * @param frame         the current frame
     * @param typeReferent  the type of Referent
     * @param hValue        the value of Referent
     * @param sName         (optional) name
     *
     * @return R_CALL or R_EXCEPTION
     */
    public static int constructArgument(Frame frame, TypeConstant typeReferent,
                                        ObjectHandle hValue, String sName)
        {
        MethodStructure constructor = ARGUMENT_CONSTRUCT;
        ObjectHandle[]  ahArg       = new ObjectHandle[constructor.getMaxVars()];
        ahArg[0] = hValue;
        ahArg[1] = sName == null ? xNullable.NULL : xString.makeHandle(sName);

        TypeComposition clzArg = ARGUMENT_TEMPLATE.
                ensureParameterizedClass(frame.f_context.f_container, typeReferent);
        return ARGUMENT_TEMPLATE.construct(frame, constructor, clzArg, null, ahArg, Op.A_STACK);
        }

    /**
     * Construct a {@code reflect.Annotation} constant.
     *
     * @param frame      the current frame
     * @param hMixin     the mixin class handle
     * @param ahAnnoArg  the array of annotation arguments
     * @param iReturn    the register to assign the value into
     *
     * @return R_CALL or R_EXCEPTION
     */
    public static int constructAnnotation(Frame frame, ClassHandle hMixin,
                                          ObjectHandle[] ahAnnoArg, int iReturn)
        {
        MethodStructure constructor = ANNOTATION_CONSTRUCT;
        ObjectHandle[]  ahArg       = new ObjectHandle[constructor.getMaxVars()];
        ahArg[0] = hMixin;
        ahArg[1] = makeArgumentArrayHandle(frame.f_context.f_container, ahAnnoArg);

        ClassTemplate template = ANNOTATION_TEMPLATE;
        return template.construct(frame, constructor,
                template.getCanonicalClass(), null, ahArg, iReturn);
        }

    /**
     * Construct a {@code reflect.AnnotationTemplate} constant.
     *
     * @param frame      the current frame
     * @param hClass     the ClassTemplate handle for the annotation
     * @param ahAnnoArg  the array of annotation arguments
     * @param iReturn    the register to assign the value into
     *
     * @return R_CALL or R_EXCEPTION
     */
    public static int constructAnnotationTemplate(Frame frame, ComponentTemplateHandle hClass,
                                                  ObjectHandle[] ahAnnoArg, int iReturn)
        {
        MethodStructure constructor = ANNOTATION_TEMPLATE_CONSTRUCT;
        ObjectHandle[]  ahArg = new ObjectHandle[constructor.getMaxVars()];
        ahArg[0] = hClass;
        ahArg[1] = makeArgumentArrayHandle(frame.f_context.f_container, ahAnnoArg);

        ClassTemplate template = ANNOTATION_TEMPLATE_TEMPLATE;
        return template.construct(frame, constructor,
                template.getCanonicalClass(), null, ahArg, iReturn);
        }

    /**
     * Helper classes for array initialization.
     */
    @FunctionalInterface
    public interface ValueSupplier
        {
        int get(Frame frame, int index);
        }

    public static class FillArray
            implements Frame.Continuation
        {
        private final ObjectHandle  hArray;
        private final xArray        template;
        private final long          cSize;
        private final ValueSupplier supplier;
        private final int           iReturn;

        private int index = -1;

        public FillArray(ArrayHandle hArray, int cSize, ValueSupplier supplier, int iReturn)
            {
            this.hArray   = hArray;
            this.template = hArray.getTemplate();
            this.cSize    = cSize;
            this.supplier = supplier;
            this.iReturn  = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            return template.assignArrayValue(
                frameCaller, hArray, index, frameCaller.popStack()) == Op.R_EXCEPTION
                    ? Op.R_EXCEPTION
                    : doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < cSize)
                {
                switch (supplier.get(frameCaller, index))
                    {
                    case Op.R_NEXT:
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }
            return frameCaller.assignValue(iReturn, hArray);
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    public static final ObjectHandle[] OBJECTS_NONE = new ObjectHandle[0];
    public static final StringHandle[] STRINGS_NONE = new StringHandle[0];
    public static final String[]       NO_NAMES     = new String[0];

    public static final Frame.Continuation NEXT = frame -> Op.R_NEXT;
    public static final Predicate          ANY  = t -> true;

    public  static ClassStructure    CONST_HELPER;
    private static ClassTemplate     ANNOTATION_TEMPLATE;
    private static ClassTemplate     RT_PARAMETER_TEMPLATE;
    private static MethodStructure   ANNOTATION_CONSTRUCT;
    private static MethodStructure   ANNOTATION_TEMPLATE_CONSTRUCT;
    private static MethodStructure   ARGUMENT_CONSTRUCT;
    private static MethodStructure   RT_PARAMETER_CONSTRUCT;
    private static MethodStructure   LIST_MAP_CONSTRUCT;
    private static MethodStructure   STRING_VALUE_OF;
    private static TypeConstant      ANNOTATION_ARRAY_TYPE;
    private static TypeConstant      ARGUMENT_ARRAY_TYPE;
    private static SignatureConstant SIG_FREEZE;
    private static SignatureConstant SIG_GET_RESOURCE;

    /**
     * Collect necessary constants for future use.
     *
     * @param container the template registry
     */
    public static void initNative(NativeContainer container)
        {
        ConstantPool pool = container.getConstantPool();

        ANNOTATION_TEMPLATE           = container.getTemplate("reflect.Annotation");
        ANNOTATION_TEMPLATE_TEMPLATE  = container.getTemplate("reflect.AnnotationTemplate");
        ARGUMENT_TEMPLATE             = container.getTemplate("reflect.Argument");
        RT_PARAMETER_TEMPLATE         = container.getTemplate("_native.reflect.RTParameter");
        ANNOTATION_CONSTRUCT          = ANNOTATION_TEMPLATE.getStructure().findMethod("construct", 2);
        ANNOTATION_TEMPLATE_CONSTRUCT = ANNOTATION_TEMPLATE_TEMPLATE.getStructure().findMethod("construct", 2);
        ARGUMENT_CONSTRUCT            = ARGUMENT_TEMPLATE.getStructure().findMethod("construct", 2);
        RT_PARAMETER_CONSTRUCT        = RT_PARAMETER_TEMPLATE.getStructure().findMethod("construct", 5);
        LIST_MAP_CONSTRUCT            = container.getClassStructure("collections.ListMap").findMethod("construct", 2);
        ANNOTATION_ARRAY_TYPE         = pool.ensureArrayType(pool.ensureEcstasyTypeConstant("reflect.Annotation"));
        ARGUMENT_ARRAY_TYPE           = pool.ensureArrayType(pool.ensureEcstasyTypeConstant("reflect.Argument"));
        CONST_HELPER                  = container.getClassStructure("_native.ConstHelper");
        STRING_VALUE_OF               = CONST_HELPER.findMethod("valueOf", 1);
        SIG_FREEZE                    = container.getClassStructure("Freezable").findMethod("freeze", 1).
                                            getIdentityConstant().getSignature();
        SIG_GET_RESOURCE              = container.getClassStructure("mgmt.ResourceProvider").findMethod("getResource", 2).
                                            getIdentityConstant().getSignature();
        }

    /**
     * Helper method for the "toString()" method invocation that pushes the result onto the frame's
     * stack. This method never throws a natural exception; instead it creates a resulting string
     * with some basic exception information.
     *
     * @param frame   the current frame
     * @param hValue  the value to get a string value for
     *
     * @return R_CALL value
     */
    public static int callValueOf(Frame frame, ObjectHandle hValue)
        {
        ObjectHandle[] ahVar = new ObjectHandle[STRING_VALUE_OF.getMaxVars()];
        ahVar[0] = hValue;
        return frame.call1(STRING_VALUE_OF, null, ahVar, Op.A_STACK);
        }

    /**
     * Helper method for the "freeze()" method invocation that pushes the result onto the frame's
     * stack.
     *
     * @param frame     the current frame
     * @param hValue    the Freezable value to call the "freeze()" on
     * @param FInPlace  if Null, don't pass it (the callee will use default), otherwise pass the
     *                  corresponding BooleanHandle
     *
     * @return R_NEXT, R_CALL or R_EXCEPTION value
     */
    public static int callFreeze(Frame frame, ObjectHandle hValue, Boolean FInPlace,
                                 Frame.Continuation continuation)
        {
        CallChain chain = hValue.getComposition().getMethodCallChain(SIG_FREEZE);
        if (chain.isEmpty())
            {
            return frame.raiseException(
                "Missing method \"freeze()\" on " + hValue.getType().getValueString());
            }

        int iResult = FInPlace == null
            ? chain.invoke(frame, hValue, Op.A_STACK)
            : chain.invoke(frame, hValue, xBoolean.makeHandle(FInPlace), Op.A_STACK);

        switch (iResult)
            {
            case Op.R_NEXT:
                return continuation.proceed(frame);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(continuation);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }
    }