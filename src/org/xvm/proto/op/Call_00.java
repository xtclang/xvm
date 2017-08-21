package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.Utils;

import org.xvm.proto.template.Function.FunctionHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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

    public Call_00(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_00);
        out.writeInt(f_nFunctionValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (f_nFunctionValue == A_SUPER)
            {
            return callSuperNN(frame, Utils.ARGS_NONE, Utils.ARGS_NONE);
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

            return hFunction.call1(frame, null, Utils.OBJECTS_NONE, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
