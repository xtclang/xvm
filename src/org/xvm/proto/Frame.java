package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class Frame
    {
    public final ServiceContext f_context;
    public final TypeCompositionTemplate.InvocationTemplate f_function;
    public final Op[] f_aop;

    public final ObjectHandle[] f_ahArgs;   // arguments; arg[0] represents "this"?
    public final int f_cArgs;               // the # of arguments
    public final ObjectHandle[] f_ahVars;   // local vars
    public final ObjectHandle[] f_ahReturns; // the return values

    Frame  m_framePrev;

    Frame(ServiceContext context, TypeCompositionTemplate.InvocationTemplate function, ObjectHandle[] ahArgs, int cVars, int cReturns)
        {
        f_context = context;
        f_function = function;
        f_ahArgs = ahArgs;
        f_cArgs = ahArgs.length;
        f_ahVars = new ObjectHandle[cVars];
        f_ahReturns = new ObjectHandle[cReturns];
        f_aop = function.m_aop;
        }

    void processOps()
        {
        int[] aiRegister = new int[1];
        int[] anScopeNextVar = new int[128]; // at index i, the first var register for scope i

        Op[] abOps = f_aop;

        int iPC = 0;

        while (iPC >= 0)
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
            }
        }
    }
