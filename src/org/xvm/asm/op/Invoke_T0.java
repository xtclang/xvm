package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xTuple.TupleHandle;
import org.xvm.runtime.template.xException;


import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_T0 rvalue-target, CONST-METHOD, rvalue-params-tuple
 */
public class Invoke_T0
        extends OpInvocable
    {
    /**
     * Construct an NVOK_T0 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     */
    public Invoke_T0(Argument argTarget, MethodConstant constMethod, Argument argValue)
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
    public Invoke_T0(DataInput in, Constant[] aconst)
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
        return OP_NVOK_T0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            ObjectHandle hArg = frame.getArgument(m_nArgTupleValue);
            if (hArg == null)
                {
                if (m_nTarget == A_STACK)
                    {
                    frame.pushStack(hTarget);
                    }
                return R_REPEAT;
                }

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
        CallChain chain = getCallChain(frame, hTarget);
        MethodStructure method = chain.getTop();

        if (ahArg.length != method.getParamCount())
            {
            return frame.raiseException("Invalid tuple argument");
            }

        return chain.isNative()
            ? hTarget.getTemplate().invokeNativeN(frame, method, hTarget, ahArg, A_IGNORE)
            : hTarget.getTemplate().invoke1(frame, chain, hTarget,
                Utils.ensureSize(ahArg, method.getMaxVars()), A_IGNORE);
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
