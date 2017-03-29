package org.xvm.proto.op;

import org.xvm.proto.*;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;
import org.xvm.proto.TypeCompositionTemplate.MethodTemplate;

/**
 * CALL_11 rvalue-function, rvalue-param, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Call_11 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Call_11(int nFunction, int nArg, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        Frame frameNew;

        if (f_nFunctionValue == A_SUPER)
            {
            ObjectHandle hThis = frame.f_ahVars[0];
            MethodTemplate methodSuper = ((MethodTemplate) frame.f_function).getSuper();

            ObjectHandle[] ahVars = new ObjectHandle[methodSuper.m_cVars];

            ahVars[0] = hThis;
            ahVars[1] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] :
                    resolveConst(frame, methodSuper.m_argTypeName[0], f_nArgValue);

            frameNew = new Frame(frame.f_context, frame, hThis, methodSuper, ahVars);
            }
        else
            {
            InvocationTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

            ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];

            ahVars[0] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] :
                    resolveConst(frame, function.m_argTypeName[0], f_nArgValue);

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
