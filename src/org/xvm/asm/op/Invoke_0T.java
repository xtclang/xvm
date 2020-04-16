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

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_0T rvalue-target, CONST-METHOD, lvalue-return-tuple
 */
public class Invoke_0T
        extends OpInvocable
    {
    /**
     * Construct an NVOK_0T op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_0T(Argument argTarget, MethodConstant constMethod, Argument[] aArgValue, Argument argReturn)
        {
        super(argTarget, constMethod);

        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_0T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argReturn != null)
            {
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVOK_0T;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> complete(frameCaller, ahTarget[0]);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }

            return complete(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget)
        {
        checkReturnTupleRegister(frame, hTarget);

        return getCallChain(frame, hTarget).invokeT(frame, hTarget, Utils.OBJECTS_NONE, m_nRetValue);
        }
    }
