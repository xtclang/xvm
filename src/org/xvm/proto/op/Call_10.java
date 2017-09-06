package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.Function.FunctionHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * CALL_10 rvalue-function, rvalue-param
 *
 * @author gg 2017.03.08
 */
public class Call_10 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgValue;

    public Call_10(int nFunction, int nArg)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue = nArg;
        }

    public Call_10(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_10);
        out.writeInt(f_nFunctionValue);
        out.writeInt(f_nArgValue);
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

            return chain.callSuper10(frame, f_nArgValue);
            }

        try
            {
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

                return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
            ahVar[0] = hArg;

            return hFunction.call1(frame, null, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
