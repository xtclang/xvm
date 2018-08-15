package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xFunction.FunctionHandle;


/**
 * CALL_N0 rvalue-function, #params:(rvalue)
 */
public class Call_N0
        extends OpCallable
    {
    /**
     * Construct a CALL_N0 op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param anArg      the r-values indicating the arguments
     *
     * @deprecated
     */
    public Call_N0(int nFunction, int[] anArg)
        {
        super(null);

        m_nFunctionId = nFunction;
        m_anArgValue = anArg;
        }

    /**
     * Construct a CALL_N0 op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue     the array of value Arguments
     */
    public Call_N0(Argument argFunction, Argument[] aArgValue)
        {
        super(argFunction);

        m_aArgValue = aArgValue;
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
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            }

        writeIntArray(out, m_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_N0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (m_nFunctionId == A_SUPER)
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

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperN1(frame, ahVar, A_IGNORE, false);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                return chain.callSuperN1(frame, ahVar, A_IGNORE, false);
                }

            if (m_nFunctionId < 0)
                {
                MethodStructure function = getMethodStructure(frame);

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, function.getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        frame.call1(function, null, ahVar, A_IGNORE);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }
                return frame.call1(function, null, ahVar, A_IGNORE);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, hFunction.getVarCount());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (anyDeferred(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    hFunction.call1(frameCaller, null, ahVar, A_IGNORE);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

            return hFunction.call1(frame, null, ahVar, A_IGNORE);
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

    @Override
    protected String getParamsString()
        {
        return getParamsString(m_anArgValue, m_aArgValue);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }