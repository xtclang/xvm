package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpInvocable;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * FBIND rvalue-fn, #params:(param-index, rvalue-param), lvalue-fn-result
 *
 * @author gg 2017.03.08
 */
public class FBind extends OpInvocable
    {
    private final int f_nFunctionValue;
    private final int[] f_anParamIx;
    private final int[] f_anParamValue;
    private final int f_nResultValue;

    public FBind(int nFunction, int[] nParamIx, int[] nParamValue, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_anParamIx = nParamIx;
        f_anParamValue = nParamValue;
        f_nResultValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);

            for (int i = 0, c = f_anParamIx.length; i < c; i++)
                {
                hFunction = hFunction.bind(f_anParamIx[i], frame.getArgument(f_anParamValue[i]));
                }

            hException = frame.assignValue(f_nResultValue, hFunction);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
