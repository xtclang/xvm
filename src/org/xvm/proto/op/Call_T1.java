package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;
import org.xvm.proto.Adapter;
import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;

import org.xvm.proto.template.collections.xTuple.TupleHandle;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.Function.FunctionHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * CALL_T1 rvalue-function, rvalue-params-tuple, lvalue return
 *
 * @author gg 2017.03.08
 */
public class Call_T1 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgTupleValue;
    private final int f_nRetValue;

    public Call_T1(int nFunction, int nTupleArg, int nRetValue)
        {
        f_nFunctionValue = nFunction;
        f_nArgTupleValue = nTupleArg;
        f_nRetValue = nRetValue;
        }

    public Call_T1(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        f_nArgTupleValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_T1);
        out.writeInt(f_nFunctionValue);
        out.writeInt(f_nArgTupleValue);
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

            return chain.callSuperN1(frame, new int[]{f_nArgTupleValue}, f_nRetValue);
            }

        try
            {
            TupleHandle hArgTuple = (TupleHandle) frame.getArgument(f_nArgTupleValue);
            if (hArgTuple == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahArg = hArgTuple.m_ahValue;

            if (f_nFunctionValue < 0)
                {
                MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);
                if (ahArg.length != Adapter.getArgCount(function))
                    {
                    frame.m_hException = xException.makeHandle("Invalid tuple argument");
                    return R_EXCEPTION;
                    }

                ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(function)];
                System.arraycopy(ahArg, 0, ahVar, 0, ahArg.length);

                return frame.call1(function, null, ahVar, f_nRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];

            if (ahArg.length != Adapter.getArgCount(getMethodStructure(frame, f_nFunctionValue)))
                {
                frame.m_hException = xException.makeHandle("Invalid tuple argument");
                return R_EXCEPTION;
                }

            System.arraycopy(ahArg, 0, ahVar, 0, ahArg.length);

            return hFunction.call1(frame, null, ahVar, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }