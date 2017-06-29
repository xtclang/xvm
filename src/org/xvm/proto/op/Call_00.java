package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xFunction.FunctionHandle;

/**
 * CALL_00 rvalue-function.
 *
 * @author gg 2017.03.08
 */
public class Call_00 extends OpCallable
    {
    private final int f_nFunctionValue;

    public Call_00(int nFunction)
        {
        f_nFunctionValue = nFunction;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (f_nFunctionValue == A_SUPER)
            {
            // in-lined version of "callSuperN"
            MethodStructure methodSuper = Adapter.getSuper(frame.f_function);

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(methodSuper)];

            return frame.call1(methodSuper, frame.getThis(), ahVar, Frame.RET_UNUSED);
            }

        if (f_nFunctionValue < 0)
            {
            MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(function)];

            return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, Utils.OBJECTS_NONE, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
