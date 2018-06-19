package org.xvm.runtime;


import java.util.concurrent.CompletableFuture;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xFunction;
import org.xvm.runtime.template.xFunction.FullyBoundHandle;
import org.xvm.runtime.template.xRef.RefHandle;

import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;


/**
 * A call stack frame.
 */
public class Frame
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

    private   int                   m_iGuard = -1;  // current guard index (-1 if none)
    private   Guard[]               m_aGuard;       // at index i, the guard for the guard index i

    public  ExceptionHandle         m_hException;   // an exception
    public  FullyBoundHandle        m_hfnFinally;   // a "finally" method for the constructors
    public  Frame                   m_frameNext;    // the next frame to call
    public  Continuation            m_continuation; // a function to call after this frame returns

    public  CallChain               m_chain;        // an invocation call chain
    public  int                     m_nDepth;       // this frame's depth in the call chain

    private ObjectHandle            m_hFrameLocal;  // a "frame local" holding area

    public static final int VAR_STANDARD         = 0;
    public static final int VAR_DYNAMIC_REF      = 1;
    public static final int VAR_STANDARD_WAITING = 2;
    public static final int VAR_DYNAMIC_WAITING  = 3;

    /**
     * Construct a frame.
     *
     * @param iReturn  positive values indicate the caller's frame register
     */
    protected Frame(Frame framePrev, MethodStructure function,
                    ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        assert framePrev != null && function != null;

        f_context = framePrev.f_context;
        f_iId = f_context.m_iFrameCounter++;
        f_fiber = framePrev.f_fiber;

        f_framePrev = framePrev;
        f_iPCPrev = framePrev.m_iPC;

        f_function = function;
        f_aOp      = function.getOps();

        f_hTarget = hTarget;
        f_hThis   = hTarget == null
                    ? null
                    : hTarget.isStruct()
                        ? hTarget
                        : hTarget.revealOrigin();

        f_ahVar = ahVar;
        f_aInfo = new VarInfo[ahVar.length];

        int cScopes = function == null ? 1 : function.getMaxScopes();
        f_anNextVar = new int[cScopes];
        f_anNextVar[0] = function == null ? 0 : function.getParamCount();

        f_iReturn = iReturn;
        f_aiReturn = aiReturn;
        }

    // construct an initial (native) frame
    protected Frame(Fiber fiber, int iCallerPC, Op[] aopNative,
                    ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context = fiber.f_context;

        f_iId = f_context.m_iFrameCounter++;
        f_fiber = fiber;
        f_framePrev = null;
        f_iPCPrev = iCallerPC;
        f_function = null;
        f_aOp = aopNative;

        f_hTarget = f_hThis = null;
        f_ahVar = ahVar;
        f_aInfo = new VarInfo[ahVar.length];

        f_anNextVar = null;

        f_iReturn = iReturn;
        f_aiReturn = aiReturn;
        }

    // construct a native frame with the same target as the caller's target
    protected Frame(Frame framePrev, Op[] aopNative, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context = framePrev.f_context;
        f_iId = f_context.m_iFrameCounter++;
        f_fiber = framePrev.f_fiber;

        f_framePrev = framePrev;
        f_iPCPrev = framePrev.m_iPC;

        f_function = null;
        f_aOp = aopNative;

        f_hTarget = framePrev.f_hTarget;
        f_hThis   = framePrev.f_hThis;

        f_ahVar = ahVar;
        f_aInfo = new VarInfo[ahVar.length];

        f_anNextVar = null;

        f_iReturn = iReturn;
        f_aiReturn = aiReturn;
        }

    // create a new frame that returns zero or one value into the specified slot
    // Note: the returned frame needs to be "initialized" before called
    public Frame createFrame1(MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(this, method, hTarget, ahVar, iReturn, null);
        }

    // create a new frame that returns a Tuple value into the specified slot
    // Note: the returned frame needs to be "initialized" before called
    public Frame createFrameT(MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return new Frame(this, method, hTarget, ahVar, Op.A_TUPLE, new int[] {iReturn});
        }

    // create a new frame that returns multiple values into the specified slots
    // Note: the returned frame needs to be "initialized" before called
    public Frame createFrameN(MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return new Frame(this, method, hTarget, ahVar, Op.A_MULTI, aiReturn);
        }

    // ensure that all the singleton constants are initialized for the specified method
    public Frame ensureInitialized(MethodStructure method, Frame frameNext)
        {
        switch (method.ensureInitialized(this, frameNext))
            {
            case Op.R_NEXT:
                return frameNext;

            case Op.R_CALL:
                return m_frameNext;

            case Op.R_EXCEPTION:
                assert m_hException != null;

                // prime the new frame with an exception;
                // it will be checked at the beginning of the frame execution
                // (an alternative approach is to replace the function's Op[] with
                //  a throwing stub)
                frameNext.m_hException = m_hException;
                m_hException = null;
                return frameNext;

            default:
                throw new IllegalStateException();
            }
        }


    // create a new pseudo-frame on the same target
    public Frame createNativeFrame(Op[] aop, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        return new Frame(this, aop, ahVar, iReturn, aiReturn);
        }

    // a convenience method
    public int call(Frame frameNext)
        {
        m_frameNext = frameNext;
        return Op.R_CALL;
        }

    // a convenience method; ahVar - prepared variables
    public int call1(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        m_frameNext = ensureInitialized(method,
            createFrame1(method, hTarget, ahVar, iReturn));
        return Op.R_CALL;
        }

    // a convenience method; ahVar - prepared variables
    public int callT(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        m_frameNext = ensureInitialized(method,
            createFrameT(method, hTarget, ahVar, iReturn));
        return Op.R_CALL;
        }

    // a convenience method
    public int callN(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        m_frameNext = ensureInitialized(method,
            createFrameN(method, hTarget, ahVar, aiReturn));
        return Op.R_CALL;
        }

    // a convenience method; ahVar - prepared variables
    public int invoke1(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        MethodStructure method = chain.getMethod(nDepth);

        Frame frameNext = createFrame1(chain.getMethod(nDepth), hTarget, ahVar, iReturn);
        frameNext.m_chain = chain;
        frameNext.m_nDepth = nDepth;

        m_frameNext = ensureInitialized(method, frameNext);
        return Op.R_CALL;
        }

    // a convenience method; ahVar - prepared variables
    public int invokeT(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        MethodStructure method = chain.getMethod(nDepth);

        Frame frameNext = createFrameT(method, hTarget, ahVar, iReturn);
        frameNext.m_chain = chain;
        frameNext.m_nDepth = nDepth;

        m_frameNext = ensureInitialized(method, frameNext);
        return Op.R_CALL;
        }

    // a convenience method
    public int invokeN(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        MethodStructure method = chain.getMethod(nDepth);

        Frame frameNext = createFrameN(method, hTarget, ahVar, aiReturn);
        frameNext.m_chain = chain;
        frameNext.m_nDepth = nDepth;

        m_frameNext = ensureInitialized(method, frameNext);
        return Op.R_CALL;
        }

    // start the specified guard as a "current" one
    public void pushGuard(Guard guard)
        {
        Guard[] aGuard = m_aGuard;
        if (aGuard == null)
            {
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

                int iPC = guard.handle(this, hException, iGuard);
                if (iPC >= 0)
                    {
                    return iPC;
                    }
                }
            }
        return Op.R_EXCEPTION;
        }

    // return one of the pre-defined arguments
    public ObjectHandle getPredefinedArgument(int nArgId)
        {
        switch (nArgId)
            {
            case Op.A_STACK:
            // TODO GG implement A_STACK
            // TODO remove A_LOCAL and replace usages with A_STACK instead?
            case Op.A_LOCAL:
                return m_hFrameLocal;

            case Op.A_SUPER:
                ObjectHandle hThis = f_hThis;
                if (hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return xFunction.makeHandle(m_chain, m_nDepth).bind(0, hThis);

            case Op.A_TARGET:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget;

            case Op.A_PUBLIC:
                if (f_hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hThis.ensureAccess(Access.PUBLIC);

            case Op.A_PROTECTED:
                if (f_hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hThis.ensureAccess(Access.PROTECTED);

            case Op.A_PRIVATE:
                if (f_hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hThis;

            case Op.A_STRUCT:
                if (f_hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hThis.ensureAccess(Access.STRUCT);

            case Op.A_SERVICE:
                return ServiceContext.getCurrentContext().m_hService;

            // TODO remove the rest of these?
            case Op.A_THIS:
                if (f_hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hThis;

            case Op.A_TYPE:
                if (f_hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hThis.getType().getTypeHandle();

            case Op.A_MODULE:
                return f_context.f_container.getModule();

            default:
                throw new IllegalStateException("Invalid argument " + nArgId);
            }
        }

    // create a new "current" scope
    public int enterScope()
        {
        int[] anNextVar = f_anNextVar;

        int iScope = ++m_iScope;

        anNextVar[iScope] = anNextVar[iScope-1];

        return iScope;
        }

    // exit the current scope and clear all the var info
    public void exitScope()
        {
        int iScope = m_iScope--;

        int iVarFrom = f_anNextVar[iScope - 1];
        int iVarTo   = f_anNextVar[iScope] - 1;

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
        assert f_hThis != null;
        return f_hThis;
        }

    public GenericTypeResolver getGenericsResolver()
        {
        return f_hThis == null ? f_function : f_hThis.getType();
        }

    public ObjectHandle getFrameLocal()
        {
        return m_hFrameLocal;
        }

    // assign a specified register on this frame
    // return R_NEXT, R_CALL, R_EXCEPTION or R_BLOCK (only if hValue is a FutureVar)
    public int assignValue(int nVar, ObjectHandle hValue)
        {
        assert hValue != null;

        if (nVar >= 0)
            {
            VarInfo info = f_aInfo[nVar];

            switch (info.getStyle())
                {
                case VAR_STANDARD:
                    if (hValue instanceof FutureHandle)
                        {
                        if (info.getType() == hValue.getType())
                            {
                            // TODO: allow hValue to be a subclass?
                            // this can only be a trivial assignment, for example:
                            // @Future Int i1;
                            // @Future Int i2 = i1;
                            break;
                            }

                        FutureHandle hFuture = (FutureHandle) hValue;
                        if (hFuture.isAssigned(this))
                            {
                            return hFuture.getVarSupport().get(this, hFuture, nVar);
                            }

                        // mark the register as "waiting for a result",
                        // blocking the next op-code from being executed
                        // and add a notification
                        CompletableFuture<ObjectHandle> cf = hFuture.m_future;
                        if (cf == null)
                            {
                            // since this ref can only be changed by this service,
                            // we can safely add a completable future now
                            cf = hFuture.m_future = new CompletableFuture();
                            }
                        cf.whenComplete((r, x) -> f_fiber.m_fResponded = true);

                        info.markWaiting();
                        f_ahVar[nVar] = hFuture;
                        return Op.R_BLOCK;
                        }
                    break;

                case VAR_DYNAMIC_REF:
                    {
                    RefHandle hVar = (RefHandle) f_ahVar[nVar];
                    // TODO: check the "weak" assignment (here or inside)
                    return hVar.getVarSupport().set(this, hVar, hValue);
                    }

                case VAR_STANDARD_WAITING:
                case VAR_DYNAMIC_WAITING:
                    // we cannot get here while waiting
                default:
                    throw new IllegalStateException();
                }

            TypeConstant typeFrom = hValue.getType();
            if (typeFrom.getPosition() != info.m_nTypeId) // quick check
                {
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
                        throw new UnsupportedOperationException("TODO - wrap"); // TODO: wrap the handle

                    default:
                        // the compiler/verifier shouldn't have allowed this
                        throw new IllegalStateException();
                    }
                }

            f_ahVar[nVar] = hValue;
            return Op.R_NEXT;
            }

        switch (nVar)
            {
            case Op.A_IGNORE:
                return Op.R_NEXT;

            case Op.A_STACK:
            case Op.A_LOCAL:
                m_hFrameLocal = hValue;
                return Op.R_NEXT;

            default:
                try
                    {
                    // the return value must point to a local property
                    PropertyConstant constProperty = (PropertyConstant) getConstant(nVar);
                    ObjectHandle hThis = getThis();

                    // TODO: check the "weak" assignment (here or inside)
                    return hThis.getTemplate().setPropertyValue(
                            this, hThis, constProperty.getName(), hValue);
                    }
                catch (ClassCastException e)
                    {
                    throw new IllegalArgumentException("nVar=" + nVar);
                    }
            }
        }

    // specialization of assignValue() that takes two return values
    // return R_NEXT, R_CALL, R_EXCEPTION or R_BLOCK (only if any of the values is a FutureVar)
    public int assignValues(int[] anVar, ObjectHandle hValue1, ObjectHandle hValue2)
        {
        int c = anVar.length;
        if (c == 0)
            {
            return Op.R_NEXT;
            }

        if (c == 1)
            {
            return assignValue(anVar[0], hValue1);
            }

        switch (assignValue(anVar[0], hValue1))
            {
            case Op.R_BLOCK:
                {
                switch (assignValue(anVar[1], hValue2))
                    {
                    case Op.R_NEXT:
                    case Op.R_BLOCK:
                        return Op.R_BLOCK;

                    case Op.R_CALL:
                        m_frameNext.setContinuation(frameCaller -> Op.R_BLOCK);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            case Op.R_NEXT:
                return assignValue(anVar[1], hValue2);

            case Op.R_CALL:
                m_frameNext.setContinuation(
                    frameCaller -> assignValue(anVar[1], hValue2));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    // return R_NEXT, R_CALL, R_EXCEPTION or R_BLOCK
    public int assignTuple(int iVar, ObjectHandle[] ahValue)
        {
        TypeComposition clazz = ensureClass(getVarInfo(iVar).getType());

        return assignValue(iVar, xTuple.makeHandle(clazz, ahValue));
        }

    // assign the return register on the caller's frame
    // return R_RETURN, R_CALL, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    public int returnValue(ObjectHandle hValue)
        {
        switch (f_iReturn)
            {
            case Op.A_IGNORE:
                return Op.R_RETURN;

            case Op.A_MULTI:
                throw new IllegalStateException();

            case Op.A_TUPLE:
                return returnAsTuple(new ObjectHandle[]{hValue});

            default:
                return returnValue(f_iReturn, hValue);
            }
        }

    // assign the specified register on the caller's frame
    // return R_RETURN, R_CALL, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    private int returnValue(int iReturn, ObjectHandle hValue)
        {
        int iResult = f_framePrev.assignValue(iReturn, hValue);
        switch (iResult)
            {
            case Op.R_NEXT:
                return Op.R_RETURN;

            case Op.R_CALL:
                m_frameNext.setContinuation(frameCaller -> Op.R_RETURN);
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_RETURN_EXCEPTION;

            case Op.R_BLOCK:
                return Op.R_BLOCK_RETURN;

            default:
                throw new IllegalArgumentException("iResult=" + iResult);
            }
        }

    // return R_RETURN, R_CALL, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    private int returnAsTuple(ObjectHandle[] ahValue)
        {
        assert f_iReturn == Op.A_TUPLE;

        int iReturn = f_aiReturn[0];

        TypeComposition clazz = ensureClass(f_framePrev.getVarInfo(iReturn).getType());
        return returnValue(iReturn, xTuple.makeHandle(clazz, ahValue));
        }

    // assign the return registers on the caller's frame
    // return R_RETURN, R_CALL, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    public int returnValues(ObjectHandle[] ahValue)
        {
        switch (f_iReturn)
            {
            case Op.A_IGNORE:
                return Op.R_RETURN;

            case Op.A_MULTI:
                switch (new Utils.AssignValues(f_aiReturn, ahValue).proceed(f_framePrev))
                    {
                    case Op.R_NEXT:
                        return Op.R_RETURN;

                    case Op.R_EXCEPTION:
                        return Op.R_RETURN_EXCEPTION;

                    case Op.R_BLOCK:
                        return Op.R_BLOCK_RETURN;

                    default:
                        throw new IllegalStateException();
                    }

            case Op.A_TUPLE:
                return returnAsTuple(ahValue);

            default:
                throw new IllegalArgumentException("iReturn=" + f_iReturn);
            }
        }

    // return R_RETURN, R_CALL, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    public int returnTuple(TupleHandle hTuple)
        {
        switch (f_iReturn)
            {
            case Op.A_MULTI:
                return returnValues(hTuple.m_ahValue);

            case Op.A_TUPLE:
                return returnValue(f_aiReturn[0], hTuple);

            default:
                // pass the tuple "as is"
                return returnValue(f_iReturn, hTuple);
            }
        }

    // return R_EXCEPTION
    public int raiseException(ExceptionHandle.WrapperException e)
        {
        return raiseException(e.getExceptionHandle());
        }

    // return R_EXCEPTION
    public int raiseException(ExceptionHandle hException)
        {
        m_hException = hException;
        return Op.R_EXCEPTION;
        }

    public ObjectHandle getConstHandle(int iArg)
        {
        ObjectHandle hValue = f_context.f_heapGlobal.ensureConstHandle(this, getConstant(iArg));
        if (hValue == null)
            {
            throw new IllegalStateException("Unsupported constant " + getConstant(iArg));
            }
        return hValue;
        }

    public Constant getConstant(int iArg)
        {
        assert iArg <= Op.CONSTANT_OFFSET;
        return f_context.f_pool.getConstant(Op.CONSTANT_OFFSET - iArg);
        }

    public int checkWaitingRegisters()
        {
        VarInfo[] aInfo = f_aInfo;
        for (int i = 0, c = aInfo.length; i < c; i++)
            {
            VarInfo info = aInfo[i];

            if (info != null && info.isWaiting())
                {
                FutureHandle hFuture = (FutureHandle) f_ahVar[i];
                if (hFuture.isAssigned(this))
                    {
                    info.stopWaiting();

                    // only standard vars needs to be replaced on the spot;
                    // the dynamic vars will "get" the value naturally on-demand
                    if (info.isStandard())
                        {
                        switch (hFuture.getVarSupport().get(this, hFuture, i))
                            {
                            case Op.R_NEXT:
                                break;

                            case Op.R_EXCEPTION:
                                return Op.R_EXCEPTION;

                            default: // the "standard" future is completely native
                                throw new IllegalStateException();
                            }
                        }
                    }
                else
                    {
                    return Op.R_BLOCK;
                    }
                }
            }

        return Op.R_NEXT;
        }

    // return a string value of the specified StringConstant
    public String getString(int iArg)
        {
        StringConstant constText = (StringConstant) getConstant(iArg);
        return constText.getValue();
        }

    // return the type (resolved) of the specified argument
    public TypeConstant getArgumentType(int iArg)
        {
        return iArg >= 0
            ? getRegisterType(iArg)
            : iArg == Op.A_THIS
                ? f_hThis.getType()            // "this" is always resolved
                : getConstant(iArg).getType(); // a constant cannot be generic
        }

    // same as getArgumentType, but treats the negative ids as "local-property" references
    public TypeConstant getLocalType(int iArg)
        {
        return iArg >= 0
            ? getRegisterType(iArg)
            : iArg == Op.A_THIS
                ? f_hThis.getType() // "this" is always resolved
                // "local property" type needs to be resolved
                : getConstant(iArg).getType().resolveGenerics(getGenericsResolver());
        }

    protected TypeConstant getRegisterType(int iArg)
        {
        VarInfo info = getVarInfo(iArg);
        if (info.isStandard())
            {
            return info.getType(); // always resolved
            }

        // Ref<RefType> -> RefType
        assert !info.isWaiting();
        return info.getType().getParamTypesArray()[0];
        }

    // same as getArgumentClass, but treats the negative ids as "local-property" references
    public TypeComposition getLocalClass(int iArg)
        {
        if (iArg >= 0)
            {
            return ensureClass(getVarInfo(iArg).getType());
            }

        // "local property"
        TypeConstant typeProp = getConstant(iArg).getType();
        return ensureClass(typeProp.resolveGenerics(getGenericsResolver()));
        }

    public TypeComposition resolveClass(int iArg)
        {
        return ensureClass(resolveType(iArg));
        }

    public TypeComposition ensureClass(TypeConstant type)
        {
        return f_context.f_templates.resolveClass(type);
        }

    public ClassTemplate ensureTemplate(IdentityConstant constClz)
        {
        return f_context.f_templates.getTemplate(constClz);
        }

    public TypeConstant resolveType(int iArg)
        {
        TypeConstant type = (TypeConstant) getConstant(iArg); // must exist
        return type.resolveGenerics(getGenericsResolver());
        }

    // return the ObjectHandle, or null if the value is "pending future", or
    // throw if the async assignment has failed
    public ObjectHandle getArgument(int iArg)
                throws ExceptionHandle.WrapperException
        {
        if (iArg >= 0)
            {
            VarInfo info = f_aInfo[iArg];
            ObjectHandle hValue = f_ahVar[iArg];

            if (hValue == null)
                {
                throw xException.makeHandle("Unassigned value").getException();
                }

            if (info != null)
                {
                switch (info.getStyle())
                    {
                    case VAR_STANDARD:
                        break;

                    case VAR_DYNAMIC_REF:
                        {
                        RefHandle hRef = (RefHandle) hValue;
                        switch (hRef.getVarSupport().get(this, hRef, Op.A_LOCAL))
                            {
                            case Op.R_NEXT:
                                return getFrameLocal();

                            case Op.R_CALL:
                                return new DeferredCallHandle(m_frameNext);

                            case Op.R_BLOCK:
                                info.markWaiting();
                                return null;

                            case Op.R_EXCEPTION:
                                throw m_hException.getException();

                            default:
                                throw new IllegalStateException();
                            }
                        }

                    case VAR_STANDARD_WAITING:
                    case VAR_DYNAMIC_WAITING:
                        // we cannot get unblocked until the standard var is assigned
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

    // unlike getArgument(), this could return a non-completed FutureVar
    // and it never throws
    public ObjectHandle getReturnValue(int iArg)
        {
        return iArg >= 0
                ? f_ahVar[iArg]
                : iArg <= Op.CONSTANT_OFFSET
                        ? getConstHandle(iArg)
                        : getPredefinedArgument(iArg);
        }

    // return the ObjectHandle[] or null if the value is "pending future", or
    // throw if the async assignment has failed
    public ObjectHandle[] getArguments(int[] aiArg, int cVars)
                throws ExceptionHandle.WrapperException
        {
        int cArgs = aiArg.length;

        assert cArgs <= cVars;

        ObjectHandle[] ahArg = new ObjectHandle[cVars];

        for (int i = 0; i < cArgs; i++)
            {
            ObjectHandle hArg = getArgument(aiArg[i]);
            if (hArg == null)
                {
                return null;
                }

            ahArg[i] = hArg;
            }

        return ahArg;
        }

    // check if the specified index points to a next available register
    public boolean isNextRegister(int nVar)
        {
        int nNext = f_anNextVar[m_iScope];
        if (nVar < nNext)
            {
            return false;
            }
        if (nVar == nNext)
            {
            return true;
            }
        throw new IllegalStateException("Invalid register index");
        }

    /**
     * Introduce a new variable for the specified type, style and an optional value.
     *
     * Note: this method increments up the "nextVar" index
     */
    public void introduceResolvedVar(TypeConstant type, String sName, int nStyle, ObjectHandle hValue)
        {
        int nVar = f_anNextVar[m_iScope]++;

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
     * @param constType  the type constant
     */
    public void introduceResolvedVar(TypeConstant constType)
        {
        introduceResolvedVar(constType, null, VAR_STANDARD, null);
        }

    /**
     * Introduce a new variable for the specified type id, name id style and an optional value.
     *
     * Note: this method increments up the "nextVar" index
     *
     * @param nTypeId  an "absolute" (positive, ConstantPool based) number (see Op.convertId())
     */
    public void introduceVar(int nTypeId, int nNameId, int nStyle, ObjectHandle hValue)
        {
        int nVar = f_anNextVar[m_iScope]++;

        f_aInfo[nVar] = new VarInfo(nTypeId, nNameId, nStyle);

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
     * @param constType  the type constant
     */
    public void introduceVar(TypeConstant constType)
        {
        introduceVar(constType.getPosition(), 0, VAR_STANDARD, null);
        }

    /**
     * Introduce a new standard variable for the specified type id.
     *
     * Note: this method increments up the "nextVar" index
     *
     * @param nTypeId  an "absolute" (positive, ConstantPool based) number (see Op.convertId())
     */
    public void introduceVar(int nTypeId)
        {
        introduceVar(nTypeId, 0, VAR_STANDARD, null);
        }

    /**
     * Introduce a new standard variable by copying the type from the specified argument.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nVarFrom  if positive, the register number; otherwise a constant id
     */
    public void introduceVarCopy(int nVarFrom)
        {
        int nVar = f_anNextVar[m_iScope]++;

        if (nVarFrom >= 0)
            {
            VarInfo infoFrom = f_aInfo[nVarFrom];

            f_aInfo[nVar] = infoFrom.m_type == null
                ? new VarInfo(infoFrom.m_nTypeId, 0, VAR_STANDARD)
                : new VarInfo(infoFrom.m_type, VAR_STANDARD);
            }
        else
            {
            // "local property" or a literal constant
            TypeConstant type = getConstant(nVarFrom).getType();

            f_aInfo[nVar] = new VarInfo(type.getPosition(), 0, VAR_STANDARD);
            }
        }

    /**
     * Introduce a new standard variable that has a type of the specified property in the context
     * of the specified target.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nTargetId  if positive, the register number holding a target (handle);
     *                     otherwise a constant id pointing to local property holding the target
     * @param constProp  the property constant whose type needs to be resolved in the context
     *                     of the target class
     */
    public void introducePropertyVar(int nTargetId, PropertyConstant constProp)
        {
        int nVar = f_anNextVar[m_iScope]++;

        f_aInfo[nVar] = new VarInfo(nTargetId, constProp.getPosition(), PROPERTY_RESOLVER);
        }

    /**
     * Introduce a new standard variable of the "ElementType" for the specified array variable.
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
     * Introduce a new standard variable of the Ref&lt;ElementType&gt; for the specified array
     * variable.
     *
     * Note: this method increments the "nextVar" index.
     *
     * @param nArrayReg  if positive, the register number holding an array handle;
     *                   otherwise a constant id pointing to an array type
     * @param nIndex     an element's index (for Tuples)
     */
    public void introduceElementRef(int nArrayReg, int nIndex)
        {
        int nVar = f_anNextVar[m_iScope]++;

        f_aInfo[nVar] = new VarInfo(nArrayReg, nIndex, ARRAY_ELEMENT_REF_RESOLVER);
        }

    /**
     * Introduce a new standard variable of the "RefType" type for the specified dynamic var.
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
        return getVarInfo(nVar).isDynamic();
        }

    /**
     * @return the RefHandle for the specified dynamic var
     */
    public RefHandle getDynamicVar(int nVar)
        {
        RefHandle hRef = (RefHandle) f_ahVar[nVar];

        return hRef.isAssigned(this) ? hRef : null;
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

            info = f_aInfo[nVar] = new VarInfo(
                f_function.getParam(nVar).getType().getPosition(), 0, VAR_STANDARD);
            info.setName(f_function.getParam(nVar).getName());
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

    // set the specified continuation to be executed *after* any existing ones,
    // but only if the previous continuations return normally (R_NEXT)
    public void setContinuation(Continuation continuation)
        {
        if (m_continuation == null)
            {
            m_continuation = continuation;
            }
        else
            {
            Continuation[] holder = new Continuation[] {m_continuation};

            // inject the new continuation to be executed after the existing one;
            // if the previous continuation causes another call (R_CALL) and the callee has
            // another continuation then we need to make sure to repeat the injection, proceeding
            // to the provided continuation only when the previous one returns normally (R_NEXT)
            m_continuation = new Continuation()
                {
                public int proceed(Frame frameCaller)
                    {
                    switch (holder[0].proceed(frameCaller))
                        {
                        case Op.R_NEXT:
                            return continuation.proceed(frameCaller);

                        case Op.R_CALL:
                            Frame frameNext = frameCaller.m_frameNext;
                            Continuation contNext = frameNext.m_continuation;

                            if (contNext == null)
                                {
                                frameNext.m_continuation = continuation;
                                }
                            else
                                {
                                holder[0] = contNext;
                                frameNext.m_continuation = this;
                                }
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            assert frameCaller.m_hException != null;
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                };
            }
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
            sb.append("\n  - ")
              .append(formatFrameDetails(f_context, frame.f_function, iPC, frame.f_aOp, frame.f_framePrev));

            iPC = frame.f_iPCPrev;
            frame = frame.f_framePrev;
            if (frame == null)
                {
                Fiber fiberCaller = fiber.f_fiberCaller;
                if (fiberCaller == null)
                    {
                    break;
                    }

                frame = fiberCaller.m_frame;

                sb.append("\n    =========");

                if (frame == null || frame.f_iId != fiber.f_iCallerId)
                    {
                    // the caller's fiber has moved away from the calling frame;
                    // simply show the calling function
                    MethodStructure fnCaller = fiber.f_fnCaller;
                    sb.append("\n  ")
                      .append(formatFrameDetails(fiberCaller.f_context, fnCaller,
                              iPC, fnCaller.getOps(), null));
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
        StringBuilder sb = new StringBuilder("Frame: ");

        if (function == null)
            {
            if (framePrev == null)
                {
                sb.append('<').append(ctx.f_sName).append('>');
                }
            else
                {
                sb.append("proxy for ").append(framePrev);
                }
            }
        else
            {
            sb.append(function.getIdentityConstant().getPathString());
            }

        if (iPC >= 0)
            {
            sb.append(" (iPC=").append(iPC)
              .append(", op=").append(aOp[iPC].getClass().getSimpleName())
              .append(')');
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
            f_nScope = nScope;
            }

        abstract public int handle(Frame frame, ExceptionHandle hException, int iGuard);

        // drop down to the scope of the exception handler;
        // implicit "enter" with an exception variable introduction
        public void introduceException(Frame frame, int iGuard, ExceptionHandle hException, String sVarName)
            {
            int nScope = f_nScope;

            frame.clearAllScopes(nScope - 1);

            frame.m_iScope = nScope;
            frame.m_iGuard = iGuard - 1;

            frame.f_anNextVar[nScope] = frame.f_anNextVar[nScope - 1];

            frame.introduceResolvedVar(hException.getType(), sVarName, VAR_STANDARD, hException);

            frame.m_hException = null;
            }
        }

    public static class AllGuard
            extends Guard
        {
        protected final int f_nFinallyRelAddress;

        public AllGuard(int nStartAddress, int nScope, int nFinallyRelAddress)
            {
            super(nStartAddress, nScope);

            f_nFinallyRelAddress = nFinallyRelAddress;
            }

        public int handle(Frame frame, ExceptionHandle hException, int iGuard)
            {
            introduceException(frame, iGuard, hException, "");

            return f_nStartAddress + f_nFinallyRelAddress;
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

            f_anClassConstId = anClassConstId;
            f_anNameConstId = anNameConstId;
            f_anCatchRelAddress = anCatchAddress;
            }

        public int handle(Frame frame, ExceptionHandle hException, int iGuard)
            {
            TypeComposition clzException = hException.getComposition();

            for (int iCatch = 0, c = f_anClassConstId.length; iCatch < c; iCatch++)
                {
                TypeComposition clzCatch = frame.resolveClass(f_anClassConstId[iCatch]);
                if (clzException.isA(clzCatch))
                    {
                    introduceException(frame, iGuard, hException,
                        frame.getString(f_anNameConstId[iCatch]));

                    return f_nStartAddress + f_anCatchRelAddress[iCatch];
                    }
                }
            return Op.R_EXCEPTION;
            }
        }

    // variable into (support for Refs and debugger)
    public class VarInfo
        {
        private int m_nTypeId;
        private TypeConstant m_type;
        private int m_nNameId;
        private String m_sVarName;
        private int m_nStyle; // one of the VAR_* values
        private RefHandle m_ref; // an "active" reference to this register
        private VarTypeResolver m_resolver;
        private int m_nTargetId; // an id of the target used to resolve this VarInfo's type

        /**
         * Construct a VarInfo based on the resolved type.
         */
        public VarInfo(TypeConstant type, int nStyle)
            {
            m_type = type;
            m_nTypeId = type.getPosition();
            m_nStyle = nStyle;
            }

        /**
         * Construct an unresolved VarIfo.
         */
        public VarInfo(int nTypeId, int nNameId, int nStyle)
            {
            m_nTypeId = nTypeId;
            m_nNameId = nNameId;
            m_nStyle = nStyle;

            assert getType() != null; // side effect or realizing the type
            }

        /**
         * Construct an unresolved VarIfo based on a custom resolver.
         */
        public VarInfo(int nTargetId, int nAuxId, VarTypeResolver resolver)
            {
            m_nTargetId = nTargetId;
            m_nTypeId = nAuxId;
            m_nStyle = VAR_STANDARD;
            m_resolver = resolver;

            assert getType() != null; // side effect or realizing the type
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
                    type = (TypeConstant) f_context.f_pool.getConstant(m_nTypeId);
                    }
                else
                    {
                    type = m_type = m_resolver.resolve(Frame.this, m_nTargetId, m_nTypeId);
                    }

                type = type.resolveGenerics(getGenericsResolver());
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
            return m_nStyle == VAR_DYNAMIC_REF || m_nStyle == VAR_DYNAMIC_WAITING;
            }

        public boolean isWaiting()
            {
            return m_nStyle >= VAR_STANDARD_WAITING;
            }

        public void markWaiting()
            {
            if (m_nStyle < VAR_STANDARD_WAITING)
                {
                // VAR_STANDARD -> VAR_STANDARD_WAITING;
                // VAR_DYNAMIC_REF -> VAR_DYNAMIC_WAITING;
                m_nStyle += 2;
                }
            }

        public void stopWaiting()
            {
            if (m_nStyle >= VAR_STANDARD_WAITING)
                {
                // VAR_STANDARD_WAITING -> VAR_STANDARD;
                // VAR_DYNAMIC_WAITING -> VAR_DYNAMIC_REF;
                m_nStyle -= 2;
                }
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
                m_ref.dereference(Frame.this);
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
                case VAR_STANDARD_WAITING:
                    return "<waiting> ";
                case VAR_DYNAMIC_WAITING:
                    return "<dynamic waiting> ";
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
        // @param frame  the frame which has just "returned"
        // return either R_NEXT, R_CALL or R_EXCEPTION
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
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            TypeConstant typeArray;
            if (nTargetReg >= 0)
                {
                VarInfo infoArray = frame.f_aInfo[nTargetReg];
                typeArray = infoArray.getType();
                }
            else
                {
                // "local property" or a literal constant
                typeArray = frame.getLocalType(nTargetReg);
                }

            if (typeArray.isParamsSpecified())
                {
                return typeArray.isTuple()
                    ? typeArray.getParamTypesArray()[iAuxId]
                    : typeArray.getParamTypesArray()[0];
                }
            return frame.f_context.f_pool.typeObject();
            }
        };

    protected static final VarTypeResolver ARRAY_ELEMENT_REF_RESOLVER = new VarTypeResolver()
        {
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            TypeConstant typeEl = ARRAY_ELEMENT_RESOLVER.resolve(frame, nTargetReg, iAuxId);
            ConstantPool pool = frame.f_context.f_pool;
            return pool.ensureParameterizedTypeConstant(pool.typeRef(), typeEl);
            }
        };

    protected static final VarTypeResolver PROPERTY_RESOLVER = new VarTypeResolver()
        {
        // nTargetReg - the target register (or property)
        // nAuxId  - the PropertyConstant id
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            ConstantPool pool = frame.f_context.f_pool;

            PropertyConstant constProperty = (PropertyConstant) pool.getConstant(iAuxId);
            TypeConstant typeTarget = frame.getLocalType(nTargetReg);

            return typeTarget.isGenericType(constProperty.getName())
                ? pool.ensureParameterizedTypeConstant(pool.typeType(),
                    constProperty.getFormalType().resolveGenerics(typeTarget))
                : constProperty.getType().resolveGenerics(typeTarget);
            }
        };

    protected static final VarTypeResolver REF_RESOLVER = new VarTypeResolver()
        {
        @Override
        public TypeConstant resolve(Frame frame, int nTargetReg, int iAuxId)
            {
            TypeConstant typeRef = frame.getVarInfo(nTargetReg).getType();
            return typeRef.getGenericParamType("RefType");
            }
        };
    }
