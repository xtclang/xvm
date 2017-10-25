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
 * NVOK_1T rvalue-target, CONST-METHOD, rvalue-param, lvalue-return-tuple
 */
public class Invoke_1T
        extends OpInvocable
    {
    /**
     * Construct an NVOK_1T op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the method argument
     * @param nRet       the l-value location for the tuple result
     */
    public Invoke_1T(int nTarget, int nMethodId, int nArg, int nRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nArgValue = nArg;
        m_nTupleRetValue = nRet;
        }

    /**
     * Construct an NVOK_1T op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_1T(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument argReturn)
        {
        super(argTarget, constMethod);

        m_argValue = argValue;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_1T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        m_nTupleRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nTupleRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nTupleRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_1T;
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
        TypeComposition clz = hTarget.f_clazz;

        CallChain chain = getCallChain(frame, hTarget.f_clazz);

        ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];
        ahVar[0] = hArg;

        return chain.isNative()
            ? clz.f_template.invokeNativeT(frame, chain.getTop(), hTarget, ahVar, m_nTupleRetValue)
            : clz.f_template.invokeT(frame, chain, hTarget, ahVar, m_nTupleRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        registerArgument(m_argReturn, registry);
        }

    private int m_nArgValue;
    private int m_nTupleRetValue;

    private Argument m_argValue;
    private Argument m_argReturn;
    }
