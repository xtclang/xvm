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
 * CALL_1N rvalue-function, rvalue-param, #returns:(lvalue)
 */
public class Call_1N
        extends OpCallable
    {
    /**
     * Construct a CALL_1N op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param nArg       the r-value of the parameter
     * @param anRet      the l-value locations for the function results
     */
    public Call_1N(int nFunction, int nArg, int[] anRet)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue      = nArg;
        f_anRetValue     = anRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_1N(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);
        f_nArgValue      = readPackedInt(in);
        f_anRetValue     = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(OP_CALL_1N);
        writePackedLong(out, f_nFunctionValue);
        writePackedLong(out, f_nArgValue);
        writeIntArray(out, f_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_1N;
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

            return chain.callSuperNN(frame, new int[]{f_nArgValue}, f_anRetValue);
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

                ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
                ahVar[0] = hArg;

                return frame.callN(function, null, ahVar, f_anRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
            ahVar[0] = hArg;

            return hFunction.callN(frame, null, ahVar, f_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int   f_nFunctionValue;
    private final int   f_nArgValue;
    private final int[] f_anRetValue;
    }
