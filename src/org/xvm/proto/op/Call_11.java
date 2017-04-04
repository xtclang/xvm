package org.xvm.proto.op;

import org.xvm.proto.*;

import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

import org.xvm.proto.template.xFunction.FunctionHandle;

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
        ObjectHandle hException;
        ObjectHandle[] ahReturn;

        if (f_nFunctionValue == A_SUPER)
            {
            Frame frameNew = createSuperCall(frame, f_nArgValue);

            ahReturn   = frameNew.f_ahReturn;
            hException = frameNew.execute();
            }
        else if (f_nFunctionValue >= 0)
            {
            FunctionHandle function = frame.f_ahVar[f_nFunctionValue].as(FunctionHandle.class);

            hException = function.invoke(frame, frame.f_ahVar,
                    new int[] {f_nArgValue}, ahReturn = new ObjectHandle[1]);
            }
        else
            {
            InvocationTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.m_cVars];

            ahVar[0] = f_nArgValue >= 0 ? frame.f_ahVar[f_nArgValue] :
                    Utils.resolveConst(frame, function.m_argTypeName[0], f_nArgValue);

            Frame frameNew = frame.f_context.createFrame(frame, function, null, ahVar);

            ahReturn   = frameNew.f_ahReturn;
            hException = frameNew.execute();
            }

        if (hException == null)
            {
            frame.f_ahVar[f_nRetValue] = ahReturn[0];
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
