package org.xvm.proto;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.CharStringConstant;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FullyBoundHandle;
import org.xvm.proto.template.xFutureRef.FutureHandle;
import org.xvm.proto.template.xRef.RefHandle;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

/**
 * A call stack frame.
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    public final ServiceContext f_context;
    public final InvocationTemplate f_function;

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

    protected Frame(ServiceContext context, Frame framePrev, InvocationTemplate function,
                    ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        f_context = context;
        f_framePrev = framePrev;
        f_function = function;
        f_hTarget = hTarget;
        f_ahVar = ahVar; // [0] - target:private for methods
        f_aInfo = new VarInfo[ahVar.length];
        f_aiIndex = new int[] {0, -1};

        int cScopes = function == null ? 1 : function.m_cScopes;
        f_anNextVar = new int[cScopes];

        f_aiReturn = aiReturn;
        }

    public ExceptionHandle execute()
        {
        f_context.m_frameCurrent = this;

        Op[] abOps = f_function.m_aop;

        if (f_hTarget == null)
            {
            f_anNextVar[0] = f_function.m_cArgs;
            }
        else  // #0 - this:private
            {
            f_ahVar[0]     = f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.PRIVATE);
            f_anNextVar[0] = 1 + f_function.m_cArgs;
            }

        for (int iPC = 0; true; )
            {
            Op op = abOps[iPC];

            try
                {
                iPC = op.process(this, iPC);
                }
            catch (RuntimeException e)
                {
                System.out.println("!!! frame " + this); // TODO: remove
                throw e;
                }

            if (iPC < 0)
                {
                if (iPC == Op.RETURN_NORMAL)
                    {
                    f_context.m_frameCurrent = f_framePrev;
                    return null;
                    }

                // Op.RETURN_EXCEPTION:
                assert m_hException != null;

                iPC = findGuard(m_hException);
                if (iPC >= 0)
                    {
                    // handled exception; go to the handler
                    m_hException = null;
                    continue;
                    }

                // not handled by this frame
                f_context.m_frameCurrent = f_framePrev;
                return m_hException;
                }
            }
        }

    // a convenience method; ahVar - prepared variables
    public ExceptionHandle call1(InvocationTemplate template, ObjectHandle hTarget,
                                 ObjectHandle[] ahVar, int iReturn)
        {
        return f_context.createFrame1(this, template, hTarget, ahVar, iReturn).execute();
        }

    // a convenience method
    public ExceptionHandle callN(InvocationTemplate template, ObjectHandle hTarget,
                                 ObjectHandle[] ahVar, int[] aiReturn)
        {
        return f_context.createFrameN(this, template, hTarget, ahVar, aiReturn).execute();
        }

    // find a first matching guard; unwind the scope and initialize the next var with the exception
    // return the PC of the catch
    private int findGuard(ExceptionHandle hException)
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
                        f_ahVar[nNextVar] = hException;
                        f_aInfo[nNextVar] = new VarInfo(clzException, constVarName.getValue());

                        f_anNextVar[nScope] = nNextVar + 1;

                        return guard.f_nStartAddress + guard.f_anCatchRelAddress[iCatch];
                        }
                    }
                }
            }
        return -1;
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
            Frame.VarInfo info = f_aInfo[i];

            if (info != null)
                {
                info.release();

                f_aInfo[i] = null;
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
            Frame.VarInfo info = f_aInfo[i];

            if (info != null)
                {
                info.release();

                f_aInfo[i] = null;
                }
            }
        }

    // return "private:this"
    public ObjectHandle getThis()
        {
        return f_ahVar[0];
        }

    public ObjectHandle getArgument(int iArg)
                throws ExceptionHandle.WrapperException
        {
        if (iArg >= 0)
            {
            Frame.VarInfo info = f_aInfo[iArg];

            return info != null && info.m_fDynamicRef ?
                    ((RefHandle) f_ahVar[iArg]).get() : f_ahVar[iArg];
            }
        else
            {
            return iArg < -Op.MAX_CONST_ID ? getPredefinedArgument(iArg) :
                f_context.f_heapGlobal.ensureConstHandle(-iArg);
            }
        }

    public ObjectHandle[] getArguments(int[] aiArg, int cVars, int ofStart)
                throws ExceptionHandle.WrapperException
        {
        int cArgs = aiArg.length;

        assert cArgs <= cVars;

        ObjectHandle[] ahArg = new ObjectHandle[cVars];

        for (int i = 0, c = cArgs; i < c; i++)
            {
            ahArg[ofStart + i] = getArgument(aiArg[i]);
            }

        return ahArg;
        }

    public ExceptionHandle assignValue(int nVar, ObjectHandle hValue)
        {
        VarInfo info = f_aInfo[nVar];

        if (info.m_fDynamicRef)
            {
            return ((RefHandle) f_ahVar[nVar]).set(hValue);
            }

        if (hValue instanceof FutureHandle)
            {
            FutureHandle hFuture = (FutureHandle) hValue;
            if (hFuture.f_fSynthetic)
                {
                try
                    {
                    hValue = hFuture.get();
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return e.getExceptionHandle();
                    }
                }
            }

        f_ahVar[nVar] = hValue;
        return null;
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
        public RefHandle m_ref; // an "active" reference to this register
        public boolean m_fDynamicRef; // true iff this variable is a "dynamic" ref

        public VarInfo(TypeComposition clazz)
            {
            this(clazz, null);
            }

        public VarInfo(TypeComposition clazz, String sName)
            {
            f_clazz = clazz;
            f_sVarName = sName;
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
