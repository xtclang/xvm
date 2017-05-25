package org.xvm.proto;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FullyBoundHandle;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xRef.RefHandle;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.JavaLong;

import java.util.function.Supplier;

/**
 * A call stack frame.
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    // VarInfo semantics
    public final ServiceContext f_context;
    public final InvocationTemplate f_function;
    public final Op[]           f_aOp;          // the op-codes
    public final ObjectHandle   f_hTarget;      // target
    public final ObjectHandle[] f_ahVar;        // arguments/local var registers
    public final VarInfo[]      f_aInfo;        // optional info for var registers
    public final int[]          f_aiReturn;     // the indexes for return values
    public final Frame          f_framePrev;    // the caller's frame
    public final int[]          f_aiIndex;      // frame indexes
                                                // [0] - current scope index (starts with 0)
                                                // [1] - current guard index (-1 if none)
    public final int[]          f_anNextVar;    // at index i, the "next available" var register for scope i
    public Guard[]              m_aGuard;       // at index i, the guard for the guard index i
    public ExceptionHandle      m_hException;   // an exception
    public FullyBoundHandle     m_hfnFinally;   // a "finally" method for the constructors
    public ObjectHandle         m_hFrameLocal;  // a "frame local" holding area; assigned by
    public int                  m_iPC;          // the program counter
    public Frame                m_frameNext;    // the next frame to call
    public Supplier<Frame>      m_continuation; // a frame supplier to call after this frame returns

    public final static int R_UNUSED = -1; // an register index for an "unused return value"
    public final static int R_FRAME  = -2; // an register index for the "frame local value"

    public static final int VAR_STANDARD = 0;
    public static final int VAR_DYNAMIC_REF = 1;
    public static final int VAR_DEFERRABLE = 2;

    protected Frame(ServiceContext context, Frame framePrev, InvocationTemplate function,
                    ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        f_context = context;
        f_framePrev = framePrev;
        f_function = function;
        f_aOp = function == null ? Op.STUB : function.m_aop;

        f_hTarget = hTarget;
        f_ahVar = ahVar; // [0] - target:private for methods
        f_aInfo = new VarInfo[ahVar.length];
        f_aiIndex = new int[] {0, -1};

        int cScopes = function == null ? 1 : function.m_cScopes;
        f_anNextVar = new int[cScopes];

        if (hTarget == null)
            {
            f_anNextVar[0] = function == null ? 0 : function.m_cArgs;
            }
        else  // #0 - this:private
            {
            f_ahVar[0]     = hTarget.f_clazz.ensureAccess(hTarget, Access.PRIVATE);
            f_anNextVar[0] = 1 + function.m_cArgs;
            }

        f_aiReturn = aiReturn;
        }
    // a convenience method; ahVar - prepared variables
    public int call1(InvocationTemplate template, ObjectHandle hTarget,
                                 ObjectHandle[] ahVar, int iReturn)
        {
        m_frameNext = f_context.createFrame1(this, template, hTarget, ahVar, iReturn);
        return Op.R_CALL;
        }

    // a convenience method
    public int callN(InvocationTemplate template, ObjectHandle hTarget,
                                 ObjectHandle[] ahVar, int[] aiReturn)
        {
        m_frameNext = f_context.createFrameN(this, template, hTarget, ahVar, aiReturn);
        return Op.R_CALL;
        }

    // find a first matching guard; unwind the scope and initialize the next var with the exception
    // return the PC of the catch or the R_EXCEPTION value
    protected int findGuard(ExceptionHandle hException)
        {
        Guard[] aGuard = m_aGuard;
        if (aGuard != null)
            {
            TypeComposition clzException = hException.f_clazz;

            for (int iGuard = f_aiIndex[Op.I_GUARD]; iGuard >= 0; iGuard--)
                {
                Guard guard = aGuard[iGuard];

                for (int iCatch = 0, c = guard.f_anClassConstId.length; iCatch < c; iCatch++)
                    {
                    TypeComposition clzCatch = f_context.f_types.
                            ensureConstComposition(guard.f_anClassConstId[iCatch]);
                    if (clzException.extends_(clzCatch))
                        {
                        int nScope = guard.f_nScope;

                        clearAllScopes(nScope - 1);

                        // implicit "enter" with an exception variable introduction
                        f_aiIndex[Op.I_SCOPE] = nScope;
                        f_aiIndex[Op.I_GUARD] = iGuard - 1;

                        int nNextVar = f_anNextVar[nScope - 1];

                        CharStringConstant constVarName = (CharStringConstant)
                                f_context.f_constantPool.getConstantValue(guard.f_anNameConstId[iCatch]);

                        introduceVar(nNextVar, clzException, constVarName.getValue(), VAR_STANDARD, hException);

                        f_anNextVar[nScope] = nNextVar + 1;
                        m_hException = null;

                        return guard.f_nStartAddress + guard.f_anCatchRelAddress[iCatch];
                        }
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
            case Op.A_SUPER:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return xFunction.makeHandle(((MethodTemplate) f_function).getSuper()).bind(0, f_hTarget);

            case Op.A_TARGET:
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
            case Op.A_MODULE:
                throw new UnsupportedOperationException("TODO");

            case Op.A_SERVICE:
                return ServiceContext.getCurrentContext().m_hService;

            default:
                throw new IllegalStateException("Invalid argument" + nArgId);
            }
        }

    // clear the var info for the specified scope
    public void clearScope(int iScope)
        {
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

    public ObjectHandle getFrameLocal()
        {
        return m_hFrameLocal;
        }

    public void forceValue(int nVar, ObjectHandle hValue)
        {
        int nResult = assignValue(nVar, hValue);
        if (nResult == Op.R_EXCEPTION)
            {
            // TODO: call an error handler?
            System.out.println("Out-of-context exception: " + m_hException);
            }
        }

    // return Op.R_NEXT or Op.R_EXCEPTION
    public int assignValue(int nVar, ObjectHandle hValue)
        {
        switch (nVar)
            {
            case R_FRAME:
                m_hFrameLocal = hValue;
                // fall through

            case R_UNUSED:
                return Op.R_NEXT;

            default:
                VarInfo info = f_aInfo[nVar];

                switch (info.m_nStyle)
                    {
                    case VAR_DYNAMIC_REF:
                        ExceptionHandle hException = ((RefHandle) f_ahVar[nVar]).set(hValue);
                        if (hException != null)
                            {
                            m_hException = hException;
                            return Op.R_EXCEPTION;
                            }
                        return Op.R_NEXT;

                    case VAR_DEFERRABLE:
                        // take as is
                        break;

                    case VAR_STANDARD:
                        if (hValue instanceof FutureHandle && ((FutureHandle) hValue).f_fSynthetic)
                            {
                            // defer the read
                            info.m_nStyle = VAR_DEFERRABLE;
                            }
                        break;
                    }

                f_ahVar[nVar] = hValue;
                return Op.R_NEXT;
            }
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

            if (info != null)
                {
                switch (info.m_nStyle)
                    {
                    case VAR_DYNAMIC_REF:
                        return ((RefHandle) hValue).get();

                    case VAR_DEFERRABLE:
                        if (hValue instanceof FutureHandle)
                            {
                            FutureHandle hFuture = (FutureHandle) hValue;
                            if (hFuture.f_fSynthetic)
                                {
                                return hFuture.get();
                                }
                            }
                        break;
                    }
                }
            return hValue;
            }

        return iArg < -Op.MAX_CONST_ID ? getPredefinedArgument(iArg) :
            f_context.f_heapGlobal.ensureConstHandle(-iArg);
        }

    // return the ObjectHandle[] or null if the value is "pending future", or
    // throw if the async assignment has failed
    public ObjectHandle[] getArguments(int[] aiArg, int cVars, int ofStart)
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

            ahArg[ofStart + i] = hArg;
            }

        return ahArg;
        }

    // return a non-negative value or -1 if the value is "pending future", or
    // throw if the async assignment has failed
    public long getIndex(int iArg)
            throws ExceptionHandle.WrapperException
        {
        if (iArg >= 0)
            {
            JavaLong hLong = (JavaLong) getArgument(iArg);
            if (hLong == null)
                {
                return -1l;
                }
            return hLong.m_lValue;
            }

        IntConstant constant = (IntConstant)
                f_context.f_heapGlobal.f_constantPool.getConstantValue(-iArg);
        return constant.getValue().getLong();
        }

    public void introduceVar(int nVar, TypeComposition clz, String sName, int nStyle, ObjectHandle hValue)
        {
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
            int cArgs;
            String sName;

            if (f_hTarget == null)
                {
                cArgs = f_function.m_cArgs;
                sName = "<arg " + nVar + ">";
                }
            else
                {
                cArgs = f_function.m_cArgs + 1;
                sName = nVar == 0 ? "<this>" : "<arg " + (nVar - 1) + ">";
                }

            if (nVar >= cArgs)
                {
                throw new IllegalStateException("Variable " + nVar + " ouf of scope " + f_function);
                }

            introduceVar(nVar, f_ahVar[nVar].f_clazz, sName, VAR_STANDARD, null);
            info = f_aInfo[nVar];
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

    // temporary
    public String getStackTrace()
        {
        StringBuilder sb = new StringBuilder();
        Frame frame = this;
        do
            {
            sb.append("\n  - ")
              .append(frame);

            frame = frame.f_framePrev;
            }
        while (frame != null);

        sb.append('\n');

        return sb.toString();
        }

    @Override
    public String toString()
        {
        return "Frame: " + (f_hTarget == null ? "" : f_hTarget) + " " +
                           (f_function == null ? "<none>" : f_function.f_sName);
        }

    // try-catch support
    public static class Guard
        {
        public final int f_nStartAddress;
        public final int f_nScope;
        public final int[] f_anClassConstId;
        public final int[] f_anNameConstId;
        public final int[] f_anCatchRelAddress;

        public Guard(int nStartAddr, int nScope, int[] anClassConstId, int[] anNameConstId, int[] anCatchAddress)
            {
            f_nStartAddress = nStartAddr;
            f_nScope = nScope;
            f_anClassConstId = anClassConstId;
            f_anNameConstId = anNameConstId;
            f_anCatchRelAddress = anCatchAddress;
            }
        }

    // variable into (support for Refs and debugger)
    public static class VarInfo
        {
        public final TypeComposition f_clazz;
        public final String f_sVarName;
        public int m_nStyle; // one of the Op.VAR_* values
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
    }
