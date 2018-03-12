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

import org.xvm.runtime.template.xFunction.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_N1 rvalue-function, #params:(rvalue) lvalue-return
 */
public class Call_N1
        extends OpCallable
    {
    /**
     * Construct a CALL_N1 op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param anArg      the r-values indicating the arguments
     * @param nRet       the l-value location for the result
     *
     * @deprecated
     */
    public Call_N1(int nFunction, int[] anArg, int nRet)
        {
        super(null);

        m_nFunctionId = nFunction;
        m_anArgValue = anArg;
        m_nRetValue  = nRet;
        }

    /**
     * Construct a CALL_N1 op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue    the array of value Arguments
     * @param argReturn    the return Argument
     */
    public Call_N1(Argument argFunction, Argument[] aArgValue, Argument argReturn)
        {
        super(argFunction);

        m_aArgValue = aArgValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_N1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        m_nRetValue  = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_N1;
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

                checkReturnRegister(frame, chain.getSuper(frame));

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, chain.getSuper(frame).getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperN1(frame, ahVar, m_nRetValue, false);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                return chain.callSuperN1(frame, ahVar, m_nRetValue, false);
                }

            if (m_nFunctionId < 0)
                {
                MethodStructure function = getMethodStructure(frame);

                ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, function.getMaxVars());
                if (ahVar == null)
                    {
                    return R_REPEAT;
                    }

                checkReturnRegister(frame, function);

                if (anyDeferred(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        frame.call1(function, null, ahVar, m_nRetValue);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }
                return frame.call1(function, null, ahVar, m_nRetValue);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            checkReturnRegister(frame, hFunction.getMethod());

            ObjectHandle[] ahVar = frame.getArguments(m_anArgValue, hFunction.getVarCount());
            if (ahVar == null)
                {
                return R_REPEAT;
                }

            if (anyDeferred(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    hFunction.call1(frameCaller, null, ahVar, m_nRetValue);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

            return hFunction.call1(frame, null, ahVar, m_nRetValue);
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
        registerArgument(m_argReturn, registry);
        }

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }