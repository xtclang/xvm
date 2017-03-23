package org.xvm.proto;

import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

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
    public final ObjectHandle[] f_ahReturns;    // the return values
    public final Frame          f_framePrev;    // the caller's frame
    public final int[]          f_aiRegister;   // execution registers
                                                // [0] - current scope
                                                // [1] - current guard index (-1 if none)
    public final int[]          f_anNextVar;    // at index i, the "next available" var register for scope i
    public Guard[]              m_aGuard;       // at index i, the guard for the guard index i
    public ObjectHandle         m_hException;

    public Frame(ServiceContext context, Frame framePrev, ObjectHandle hTarget,
                 InvocationTemplate function, ObjectHandle[] ahVars, ObjectHandle[] ahReturns)
        {
        f_context = context;
        f_framePrev = framePrev;
        f_function = function;
        f_hTarget = hTarget;
        f_ahReturns = ahReturns;
        f_ahVars = ahVars; // [0] - target:private for methods
        f_aiRegister = new int[] {0, -1};
        f_anNextVar = new int[f_function.m_cScopes];
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

            switch (iPC)
                {
                case Op.RETURN_EXCEPTION:
                    assert m_hException != null;

                    iPC = findGuard(m_hException);
                    if (iPC >= 0)
                        {
                        // go to the handler
                        continue;
                        }

                    // not handled by this frame
                    return m_hException;

                case Op.RETURN_NORMAL:
                    return null;
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
                    TypeComposition clzCatch = resolveClass(guard.f_anClassConstId[iCatch]);
                    if (clzException.extends_(clzCatch))
                        {
                        int nScope = guard.f_nScope - 1;
                        f_aiRegister[Op.I_SCOPE] = nScope;

                        int nNextVar = f_anNextVar[nScope]++;
                        f_ahVars[nNextVar] = hException;

                        return guard.f_nStartAddress + guard.f_anCatchRelAddress[iCatch];
                        }
                    }
                }
            }
        return -1;
        }

    // TODO: move to ConstPoolAdapter?
    public TypeComposition resolveClass(int nClassConstId)
        {
        ClassConstant constClass = f_context.f_constantPool.getClassConstant(nClassConstId);
        String sClass = ConstantPoolAdapter.getClassName(constClass);

        // TODO: use the generic info when available
        TypeCompositionTemplate template = f_context.f_types.getTemplate(sClass);

        assert template != null;
        return template.f_clazzCanonical;
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
