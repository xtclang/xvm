package org.xvm.runtime;


import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.concurrent.CompletableFuture;

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
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.annotations.xFutureVar;
import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.GenericArrayHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.reflect.xModule;
import org.xvm.runtime.template.reflect.xPackage;
import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;


/**
 * Various helpers.
 */
public abstract class Utils
    {
    /**
     * Collect necessary constants for future use.
     *
     * @param templates the template registry
     */
    public static void initNative(TemplateRegistry templates)
        {
        REGISTRY               = templates;
        ANNOTATION_TEMPLATE    = templates.getTemplate("reflect.Annotation");
        ARGUMENT_TEMPLATE      = templates.getTemplate("reflect.Argument");
        RT_PARAMETER_TEMPLATE  = templates.getTemplate("_native.reflect.RTParameter");
        ANNOTATION_CONSTRUCT   = ANNOTATION_TEMPLATE.getStructure().findMethod("construct", 2);
        ARGUMENT_CONSTRUCT     = ARGUMENT_TEMPLATE.getStructure().findMethod("construct", 2);
        RT_PARAMETER_CONSTRUCT = RT_PARAMETER_TEMPLATE.getStructure().findMethod("construct", 5);
        LIST_MAP_CONSTRUCT     = templates.getClassStructure("collections.ListMap").findMethod("construct", 2);
        }

    /**
     * Ensure that the specified array of arguments is of the specified size.
     *
     * @param ahArg  the array of arguments
     * @param cVars  the desired array size
     *
     * @return the array of the desired size containing all the arguments
     */
    public static ObjectHandle[] ensureSize(ObjectHandle[] ahArg, int cVars)
        {
        int cArgs = ahArg.length;
        if (cArgs == cVars)
            {
            return ahArg;
            }

        if (cArgs < cVars)
            {
            ObjectHandle[] ahVar = new ObjectHandle[cVars];
            System.arraycopy(ahArg, 0, ahVar, 0, cArgs);
            return ahVar;
            }

        throw new IllegalArgumentException("Requested size " + cVars +
            " is less than the array size " + cArgs);
        }

    /**
     * Create a FullyBoundHandle representing a finalizer of the specified constructor.
     *
     * @param constructor  the constructor
     * @param ahArg        the arguments to bind
     *
     * @return a FullyBoundHandle representing the finalizer or null if there is no finalizer
     */
    public static FullyBoundHandle makeFinalizer(MethodStructure constructor, ObjectHandle[] ahArg)
        {
        MethodStructure methodFinally = constructor.getConstructFinally();

        return methodFinally == null
            ? null
            : xRTFunction.makeHandle(methodFinally).bindArguments(ahArg);
        }

    /**
     * Helper method for the "toString()" method invocation that pushes the result onto the frame's
     * stack.
     *
     * @param frame   the current frame
     * @param hValue  the value to get a property for
     *
     * @return one of R_EXCEPTION, R_NEXT or R_CALL values
     */
    public static int callToString(Frame frame, ObjectHandle hValue)
        {
        CallChain chain = hValue.getComposition().getMethodCallChain(frame.poolContext().sigToString());
        return chain.isNative()
            ? hValue.getTemplate().buildStringValue(frame, hValue, Op.A_STACK)
            : chain.invoke(frame, hValue, OBJECTS_NONE, Op.A_STACK);
        }

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
                    frameCaller.assignValues(aiReturn, xBoolean.TRUE, frame.popStack()));
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

        ServiceContext ctx;
        long lFiberId;

        if (frame == null)
            {
            ctx = ServiceContext.getCurrentContext();
            lFiberId = -1;
            }
        else
            {
            ctx = frame.f_context;
            lFiberId = frame.f_fiber.getId();
            }

        System.out.println(new Timestamp(System.currentTimeMillis())
            + " " + ctx + ", fiber " + lFiberId + ": " + sMsg);
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
                if (hValue instanceof DeferredCallHandle)
                    {
                    DeferredCallHandle hDeferred = ((DeferredCallHandle) hValue);
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
                if (hValue instanceof DeferredCallHandle)
                    {
                    DeferredCallHandle hDeferred = ((DeferredCallHandle) hValue);
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

    public static class ContinuationChain
            implements Frame.Continuation
        {
        public ContinuationChain(Frame.Continuation step0)
            {
            f_list = new ArrayList<>();
            f_list.add(step0);
            }

        public void add(Frame.Continuation stepNext)
            {
            f_list.add(stepNext);
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            while (++index < f_list.size())
                {
                int iResult = f_list.get(index).proceed(frameCaller);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        continue;

                    case Op.R_CALL:
                        Frame              frameNext = frameCaller.m_frameNext;
                        Frame.Continuation contNext  = frameNext.m_continuation;

                        if (contNext != null)
                            {
                            // the previous continuation caused another call and the callee has
                            // its own continuations; in that case we need to execute those
                            // continuations before continuing with our own chain
                            // (assuming everyone returns normally)
                            f_list.set(index--, contNext);
                            }
                        frameNext.m_continuation = this;
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        assert frameCaller.m_hException != null;
                        return Op.R_EXCEPTION;

                    default:
                        if (iResult >= 0)
                            {
                            // only the very last continuation can return a specific op index
                            // (see OpCondJump)
                            assert index + 1 == f_list.size();
                            return iResult;
                            }
                        throw new IllegalStateException();
                    }
                }
            return Op.R_NEXT;
            }

        private final List<Frame.Continuation> f_list;
        private int index = -1;
        }


    // ----- comparison support --------------------------------------------------------------------

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

    protected static int completeEquals(Frame frame, TypeConstant type2,
                                        ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle hResult = frame.popStack();
        return hResult == xBoolean.FALSE
            ? frame.assignValue(iReturn, hResult)
            : type2.callEquals(frame, hValue1, hValue2, iReturn);
        }

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


    // ----- toString support ----------------------------------------------------------------------

    public static class TupleToString
            implements Frame.Continuation
        {
        public TupleToString(StringBuilder sb, ObjectHandle[] ahValue,
                             String[] asLabel, Frame.Continuation nextStep)
            {
            this.sb       = sb;
            this.ahValue  = ahValue;
            this.asLabel  = asLabel;
            this.nextStep = nextStep;
            }

        public int doNext(Frame frameCaller)
            {
            loop: while (++index < ahValue.length)
                {
                switch (callToString(frameCaller, ahValue[index]))
                    {
                    case Op.R_NEXT:
                        if (updateResult(frameCaller))
                            {
                            continue loop;
                            }
                        else
                            {
                            break loop;
                            }

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            finishResult();
            return nextStep.proceed(frameCaller);
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            if (updateResult(frameCaller))
                {
                return doNext(frameCaller);
                }

            // too much text; enough for an output...
            return nextStep.proceed(frameCaller);
            }

        // return false if the buffer is full
        protected boolean updateResult(Frame frameCaller)
            {
            StringHandle hString = (StringHandle) frameCaller.popStack();
            String sLabel = asLabel == null ? null : asLabel[index];

            if (sLabel != null)
                {
                sb.append(sLabel).append('=');
                }
            sb.append(hString.getValue());

            if (sb.length() < MAX_LEN)
                {
                sb.append(", ");
                return true;
                }

            sb.append("...");
            return false;
            }

        protected void finishResult()
            {
            if (sb.length() >= 2 && sb.charAt(sb.length() - 2) == ',')
                {
                sb.setLength(sb.length() - 2); // remove the trailing ", "
                sb.append(')');
                }
            }

        protected static final int MAX_LEN = 16*1024;

        protected final StringBuilder      sb;
        protected final ObjectHandle[]     ahValue;
        protected final String[]           asLabel;
        protected final Frame.Continuation nextStep;

        protected int index = -1;
        }


    // ----- various run-time support --------------------------------------------------------------

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

                    // create a pseudo frame to deal with the wait, but don't allow any other fiber
                    // to interleave until a response comes back (as in "forbidden" reentrancy)
                    Frame frameWait = Utils.createWaitFrame(frame, cfResult, Op.A_BLOCK);
                    frameWait.addContinuation(continuation);

                    return frame.call(frameWait);
                    }
                }

            // we are on the main context and can actually perform the initialization
            if (!constSingleton.markInitializing())
                {
                // this can only happen if we are called recursively
                return frame.raiseException("Circular initialization");
                }

            IdentityConstant constValue = constSingleton.getClassConstant();
            int              iResult;

            switch (constValue.getFormat())
                {
                case Module:
                    iResult = xModule.INSTANCE.createConstHandle(frame, constValue);
                    break;

                case Package:
                    iResult = xPackage.INSTANCE.createConstHandle(frame, constValue);
                    break;

                case Property:
                    iResult = callPropertyInitializer(frame, (PropertyConstant) constValue);
                    break;

                case Class:
                    {
                    ClassConstant idClz = (ClassConstant) constValue;
                    ClassStructure clz  = (ClassStructure) idClz.getComponent();

                    assert clz.isSingleton();

                    ClassTemplate template = ctxCurr.f_templates.getTemplate(idClz);
                    if (template.getStructure().getFormat() == Format.ENUMVALUE)
                        {
                        // this can happen if the constant's handle was not initialized or
                        // assigned on a different constant pool
                        iResult = template.createConstHandle(frame, constSingleton);
                        }
                    else
                        {
                        // the class must have a no-params constructor to call
                        MethodStructure constructor = clz.findConstructor(TypeConstant.NO_TYPES);
                        iResult = template.construct(frame, constructor,
                                template.getCanonicalClass(), null, Utils.OBJECTS_NONE, Op.A_STACK);
                        }
                    break;
                    }

                default:
                    throw new IllegalStateException("unexpected defining constant: " + constValue);
                }

            switch (iResult)
                {
                case Op.R_NEXT:
                    constSingleton.setHandle(frame.popStack());
                    break; // next constant

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        {
                        constSingleton.setHandle(frameCaller.popStack());
                        return initConstants(frameCaller, listSingletons, continuation);
                        });
                    return Op.R_CALL;

                default:
                    throw new IllegalStateException();
                }
            }

        return continuation.proceed(frame);
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

        assert prop.isStatic();

        Constant constVal = prop.getInitialValue();
        if (constVal == null)
            {
            // there must be an initializer
            MethodStructure methodInit = prop.getInitializer();
            ObjectHandle[]  ahVar      =
                Utils.ensureSize(Utils.OBJECTS_NONE, methodInit.getMaxVars());

            return frame.call1(methodInit, null, ahVar, Op.A_STACK);
            }

        return frame.pushDeferredValue(frame.getConstHandle(constVal));
        }

    /**
     * Create a pseudo frame that will wait on the specified future.
     *
     * @param frame     the caller frame
     * @param cfResult  the CompletableFuture to wait for
     * @param iReturn   the return register for the result
     *
     * @return a new frame
     */
    public static Frame createWaitFrame(Frame frame,
                                        CompletableFuture<ObjectHandle> cfResult, int iReturn)
        {
        ObjectHandle[] ahFuture = new ObjectHandle[]{xFutureVar.makeHandle(cfResult)};

        Frame frameNext = frame.createNativeFrame(WAIT_AND_RETURN, ahFuture, iReturn, null);

        frameNext.f_aInfo[0] = frame.new VarInfo(xFutureVar.TYPE, Frame.VAR_DYNAMIC_REF);

        return frameNext;
        }

    /**
     * Create a pseudo frame that will wait on multiple specified futures.
     *
     * @param frame     the caller frame
     * @param cfResult  the CompletableFuture to wait for
     * @param aiReturn  the return registers for the results
     *
     * @return a new frame
     */
    public static Frame createWaitFrame(Frame frame,
                                        CompletableFuture<ObjectHandle[]> cfResult, int[] aiReturn)
        {
        int            cReturns = aiReturn.length;
        ObjectHandle[] ahFuture = new ObjectHandle[cReturns];

        Frame frameNext = frame.createNativeFrame(WAIT_AND_RETURN, ahFuture, Op.A_MULTI, aiReturn);

        // create a pseudo frame to deal with the multiple waits
        for (int i = 0; i < cReturns; i++)
            {
            int iResult = i;

            CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahResult[iResult]);

            ahFuture[i] = xFutureVar.makeHandle(cfReturn);
            frameNext.f_aInfo[i] = frame.new VarInfo(xFutureVar.TYPE, Frame.VAR_DYNAMIC_REF);
            }

        return frameNext;
        }

    private static final Op[] WAIT_AND_RETURN = new Op[]
        {
        new Op()
            {
            public int process(Frame frame, int iPC)
                {
                int cValues = frame.f_ahVar.length;

                assert cValues > 0;

                if (cValues == 1)
                    {
                    assert frame.f_aiReturn == null;

                    boolean      fNoReentrancy = frame.f_iReturn == Op.A_BLOCK;
                    FutureHandle hFuture       = (FutureHandle) frame.f_ahVar[0];

                    return hFuture.isAssigned()
                        ? frame.returnValue(hFuture, true)
                        : fNoReentrancy
                            ? R_PAUSE
                            : R_REPEAT;
                    }

                assert frame.f_iReturn == A_MULTI;

                FutureHandle[] ahFuture = new FutureHandle[cValues];
                for (int i = 0; i < cValues; i++)
                    {
                    FutureHandle hFuture = (FutureHandle) frame.f_ahVar[i];
                    if (hFuture.isAssigned())
                        {
                        ahFuture[i] = hFuture;
                        }
                    else
                        {
                        return R_REPEAT;
                        }
                    }

                boolean[] afDynamic = new boolean[cValues];
                Arrays.fill(afDynamic, true);

                return frame.returnValues(ahFuture, afDynamic);
                }

            public String toString()
                {
                return "WaitAndReturn";
                }
            }
        };

    /**
     * An abstract base for in-place operation support.
     */
    public static abstract class AbstractInPlace
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
         * @param fPost    if true, the the operation is performed after the current value is returned
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
                        int cParamsDefault  = constructMixin.getDefaultParamCount();
                        int cParamsRequired = cParamsAll - cParamsDefault;
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
                            continue NextStep;
                            }

                        Constant[] aconstArg = aAnno[iAnno].getParams();
                        Constant   constArg  = iArg < aconstArg.length
                            ? aconstArg[iArg]
                            : constructMixin.getParam(iArg).getDefaultValue();

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
                        MethodStructure constructor = ARGUMENT_CONSTRUCT;
                        Parameter       param       = constructMixin.getParam(iArg);
                        ObjectHandle[]  ahArg       = new ObjectHandle[constructor.getMaxVars()];
                        ahArg[0] = hValue;
                        ahArg[1] = xString.makeHandle(param.getName());

                        ClassComposition clzArg = ARGUMENT_TEMPLATE.
                            ensureParameterizedClass(frameCaller.poolContext(), param.getType());
                        int iResult = ARGUMENT_TEMPLATE.construct(frameCaller, constructor,
                                            clzArg, null, ahArg, Op.A_STACK);
                        if (iResult == Op.R_CALL)
                            {
                            frameCaller.m_frameNext.addContinuation(this);

                            stageNext = Stage.Value;
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

                        MethodStructure constructor = ANNOTATION_CONSTRUCT;
                        ObjectHandle[]  ahArg       = new ObjectHandle[constructor.getMaxVars()];
                        ahArg[0] = hMixin;
                        ahArg[1] = makeArgumentArrayHandle(frameCaller.poolContext(), ahAnnoArg);

                        ClassTemplate templateAnno = ANNOTATION_TEMPLATE;
                        int iResult = templateAnno.construct(frameCaller, constructor,
                                templateAnno.getCanonicalClass(), null, ahArg, Op.A_STACK);
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
                    makeAnnoArrayHandle(frameCaller.poolContext(), ahAnno));
            }

        enum Stage {Mixin, ArgumentArray, Value, Argument, Annotation};
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
    public static ArrayHandle makeAnnoArrayHandle(ConstantPool pool, ObjectHandle[] ahAnno)
        {
        if (ANNOTATION_ARRAY_CLZ == null)
            {
            TypeConstant typeArray = pool.ensureParameterizedTypeConstant(
                pool.typeArray(), pool.ensureEcstasyTypeConstant("reflect.Annotation"));
            ANNOTATION_ARRAY_CLZ = REGISTRY.resolveClass(typeArray);
            }

        return new GenericArrayHandle(ANNOTATION_ARRAY_CLZ, ahAnno, xArray.Mutability.Constant);
        }

    /**
     * @return a constant Argument array handle
     */
    public static ArrayHandle makeArgumentArrayHandle(ConstantPool pool, ObjectHandle[] ahArg)
        {
        if (ARGUMENT_ARRAY_CLZ == null)
            {
            TypeConstant typeArray = pool.ensureParameterizedTypeConstant(
                pool.typeArray(), pool.ensureEcstasyTypeConstant("reflect.Argument"));
            ARGUMENT_ARRAY_CLZ = REGISTRY.resolveClass(typeArray);
            }

        return new GenericArrayHandle(ARGUMENT_ARRAY_CLZ, ahArg, xArray.Mutability.Constant);
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

                ConstantPool     pool      = frameCaller.poolContext();
                ClassTemplate    template  = RT_PARAMETER_TEMPLATE;
                TypeConstant     typeParam = pool.ensureParameterizedTypeConstant(typeRTParameter, type);
                ClassComposition clzParam  = pool.ensureClassComposition(typeParam, template);

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
     * Construct a ListMap based on the arrays of keys and values.
     *
     * @param frame     the current frame
     * @param clzMap    the ListMap class
     * @param haKeys    the array of keys
     * @param haValues  the array of values
     * @param iReturn   the register to place the ListMap handle into
     *
     * @return R_CALL or R_EXCEPTION
     */
    public static int constructListMap(Frame frame, ClassComposition clzMap,
                                       ArrayHandle haKeys, ArrayHandle haValues, int iReturn)
        {
        MethodStructure constructor = LIST_MAP_CONSTRUCT;
        ObjectHandle[]  ahArg       = new ObjectHandle[constructor.getMaxVars()];
        ahArg[0] = haKeys;
        ahArg[1] = haValues;

        return clzMap.getTemplate().construct(frame, constructor, clzMap, null, ahArg, iReturn);
        }


    // ----- constants -----------------------------------------------------------------------------

    public final static ObjectHandle[] OBJECTS_NONE = new ObjectHandle[0];

    private static TemplateRegistry REGISTRY;

    // assigned by initNative()
    private static ClassTemplate    ANNOTATION_TEMPLATE;
    private static MethodStructure  ANNOTATION_CONSTRUCT;
    private static ClassTemplate    ARGUMENT_TEMPLATE;
    private static MethodStructure  ARGUMENT_CONSTRUCT;
    private static ClassTemplate    RT_PARAMETER_TEMPLATE;
    private static MethodStructure  RT_PARAMETER_CONSTRUCT;
    private static MethodStructure  LIST_MAP_CONSTRUCT;

    // assigned lazily
    private static ClassComposition ANNOTATION_ARRAY_CLZ;
    private static ClassComposition ARGUMENT_ARRAY_CLZ;
    }
