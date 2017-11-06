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

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.Function.FunctionHandle;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CALL_TN rvalue-function, rvalue-params-tuple, #returns:(lvalue)
 */
public class Call_TN
        extends OpCallable
    {
    /**
     * Construct a CALL_TN op.
     *
     * @param nFunction   the r-value indicating the function to call
     * @param nTupleArg   the r-value indicating the tuple holding the arguments
     * @param anRetValue  the l-value locations for the result
     *
     * @deprecated
     */
    public Call_TN(int nFunction, int nTupleArg, int[] anRetValue)
        {
        super(null);

        m_nFunctionId = nFunction;
        m_nArgTupleValue = nTupleArg;
        m_anRetValue = anRetValue;
        }

    /**
     * Construct a CALL_TN op based on the passed arguments.
     *
     * @param argFunction  the function Argument
     * @param argValue     the value Argument
     * @param aArgReturn   the return Registers
     */
    public Call_TN(Argument argFunction, Argument argValue, Argument[] aArgReturn)
        {
        super(argFunction);

        m_argValue = argValue;
        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_TN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        m_anRetValue     = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argValue != null)
            {
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            m_anRetValue = encodeArguments(m_aArgReturn, registry);
            }

        writePackedLong(out, m_nArgTupleValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_TN;
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

                checkReturnRegisters(frame, chain.getSuper(frame).getIdentityConstant());

                if (isProperty(hArg))
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                    Frame.Continuation stepNext = frameCaller ->
                        chain.callSuperNN(frameCaller, ((TupleHandle) ahArg[0]).m_ahValue, m_anRetValue);

                    return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                    }
                return chain.callSuperNN(frame, ((TupleHandle) hArg).m_ahValue, m_anRetValue);
                }

            if (m_nFunctionId < 0)
                {
                MethodStructure function = getMethodStructure(frame);

                checkReturnRegisters(frame, function.getIdentityConstant());

                if (isProperty(hArg))
                    {
                    ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                    Frame.Continuation stepNext = frameCaller ->
                        complete(frameCaller, function, (TupleHandle) ahArg[0]);

                    return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                    }

                return complete(frame, function, (TupleHandle) hArg);
                }

            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            checkReturnRegisters(frame, hFunction.getMethodId());

            if (isProperty(hArg))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, hFunction, (TupleHandle) ahArg[0]);

                return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
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
            return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, function.getMaxVars());

        return frame.callN(function, null, ahVar, m_anRetValue);
        }

    protected int complete(Frame frame, FunctionHandle hFunction, TupleHandle hArg)
        {
        ObjectHandle[] ahArg = hArg.m_ahValue;
        if (ahArg.length != hFunction.getParamCount())
            {
            return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, hFunction.getVarCount());

        return hFunction.callN(frame, null, ahVar, m_anRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        registerArguments(m_aArgReturn, registry);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
}