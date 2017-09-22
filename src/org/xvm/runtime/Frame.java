package org.xvm.runtime;


import java.util.Collections;
import java.util.Map;

import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.IntConstant;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.Function;
import org.xvm.runtime.template.Function.FullyBoundHandle;
import org.xvm.runtime.template.Ref.RefHandle;

import org.xvm.runtime.template.annotations.xFutureRef.FutureHandle;

import org.xvm.runtime.template.collections.xTuple;


/**
 * A call stack frame.
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    public final Adapter         f_adapter; // TEMPORARY
    public final Fiber           f_fiber;
    public final ServiceContext  f_context;      // same as f_fiber.f_context
    public final MethodStructure f_function;
    public final Op[]            f_aOp;          // the op-codes
    public final Constant[]      f_aconst;       // local constants
    public final ObjectHandle    f_hTarget;      // target
    public final ObjectHandle[]  f_ahVar;        // arguments/local var registers
    public final VarInfo[]       f_aInfo;        // optional info for var registers
    public final int             f_iReturn;      // an index for a single return value;
    // a negative value below RET_LOCAL indicates an
    // automatic tuple conversion into a (-i-1) register
    public final int[]           f_aiReturn;     // indexes for multiple return values
    public final Frame           f_framePrev;    // the caller's frame
    public final int             f_iPCPrev;      // the caller's PC (used only for async reporting)
    public final int             f_iId;          // the frame's id (used only for async reporting)
    public final int[]           f_anNextVar;
    // at index i, the "next available" var register for scope i

    public int m_iScope;       // current scope index (starts with 0)
    public int m_iGuard = -1;  // current guard index (-1 if none)
    public  int              m_iPC;          // the program counter
    public  Guard[]          m_aGuard;       // at index i, the guard for the guard index i
    public  ExceptionHandle  m_hException;   // an exception
    public  FullyBoundHandle m_hfnFinally;   // a "finally" method for the constructors
    public  Frame            m_frameNext;    // the next frame to call
    public  Continuation     m_continuation; // a function to call after this frame returns
    public  CallChain        m_chain;        // an invocation call chain
    public  int              m_nDepth;       // this frame's depth in the call chain
    private ObjectHandle     m_hFrameLocal;  // a "frame local" holding area

    // positive return values indicate a caller's frame register
    // negative value above RET_LOCAL indicate an automatic tuple conversion
    public final static int RET_LOCAL  = -65000;
    // an indicator for the "frame local single value"
    public final static int RET_UNUSED = -65001;  // an indicator for an "unused return value"
    public final static int RET_MULTI  = -65002;   // an indicator for "multiple return values"

    // the first of the multiple return into the "frame local"
    public final static int[] RET_FIRST_LOCAL = new int[] {RET_LOCAL};

    public final static Constant[] NO_CONSTS = new Constant[0];

    public static final int VAR_STANDARD    = 0;
    public static final int VAR_DYNAMIC_REF = 1;
    public static final int VAR_WAITING     = 2;

    // construct a frame
    protected Frame(Frame framePrev, MethodStructure function,
            ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn, int[] aiReturn)
        {
        f_context = framePrev.f_context;
        f_iId = f_context.m_iFrameCounter++;
        f_fiber = framePrev.f_fiber;

        f_framePrev = framePrev;
        f_iPCPrev = framePrev.m_iPC;

        f_adapter = f_context.f_types.f_adapter;

        f_function = function;
        f_aOp      = function == null ? Op.STUB : function.getOps();
        f_aconst   = function == null ? NO_CONSTS : function.getLocalConstants();

        f_hTarget = hTarget;

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
        f_adapter = f_context.f_types.f_adapter;
        f_iId = f_context.m_iFrameCounter++;
        f_fiber = fiber;
        f_framePrev = null;
        f_iPCPrev = iCallerPC;
        f_function = null;
        f_aOp = aopNative;
        f_aconst = NO_CONSTS;       // TODO review

        f_hTarget = null;
        f_ahVar = ahVar;
        f_aInfo = new VarInfo[ahVar.length];

        f_anNextVar = null;

        f_iReturn = iReturn;
        f_aiReturn = aiReturn;
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
        m_frameNext = f_context.createFrame1(this, method, hTarget, ahVar, iReturn);
        return Op.R_CALL;
        }

    // a convenience method
    public int callN(MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        m_frameNext = f_context.createFrameN(this, method, hTarget, ahVar, aiReturn);
        return Op.R_CALL;
        }

    // a convenience method; ahVar - prepared variables
    public int invoke1(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        Frame frameNext = m_frameNext = f_context.createFrame1(this,
                chain.getMethod(nDepth), hTarget, ahVar, iReturn);
        frameNext.m_chain = chain;
        frameNext.m_nDepth = nDepth;
        return Op.R_CALL;
        }

    // a convenience method
    public int invokeN(CallChain chain, int nDepth, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        Frame frameNext = m_frameNext = f_context.createFrameN(this,
                chain.getMethod(nDepth), hTarget, ahVar, aiReturn);
        frameNext.m_chain = chain;
        frameNext.m_nDepth = nDepth;
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
            case Op.A_LOCAL:
                return m_hFrameLocal;

            case Op.A_SUPER:
                ObjectHandle hThis = f_hTarget;
                if (hThis == null)
                    {
                    throw new IllegalStateException();
                    }
                return Function.makeHandle(m_chain, m_nDepth).bind(0, f_hTarget);

            case Op.A_TARGET: // same as this:private; never used
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget;

            case Op.A_PUBLIC:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PUBLIC);

            case Op.A_PROTECTED:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PROTECTED);

            case Op.A_PRIVATE:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PRIVATE);

            case Op.A_STRUCT:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.STRUCT);

            case Op.A_TYPE:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }

            case Op.A_FRAME:
                throw new UnsupportedOperationException("TODO");

            case Op.A_MODULE:
                return f_context.f_container.getModule();

            case Op.A_SERVICE:
                return ServiceContext.getCurrentContext().m_hService;

            default:
                throw new IllegalStateException("Invalid argument" + nArgId);
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
        assert f_hTarget != null;
        return f_hTarget;
        }

    public Map<String, Type> getActualTypes()
        {
        if (f_hTarget == null)
            {
            // TODO: do we need to collect formal type parameters for a function?
            return Collections.EMPTY_MAP;
            }
        return f_hTarget.f_clazz.f_mapGenericActual;
        }

    public ObjectHandle getFrameLocal()
        {
        return m_hFrameLocal;
        }

    // return R_NEXT, R_EXCEPTION or R_BLOCK (only if hValue is a FutureRef)
    public int assignValue(int nVar, ObjectHandle hValue)
        {
        if (nVar >= 0)
            {
            VarInfo info = f_aInfo[nVar];

            switch (info.m_nStyle)
                {
                case VAR_STANDARD:
                    if (hValue instanceof FutureHandle)
                        {
                        if (info.f_clazz == hValue.f_clazz)
                            {
                            // TODO: allow hValue to be a subclass?
                            // this can only be a trivial assignment, for example:
                            // @Future Int i1;
                            // @Future Int i2 = i1;
                            break;
                            }

                        FutureHandle hFuture = (FutureHandle) hValue;
                        if (hFuture.isDone())
                            {
                            try
                                {
                                hValue = hFuture.get();
                                }
                            catch (ExceptionHandle.WrapperException e)
                                {
                                return raiseException(e);
                                }
                            }
                        else
                            {
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

                            f_ahVar[nVar] = hFuture;
                            info.m_nStyle = VAR_WAITING;
                            return Op.R_BLOCK;
                            }
                        }
                    break;

                case VAR_DYNAMIC_REF:
                    {
                    ExceptionHandle hException = ((RefHandle) f_ahVar[nVar]).set(hValue);
                    return hException == null ? Op.R_NEXT : raiseException(hException);
                    }

                default:
                    throw new IllegalStateException();
                }

            f_ahVar[nVar] = hValue;
            return Op.R_NEXT;
            }

        switch (nVar)
            {
            case RET_UNUSED:
                return Op.R_NEXT;

            case RET_LOCAL:
                m_hFrameLocal = hValue;
                return Op.R_NEXT;

            default:
                throw new IllegalArgumentException("nVar=" + nVar);
            }
        }

    // specialization of assignValue() that takes up to two return values
    // return R_NEXT, R_EXCEPTION or R_BLOCK (only if any of the values is a FutureRef)
    public int assignValues(int[] anVar, ObjectHandle hValue1, ObjectHandle hValue2)
        {
        int c = anVar.length;
        if (c == 0)
            {
            return Op.R_NEXT;
            }

        int iResult1 = assignValue(anVar[0], hValue1);
        if (iResult1 == Op.R_EXCEPTION || c == 1 || hValue2 == null)
            {
            return iResult1;
            }

        if (iResult1 == Op.R_BLOCK)
            {
            int iResult2 = assignValue(anVar[1], hValue2);
            return iResult2 == Op.R_EXCEPTION ? Op.R_EXCEPTION : Op.R_BLOCK;
            }

        return assignValue(anVar[1], hValue2);
        }

    // return R_RETURN, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    public int returnValue(int iReturn, int iArg)
        {
        assert iReturn >= 0 || iReturn == RET_LOCAL;

        int iResult = f_framePrev.assignValue(iReturn, getReturnValue(iArg));
        switch (iResult)
            {
            case Op.R_EXCEPTION:
                return Op.R_RETURN_EXCEPTION;

            case Op.R_BLOCK:
                return Op.R_BLOCK_RETURN;

            case Op.R_NEXT:
                return Op.R_RETURN;

            default:
                throw new IllegalArgumentException("iResult=" + iResult);
            }
        }

    // return R_RETURN, R_RETURN_EXCEPTION or R_BLOCK_RETURN
    public int returnTuple(int iReturn, int[] aiArg)
        {
        assert iReturn >= 0;

        int c = aiArg.length;
        ObjectHandle[] ahValue = new ObjectHandle[c];
        for (int i = 0; i < c; i++)
            {
            ahValue[i] = getReturnValue(aiArg[i]);
            }

        TypeComposition clazz = f_framePrev.getVarInfo(iReturn).f_clazz;
        int iResult = f_framePrev.assignValue(iReturn, xTuple.makeHandle(clazz, ahValue));
        switch (iResult)
            {
            case Op.R_EXCEPTION:
                return Op.R_RETURN_EXCEPTION;

            case Op.R_BLOCK:
                return Op.R_BLOCK_RETURN;

            case Op.R_NEXT:
                return Op.R_RETURN;

            default:
                throw new IllegalArgumentException("iResult=" + iResult);
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

    private ObjectHandle getReturnValue(int iArg)
        {
        return iArg >= 0
                ? f_ahVar[iArg]
                : iArg <= Op.CONSTANT_OFFSET
                        ? getConstant(iArg)
                        : getPredefinedArgument(iArg);

        }

    private ObjectHandle getConstant(int iArg)
        {
        return f_context.f_heapGlobal.ensureConstHandle(this, Op.CONSTANT_OFFSET - iArg);
        }

    public int checkWaitingRegisters()
        {
        ExceptionHandle hException = null;

        VarInfo[] aInfo = f_aInfo;
        for (int i = 0, c = aInfo.length; i < c; i++)
            {
            VarInfo info = aInfo[i];

            if (info != null && info.m_nStyle == VAR_WAITING)
                {
                FutureHandle hFuture = (FutureHandle) f_ahVar[i];
                if (hFuture.isDone())
                    {
                    try
                        {
                        f_ahVar[i] = hFuture.get();
                        }
                    catch (ExceptionHandle.WrapperException e)
                        {
                        // use just the last exception
                        hException = e.getExceptionHandle();
                        }
                    info.m_nStyle = VAR_STANDARD;
                    }
                else
                    {
                    return Op.R_BLOCK;
                    }
                }
            }

        return hException == null ? Op.R_NEXT : raiseException(hException);
        }

    // return the class of the specified argument
    public TypeComposition getArgumentClass(int iArg)
        {
        return iArg >= 0 ? getVarInfo(iArg).f_clazz :
            f_context.f_heapGlobal.getConstTemplate(-iArg).f_clazzCanonical;          // TODO review iArg
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

            if (info != null && info.m_nStyle == VAR_DYNAMIC_REF)
                {
                hValue = ((RefHandle) hValue).get();

                if (hValue == null)
                    {
                    info.m_nStyle = VAR_WAITING;
                    }
                }

            return hValue;
            }

        return iArg < -Op.MAX_CONST_ID ? getPredefinedArgument(iArg) :
            f_context.f_heapGlobal.ensureConstHandle(this, -iArg);
        }

    // return the ObjectHandle[] or null if the value is "pending future", or
    // throw if the async assignment has failed
    public ObjectHandle[] getArguments(int[] aiArg, int cVars)
                throws ExceptionHandle.WrapperException
        {
        int cArgs = aiArg.length;

        assert cArgs <= cVars;

        ObjectHandle[] ahArg = new ObjectHandle[cVars];

        for (int i = 0, c = cArgs; i < c; i++)
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

    // return a non-negative value or -1 if the value is "pending future", or
    // throw if the async assignment has failed
    public long getIndex(int iArg)
            throws ExceptionHandle.WrapperException
        {
        long lIndex;
        if (iArg >= 0)
            {
            JavaLong hLong = (JavaLong) getArgument(iArg);
            if (hLong == null)
                {
                return -1l;
                }
            lIndex = hLong.m_lValue;
            }
        else
            {
            IntConstant constant = (IntConstant) f_context.f_pool.getConstant(-iArg);
            lIndex = constant.getValue().getLong();
            }

        if (lIndex < 0)
            {
            throw IndexSupport.outOfRange(lIndex, 0).getException();
            }
        return lIndex;
        }

    // Note: this method increments up the "nextVar" index
    public void introduceVar(TypeComposition clz, String sName, int nStyle, ObjectHandle hValue)
        {
        int nVar = f_anNextVar[m_iScope]++;

        f_aInfo[nVar] = new VarInfo(clz, sName, nStyle);

        if (hValue != null)
            {
            f_ahVar[nVar] = hValue;
            }
        }

    public VarInfo getVarInfo(int nVar)
        {
        VarInfo info = f_aInfo[nVar];
        if (info == null)
            {
            int cArgs = f_function.getParamCount();
            String sName = "<arg " + nVar + ">";

            if (nVar >= cArgs)
                {
                throw new IllegalStateException("Variable " + nVar + " ouf of scope " + f_function);
                }

            info = f_aInfo[nVar] = new VarInfo(f_ahVar[nVar].f_clazz, sName, VAR_STANDARD);
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

    public void setContinuation(Continuation continuation)
        {
        if (m_continuation == null)
            {
            m_continuation = continuation;
            }
        else
            {
            Continuation[] holder = new Continuation[] {m_continuation};

            // inject the new continuation before the existing one;
            // if the previous continuation causes another call (R_CALL) and the callee has another
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
              .append(formatFrameDetails(f_context, frame.f_function, iPC, frame.f_aOp));

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
                              iPC, fnCaller.getOps()));
                    break;
                    }
                fiber = fiberCaller;
                }
            }

        sb.append('\n');

        return sb.toString();
        }

    protected static String formatFrameDetails(ServiceContext ctx, MethodStructure function,
                                               int iPC, Op[] aOp)
        {
        StringBuilder sb = new StringBuilder("Frame: ");

        if (function == null)
            {
            sb.append('<').append(ctx.f_sName).append('>');
            }
        else
            {
            Component container = function.getParent().getParent();

            sb.append(container.getName())
                    .append('.')
                    .append(function.getName());

            while (!(container instanceof ClassStructure))
                {
                container = container.getParent();
                sb.insert(0, container.getName() + '.');
                }
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
        return formatFrameDetails(f_context, f_function, -1, null);
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

            frame.introduceVar(hException.f_clazz, sVarName, VAR_STANDARD, hException);

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
            introduceException(frame, iGuard, hException, null);

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
            ServiceContext context = frame.f_context;
            TypeComposition clzException = hException.f_clazz;

            for (int iCatch = 0, c = f_anClassConstId.length; iCatch < c; iCatch++)
                {
                TypeComposition clzCatch = context.f_types.
                        ensureComposition(f_anClassConstId[iCatch], frame.getActualTypes());
                if (clzException.isA(clzCatch))
                    {
                    StringConstant constVarName = (StringConstant)
                            context.f_pool.getConstant(f_anNameConstId[iCatch]);

                    introduceException(frame, iGuard, hException, constVarName.getValue());

                    return f_nStartAddress + f_anCatchRelAddress[iCatch];
                    }
                }
            return Op.R_EXCEPTION;
            }
        }

    // variable into (support for Refs and debugger)
    public static class VarInfo
        {
        public final TypeComposition f_clazz;
        public final String f_sVarName;
        public int m_nStyle; // one of the VAR_* values
        public RefHandle m_ref; // an "active" reference to this register

        public VarInfo(TypeComposition clazz, String sName, int nStyle)
            {
            f_clazz = clazz;
            f_sVarName = sName;
            m_nStyle = nStyle;
            }

        // this VarInfo goes out of scope
        public void release()
            {
            if (m_ref != null)
                {
                m_ref.dereference();
                }
            }
        }

    public interface Continuation
        {
        // @param frame  the frame which has just "returned"
        // return either R_NEXT, R_CALL or R_EXCEPTION
        int proceed(Frame frameCaller);
        }
    }
