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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_10 rvalue-target, rvalue-method, rvalue-param
 */
public class Invoke_10
        extends OpInvocable
    {
    /**
     * Construct an NVOK_10 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     */
    public Invoke_10(Argument argTarget, MethodConstant constMethod, Argument argValue)
        {
        super(argTarget, constMethod);

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_10(DataInput in, Constant[] aconst)
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
        return OP_NVOK_10;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg    = frame.getArgument(m_nArgValue);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                        resolveArg(frameCaller, frameCaller.popStack(), hArg))
                    : resolveArg(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArg(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        return isDeferred(hArg)
                ? hArg.proceed(frame, frameCaller ->
                    complete(frameCaller, hTarget, frameCaller.popStack()))
                : complete(frame, hTarget, hArg);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        return getCallChain(frame, hTarget).invoke(frame, hTarget, hArg, A_IGNORE);
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
        return Argument.toIdString(m_argValue, m_nArgValue);
        }

    private int m_nArgValue;

    private Argument m_argValue;
    }
