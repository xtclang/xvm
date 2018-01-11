package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_10 rvalue-target, rvalue-method, rvalue-param
 */
public class Invoke_10
        extends OpInvocable
    {
    /**
     * Construct an NVOK_10 op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the method argument
     *
     * @deprecated
     */
    public Invoke_10(int nTarget, int nMethodId, int nArg)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nArgValue = nArg;
        }

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
            ObjectHandle hArg = frame.getArgument(m_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    resolveArg(frameCaller, ahTarget[0], hArg);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return resolveArg(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArg(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        if (isProperty(hArg))
            {
            ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
            Frame.Continuation stepNext = frameCaller -> complete(frameCaller, hTarget, ahArg[0]);

            return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
            }

        return complete(frame, hTarget, hArg);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        CallChain chain = getCallChain(frame, hTarget.f_clazz);

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];
        ahVar[0] = hArg;

        return chain.isNative()
            ? hTarget.getTemplate().invokeNative1(frame, chain.getTop(), hTarget, ahVar[0], Frame.RET_UNUSED)
            : hTarget.getTemplate().invoke1(frame, chain, hTarget, ahVar, Frame.RET_UNUSED);
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
