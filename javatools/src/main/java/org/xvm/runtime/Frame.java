package org.xvm.runtime;


import java.util.ArrayDeque;
import java.util.Deque;

import java.util.concurrent.CompletableFuture;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils.ContinuationChain;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;

import org.xvm.runtime.template.annotations.xFutureVar;
import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;


/**
 * A call stack frame.
 */
public class Frame
        implements GenericTypeResolver
    {
    public final Fiber              f_fiber;
    public final ServiceContext     f_context;      // same as f_fiber.f_context

    protected final MethodStructure f_function;
    protected final Op[]            f_aOp;          // the op-codes
    protected final ObjectHandle    f_hTarget;      // the passed in target
    protected final ObjectHandle    f_hThis;        // the "inception" view of the target

    public final ObjectHandle[]     f_ahVar;        // arguments/local var registers
    public final VarInfo[]          f_aInfo;        // optional info for var registers

    protected final int             f_iReturn;      // an index for a single return value;
    protected final int[]           f_aiReturn;     // indexes for multiple return values

    public final Frame              f_framePrev;    // the caller's frame
    public final int[]              f_anNextVar;    // at index i, the "next available" var register for scope i

    protected final int             f_iPCPrev;      // the caller's PC (used only for async reporting)
    protected final int             f_iId;          // the frame's id (used only for async reporting)

    public  int                     m_iPC;          // the program counter
    public  int                     m_iScope;       // current scope index (starts with 0)

    private int                     m_iGuard = -1;  // current guard index (-1 if none)
    private Guard[]                 m_aGuard;       // at index i, the guard for the guard index i

    public  ExceptionHandle         m_hException;   // an exception
    public  DeferredGuardAction     m_deferred;     // a deferred action to be performed by FinallyEnd
    public  FullyBoundHandle        m_hfnFinally;   // a "finally" method for the constructors
    public  Frame                   m_frameNext;    // the next frame to call
    public  Continuation            m_continuation; // a function to call after this frame returns

    public  CallChain               m_chain;        // an invocation call chain
    public  int                     m_nDepth;       // this frame's depth in the call chain

    private ObjectHandle            m_hStackTop;    // the top of the local stack
    private Deque<ObjectHandle>     m_stack;        // a remainder of the stack

    public static final int VAR_STANDARD         = 0;
    public static final int VAR_DYNAMIC_REF      = 1;

    /**
     * Construct a frame.
     *
     * @param iReturn  positive values indicate the caller's frame register
     */
    protected Frame(Frame framePrev, MethodStructure function,
                    ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        assert framePrev != null && function != null;

        f_context   = framePrev.f_context;
        f_iId       = f_context.m_iFrameCounter++;
        f_fiber     = framePrev.f_fiber;
        f_framePrev = framePrev;
        f_iPCPrev   = framePrev.m_iPC;
        f_function  = function;
        f_aOp       = function.getOps();
        f_hTarget   = hTarget;
        f_hThis     = hTarget == null
                    ? null
                    : hTarget.isStruct()
                        ? hTarget
                        : hTarget.revealOrigin();
        f_ahVar     = ahVar;
        f_aInfo     = new VarInfo[ahVar.length];

        int cScopes = function == null ? 1 : function.getMaxScopes();
        f_anNextVar = new int[cScopes];
        f_anNextVar[0] = function == null ? 0 : function.getParamCount();

        f_iReturn   = iReturn;
        f_aiReturn  = aiReturn;
        }

    /**
     * Construct an initial (native) frame that has no "previous" frame.
     */
    protected Frame(Fiber fiber, int iCallerPC, Op[] aopNative,
                    ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context   = fiber.f_context;
        f_iId       = f_context.m_iFrameCounter++;
        f_fiber     = fiber;
        f_framePrev = null;
        f_iPCPrev   = iCallerPC;
        f_function  = null;
        f_aOp       = aopNative;
        f_hTarget   = f_hThis = null;
        f_ahVar     = ahVar;
        f_aInfo     = new VarInfo[ahVar.length];
        f_anNextVar = null;
        f_iReturn   = iReturn;
        f_aiReturn  = aiReturn;
        }

    /**
     * Construct a native frame with the same target as the caller's target.
     */
    protected Frame(Frame framePrev, Op[] aopNative, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context   = framePrev.f_context;
        f_iId       = f_context.m_iFrameCounter++;
        f_fiber     = framePrev.f_fiber;
        f_framePrev = framePrev;
        f_iPCPrev   = framePrev.m_iPC;
        f_function  = null;
        f_aOp       = aopNative;
        f_hTarget   = framePrev.f_hTarget;
        f_hThis     = framePrev.f_hThis;
        f_ahVar     = ahVar;
        f_aInfo     = new VarInfo[ahVar.length];
        f_anNextVar = null;
        f_iReturn   = iReturn;
        f_aiReturn  = aiReturn;
        }

    /**
     * Create a new frame that returns zero or one value into the specified slot.
     *
     * Note: the returned frame needs to be "initialized" before called.
     */
    public Frame createFrame1(MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(this, method, hTarget, ahVar, iReturn, null);
        }

    /**
     * Create a new frame that returns a Tuple value into the specified slot.
     *
     * Note: the returned frame needs to be "initialized" before called.
     */
    public Frame createFrameT(MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(this, method, hTarget, ahVar, Op.A_TUPLE, new int[] {iReturn});
        }

    /**
     * Create a new frame that returns multiple values into the specified slots.
     *
     * Note: the returned frame needs to be "initialized" before called.
     */
    public Frame createFrameN(MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return new Frame(this, method, hTarget, ahVar, Op.A_MULTI, aiReturn);
        }

    /**
     * Create a new pseudo-frame on the same target as this frame.
     */
    public Frame createNativeFrame(Op[] aop, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        return new Frame(this, aop, ahVar, iReturn, aiReturn);
        }

    /**
     * Ensure that all the singleton constants are initialized for the specified frame.
     *
     * @param frameNext  the frame to be called by this frame
     *
     * @return one of the {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    protected int ensureInitialized(Frame frameNext)
        {
        return frameNext.f_function.ensureInitialized(this, frameNext);
        }

    /**
     * @return iff this frame is native
     */
    public boolean isNative()
        {
        return f_function == null;
        }

    /**
     * @return iff this frame and all the previous frames are native
     */
    public boolean isNativeStack()
        {
        Frame frame = this;
        do
            {
            if (!frame.isNative())
                {
                return false;
                }
            frame = frame.f_framePrev;
            }
        while (frame != null);

        return true;
        }

    /**
     * Find a caller frame by its id.
     *
     * @return the corresponding frame in the chain or null
     */
    protected Frame findCallerFrame(int id)
        {
        Frame frame = this;
        do
            {
            if (frame.f_iId == id)
                {
                return frame;
                }
            frame = frame.f_framePrev;
            }
        while (frame != null);

        return null;
        }

    // a convenience method
    public int call(Frame frameNext)
        {
        assert frameNext.f_framePrev == this;
        m_frameNext = frameNext;
        return Op.R_CALL;
        }

    // a convenience method
    public int callInitialized(Frame frameNext)
        {
        return ensureInitialized(frameNext);
        }

    // a convenience method; ahVar - prepared variables
    public int call1(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return ensureInitialized(createFrame1(method, hTarget, ahVar, iReturn));
        }

    // a convenience method; ahVar - prepared variables
    public int callT(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return ensureInitialized(createFrameT(method, hTarget, ahVar, iReturn));
        }

    // a convenience method
    public int callN(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return ensureInitialized(createFrameN(method, hTarget, ahVar, aiReturn));
        }

    // a convenience method; ahVar - prepared variables
    public int invoke1(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        Frame frameNext = createFrame1(chain.getMethod(nDepth), hTarget, ahVar, iReturn);

        frameNext.m_chain  = chain;
        frameNext.m_nDepth = nDepth;

        return ensureInitialized(frameNext);
        }

    // a convenience method; ahVar - prepared variables
    public int invokeT(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        Frame frameNext = createFrameT(chain.getMethod(nDepth), hTarget, ahVar, iReturn);

        frameNext.m_chain  = chain;
        frameNext.m_nDepth = nDepth;

        return ensureInitialized(frameNext);
        }

    // a convenience method
    public int invokeN(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        Frame frameNext = createFrameN(chain.getMethod(nDepth), hTarget, ahVar, aiReturn);

        frameNext.m_chain  = chain;
        frameNext.m_nDepth = nDepth;

        return ensureInitialized(frameNext);
        }

    // start the specified guard as a "current" one
    public void pushGuard(Guard guard)
        {
        Guard[] aGuard = m_aGuard;
        if (aGuard == null)
            {
            assert m_iGuard == -1;
            aGuard = m_aGuard = new Guard[f_anNextVar.length]; // # of scopes
            }
        aGuard[++m_iGuard] = guard;
        }

    // drop the top-most guard
    public void popGuard()
        {
        m_aGuard[m_iGuard--] = null;
        }

    // find a first matching guard; unwind the scope and initialize the next var with the exception
    // return the PC of the catch or the R_EXCEPTION value
    protected int findGuard(ExceptionHandle hException)
        {
        Guard[] aGuard = m_aGuard;
        if (aGuard != null)
            {
            for (int iGuard = m_iGuard; iGuard >= 0; iGuard--)
                {
                Guard guard = aGuard[iGuard];

                int iPC = guard.handleException(this, hException, iGuard);
                if (iPC >= 0)
                    {
                    return iPC;
                    }
                }
            }
        return Op.R_EXCEPTION;
        }

    // find the closest "AllGuard" and continue with execution of the corresponding code
    public int processAllGuard(DeferredGuardAction deferredAction)
        {
        Guard[] aGuard = m_aGuard;
        assert aGuard != null;

        for (int iGuard = deferredAction.getGuardIndex(); iGuard >= 0; iGuard--)
            {
            Guard guard = aGuard[iGuard];
            if (guard instanceof AllGuard)
                {
                deferredAction.setGuardIndex(iGuard - 1);
                m_deferred = deferredAction;

                return ((AllGuard) guard).handleJump(this, iGuard);
                }
            }

        m_deferred = null;

        return deferredAction.complete(this);
        }

    /**
     * Obtain a handle for one of the pre-defined arguments.
     *
     * @param iArgId  the argument id (negative value in the reserved range)
     *
     * @return a corresponding handle
     */
    protected ObjectHandle getPredefinedArgument(int iArgId)
        {
        switch (iArgId)
            {
            case Op.A_STACK:
                ObjectHandle hValue = popStack();
                return hValue == null
                    ? makeDeferredException("Run-time error: empty stack")
                    : hValue;

            case Op.A_DEFAULT:
                return ObjectHandle.DEFAULT;

            case Op.A_SUPER:
                return f_hThis == null
                    ? makeDeferredException("Run-time error: no target")
                    : xRTFunction.makeHandle(m_chain, m_nDepth).bind(this, 0, f_hThis);

            case Op.A_TARGET:
                return f_hTarget == null
                    ? makeDeferredException("Run-time error: no target")
                    : f_hTarget;

            case Op.A_PUBLIC:
                return f_hThis == null
                    ? makeDeferredException("Run-time error: no target")
                    : f_hThis.ensureAccess(Access.PUBLIC);

            case Op.A_PROTECTED:
                return f_hThis == null
                    ? makeDeferredException("Run-time error: no target")
                    : f_hThis.ensureAccess(Access.PROTECTED);

            case Op.A_PRIVATE:
                return f_hThis == null
                    ? makeDeferredException("Run-time error: no target")
                    : f_hThis.ensureAccess(Access.PRIVATE);

            case Op.A_STRUCT:
                return f_hThis == null
                    ? makeDeferredException("Run-time error: no target")
                    : f_hThis.ensureAccess(Access.STRUCT);

            case Op.A_SERVICE:
                {
                ObjectHandle hService = f_context.getService();
                return hService == null
                    ? makeDeferredException("No service")
                    : hService;
                }

            default:
                throw new IllegalStateException("Invalid argument " + iArgId);
            }
        }

    private DeferredCallHandle makeDeferredException(String sMsg)
        {
        return new DeferredCallHandle(xException.makeHandle(this, sMsg));
        }

    /**
     * Obtain the type of the specified pre-defined argument.
     *
     * @param iArgId  the argument id (negative value in the reserved range)
     * @param hArg  in case the argument id points to the stack, indicates the argument itself
     *
     * @return the type of the specified per-defined argument or null if the argument points to the
     *         stack and the operation is going to push the stack (hArg == null)
     */
    public TypeConstant getPredefinedArgumentType(int iArgId, ObjectHandle hArg)
        {
        if (iArgId == Op.A_STACK)
            {
            return hArg == null
                ? null
                : hArg.getType();
            }
        return getPredefinedArgument(iArgId).getType();
        }

    // create a new "current" scope
    public int enterScope(int nNextVar)
        {
        int iScope = ++m_iScope;

        f_anNextVar[iScope] = nNextVar;

        return iScope;
        }

    // exit the current scope and clear all the var info
    public void exitScope()
        {
        int iScope = m_iScope--;

        int[] anNextVar = f_anNextVar;
        int   iVarFrom  = anNextVar[iScope - 1];
        int   iVarTo    = anNextVar[iScope] - 1;

        for (int i = iVarFrom; i <= iVarTo; i++)
            {
            VarInfo info = f_aInfo[i];

            if (info != null)
                {
                info.release();

                f_aInfo[i] = null;
                f_ahVar[i] = null;
                }
            }
        }

    // clear the var info for all scopes above the specified one
    public void clearAllScopes(int iScope)
        {
        int iVarFrom = f_anNextVar[iScope];
        int iVarTo   = f_ahVar.length - 1;

        for (int i = iVarFrom; i <= iVarTo; i++)
            {
            VarInfo info = f_aInfo[i];

            if (info != null)
                {
                info.release();

                f_aInfo[i] = null;
                f_ahVar[i] = null;
                }
            }
        }

    // return "private:this"
    public ObjectHandle getThis()
        {
        if (f_hThis == null)
            {
            throw new IllegalStateException("Frame has no \"this\": " + toString());
            }
        return f_hThis;
        }

    /**
     * Push a value on the local stack.
     *
     * @param hValue  a value to push
     *
     * @return Op.R_NEXT
     */
    public int pushStack(ObjectHandle hValue)
        {
        assert hValue != null && !(hValue instanceof DeferredCallHandle);
        if (m_hStackTop != null)
            {
            Deque<ObjectHandle> stack = m_stack;
            if (stack == null)
                {
                stack = m_stack = new ArrayDeque<>();
                }
            stack.push(m_hStackTop);
            }
        m_hStackTop = hValue;
        return Op.R_NEXT;
        }

    /**
     * Pop a value from the local stack.
     *
     * @return a value from the stack
     */
    public ObjectHandle popStack()
        {
        ObjectHandle hValue = m_hStackTop;
        assert hValue != null;

        Deque<ObjectHandle> stack = m_stack;
        m_hStackTop = stack == null || stack.isEmpty()
                ? null
                : stack.pop();

        return hValue;
        }

    /**
     * Peek at the value at the top of the local stack.
     *
     * @return a value at the top of the stack
     */
    public ObjectHandle peekStack()
        {
        return m_hStackTop;
        }

    /**
     * Assign a specified register on this frame.
     *
     * @param nVar    the register id
     * @param hValue  the value to assign
     *
     * @return R_NEXT, R_CALL, R_EXCEPTION
     */
    public int assignValue(int nVar, ObjectHandle hValue)
        {
        if (hValue == null)
            {
            // this is only possible for a conditional return pass through; for example:
            //     conditional Int foo()
            //        {
            //        return bar();
            //        }

            if (nVar >= 0)
                {
                f_ahVar[nVar] = null;
                }
            else
                {
                assert nVar == Op.A_IGNORE;
                }

            return Op.R_NEXT;
            }

        assert !(hValue instanceof DeferredCallHandle);

        if (nVar >= 0)
            {
            VarInfo info = getVarInfo(nVar);

            switch (info.getStyle())
                {
                case VAR_STANDARD:
                    break;

                case VAR_DYNAMIC_REF:
                    {
                    RefHandle hVar = (RefHandle) f_ahVar[nVar];
                    // TODO: consider moving the "transfer the referent" logic here (see xVar)
                    // TODO: check the "weak" assignment (here or inside)
                    return hVar.getVarSupport().setReferent(this, hVar, hValue);
                    }

                default:
                    throw new IllegalStateException();
                }

            TypeConstant typeFrom = hValue.getType();
            if (typeFrom.getPosition() != info.m_nTypeId) // quick check
                {
                // TODO: should this check be done by the class itself?
                typeFrom = hValue.revealOrigin().getType();

                // TODO: how to minimize the probability of getting here?
                TypeConstant typeTo = info.getType();

                switch (typeFrom.calculateRelation(typeTo))
                    {
                    case IS_A:
                        // no need to do anything
                        break;

                    case IS_A_WEAK:
                        // the types are assignable, but we need to inject a "safe-wrapper" proxy;
                        // for example, in the case of:
                        //      List<Object> lo;
                        //      List<String> ls = ...;
                        //      lo = ls;
                        // "add(Object o)" method needs to be wrapped on "lo" reference, to ensure the
                        // run-time type of "String"
                        if (REPORT_WRAPPING && !typeTo.isTypeOfType())
                            {
                            System.err.println("WARNING: wrapping required from: " + typeFrom.getValueString()
                                + " to: " + typeTo.getValueString());
                            }
                        break;

                    default:
                        // why did the compiler/verifier allow this?
                        if (typeFrom.isAutoNarrowing())
                            {
                            // TODO: how to get the narrowing context?
                            }
                        else
                            {
                            System.err.println("WARNING: suspicious assignment from: " + typeFrom.getValueString()
                                + " to: " + typeTo.getValueString());
                            }
                    }
                }

            f_ahVar[nVar] = hValue;
            return Op.R_NEXT;
            }

        switch (nVar)
            {
            case Op.A_IGNORE:
            case Op.A_BLOCK:
                return Op.R_NEXT;

            case Op.A_STACK:
                return pushStack(hValue);

            default:
                try
                    {
                    // the value must point to a local property
                    PropertyConstant idProp = (PropertyConstant) getConstant(nVar);
                    ObjectHandle     hThis  = getThis();

                    // TODO: check the "weak" assignment (here or inside)
                    return hThis.getTemplate().setPropertyValue(this, hThis, idProp, hValue);
                    }
                catch (ClassCastException e)
                    {
                    throw new IllegalArgumentException("nVar=" + nVar);
                    }
            }
        }

    /**
     * Specialization of assignValue() that takes any number of return values.
     *
     * @param anVar     the array of two register ids
     * @param ahValue   the values to assign
     *
     * @return R_NEXT, R_CALL, R_EXCEPTION
     *
     * @see {@link Utils.AssignValues}
     */
    public int assignValues(int[] anVar, ObjectHandle... ahValue)
        {
        int c = Math.min(anVar.length, ahValue.length);
        return assignValues(anVar, ahValue, 0, c);
        }

    /**
     * Specialization of assignValue() that takes any number of return values.
     *
     * @param anVar     the array of two register ids
     * @param ahValue   the values to assign
     * @param i         specifies the next value to return
     * @param c         specifies the total number of values to return
     *
     * @return R_NEXT, R_CALL, R_EXCEPTION
     *
     * @see {@link Utils.AssignValues}
     */
    private int assignValues(int[] anVar, ObjectHandle[] ahValue, int i, int c)
        {
        while (true)
            {
            switch (c - i)
                {
                default:
                    {
                    ObjectHandle hVal = ahValue[i];
                    if (hVal == null)
                        {
                        // a "null" value can only occur in a conditional assignment; we need to
                        // avoid the scenario in which some values are assigned and others are not
                        assert i == 1 && ahValue[0] == xBoolean.FALSE;
                        return Op.R_NEXT;
                        }

                    switch (assignValue(anVar[i], hVal))
                        {
                        case Op.R_NEXT:
                            ++i;
                            break;

                        case Op.R_CALL:
                            int iNext = i+1;
                            m_frameNext.addContinuation(
                                frameCaller -> assignValues(anVar, ahValue, iNext, c));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }

                    break;
                    }

                case 1:
                    {
                    ObjectHandle hVal = ahValue[i];
                    if (hVal == null)
                        {
                        assert i == 1 && ahValue[0] == xBoolean.FALSE;
                        return Op.R_NEXT;
                        }
                    return assignValue(anVar[i], hVal);
                    }

                case 0:
                    return Op.R_NEXT;
                }
            }
        }

    /**
     * Assign a specified register on this frame to a tuple.
     *
     * @param nVar     the register id
     * @param ahValue  the handles to make up a TupleHandle out of
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION values
     */
    public int assignTuple(int nVar, ObjectHandle... ahValue)
        {
        ClassComposition clazz = ensureClass(getVarInfo(nVar).getType());

        return assignValue(nVar, xTuple.makeHandle(clazz, ahValue));
        }

    /**
     * Assign a future result to the specified register on this frame.
     *
     * @param iReturn   the register id
     * @param cfResult  the future to assign
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION values
     */
    public int assignFutureResult(int iReturn, CompletableFuture<ObjectHandle> cfResult)
        {
        if (cfResult.isDone() && !cfResult.isCompletedExceptionally())
            {
            try
                {
                return assignValue(iReturn, cfResult.get());
                }
            catch (Throwable e)
                {
                assert false; // must not happen
                }
            }

        if (isDynamicVar(iReturn))
            {
            return assignValue(iReturn, xFutureVar.makeHandle(cfResult));
            }

        // the wait frame will deal with exceptions
        return call(Utils.createWaitFrame(this, cfResult, iReturn));
        }

    /**
     * Assign a specified register on this frame with a handle that can potentially be a
     * DeferredCallHandle.
     *
     * @param nVar    the register id
     * @param hValue  the value to assign
     *
     * @return R_NEXT, R_CALL, R_EXCEPTION
     */
    public int assignDeferredValue(int nVar, ObjectHandle hValue)
        {
        return hValue instanceof DeferredCallHandle
                ? hValue.proceed(this, frameCaller ->
                    frameCaller.assignValue(nVar, frameCaller.popStack()))
                : assignValue(nVar, hValue);
        }

    /**
     * Assign specified registers on this frame with a True and a handle that can potentially be a
     * DeferredCallHandle.
     *
     * @param anVar   the registers
     * @param hValue  the value to assign
     *
     * @return R_NEXT, R_CALL, R_EXCEPTION
     */
    public int assignConditionalDeferredValue(int[] anVar, ObjectHandle hValue)
        {
        return hValue instanceof DeferredCallHandle
                ? hValue.proceed(this, frameCaller ->
                    frameCaller.assignValues(anVar, xBoolean.TRUE, frameCaller.popStack()))
                : assignValues(anVar, xBoolean.TRUE, hValue);
        }

    /**
     * Push a potentially deferred value on the local stack.
     *
     * @param hValue  a value to push
     *
     * @return R_NEXT, R_CALL or R_EXCEPTION
     */
    public int pushDeferredValue(ObjectHandle hValue)
        {
        return hValue instanceof DeferredCallHandle
                ? hValue.proceed(this, frameCaller -> Op.R_NEXT)
                : pushStack(hValue);
        }

    /**
     * Process a void return.
     *
     * @return R_RETURN or R_RETURN_EXCEPTION
     */
    public int returnVoid()
        {
        switch (f_iReturn)
            {
            case Op.A_IGNORE:
            case Op.A_BLOCK:
                return Op.R_RETURN;

            case Op.A_TUPLE:
                return returnValue(f_aiReturn[0], xTuple.H_VOID, false);

            default:
                return returnValue(f_iReturn, xTuple.H_VOID, false);
            }
        }

    /**
     * Assign the return register on the caller's frame.
     *
     * @param hValue    the value to assign
     * @param fDynamic  if true, the value is a RefHandle that may need to be de-referenced unless
     *                  the receiver is a dynamic register itself
     *
     * @return R_RETURN, R_CALL, R_RETURN_EXCEPTION
     */
    public int returnValue(ObjectHandle hValue, boolean fDynamic)
        {
        switch (f_iReturn)
            {
            case Op.A_MULTI:
                assert f_function.isConditionalReturn() && hValue.equals(xBoolean.FALSE);
                return returnValue(f_aiReturn[0], hValue, fDynamic);

            case Op.A_TUPLE:
                Frame        framePrev = f_framePrev;
                int          iReturn   = f_aiReturn[0];
                TypeConstant typeTuple = iReturn < 0
                        ? poolContext().typeTuple0()
                        : framePrev.getVarInfo(iReturn).getType();
                if (fDynamic)
                    {
                    if (framePrev.isDynamicVar(iReturn))
                        {
                        // dynamic -> dynamic Tuple, e.g. @Future Tuple<T> t = f();
                        return returnValue(iReturn, hValue, true);
                        }
                    else
                        {
                        // dynamic -> regular [singular] Tuple
                        RefHandle hRef    = (RefHandle) hValue;
                        int       iResult = hRef instanceof FutureHandle
                            ? ((FutureHandle) hRef).waitAndAssign(framePrev, Op.A_STACK)
                            : hRef.getVarSupport().getReferent(framePrev, hRef, Op.A_STACK);

                        switch (iResult)
                            {
                            case Op.R_NEXT:
                                hValue = popStack();
                                break;

                            case Op.R_CALL:
                                framePrev.m_frameNext.addContinuation(frameCaller ->
                                    frameCaller.assignValue(iReturn,
                                        xTuple.makeImmutableHandle(typeTuple, frameCaller.popStack())));
                                if (m_continuation != null)
                                    {
                                    // transfer the continuation
                                    framePrev.m_frameNext.addContinuation(m_continuation);
                                    }
                                return Op.R_RETURN_CALL;

                            case Op.R_EXCEPTION:
                                return Op.R_RETURN_EXCEPTION;

                            default:
                                throw new IllegalStateException();
                            }
                        }
                    }
                return returnValue(iReturn, xTuple.makeImmutableHandle(typeTuple, hValue), false);

            default:
                return returnValue(f_iReturn, hValue, fDynamic);
            }
        }

    /**
     * Assign the specified register on the caller's frame.
     *
     * @param iReturn   the register id
     * @param hValue    the value to assign
     * @param fDynamic  if true, the value is a RefHandle that may need to be de-referenced unless
     *                  the receiver is a dynamic register itself
     *
     * @return R_RETURN, R_CALL, R_RETURN_EXCEPTION
     */
    public int returnValue(int iReturn, ObjectHandle hValue, boolean fDynamic)
        {
        Frame framePrev = f_framePrev;

        int iResult;
        if (fDynamic)
            {
            if (framePrev.isDynamicVar(iReturn))
                {
                // dynamic -> dynamic
                RefHandle hVar = (RefHandle) framePrev.f_ahVar[iReturn];
                iResult = hVar.getVarSupport().setReferent(framePrev, hVar, hValue);
                }
            else
                {
                // dynamic -> regular
                RefHandle hRef = (RefHandle) hValue;

                iResult = hRef instanceof FutureHandle
                        ? ((FutureHandle) hRef).waitAndAssign(framePrev, iReturn)
                        : hRef.getVarSupport().getReferent(framePrev, hRef, iReturn);
                }
            }
        else
            {
            // regular -> any
            iResult = framePrev.assignValue(iReturn, hValue);
            }

        switch (iResult)
            {
            case Op.R_NEXT:
                return Op.R_RETURN;

            case Op.R_CALL:
                if (m_continuation != null)
                    {
                    // transfer the continuation
                    framePrev.m_frameNext.addContinuation(m_continuation);
                    }
                return Op.R_RETURN_CALL;

            case Op.R_EXCEPTION:
                return Op.R_RETURN_EXCEPTION;

            default:
                throw new IllegalArgumentException("iResult=" + iResult);
            }
        }

    /**
     * Assign the return registers on the caller's frame.
     *
     * @param ahValue    the values to assign
     * @param afDynamic  if not null, indicates witch values are RefHandle(s) that may need to be
     *                   de-referenced unless the receiver is a dynamic register itself
     *
     * @return R_RETURN, R_CALL or R_RETURN_EXCEPTION
     */
    public int returnValues(ObjectHandle[] ahValue, boolean[] afDynamic)
        {
        switch (f_iReturn)
            {
            case Op.A_IGNORE:
            case Op.A_BLOCK:
                return Op.R_RETURN;

            case Op.A_MULTI:
                return new Utils.ReturnValues(f_aiReturn, ahValue, afDynamic).proceed(this);

            case Op.A_TUPLE:
                if (afDynamic == null)
                    {
                    int              iReturn = f_aiReturn[0];
                    ClassComposition clazz   = ensureClass(f_framePrev.getVarInfo(iReturn).getType());

                    return returnValue(iReturn, xTuple.makeHandle(clazz, ahValue), false);
                    }
                else
                    {
                    // TODO: dynamic -> tuple
                    throw new UnsupportedOperationException();
                    }

            default:
                throw new IllegalArgumentException("iReturn=" + f_iReturn);
            }
        }

    /**
     * Assign the return tuple on the caller's frame.
     *
     * @return R_RETURN, R_CALL or R_RETURN_EXCEPTION
     */
    public int returnTuple(TupleHandle hTuple)
        {
        switch (f_iReturn)
            {
            case Op.A_MULTI:
                return returnValues(hTuple.m_ahValue, null);

            case Op.A_TUPLE:
                return returnValue(f_aiReturn[0], hTuple, false);

            default:
                // pass the tuple "as is"
                return returnValue(f_iReturn, hTuple, false);
            }
        }

    // return R_EXCEPTION
    public int raiseException(ExceptionHandle.WrapperException e)
        {
        return raiseException(e.getExceptionHandle());
        }

    // return R_EXCEPTION
    public int raiseException(String sMsg)
        {
        m_hException = xException.makeHandle(this, sMsg);
        return Op.R_EXCEPTION;
        }

    // return R_EXCEPTION
    public int raiseException(ExceptionHandle hException)
        {
        m_hException = hException;
        return Op.R_EXCEPTION;
        }

    /**
     * @return an array of function's local constants
     */
    public Constant[] localConstants()
        {
        assert f_function != null;
        return f_function.getLocalConstants();
        }

    /**
     * @return the ConstantPool to be used to create new constants that may be required by the
     *         run-time execution
     */
    public ConstantPool poolContext()
        {
        return f_context.f_pool;
        }

    /**
     * @return an ObjectHandle for the constant (could be a DeferredCallHandle)
     */
    public ObjectHandle getConstHandle(Constant constant)
        {
        return f_context.f_container.ensureConstHandle(this, constant);
        }

    /**
     * @return an ObjectHandle for the constant id (could be a DeferredCallHandle)
     */
    public ObjectHandle getConstHandle(int iArg)
        {
        return f_context.f_container.ensureConstHandle(this, getConstant(iArg));
        }

    public Constant getConstant(int iArg)
        {
        assert iArg <= Op.CONSTANT_OFFSET;
        return localConstants()[Op.CONSTANT_OFFSET - iArg];
        }

    // return a string value of the specified StringConstant
    public String getString(int iArg)
        {
        StringConstant constText = (StringConstant) getConstant(iArg);
        return constText.getValue();
        }

    /**
     * Obtain the type of the specified argument. Negative argument ids are treated as constants.
     *
     * @param iArg  the argument id
     *
     * @return the type (resolved) of the specified argument or null if the argument points to the
     *         stack
     */
    public TypeConstant getArgumentType(int iArg)
        {
        return iArg >= 0
            ? getRegisterType(iArg)
            : iArg <= Op.CONSTANT_OFFSET
                ? getConstant(iArg).getType()  // a constant cannot be generic
                : getPredefinedArgumentType(iArg, null);
        }

    /**
     * Similarly to {@link #getArgumentType}, obtain the type of the specified argument except
     * that the negative ids are treated as "local-property" references.
     *
     * @param iArg  the argument id
     * @param hArg  in case the argument id points to the stack, indicates the argument itself
     *
     * @return the type (resolved) of the specified argument or null if the argument points to the
     *         stack and the operation is going to push the stack (hArg == null)
     */
    public TypeConstant getLocalType(int iArg, ObjectHandle hArg)
        {
        return iArg >= 0
            ? getRegisterType(iArg)
            : iArg <= Op.CONSTANT_OFFSET
                // "local property" type needs to be resolved
                ? getConstant(iArg).getType().resolveGenerics(poolContext(), getGenericsResolver())
                : getPredefinedArgumentType(iArg, hArg);
        }

    /**
     * Obtain the type of the specified register.
     *
     * @param nRegister  the register
     *
     * @return the actual type of the specified register
     */
    protected TypeConstant getRegisterType(int nRegister)
        {
        VarInfo info = getVarInfo(nRegister);
        if (info.isStandard())
            {
            return info.getType(); // always resolved
            }

        // Ref<Referent> -> Referent
        return info.getType().getParamType(0);
        }

    public ClassComposition resolveClass(int iArg)
        {
        return ensureClass(resolveType(iArg));
        }

    public ClassComposition ensureClass(TypeConstant type)
        {
        return f_context.f_templates.resolveClass(type);
        }

    public ClassTemplate ensureTemplate(IdentityConstant constClz)
        {
        return f_context.f_templates.getTemplate(constClz);
        }

    public TypeConstant resolveType(int iArg)
        {
        return resolveType((TypeConstant) getConstant(iArg)); // must exist
        }

    public TypeConstant resolveType(TypeConstant type)
        {
        ConstantPool pool = poolContext();

        type = type.resolveGenerics(pool, getGenericsResolver());
        if (type.isAutoNarrowing() && f_hThis != null)
            {
            type = type.resolveAutoNarrowing(pool, false, f_hThis.getType());
            }

        if (type.containsFormalType(true))
            {
            // soft assertion
            System.err.println("ERROR: Unresolved type " + type);
            }
        return type;
        }

    /**
     * @return an ObjectHandle (could be a DeferredCallHandle)
     *
     * @throw ExceptionHandle.WrapperException if the value cannot be retrieved
     */
    public ObjectHandle getArgument(int iArg)
                throws ExceptionHandle.WrapperException
        {
        if (iArg >= 0)
            {
            ObjectHandle hValue = f_ahVar[iArg];
            if (hValue == null)
                {
                // there is a possibility this method introduced a default value at the sub class
                // that didn't exist at the base class
                int cDefault = f_function.getDefaultParamCount();
                int cAll     = f_function.getParamCount();
                if (cDefault > 0 && iArg < cAll && iArg >= cAll - cDefault)
                    {
                    return getDefaultArgument(iArg);
                    }

                VarInfo info  = f_aInfo[iArg];
                String  sName = info == null ? "" : info.getName();
                throw xException.illegalState(this,
                    "Unassigned value: \"" + sName + '"').getException();
                }

            if (hValue == ObjectHandle.DEFAULT)
                {
                return getDefaultArgument(iArg);
                }

            VarInfo info = f_aInfo[iArg];
            if (info != null)
                {
                switch (info.getStyle())
                    {
                    case VAR_STANDARD:
                        break;

                    case VAR_DYNAMIC_REF:
                        {
                        RefHandle hRef = (RefHandle) hValue;
                        switch (hRef.getVarSupport().getReferent(this, hRef, Op.A_STACK))
                            {
                            case Op.R_NEXT:
                                return popStack();

                            case Op.R_CALL:
                                return new DeferredCallHandle(m_frameNext);

                            case Op.R_EXCEPTION:
                                throw m_hException.getException();

                            default:
                                throw new IllegalStateException();
                            }
                        }

                    default:
                        throw new IllegalStateException();
                    }
                }
            return hValue;
            }

        return iArg <= Op.CONSTANT_OFFSET
                ? getConstHandle(iArg)
                : getPredefinedArgument(iArg);
        }

    /**
     * @return the default value for the specified argument
     *
     * @throw ExceptionHandle.WrapperException if the default value cannot be retrieved
     */
    private ObjectHandle getDefaultArgument(int iArg)
            throws ExceptionHandle.WrapperException
        {
        Constant constValue = f_function.getParam(iArg).getDefaultValue();
        if (constValue == null)
            {
            throw xException.illegalState(this, "Unknown default value for argument \"" +
                f_function.getParam(iArg).getName() + '"').getException();
            }
        return getConstHandle(constValue);
        }

    /**
     * Unlike getArgument(), this could return a non-completed FutureHandle and it never throws
     *
     * @return an ObjectHandle (could be DeferredCallHandle)
     *
     * @throw ExceptionHandle.WrapperException if the default value cannot be retrieved
     */
    public ObjectHandle getReturnValue(int iArg)
            throws ExceptionHandle.WrapperException
        {
        ObjectHandle hValue = iArg >= 0
                ? f_ahVar[iArg]
                : iArg <= Op.CONSTANT_OFFSET
                        ? getConstHandle(iArg)
                        : getPredefinedArgument(iArg);

        return hValue == ObjectHandle.DEFAULT
                 ? getDefaultArgument(iArg)
                 : hValue;
        }

    /**
     * Create an array of ObjectHandles holding the specified arguments.
     * <p/>
     * Note, that the arguments are retrieved in the inverse order, to allow the
     * {@link org.xvm.compiler.ast.InvocationExpression}, {@link org.xvm.compiler.ast.NewExpression}
     * and {@link org.xvm.compiler.ast.RelOpExpression} to use stack collecting the arguments.
     *
     * @return the array of handles (can contain DeferredCallHandle objects)
     *
     * @throws ExceptionHandle.WrapperException if the async assignment has failed
     */
    public ObjectHandle[] getArguments(int[] aiArg, int cVars)
                throws ExceptionHandle.WrapperException
        {
        int cArgs = aiArg.length;

        ObjectHandle[] ahArg = new ObjectHandle[Math.max(cArgs, cVars)];

        for (int i = cArgs - 1; i >= 0; --i)
            {
            ahArg[i] = getArgument(aiArg[i]);
            }

        return ahArg;
        }

    // check if the specified index points to an unused register
    public boolean isNextRegister(int nVar)
        {
        return nVar >= f_anNextVar[m_iScope];
        }

    /**
     * Introduce a new variable for the specified type, style and an optional value.
     */
    public void introduceResolvedVar(int nVar, TypeConstant type, String sName,
                                     int nStyle, ObjectHandle hValue)
        {
        f_anNextVar[m_iScope] = Math.max(f_anNextVar[m_iScope], nVar + 1);

        VarInfo info = new VarInfo(type, nStyle);
        info.setName(sName);

        f_aInfo[nVar] = info;
        if (hValue != null)
            {
            f_ahVar[nVar] = hValue;
            }
        }

    /**
     * Introduce a new unnamed standard variable for the specified type.
     *
     * Note: this method increments up the "nextVar" index
     *
     * @param nVar       the variable to introduce
     * @param constType  the type constant
     */
    public void introduceResolvedVar(int nVar, TypeConstant constType)
        {
        introduceResolvedVar(nVar, constType, null, VAR_STANDARD, null);
        }

    /**
     * Introduce a new variable for the specified type id, name id style and an optional value.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nVar     the variable to introduce
     * @param nTypeId  an "absolute" (positive, ConstantPool based) number (see Op.convertId())
     */
    public void introduceVar(int nVar, int nTypeId, int nNameId, int nStyle, ObjectHandle hValue)
        {
        f_anNextVar[m_iScope] = Math.max(f_anNextVar[m_iScope], nVar + 1);

        f_aInfo[nVar] = new VarInfo(nTypeId, nNameId, nStyle);

        if (hValue != null)
            {
            f_ahVar[nVar] = hValue;
            }
        }

    /**
     * Introduce a new standard variable for the specified type id.
     *
     * Note: this method increments up the "nextVar" index
     *
     * @param nVar     the variable to introduce
     * @param nTypeId  an "absolute" (positive, ConstantPool based) number (see Op.convertId())
     */
    public void introduceVar(int nVar, int nTypeId)
        {
        introduceVar(nVar, nTypeId, 0, VAR_STANDARD, null);
        }

    /**
     * Introduce a new standard variable by copying the type from the specified argument.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nVar      the variable to introduce
     * @param nVarFrom  if positive, the register number; otherwise a constant id
     */
    public void introduceVarCopy(int nVar, int nVarFrom)
        {
        f_anNextVar[m_iScope] = Math.max(f_anNextVar[m_iScope], nVar + 1);

        if (nVarFrom >= 0)
            {
            VarInfo infoFrom = getVarInfo(nVarFrom);

            f_aInfo[nVar] = infoFrom.m_type == null
                ? new VarInfo(infoFrom.m_nTypeId, 0, VAR_STANDARD)
                : new VarInfo(infoFrom.m_type, VAR_STANDARD);
            }
        else
            {
            // "local property" or a literal constant
            TypeConstant type = getConstant(nVarFrom).getType();

            f_aInfo[nVar] = new VarInfo(type, VAR_STANDARD);
            }
        }

    /**
     * Introduce a new standard variable that has a type of the specified property in the context
     * of the specified target.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nVar       the variable to introduce
     * @param nTargetId  if positive, the register number holding a target (handle);
     *                     otherwise a constant id pointing to local property holding the target
     * @param nPropId    the property constant id (whose type needs to be resolved in the context)
     */
    public void introducePropertyVar(int nVar, int nTargetId, int nPropId)
        {
        f_anNextVar[m_iScope] = Math.max(f_anNextVar[m_iScope], nVar + 1);

        f_aInfo[nVar] = new VarInfo(nTargetId, nPropId, PROPERTY_RESOLVER);
        }

    /**
     * Introduce a new standard variable that has a type of the method return value.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nVar       the variable to introduce
     * @param nMethodId  the method id (if negative - frame specific; otherwise - absolute)
     * @param index      the return value index (-1 for a Tuple)
     */
    public void introduceMethodReturnVar(int nVar, int nMethodId, int index)
        {
        f_anNextVar[m_iScope] = Math.max(f_anNextVar[m_iScope], nVar + 1);

        f_aInfo[nVar] = new VarInfo(nMethodId, index, METHOD_RESOLVER);
        }

    /**
     * Introduce a new standard variable of the "Element" for the specified array variable.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nArrayReg  if positive, the register number holding an array handle;
     *                   otherwise a constant id pointing to an array type
     * @param nIndex     an element's index (for Tuples)
     */
    public void introduceElementVar(int nArrayReg, int nIndex)
        {
        int nVar = f_anNextVar[m_iScope]++;

        f_aInfo[nVar] = new VarInfo(nArrayReg, nIndex, ARRAY_ELEMENT_RESOLVER);
        }

    /**
     * Introduce a new standard variable of the "Referent" type for the specified dynamic var.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nVarReg  the register number holding a dynamic var handle
     */
    public void introduceRefTypeVar(int nVarReg)
        {
        int nVar = f_anNextVar[m_iScope]++;

        f_aInfo[nVar] = new VarInfo(nVarReg, 0, REF_RESOLVER);
        }

    /**
     * @return true if the specified register holds a "dynamic var"
     */
    public boolean isDynamicVar(int nVar)
        {
        return nVar >= 0 && getVarInfo(nVar).isDynamic();
        }

    /**
     * @return the RefHandle for the specified dynamic var
     */
    public RefHandle getDynamicVar(int nVar)
        {
        RefHandle hRef = (RefHandle) f_ahVar[nVar];

        return hRef.isAssigned() ? hRef : null;
        }

    /**
     * @return the VarInfo for the specified register
     */
    public VarInfo getVarInfo(int nVar)
        {
        VarInfo info = f_aInfo[nVar];
        if (info == null)
            {
            int cArgs = f_function.getParamCount();
            if (nVar >= cArgs)
                {
                throw new IllegalStateException("Variable " + nVar + " ouf of scope " + f_function);
                }

            Parameter    param = f_function.getParam(nVar);
            TypeConstant type  = param.getType().resolveGenerics(poolContext(), getGenericsResolver());

            info = f_aInfo[nVar] = new VarInfo(type, VAR_STANDARD);
            info.setName(param.getName());
            }
        return info;
        }

    // construct-finally support
    public void chainFinalizer(FullyBoundHandle hFinalizer)
        {
        if (hFinalizer != null)
            {
            Frame frameTop = this;
            while (frameTop.m_hfnFinally == null)
                {
                frameTop = frameTop.f_framePrev;
                }
            frameTop.m_hfnFinally = hFinalizer.chain(frameTop.m_hfnFinally);
            }
        }

    /**
     * Place the specified continuation to be executed when this frame "returns". The specified
     * continuation will be executed *after* any existing ones (FIFO), but only if all previously
     * registered continuations return normally (R_NEXT or R_CALL).
     *
     * @param continuation  the continuation to add
     */
    public void addContinuation(Continuation continuation)
        {
        if (m_continuation == null)
            {
            m_continuation = continuation;
            }
        else
            {
            if (!(m_continuation instanceof ContinuationChain))
                {
                m_continuation = new ContinuationChain(m_continuation);
                }

            // the new continuation is to be executed after the existing ones (FIFO)
            ((ContinuationChain) m_continuation).add(continuation);
            }
        }


    // ----- GenericTypeResolver interface ---------------------------------------------------------

    public GenericTypeResolver getGenericsResolver()
        {
        return this;
        }

    @Override
    public TypeConstant resolveGenericType(String sFormalName)
        {
        return f_hThis == null
                ? null
                : f_hThis.getType().resolveGenericType(sFormalName);
        }

    @Override
    public TypeConstant resolveFormalType(FormalConstant constFormal)
        {
        MethodStructure method   = f_function;
        MethodConstant  idMethod = method.getIdentityConstant();
        int             nRegister;

        FindRegister:
        switch (constFormal.getFormat())
            {
            case Property:
                {
                String sFormalName = constFormal.getName();
                if (method.getIdentityConstant().isLambda() && method.isStatic())
                    {
                    // generic types are passed to "static" lambdas as type parameters
                    for (int i = 0, c = method.getTypeParamCount(); i < c; i++)
                        {
                        Parameter param = method.getParam(i);

                        if (sFormalName.equals(param.getName()))
                            {
                            nRegister = i;
                            break FindRegister;
                            }
                        }
                    return null;
                    }
                return resolveGenericType(sFormalName);
                }

            case TypeParameter:
                {
                // look for a match only amongst the method's formal type parameters
                TypeParameterConstant constParam = (TypeParameterConstant) constFormal;

                if (!constParam.getMethod().equals(idMethod))
                    {
                    return null;
                    }

                nRegister = constParam.getRegister();
                break;
                }

            case FormalTypeChild:
                throw new UnsupportedOperationException("TODO");

            default:
                throw new IllegalStateException();
            }

        TypeConstant typeType = f_ahVar[nRegister].getType();

        // type parameter's type must be of Type<DataType, OuterType>
        assert typeType.isTypeOfType() && typeType.getParamsCount() >= 1;
        return typeType.getParamType(0);
        }

    // temporary
    public String getStackTrace()
        {
        StringBuilder sb = new StringBuilder();
        Frame frame = this;
        Fiber fiber = frame.f_fiber;
        int iPC = m_iPC;

        if (f_fiber.getStatus() != Fiber.FiberStatus.Running)
            {
            // the exception was caused by the previous op-code
            iPC--;
            }

        while (true)
            {
            sb.append("\n\t")
              .append(formatFrameDetails(frame.f_context, frame.f_function, iPC, frame.f_aOp, frame.f_framePrev));

            iPC   = frame.f_iPCPrev;
            frame = frame.f_framePrev;
            if (frame == null)
                {
                Fiber fiberCaller = fiber.f_fiberCaller;
                if (fiberCaller == null ||
                        fiberCaller.f_context.f_container != fiber.f_context.f_container)
                    {
                    break;
                    }

                frame = fiberCaller.getFrame();

                sb.append("\n    =========");

                if (frame != null)
                    {
                    frame = frame.findCallerFrame(fiber.f_iCallerId);
                    }
                if (frame == null)
                    {
                    // the caller's fiber has moved away from the calling frame;
                    // simply show the calling function
                    MethodStructure fnCaller = fiber.f_fnCaller;
                    sb.append("\n  ")
                      .append(formatFrameDetails(fiberCaller.f_context, fnCaller, -1, null, null));
                    break;
                    }
                fiber = fiberCaller;
                }
            }

        sb.append('\n');

        return sb.toString();
        }

    protected static String formatFrameDetails(ServiceContext ctx, MethodStructure function,
                                               int iPC, Op[] aOp, Frame framePrev)
        {
        StringBuilder sb = new StringBuilder("at ");

        if (function == null)
            {
            if (framePrev == null)
                {
                sb.append('<').append(ctx.f_sName).append('>');
                }
            else
                {
                sb.append("synthetic call by ").append(framePrev);
                if (framePrev.m_iPC >= 0)
                    {
                    sb.append(" at [").append(framePrev.m_iPC).append(']');
                    }
                }
            }
        else
            {
            sb.append(function.getIdentityConstant().getPathString());
            }

        if (iPC >= 0)
            {
            int nLine = 0;
            if (function != null)
                {
                nLine = function.calculateLineNumber(iPC);
                }

            if (nLine > 0)
                {
                sb.append(" (line=").append(nLine);
                }
            else
                {
                sb.append(" (iPC=").append(iPC);
                }
            sb.append(", op=").append(aOp[iPC].getClass().getSimpleName());
            sb.append(')');
            }

        return sb.toString();
        }

    @Override
    public String toString()
        {
        return formatFrameDetails(f_context, f_function, -1, null, f_framePrev);
        }

    // try-catch support
    public abstract static class Guard
        {
        protected final int f_nStartAddress;
        protected final int f_nScope;

        protected Guard(int nStartAddress, int nScope)
            {
            f_nStartAddress = nStartAddress;
            f_nScope        = nScope;
            }

        abstract public int handleException(Frame frame, ExceptionHandle hException, int iGuard);

        /**
         * Drop down to the scope of the exception/finally handler;
         * perform an implicit "enter" with a variable introduction (exception or Null)
         */
        protected void introduceValue(Frame frame, int iGuard, ObjectHandle hValue, String sVarName)
            {
            int nScope = f_nScope;

            frame.clearAllScopes(nScope - 1);

            frame.m_iScope = nScope;
            frame.m_iGuard = iGuard - 1;

            frame.f_anNextVar[nScope] = frame.f_anNextVar[nScope - 1];

            int nVar = frame.f_anNextVar[nScope]++;

            frame.introduceResolvedVar(nVar, hValue.getType(), sVarName, VAR_STANDARD, hValue);

            frame.m_hException = null;
            }
        }

    public static class AllGuard
            extends Guard
        {
        protected final int f_nFinallyStartAddress;

        public AllGuard(int nStartAddress, int nScope, int nFinallyStartOffset)
            {
            super(nStartAddress, nScope);

            f_nFinallyStartAddress = nStartAddress + nFinallyStartOffset;
            }

        @Override
        public int handleException(Frame frame, ExceptionHandle hException, int iGuard)
            {
            introduceValue(frame, iGuard, hException, "");

            // need to jump to the instruction past the FinallyStart
            return f_nFinallyStartAddress + 1;
            }

        protected int handleJump(Frame frame, int iGuard)
            {
            introduceValue(frame, iGuard, xNullable.NULL, "");

            // need to jump to the instruction past the FinallyStart
            return f_nFinallyStartAddress + 1;
            }
        }

    public static class MultiGuard
            extends Guard
        {
        protected final int[] f_anClassConstId;
        protected final int[] f_anNameConstId;
        protected final int[] f_anCatchRelAddress;

        public MultiGuard(int nStartAddress, int nScope, int[] anClassConstId,
                          int[] anNameConstId, int[] anCatchAddress)
            {
            super(nStartAddress, nScope);

            f_anClassConstId    = anClassConstId;
            f_anNameConstId     = anNameConstId;
            f_anCatchRelAddress = anCatchAddress;
            }

        @Override
        public int handleException(Frame frame, ExceptionHandle hException, int iGuard)
            {
            TypeComposition clzException = hException.getComposition();

            for (int iCatch = 0, c = f_anClassConstId.length; iCatch < c; iCatch++)
                {
                ClassComposition clzCatch = frame.resolveClass(f_anClassConstId[iCatch]);
                if (clzException.getType().isA(clzCatch.getType()))
                    {
                    introduceValue(frame, iGuard, hException,
                        frame.getString(f_anNameConstId[iCatch]));

                    return f_nStartAddress + f_anCatchRelAddress[iCatch];
                    }
                }
            return Op.R_EXCEPTION;
            }
        }

    /**
     * A deferred action to be performed by the "FinallyEnd" op.
     */
    public static abstract class DeferredGuardAction
        {
        protected DeferredGuardAction(int ixGuard)
            {
            this(ixGuard, 0);
            }

        protected DeferredGuardAction(int ixGuardStart, int ixGuardBase)
            {
            assert ixGuardStart >= ixGuardBase;

            m_ixGuard     = ixGuardStart;
            m_ixGuardBase = ixGuardBase;
            }

        abstract public int complete(Frame frame);

        /**
         * @return the Guard index for this pseudo handle
         */
        public int getGuardIndex()
            {
            return m_ixGuard;
            }

        /**
         * Set the guard index.
         *
         * @param iGuard  the Guard index
         */
        public void setGuardIndex(int iGuard)
            {
            m_ixGuard = iGuard > m_ixGuardBase
                    ? iGuard
                    : -1;
            }

        private int m_ixGuard;     // the index of the next AllGuard to proceed to
        private int m_ixGuardBase; // the index of the AllGuard to stop at
        }

    // variable into (support for Refs and debugger)
    public class VarInfo
        {
        private int             m_nTypeId;
        private TypeConstant    m_type;
        private int             m_nNameId;
        private String          m_sVarName;
        private int             m_nStyle; // one of the VAR_* values
        private RefHandle       m_ref; // an "active" reference to this register TODO: should be a WeakRef
        private VarTypeResolver m_resolver;
        private int             m_nTargetId; // an id of the target used to resolve this VarInfo's type

        /**
         * Construct an unnamed VarInfo based on the resolved type.
         */
        public VarInfo(TypeConstant type, int nStyle)
            {
            m_type    = type;
            m_nTypeId = type.getPosition();
            m_nStyle  = nStyle;
            }

        /**
         * Construct a named VarInfo with an unresolved type.
         */
        public VarInfo(int nTypeId, int nNameId, int nStyle)
            {
            m_nTypeId = nTypeId;
            m_nNameId = nNameId;
            m_nStyle  = nStyle;
            }

        /**
         * Construct an unresolved VarInfo based on a custom resolver.
         */
        public VarInfo(int nTargetId, int nAuxId, VarTypeResolver resolver)
            {
            m_nTargetId = nTargetId;
            m_nTypeId   = nAuxId;
            m_nStyle    = VAR_STANDARD;
            m_resolver  = resolver;

            assert getType() != null; // side effect of realizing the type
            }

        public String getName()
            {
            String sName = m_sVarName;
            if (sName == null)
                {
                sName = m_sVarName = m_nNameId < 0 ? getString(m_nNameId) : "";
                }
            return sName;
            }

        public void setName(String sName)
            {
            m_sVarName = sName;
            }

        public TypeConstant getType()
            {
            TypeConstant type = m_type;
            if (type == null)
                {
                if (m_resolver == null)
                    {
                    type = (TypeConstant) localConstants()[m_nTypeId];
                    }
                else
                    {
                    type = m_resolver.resolve(Frame.this, m_nTargetId, m_nTypeId);
                    }

                m_type    = type = type.resolveGenerics(poolContext(), getGenericsResolver());
                m_nTypeId = type.getPosition();
                }
            return type;
            }

        public int getStyle()
            {
            return m_nStyle;
            }

        public boolean isStandard()
            {
            return m_nStyle == VAR_STANDARD;
            }

        public boolean isDynamic()
            {
            return m_nStyle == VAR_DYNAMIC_REF;
            }

        public RefHandle getRef()
            {
            return m_ref;
            }

        public void setRef(RefHandle ref)
            {
            m_ref = ref;
            }

        // this VarInfo goes out of scope
        public void release()
            {
            if (m_ref != null)
                {
                m_ref.dereference();
                }
            }

        protected String getStyleName()
            {
            switch (m_nStyle)
                {
                case VAR_STANDARD:
                    return "";
                case VAR_DYNAMIC_REF:
                    return "<dynamic> ";
                default:
                    return "unknown ";
                }
            }

        public String toString()
            {
            return getStyleName() + getName();
            }
        }

    public interface Continuation
        {
        /**
         * Proceed with a deferred execution.
         *
         * @param frameCaller  the frame which has just "returned"
         *
         * @return R_NEXT, R_CALL, R_EXCEPTION or a positive iPC value
         */
        int proceed(Frame frameCaller);
        }

    /**
     * An internal VarInfo type resolver.
     */
    interface VarTypeResolver
        {
        TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId);
        }

    protected static final VarTypeResolver ARRAY_ELEMENT_RESOLVER = new VarTypeResolver()
        {
        /**
         * @param nTargetReg  the register or property holding an array
         * @param iAuxId      the array element index
         */
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            TypeConstant typeArray;
            if (nTargetReg >= 0)
                {
                VarInfo infoArray = frame.getVarInfo(nTargetReg);
                typeArray = infoArray.getType();
                }
            else
                {
                // "local property" or a literal constant
                typeArray = frame.getLocalType(nTargetReg, null);
                }

            return typeArray.getParamType(typeArray.isTuple() ? iAuxId : 0);
            }
        };

    protected static final VarTypeResolver PROPERTY_RESOLVER = new VarTypeResolver()
        {
        /**
         * @param nTargetReg  the register or property to retrieve the property of
         * @param iPropId     the PropertyConstant id (relative)
         */
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iPropId)
            {
            ConstantPool pool = frame.poolContext();

            PropertyConstant constProperty = (PropertyConstant) frame.getConstant(iPropId);
            TypeConstant     typeTarget    = frame.getLocalType(nTargetReg, null);

            return constProperty.isFormalType()
                ? constProperty.getFormalType().resolveGenerics(pool, typeTarget).getType()
                : constProperty.getType().resolveGenerics(pool, typeTarget);
            }
        };

    protected static final VarTypeResolver METHOD_RESOLVER = new VarTypeResolver()
        {
        /**
         * @param nTargetReg  the method constant id to use the return signature of
         *                    (if negative - frame specific; otherwise - absolute)
         * @param iAuxId      the return value index (-1 for a Tuple)
         */
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            ConstantPool pool = frame.poolContext();

            MethodConstant idMethod = (MethodConstant) (nTargetReg < 0
                ? frame.getConstant(nTargetReg)
                : pool.getConstant(nTargetReg));

            return iAuxId >= 0
                ? idMethod.getRawReturns()[iAuxId].
                    resolveGenerics(pool, frame.getGenericsResolver())
                : pool.ensureParameterizedTypeConstant(
                    pool.typeTuple(), idMethod.getSignature().getRawReturns()).
                        resolveGenerics(pool, frame.getGenericsResolver());
            }
        };

    protected static final VarTypeResolver REF_RESOLVER = new VarTypeResolver()
        {
        /**
         * @param nTargetReg  the referent register
         * @param iAuxId      unused
         */
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            TypeConstant typeRef = frame.getVarInfo(nTargetReg).getType();
            return typeRef.resolveGenericType("Referent");
            }
        };

    // ----- TEMPORARY ------

    static final boolean REPORT_WRAPPING = System.getProperties().containsKey("DEBUG");
    }
