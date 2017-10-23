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
     *
     * @deprecated
     */
    public Call_1N(int nFunction, int nArg, int[] anRet)
        {
        super((Argument) null);

        m_nFunctionId = nFunction;
        m_nArgValue  = nArg;
        m_anRetValue = anRet;
        }

    /**
     * Construct a CALL_1N op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param argValue     the value Argument
     * @param aRegReturn   the return Registers
     */
    public Call_1N(Argument argFunction, Argument argValue, Register[] aRegReturn)
        {
        super(argFunction);

        m_argValue = argValue;
        m_aRegReturn = aRegReturn;
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
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_anRetValue = encodeArguments(m_aRegReturn, registry);
            }

        writePackedLong(out, m_nArgValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_1N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            if (m_nFunctionId == A_SUPER)
                {
                CallChain chain = frame.m_chain;
                if (chain == null)
                    {
                    throw new IllegalStateException();
                    }

                if (isProperty(hArg))
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperNN(frame, ahArg, m_anRetValue);

                    return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                    }

                return chain.callSuperNN(frame, new ObjectHandle[]{hArg}, m_anRetValue);
                }

            if (m_nFunctionId < 0)
                {
                MethodStructure function = getMethodStructure(frame);

                if (isProperty(hArg))
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                    Frame.Continuation stepNext = frameCaller ->
                        complete(frameCaller, ahArg[0], function);

                    return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                    }

                return complete(frame, hArg, function);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hArg))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], hFunction);

                return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, hArg, hFunction);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hArg, MethodStructure function)
        {
        ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];
        ahVar[0] = hArg;

        return frame.callN(function, null, ahVar, m_anRetValue);
        }

    protected int complete(Frame frame, ObjectHandle hArg, FunctionHandle hFunction)
        {
        ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
        ahVar[0] = hArg;

        return hFunction.callN(frame, null, ahVar, m_anRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        }

    private int   m_nArgValue;
    private int[] m_anRetValue;

    private Argument m_argValue;
    private Register[] m_aRegReturn;
    }
