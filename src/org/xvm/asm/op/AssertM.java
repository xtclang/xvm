package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT_M rvalue, STRING
 */
public class AssertM
        extends Op
    {
    /**
     * Construct an ASSERT_T op.
     *
     * @param nTest  the r-value to test
     * @param nMsgId  the text to display on assertion failure
     *
     * @deprecated
     */
    public AssertM(int nTest, int nMsgId)
        {
        m_nTest = nTest;
        m_nMsgConstId = nMsgId;
        }

    /**
     * Construct an ASSERT_T op based on the specified arguments.
     *
     * @param argTest   the test Argument
     * @param constMsg  the message StringConstant
     */
    public AssertM(Argument argTest, StringConstant constMsg)
        {
        m_argTest = argTest;
        m_constMsg = constMsg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public AssertM(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTest = readPackedInt(in);
        m_nMsgConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTest != null)
            {
            m_nTest = encodeArgument(m_argTest, registry);
            m_nMsgConstId = encodeArgument(m_constMsg, registry);
            }

        out.writeByte(OP_ASSERT_M);
        writePackedLong(out, m_nTest);
        writePackedLong(out, m_nMsgConstId);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT_M;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nTest);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, iPC, (BooleanHandle) ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return complete(frame, iPC, (BooleanHandle) hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, BooleanHandle hTest)
        {
        if (hTest.get())
            {
            return iPC + 1;
            }

        return frame.raiseException(xException.makeHandle(
            "Assertion failed: \"" + frame.getString(m_nMsgConstId) + '"'));
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argTest = registerArgument(m_argTest, registry);
        m_constMsg = (StringConstant) registerArgument(m_constMsg, registry);
        }

    private int m_nTest;
    private int m_nMsgConstId;

    private Argument m_argTest;
    private StringConstant m_constMsg;
    }
