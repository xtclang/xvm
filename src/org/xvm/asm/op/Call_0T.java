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
     *
     * @deprecated
     */
    public Call_0T(int nFunction, int nRet)
        {
        super(nFunction);

        m_nTupleRetValue = nRet;
        }

    /**
     * Construct a CALL_0T op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param regReturn    the return Register
     */
    public Call_0T(Argument argFunction, Register regReturn)
        {
        super(argFunction);

        m_regReturn = regReturn;
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
        super(readPackedInt(in));

        m_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_regReturn != null)
            {
            m_nTupleRetValue = encodeArgument(m_regReturn, registry);
            }

        writePackedLong(out, m_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_0T;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (m_nFunctionValue == A_SUPER)
            {
            CallChain chain = frame.m_chain;
            if (chain == null)
                {
                throw new IllegalStateException();
                }

            return chain.callSuper01(frame, -m_nTupleRetValue - 1);
            }

        if (m_nFunctionValue < 0)
            {
            MethodStructure function = getMethodStructure(frame);

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];

            return frame.call1(function, null, ahVar, -m_nTupleRetValue - 1);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, null, Utils.OBJECTS_NONE, -m_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private int m_nTupleRetValue;

    private Register m_regReturn;
    }
