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
 * CALL_0N rvalue-function,  #returns:(lvalue)
 */
public class Call_0N
        extends OpCallable
    {
    /**
     * Construct a CALL_0N op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param anRet      the l-values to store the function results in
     */
    public Call_0N(int nFunction, int[] anRet)
        {
        f_nFunctionValue = nFunction;
        f_anRetValue     = anRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_0N(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);
        f_anRetValue     = readIntArray(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_0N;
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

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_CALL_0N);
        writePackedLong(out, f_nFunctionValue);
        writeIntArray(out, f_anRetValue);
        }

    private final int   f_nFunctionValue;
    private final int[] f_anRetValue;
    }
