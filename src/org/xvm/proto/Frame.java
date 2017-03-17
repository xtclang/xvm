package org.xvm.proto;

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
    public final int[]          f_anRetTypeId;  // the return types
    public final Frame          f_framePrev;
    public ObjectHandle         m_hException;

    public Frame(ServiceContext context, Frame framePrev, ObjectHandle hTarget,
                 TypeCompositionTemplate.InvocationTemplate function, ObjectHandle[] ahVars, ObjectHandle[] ahReturns)
        {
        f_context = context;
        f_framePrev = framePrev;
        f_function = function;
        f_hTarget = hTarget;
        f_anRetTypeId = function.m_anRetTypeId;
        f_ahReturns = ahReturns;
        f_ahVars = ahVars; // [0] - target:private for methods
        }

    public ObjectHandle execute()
        {
        int[] aiRegister = new int[1]; // current scope at index 0
        int[] anScopeNextVar = new int[f_function.m_cScopes]; // at index i, the first var register for scope i

        anScopeNextVar[0] = f_function.m_cArgs;

        Op[] abOps = f_function.m_aop;

        if (f_hTarget != null)
            {
            f_ahVars[0] = f_hTarget; // TODO: replace with this:private
            anScopeNextVar[0]++; // this
            }

        int iPC = 0;

        while (true)
            {
            Op op = abOps[iPC];

            if (op == null)
                {
                iPC++;
                }
            else
                {
                iPC = op.process(this, iPC, aiRegister, anScopeNextVar);
                }

            switch (iPC)
                {
                case Op.RETURN_EXCEPTION:
                    assert m_hException != null;

                    // iPC = findGuard()
                    if (iPC < 0)
                        {
                        // not handled by this frame
                        return m_hException;
                        }
                    break;

                case Op.RETURN_NORMAL:
                    return null;
                }
            }
        }

    @Override
    public String toString()
        {
        return "Frame:" + (f_hTarget == null ? "" : f_hTarget) + " " + f_function.f_sName;
        }
    }
