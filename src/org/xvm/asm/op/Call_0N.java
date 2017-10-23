package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;
import org.xvm.asm.Register;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.Function.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;


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
     *
     * @deprecated
     */
    public Call_0N(int nFunction, int[] anRet)
        {
        super((Argument) null);

        m_nFunctionId = nFunction;
        m_anRetValue = anRet;
        }

    /**
     * Construct a CALL_0N op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aRegReturn    the return Registers
     */
    public Call_0N(Argument argFunction, Register[] aRegReturn)
        {
        super(argFunction);

        m_aRegReturn = aRegReturn;
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
        super(in, aconst);

        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aRegReturn != null)
            {
            m_anRetValue = encodeArguments(m_aRegReturn, registry);
            }

        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_0N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (m_nFunctionId == A_SUPER)
            {
            CallChain chain = frame.m_chain;
            if (chain == null)
                {
                throw new IllegalStateException();
                }

            return chain.callSuperNN(frame, Utils.OBJECTS_NONE, m_anRetValue);
            }

        if (m_nFunctionId < 0)
            {
            MethodStructure function = getMethodStructure(frame);

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];

            return frame.callN(function, null, ahVar, m_anRetValue);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.callN(frame, null, Utils.OBJECTS_NONE, m_anRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int[] m_anRetValue;

    private Register[] m_aRegReturn;
    }
