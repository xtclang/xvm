package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT_M rvalue, STRING
 */
public class AssertM
        extends Assert
    {
    /**
     * Construct an ASSERT_M op based on the specified arguments.
     *
     * @param argTest      the test Argument
     * @param constructor  the exception constructor (or null for a breakpoint)
     * @param constMsg     the message StringConstant
     */
    public AssertM(Argument argTest, MethodConstant constructor, StringConstant constMsg)
        {
        super(argTest, constructor);
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
        super(in, aconst);
        m_nMsgConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constMsg != null)
            {
            m_nMsgConstId = encodeArgument(m_constMsg, registry);
            }

        writePackedLong(out, m_nMsgConstId);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT_M;
        }

    @Override
    protected String buildMessage(Frame frame)
        {
        return frame.getString(m_nMsgConstId);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constMsg = (StringConstant) registerArgument(m_constMsg, registry);
        }

    @Override
    public String toString()
        {
        return super.toString() + ' ' + Argument.toIdString(m_constMsg, m_nMsgConstId);
        }

    protected int m_nMsgConstId;

    private StringConstant m_constMsg;
    }