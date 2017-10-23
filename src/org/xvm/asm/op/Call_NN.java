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


/**
 * CALL_NN rvalue-function, #params:(rvalue) #returns:(lvalue)
 */
public class Call_NN
        extends OpCallable
    {
    /**
     * Construct a CALL_NN op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param anArg      the r-values indicating the arguments
     * @param anRet      the l-value locations for the result
     *
     * @deprecated
     */
    public Call_NN(int nFunction, int[] anArg, int[] anRet)
        {
        super(null);

        m_nFunctionId = nFunction;
        m_anArgValue = anArg;
        m_anRetValue = anRet;
        }

    /**
     * Construct a CALL_NN op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param aArgValue    the array of value Arguments
     * @param aRegReturn   the return Register
     */
    public Call_NN(Argument argFunction, Argument[] aArgValue, Register[] aRegReturn)
        {
        super(argFunction);

        m_aArgValue = aArgValue;
        m_aRegReturn = aRegReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_NN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            m_anRetValue = encodeArguments(m_aRegReturn, registry);
            }

        writeIntArray(out, m_anArgValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_NN;
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

                if (anyProperty(ahVar))
                    {
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperNN(frame, ahVar, m_anRetValue);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }

                return chain.callSuperNN(frame, ahVar, m_anRetValue);
                }

            if (m_nFunctionId < 0)
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
                        frame.callN(function, null, ahVar, m_anRetValue);

                    return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                    }
                return frame.callN(function, null, ahVar, m_anRetValue);
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

            if (anyProperty(ahVar))
                {
                Frame.Continuation stepNext = frameCaller ->
                    hFunction.callN(frameCaller, null, ahVar, m_anRetValue);

                return new Utils.GetArguments(ahVar, stepNext).doNext(frame);
                }

            return hFunction.callN(frame, null, ahVar, m_anRetValue);
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
    private int[] m_anRetValue;

    private Argument[] m_aArgValue;
    private Register[] m_aRegReturn;
    }