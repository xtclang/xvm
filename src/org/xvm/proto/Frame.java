package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.02.15
 */
public class Frame
        extends Ops
    {
    final ServiceContext f_context;
    final TypeCompositionTemplate.InvocationTemplate f_function;
    final byte[] f_abOps;

    final ObjectHandle[] f_ahArgs; // arguments; arg[0] represents "this"?
    final int f_cArgs;     // the # of arguments
    final ObjectHandle[] f_ahVars; // local vars
    final ObjectHandle[] f_ahReturns; // the return values

    Frame  m_framePrev;

    static final int I_SCOPE = 0;

    Frame(ServiceContext context, TypeCompositionTemplate.InvocationTemplate function, ObjectHandle[] ahArgs, int cVars, int cReturns)
        {
        f_context = context;
        f_function = function;
        f_ahArgs = ahArgs;
        f_cArgs = ahArgs.length;
        f_ahVars = new ObjectHandle[cVars];
        f_ahReturns = new ObjectHandle[cReturns];
        f_abOps = function.m_abOps;
        }

    void processOps()
        {
        // execution-registers; [0] = iScope
        int[] aiRegister = new int[1];
        int[] anScopeNextVar = new int[128]; // at index i, the first var register for scope i

        byte[] abOps = f_abOps;
        OpInfo[] aInfo = s_aInfo;

        int iPC = 0;

        while (true)
            {
            int nOp = abOps[iPC];
            switch (aInfo[nOp].groupId)
                {
                case GROUP_1:
                    iPC = processGroup1(iPC, nOp, aiRegister, anScopeNextVar);
                    break;

                case GROUP_2:
                    iPC = processGroup2(iPC, nOp, aiRegister, anScopeNextVar);
                    break;

                default:
                    throw new RuntimeException("Invalid group " + nOp);                }
            }
        }

    int processGroup1(int iPC, int nOp, int[] aiRegister, int[] anScopeNextVar)
        {
        switch (nOp)
            {
            case ENTER:
                return processENTER(iPC, aiRegister, anScopeNextVar);

            case EXIT:
                return processEXIT(iPC, aiRegister, anScopeNextVar);

            default:
                throw new RuntimeException("Invalid op " + nOp);
            }
        }

    int processENTER(int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        anScopeNextVar[iScope+1] = anScopeNextVar[iScope];
        aiRegister[I_SCOPE] = iScope+1;
        return iPC + 1;
        }

    int processEXIT(int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        aiRegister[I_SCOPE] = --iScope;
        return iPC + 1;
        }

    int processGroup2(int iPC, int nOp, int[] aiRegister, int[] anScopeNextVar)
        {
        switch (nOp)
            {
            case VAR:
                return processVAR(iPC, aiRegister, anScopeNextVar);

            case IVAR:
                return processIVAR(iPC, aiRegister, anScopeNextVar);

            case MOV:
                return processMOV(iPC, aiRegister, anScopeNextVar);

            default:
                throw new RuntimeException("Invalid op " + nOp);
            }
        }

    int processVAR(int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        int nNextVar = anScopeNextVar[iScope];

        int nConstType = f_abOps[++iPC];
        f_ahVars[nNextVar] = f_context.createHandle(nConstType, 0);

        anScopeNextVar[iScope] = nNextVar+1;
        return iPC;
        }

    int processIVAR(int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iScope = aiRegister[I_SCOPE];
        int nNextVar = anScopeNextVar[iScope];

        int nConstType = f_abOps[++iPC];
        int nConstValue = f_abOps[++iPC];
        f_ahVars[nNextVar] = f_context.createHandle(nConstType, nConstValue);

        anScopeNextVar[iScope] = nNextVar+1;
        return iPC;
        }

    int processMOV(int iPC, int[] aiRegister, int[] anScopeNextVar)
        {
        int iL = f_abOps[++iPC];
        int iR = f_abOps[++iPC];
        f_ahVars[iR] = f_ahVars[iL];
        return iPC;
        }
    }
