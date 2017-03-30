package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * CALL_N1 rvalue-function, #params:(rvalue) lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Call_N1 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;
    private final int f_nRetValue;

    public Call_N1(int nFunction, int[] anArg, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame frameNew;

        if (f_nFunctionValue == A_SUPER)
            {
            MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

            ObjectHandle[] ahVars = new ObjectHandle[methodSuper.m_cVars];

            ObjectHandle hThis = frame.f_ahVars[0];

            ahVars[0] = hThis;
            for (int i = 0, c = f_anArgValue.length; i < c; i++)
                {
                int nArg = f_anArgValue[i];

                ahVars[i + 1] = nArg >= 0 ? frame.f_ahVars[nArg] :
                        resolveConst(frame, methodSuper.m_argTypeName[i + 1], nArg);
                }

            frameNew = new Frame(frame.f_context, frame, hThis, methodSuper, ahVars);
            }
        else
            {
            FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];

            for (int i = 0, c = f_anArgValue.length; i < c; i++)
                {
                int nArg = f_anArgValue[i];

                ahVars[i] = nArg >= 0 ? frame.f_ahVars[nArg] :
                        resolveConst(frame, function.m_argTypeName[i], nArg);
                }

            frameNew = new Frame(frame.f_context, frame, null, function, ahVars);
            }

        ObjectHandle hException = frameNew.execute();

        if (hException == null)
            {
            frame.f_ahVars[f_nRetValue] = frameNew.f_ahReturns[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }