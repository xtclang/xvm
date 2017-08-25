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

    public Call_01(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_01);
        out.writeInt(f_nFunctionValue);
        out.writeInt(f_nRetValue);
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

            return chain.callSuper01(frame, f_nRetValue);
            }

        if (f_nFunctionValue < 0)
            {
            MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(function)];

            return frame.call1(function, null, ahVar, f_nRetValue);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, null, Utils.OBJECTS_NONE, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
