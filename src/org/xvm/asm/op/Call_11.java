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
 * CALL_11 rvalue-function, rvalue-param, lvalue-return
 */
public class Call_11
        extends OpCallable
    {
    /**
     * Construct a CALL_11 op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param nArg       the r-value indicating the argument
     * @param nRet       the l-value location for the result
     */
    public Call_11(int nFunction, int nArg, int nRet)
        {
        f_nFunctionValue = nFunction;
        f_nArgValue      = nArg;
        f_nRetValue      = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_11(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nFunctionValue = readPackedInt(in);
        f_nArgValue      = readPackedInt(in);
        f_nRetValue      = readPackedInt(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_11;
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

            return chain.callSuperN1(frame, new int[]{f_nArgValue}, f_nRetValue);
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

                return frame.call1(function, null, ahVar, f_nRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(f_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
            ahVar[0] = hArg;

            return hFunction.call1(frame, null, ahVar, f_nRetValue);
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
        out.writeByte(OP_CALL_11);
        writePackedLong(out, f_nFunctionValue);
        writePackedLong(out, f_nArgValue);
        writePackedLong(out, f_nRetValue);
        }

    private final int f_nFunctionValue;
    private final int f_nArgValue;
    private final int f_nRetValue;
    }
