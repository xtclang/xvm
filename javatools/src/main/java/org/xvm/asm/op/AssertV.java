package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean.BooleanHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * ASSERT_V rvalue, STRING, #vals(rvalue)
 */
public class AssertV
        extends AssertM
    {
    /**
     * Construct an ASSERT_T op based on the specified arguments.
     *
     * @param argTest      the test Argument
     * @param constructor  the exception constructor (or null for a breakpoint)
     * @param constMsg     the message StringConstant
     * @param aArgValue    the value Arguments
     */
    public AssertV(Argument argTest, MethodConstant constructor, StringConstant constMsg, Argument[] aArgValue)
        {
        super(argTest, constructor, constMsg);
        m_aArgValue = aArgValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public AssertV(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        m_anValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anValue = encodeArguments(m_aArgValue, registry);
            }

        writeIntArray(out, m_anValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT_V;
        }

    @Override
    protected int evaluate(Frame frame, int iPC, BooleanHandle hTest)
        {
        if (hTest.get())
            {
            return iPC + 1;
            }

        // first, get the unformatted String from the constant pool and split it up into its
        // pieces
        if (m_asParts == null)
            {
            String   sMsg    = frame.getString(m_nMsgConstId);
            int      cVals   = m_anValue.length;
            String[] asParts = new String[cVals + 1];
            for (int i = 0; i < cVals; ++i)
                {
                String sReplace = "{" + i + "}";
                int of = sMsg.indexOf(sReplace);
                if (of > 0)
                    {
                    asParts[i] = sMsg.substring(0, of);
                    sMsg = sMsg.substring(of + sReplace.length());
                    }
                else
                    {
                    asParts[i] = "";
                    }
                }
            assert sMsg != null;
            asParts[cVals] = sMsg;
            m_asParts = asParts;
            }

        // get the trace variable and constant values to display; note that some values could
        // be unassigned conditional returns
        int            cArgs = m_anValue.length;
        ObjectHandle[] ahArg = new ObjectHandle[cArgs];
        for (int i = 0; i < cArgs; i++)
            {
            try
                {
                ahArg[i] = frame.getArgument(m_anValue[i]);
                }
            catch (Exception e)
                {
                ahArg[i] = xString.EMPTY_STRING;
                }
            }

        // build the assertion message and finish by throwing it
        StringBuilder      sb         = new StringBuilder(m_asParts[0]);
        Frame.Continuation doComplete = (frameCaller) -> complete(frameCaller, iPC, sb.toString());
        return new MessageToString(sb, ahArg, m_asParts, doComplete).doNext(frame);
        }

    /**
     * Helper class to create a string message.
     */
    private static class MessageToString
            implements Frame.Continuation
        {
        public MessageToString(
                StringBuilder      sb,
                ObjectHandle[]     ahValue,
                String[]           asLabel,
                Frame.Continuation nextStep)
            {
            this.sb       = sb;
            this.ahValue  = ahValue;
            this.asLabel  = asLabel;
            this.nextStep = nextStep;
            }

        protected int doNext(Frame frameCaller)
            {
            loop: while (++index < ahValue.length)
                {
                switch (Utils.callValueOf(frameCaller, ahValue[index]))
                    {
                    case Op.R_NEXT:
                        if (updateResult(frameCaller))
                            {
                            continue; // loop;
                            }
                        else
                            {
                            break loop;
                            }

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    default:
                        throw new IllegalStateException();
                    }
                }

            return nextStep.proceed(frameCaller);
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            if (updateResult(frameCaller))
                {
                return doNext(frameCaller);
                }

            // too much text; enough for an output...
            return nextStep.proceed(frameCaller);
            }

        /**
         * @return false iff the output is too long
         */
        protected boolean updateResult(Frame frameCaller)
            {
            StringHandle hMsg = (StringHandle) frameCaller.popStack();
            char[]       ach  = hMsg.getValue();

            if (ach.length > MAX_VAL)
                {
                sb.append(ach, 0, MAX_VAL)
                  .append("...");
                }
            else
                {
                sb.append(ach);
                }

            if (sb.length() > MAX_LEN)
                {
                sb.append("...");
                return false;
                }

            sb.append(asLabel[index+1]);
            return true;
            }

        protected static final int MAX_VAL = 2*1024;
        protected static final int MAX_LEN = 16*1024;

        protected final StringBuilder      sb;
        protected final ObjectHandle[]     ahValue;
        protected final String[]           asLabel;
        protected final Frame.Continuation nextStep;

        protected int index = -1;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append(" (");

        for (int i = 0, c = Math.max(m_anValue   == null ? 0 : m_anValue.length,
                                     m_aArgValue == null ? 0 : m_aArgValue.length); i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(", ");
                }

            sb.append(Argument.toIdString(m_aArgValue == null ? null : m_aArgValue[i],
                                          m_anValue   == null ? Register.UNKNOWN : m_anValue[i]));
            }

        sb.append(')');
        return sb.toString();
        }

    private int[] m_anValue;

    private Argument[] m_aArgValue;

    private transient volatile String[] m_asParts;
    }