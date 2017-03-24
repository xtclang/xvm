package org.xvm.proto.op;

import org.xvm.proto.*;

import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;

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
        FunctionTemplate function = getFunctionTemplate(frame, f_nFunctionValue);

        ObjectHandle[] ahVars = new ObjectHandle[function.m_cVars];
        ahVars[0] = f_nArgValue >= 0 ? frame.f_ahVars[f_nArgValue] : resolveConstArgument(frame, 0, f_nArgValue);

        ObjectHandle[] ahRet = new ObjectHandle[1];

        ObjectHandle hException = new Frame(frame.f_context, frame, null, function, ahVars, ahRet).execute();

        if (hException == null)
            {
            frame.f_ahVars[f_nRetValue] = ahRet[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
