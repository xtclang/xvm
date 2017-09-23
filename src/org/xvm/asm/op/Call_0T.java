package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.Function.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_0T rvalue-function, lvalue-return-tuple
 */
public class Call_0T
        extends OpCallable
    {
    /**
     * Construct a CALL_0T op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param nRet       the l-value indicating the tuple result location
     */
    public Call_0T(int nFunction, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_nTupleRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_0T(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);
        f_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_0T;
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

            return chain.callSuper01(frame, -f_nTupleRetValue - 1);
            }

        if (f_nFunctionValue < 0)
            {
            MethodStructure function = getMethodStructure(frame, -f_nFunctionValue);

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];

            return frame.call1(function, null, ahVar, -f_nTupleRetValue - 1);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, null, Utils.OBJECTS_NONE, -f_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_CALL_0T);
        writePackedLong(out, f_nFunctionValue);
        writePackedLong(out, f_nTupleRetValue);
        }

    private final int f_nFunctionValue;
    private final int f_nTupleRetValue;
    }
