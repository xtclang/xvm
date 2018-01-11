package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_T1  rvalue-target, CONST-METHOD, rvalue-params-tuple, lvalue-return
 */
public class Invoke_T1
        extends OpInvocable
    {
    /**
     * Construct an NVOK_T1 op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the tuple of method arguments
     * @param nRet       the l-value location for the result
     *
     * @deprecated
     */
    public Invoke_T1(int nTarget, int nMethodId, int nArg, int nRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nArgTupleValue = nArg;
        m_nRetValue = nRet;
        }

    /**
     * Construct an NVOK_T1 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_T1(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument argReturn)
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
    public Invoke_T1(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nArgTupleValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nArgTupleValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nArgTupleValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_T1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg = frame.getArgument(m_nArgTupleValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    resolveTuple(frameCaller, ahTarget[0], hArg);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
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
        if (isProperty(hArg))
            {
            ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
            Frame.Continuation stepNext = frameCaller ->
                complete(frameCaller, hTarget, ((TupleHandle) ahArg[0]).m_ahValue);

            return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
            }

        return complete(frame, hTarget, ((TupleHandle) hArg).m_ahValue);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg)
        {
        CallChain chain = getCallChain(frame, hTarget);
        MethodStructure method = chain.getTop();

        if (ahArg.length != method.getParamCount())
            {
            return frame.raiseException(xException.makeHandle("Invalid tuple argument"));
            }

        checkReturnRegister(frame, method);

        return chain.isNative()
            ? hTarget.getTemplate().invokeNativeN(frame, method, hTarget, ahArg, m_nRetValue)
            : hTarget.getTemplate().invoke1(frame, chain, hTarget,
            Utils.ensureSize(ahArg, method.getMaxVars()), m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        }

    private int m_nArgTupleValue;

    private Argument m_argValue;
    }
