package org.xvm.proto;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;
import org.xvm.proto.TypeCompositionTemplate.Access;
import org.xvm.proto.template.xFunction;

/**
 * A call stack frame.
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    public final ServiceContext f_context;
    public final TypeCompositionTemplate.InvocationTemplate f_function;

    public final ObjectHandle   f_hTarget;      // target
    public final ObjectHandle[] f_ahVars;       // arguments/local vars (index 0 for target:private)
    public final ObjectHandle[] f_ahReturns;    // the return value(s)
    public final Frame          f_framePrev;    // the caller's frame
    public final int[]          f_aiRegister;   // execution registers
                                                // [0] - current scope
                                                // [1] - current guard index (-1 if none)
    public final int[]          f_anNextVar;    // at index i, the "next available" var register for scope i
    public Guard[]              m_aGuard;       // at index i, the guard for the guard index i
    public ObjectHandle         m_hException;   // an exception

    public Frame(ServiceContext context, Frame framePrev, ObjectHandle hTarget,
                 InvocationTemplate function, ObjectHandle[] ahVars)
        {
        f_context = context;
        f_framePrev = framePrev;
        f_function = function;
        f_hTarget = hTarget;
        f_ahVars = ahVars; // [0] - target:private for methods
        f_aiRegister = new int[] {0, -1};
        f_anNextVar = new int[f_function.m_cScopes];

        int c = function.m_cReturns;
        f_ahReturns = c == 0 ? Utils.OBJECTS_NONE : new  ObjectHandle[c];
        }

    public ObjectHandle execute()
        {
        Op[] abOps = f_function.m_aop;

        if (f_hTarget == null)
            {
            f_anNextVar[0] = f_function.m_cArgs;
            }
        else  // #0 - this:private
            {
            f_ahVars[0]    = f_hTarget; // TODO: replace with this:private
            f_anNextVar[0] = 1 + f_function.m_cArgs;
            }

        for (int iPC = 0; true; )
            {
            Op op = abOps[iPC];

            if (op == null)
                {
                iPC++;
                }
            else
                {
                iPC = op.process(this, iPC);
                }

            if (iPC < 0)
                {
                if (iPC == Op.RETURN_NORMAL)
                    {
                    return null;
                    }

                // Op.RETURN_EXCEPTION:
                assert m_hException != null;

                iPC = findGuard(m_hException);
                if (iPC >= 0)
                    {
                    // handled exception; go to the handler
                    continue;
                    }

                // not handled by this frame
                return m_hException;
                }
            }
        }

    // find a first matching guard; unwind the scope and initialize the next var with the exception
    // return the PC of the catch
    private int findGuard(ObjectHandle hException)
        {
        Guard[] aGuard = m_aGuard;
        if (aGuard != null)
            {
            TypeComposition clzException = hException.f_clazz;

            for (int iGuard = f_aiRegister[Op.I_GUARD]; iGuard >= 0; iGuard--)
                {
                Guard guard = aGuard[iGuard];

                for (int iCatch = 0, c = guard.f_anClassConstId.length; iCatch < c; iCatch++)
                    {
                    TypeComposition clzCatch = f_context.f_types.
                            ensureConstComposition(guard.f_anClassConstId[iCatch]);
                    if (clzException.extends_(clzCatch))
                        {
                        int nScope = guard.f_nScope - 1;
                        f_aiRegister[Op.I_SCOPE] = nScope;
                        f_aiRegister[Op.I_GUARD] = iGuard - 1;

                        int nNextVar = f_anNextVar[nScope]++;
                        f_ahVars[nNextVar] = hException;

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
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.Public);

            case Op.A_PROTECTED:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.Protected);

            case Op.A_PRIVATE:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.Private);

            case Op.A_STRUCT:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
                return f_hTarget.f_clazz.ensureAccess(f_hTarget, Access.Struct);

            case Op.A_TYPE:
                if (f_hTarget == null)
                    {
                    throw new IllegalStateException();
                    }
            case Op.A_FRAME:
            case Op.A_SERVICE:
            case Op.A_MODULE:
                throw new UnsupportedOperationException();

            default:
                throw new IllegalStateException("Invalid argument" + nArgId);
            }
        }

    @Override
    public String toString()
        {
        return "Frame:" + (f_hTarget == null ? "" : f_hTarget) + " " + f_function.f_sName;
        }

    public static class Guard
        {
        public final int f_nStartAddress;
        public final int f_nScope;
        public final int[] f_anClassConstId;
        public final int[] f_anCatchRelAddress;

        public Guard(int nStartAddr, int nScope, int[] anClassConstId, int[] anCatchAddress)
            {
            f_nStartAddress = nStartAddr;
            f_nScope = nScope;
            f_anClassConstId = anClassConstId;
            f_anCatchRelAddress = anCatchAddress;
            }
        }
    }
