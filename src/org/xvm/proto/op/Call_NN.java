package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import org.xvm.proto.template.Function.FunctionHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * CALL_NN rvalue-function, #params:(rvalue) #returns:(lvalue)
 *
 * @author gg 2017.03.08
 */
public class Call_NN extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;
    private final int[] f_anRetValue;

    public Call_NN(int nFunction, int[] anArg, int[] anRet)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
        f_anRetValue = anRet;
        }

    public Call_NN(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        f_anArgValue = readIntArray(in);
        f_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_NN);
        out.writeInt(f_nFunctionValue);
        writeIntArray(out, f_anArgValue);
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

            return chain.callSuperNN(frame, f_anArgValue, f_anRetValue);
            }

        try
            {
            if (f_nFunctionValue < 0)
                {
                MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);

                ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, function.getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                return frame.callN(function, null, ahVar, f_anRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, hFunction.getVarCount());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            return hFunction.callN(frame, null, ahVar, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }