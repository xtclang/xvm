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

import org.xvm.runtime.template.Function.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_NT rvalue-function, #params:(rvalue) lvalue-return-tuple
 */
public class Call_NT
        extends OpCallable
    {
    /**
     * Construct a CALL_NT op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param anArg      the r-values indicating the arguments
     * @param nTupleRet  the l-value location for the tuple result
     */
    public Call_NT(int nFunction, int[] anArg, int nTupleRet)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue     = anArg;
        f_nTupleRetValue = nTupleRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_NT(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);
        f_anArgValue     = readIntArray(in);
        f_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_CALL_NT);
        writePackedLong(out, f_nFunctionValue);
        writeIntArray(out, f_anArgValue);
        writePackedLong(out, f_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_NT;
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

            return chain.callSuperN1(frame, f_anArgValue, -f_nTupleRetValue - 1);
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

                return frame.call1(function, null, ahVar, -f_nTupleRetValue - 1);
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

            return hFunction.call1(frame, null, ahVar, -f_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nFunctionValue;
    private final int[] f_anArgValue;
    private final int   f_nTupleRetValue;
    }