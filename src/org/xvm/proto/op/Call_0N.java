package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.CallChain;
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
 * CALL_0N rvalue-function,  #returns:(lvalue)
 *
 * @author gg 2017.03.08
 */
public class Call_0N extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anRetValue;

    public Call_0N(int nFunction, int[] anRet)
        {
        f_nFunctionValue = nFunction;
        f_anRetValue = anRet;
        }

    public Call_0N(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        f_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_0N);
        out.writeInt(f_nFunctionValue);
        writeIntArray(out, f_anRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (f_nFunctionValue == A_SUPER)
            {
            CallChain chain = frame.m_chain;
            if (chain == null)
                {
                throw new IllegalStateException();
                }

            return chain.callSuperNN(frame, Utils.ARGS_NONE, f_anRetValue);
            }

        if (f_nFunctionValue < 0)
            {
            MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];

            return frame.callN(function, null, ahVar, f_anRetValue);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.callN(frame, null, Utils.OBJECTS_NONE, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
