package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.FunctionTemplate;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_01 rvalue-function, lvalue-return
 *
 * @author gg 2017.03.08
 */
public class Call_01 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nRetValue;

    public Call_01(int nFunction, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (f_nFunctionValue == A_SUPER)
            {
            return callSuper01(frame, f_nRetValue);
            }

        if (f_nFunctionValue < 0)
            {
            FunctionTemplate function = getFunctionTemplate(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.m_cVars];

            return frame.call1(function, null, ahVar, f_nRetValue);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, Utils.OBJECTS_NONE, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
