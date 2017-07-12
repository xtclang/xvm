package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;


import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_1N rvalue-function, rvalue-param, #returns:(lvalue)
 *
 * @author gg 2017.03.08
 */
public class Call_1N extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgValue;
    private final int[] f_anRetValue;

    public Call_1N(int nFunction, int nArg, int[] anRet)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue = nArg;
        f_anRetValue = anRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (f_nFunctionValue == A_SUPER)
                {
                return callSuperNN(frame, new int[]{f_nArgValue}, f_anRetValue);
                }

            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            if (f_nFunctionValue < 0)
                {
                MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);

                ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(function)];
                ahVar[0] = hArg;

                return frame.callN(function, null, ahVar, f_anRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
            ahVar[0] = hArg;

            return hFunction.callN(frame, null, ahVar, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
