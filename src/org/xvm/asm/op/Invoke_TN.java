package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_TN rvalue-target, CONST-METHOD, rvalue-params-tuple, #returns:(lvalue)
 */
public class Invoke_TN
        extends OpInvocable
    {
    /**
     * Construct an NVOK_TN op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param aArgReturn   the array of Registers to move the results into
     */
    public Invoke_TN(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument[] aArgReturn)
        {
        super(argTarget, constMethod);

        m_argValue = argValue;
        m_aArgReturn = aArgReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_TN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

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
        return OP_NVOK_TN;
        }

    @Override
    protected boolean isMultiReturn()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg    = frame.getArgument(m_nArgTupleValue);

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    resolveTuple(frameCaller, ahTarget[0], hArg);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }

            return resolveTuple(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveTuple(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        // Tuple values cannot be local properties
        if (isDeferred(hArg))
            {
            ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
            Frame.Continuation stepNext = frameCaller ->
                complete(frameCaller, hTarget, ((TupleHandle) ahArg[0]).m_ahValue);

            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

        return complete(frame, hTarget, ((TupleHandle) hArg).m_ahValue);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        checkReturnRegisters(frame, hTarget);

        return getCallChain(frame, hTarget).invoke(frame, hTarget, ahArg, m_anRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
    }
