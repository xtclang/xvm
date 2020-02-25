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

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_T0 rvalue-function, rvalue-params-tuple
 */
public class Call_T0
        extends OpCallable
    {
    /**
     * Construct a CALL_T0 op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param argValue     the value Argument
     */
    public Call_T0(Argument argFunction, Argument argValue)
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
    public Call_T0(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nArgTupleValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_T0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            // while the argument could be a local property holding a Tuple,
            // the Tuple values cannot be local properties
            ObjectHandle hArg = frame.getArgument(m_nArgTupleValue);
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

                if (isDeferred(hArg))
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperNN(frameCaller, ((TupleHandle) ahArg[0]).m_ahValue, Utils.ARGS_NONE);

                    return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                    }
                return chain.callSuperNN(frame, ((TupleHandle) hArg).m_ahValue, Utils.ARGS_NONE);
                }

            if (m_nFunctionId <= CONSTANT_OFFSET)
                {
                MethodStructure function = getMethodStructure(frame);
                if (function == null)
                    {
                    return R_EXCEPTION;
                    }

                if (isDeferred(hArg))
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                    Frame.Continuation stepNext = frameCaller ->
                        complete(frameCaller, function, (TupleHandle) ahArg[0]);

                    return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                    }

                return complete(frame, function, (TupleHandle) hArg);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hArg))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, hFunction, (TupleHandle) ahArg[0]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, hFunction, (TupleHandle) hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, MethodStructure function, TupleHandle hArg)
        {
        ObjectHandle[] ahArg = hArg.m_ahValue;
        if (ahArg.length != function.getParamCount())
            {
            return frame.raiseException("Invalid tuple argument");
            }

        if (function.isNative())
            {
            return getNativeTemplate(frame, function).
                invokeNativeN(frame, function, null, ahArg, A_IGNORE);
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, function.getMaxVars());
        return frame.call1(function, null, ahVar, A_IGNORE);
        }

    protected int complete(Frame frame, FunctionHandle hFunction, TupleHandle hArg)
        {
        ObjectHandle[] ahArg = hArg.m_ahValue;
        if (ahArg.length != hFunction.getParamCount())
            {
            return frame.raiseException("Invalid tuple argument");
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, hFunction.getVarCount());

        return hFunction.call1(frame, null, ahVar, A_IGNORE);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    protected String getParamsString()
        {
        return Argument.toIdString(m_argValue, m_nArgTupleValue);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
    }