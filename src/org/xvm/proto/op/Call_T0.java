package org.xvm.proto.op;

import org.xvm.asm.MethodStructure;

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
 * CALL_T0 rvalue-function, rvalue-params-tuple
 *
 * @author gg 2017.03.08
 */
public class Call_T0 extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int f_nArgTupleValue;

    public Call_T0(int nFunction, int nTupleArg)
        {
        f_nFunctionValue = nFunction;
        f_nArgTupleValue = nTupleArg;
        }

    public Call_T0(DataInput in)
            throws IOException
        {
        f_nFunctionValue = in.readInt();
        f_nArgTupleValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_T0);
        out.writeInt(f_nFunctionValue);
        out.writeInt(f_nArgTupleValue);
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

            return chain.callSuper10(frame, f_nArgTupleValue);
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
                if (ahArg.length != function.getParamCount())
                    {
                    return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
                    }

                ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
                System.arraycopy(ahArg, 0, ahVar, 0, ahArg.length);

                return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];

            if (ahArg.length != getMethodStructure(frame, f_nFunctionValue).getParamCount())
                {
                return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
                }

            System.arraycopy(ahArg, 0, ahVar, 0, ahArg.length);

            return hFunction.call1(frame, null, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }