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

import org.xvm.runtime.template.Function.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_10 rvalue-function, rvalue-param
 */
public class Call_10
        extends OpCallable
    {
    /**
     * Construct a CALL_10 op.
     *
     * @param nFunction  the r-value indicating the function to call
     * @param nArg       the r-value indicating the argument
     *
     * @deprecated
     */
    public Call_10(int nFunction, int nArg)
        {
        super(null);

        m_nFunctionId = nFunction;
        m_nArgValue = nArg;
        }

    /**
     * Construct a CALL_10 op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param argValue     the value Argument
     */
    public Call_10(Argument argFunction, Argument argValue)
        {
        super(argFunction);

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_10(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_10;
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
                        chain.callSuper10(frame, ahArg[0]);

                    return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                    }

                return chain.callSuper10(frame, hArg);
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

        return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
        }

    protected int complete(Frame frame, ObjectHandle hArg, FunctionHandle hFunction)
        {
        ObjectHandle[] ahVar = new ObjectHandle[hFunction.getVarCount()];
        ahVar[0] = hArg;

        return hFunction.call1(frame, null, ahVar, Frame.RET_UNUSED);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }
