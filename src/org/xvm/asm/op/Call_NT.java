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
     *
     * @deprecated
     */
    public Call_NT(int nFunction, int[] anArg, int nTupleRet)
        {
        super(nFunction);

        m_anArgValue = anArg;
        m_nTupleRetValue = nTupleRet;
        }

    /**
     * Construct a CALL_NT op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue    the array of value Arguments
     * @param regReturn    the return Register
     */
    public Call_NT(Argument argFunction, Argument[] aArgValue, Register regReturn)
        {
        super(argFunction);

        m_aArgValue = aArgValue;
        m_regReturn = regReturn;
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
        super(readPackedInt(in));

        m_anArgValue = readIntArray(in);
        m_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_nTupleRetValue = encodeArgument(m_regReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writePackedLong(out, m_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_NT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (m_nFunctionValue == A_SUPER)
                {
                CallChain chain = frame.m_chain;
                if (chain == null)
                    {
                    throw new IllegalStateException();
                    }

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, chain.getSuper(frame).getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                if (anyProperty(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperN1(frame, ahVar, -m_nTupleRetValue - 1);

                    return new Utils.GetArguments(ahVar, new int[]{0}, stepNext).doNext(frame);
                    }

                return chain.callSuperN1(frame, ahVar, -m_nTupleRetValue - 1);
                }

            if (m_nFunctionValue < 0)
                {
                MethodStructure function = getMethodStructure(frame);

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, function.getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                if (anyProperty(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        frame.call1(function, null, ahVar, -m_nTupleRetValue - 1);

                    return new Utils.GetArguments(ahVar, new int[]{0}, stepNext).doNext(frame);
                    }
                return frame.call1(function, null, ahVar, -m_nTupleRetValue - 1);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionValue);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, hFunction.getVarCount());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (anyProperty(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    hFunction.call1(frameCaller, null, ahVar, -m_nTupleRetValue - 1);

                return new Utils.GetArguments(ahVar, new int[]{0}, stepNext).doNext(frame);
                }

            return hFunction.call1(frame, null, ahVar, -m_nTupleRetValue - 1);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    private int[] m_anArgValue;
    private int   m_nTupleRetValue;

    private Argument[] m_aArgValue;
    private Register m_regReturn;
    }