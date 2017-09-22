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
 * CALL_N0 rvalue-function, #params:(rvalue)
 */
public class Call_N0
        extends OpCallable
    {
    private final int f_nFunctionValue;
    private final int[] f_anArgValue;

    public Call_N0(int nFunction, int[] anArg)
        {
        f_nFunctionValue = nFunction;
        f_anArgValue = anArg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_N0(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);
        f_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_CALL_N0);
        writePackedLong(out, f_nFunctionValue);

        writeIntArray(out, f_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_N0;
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

            return chain.callSuperN1(frame, f_anArgValue, Frame.RET_UNUSED);
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

                return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
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

            return hFunction.call1(frame, null, ahVar, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }