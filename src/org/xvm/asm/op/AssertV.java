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
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;
import org.xvm.runtime.template.xString.StringHandle;


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
    protected String buildMessage(Frame frame)
            throws ExceptionHandle.WrapperException
        {
        if (m_asParts == null)
            {
            String sMsg  = frame.getString(m_nMsgConstId);
            int    cVals = m_anValue.length;
            m_asParts = new String[cVals + 1];
            for (int i = 0; i < cVals; ++i)
                {
                String sReplace = "{" + i + "}";
                int of = sMsg.indexOf(sReplace);
                if (of > 0)
                    {
                    m_asParts[cVals] = sMsg.substring(0, of);
                    sMsg = sMsg.substring(of + sReplace.length());
                    }
                else
                    {
                    m_asParts[cVals] = "";
                    }
                }
            m_asParts[cVals] = sMsg;
            }

        // since these are all local vars or constants, this shouldn't be complicated (no retry
        // or deferral should be necessary)
        ObjectHandle[] ahValue = frame.getArguments(m_anValue, m_anValue.length);
        assert ahValue != null;
        assert !anyDeferred(ahValue);

        StringBuilder sb = new StringBuilder();
        for ()

        return sb.toString();
        }

    public static class ArrayToString
            implements Frame.Continuation
        {
        public ArrayToString(StringBuilder sb, ObjectHandle[] ahValue,
                String[] asLabel, Frame.Continuation nextStep)
            {
            this.sb = sb;
            this.ahValue = ahValue;
            this.asLabel = asLabel;
            this.nextStep = nextStep;
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

        // return false if the buffer is full
        protected boolean updateResult(Frame frameCaller)
            {
            StringHandle hString = (StringHandle) frameCaller.popStack();
            String sLabel = asLabel == null ? null : asLabel[index];

            if (sLabel != null)
                {
                sb.append(sLabel).append('=');
                }
            sb.append(hString.getValue());

            if (sb.length() < 1024*32)
                {
                sb.append(", ");
                return true;
                }

            sb.append("...");
            return false;
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < ahValue.length)
                {
                switch (callToString(frameCaller, ahValue[index]))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        continue;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            sb.setLength(sb.length() - 2); // remove the trailing ", "
            sb.append(')');

            return nextStep.proceed(frameCaller);
            }

        final private StringBuilder      sb;
        final private ObjectHandle[]     ahValue;
        final private String[]           asLabel;
        final private Frame.Continuation nextStep;

        private int index = -1;
        }
//        if (anyDeferred(ahValue))
//            {
//            int cValues = ahValue.length;
//
//            ObjectHandle[] ahResolved = new ObjectHandle[cValues];
//            System.arraycopy(ahValue, 0, ahResolved, 0, cValues);
//
//            // ahValue still holds original PropertyHandles
//            Frame.Continuation stepNext = frameCaller ->
//                raiseException(frameCaller, ahResolved, ahValue);
//
//            return new Utils.GetArguments(ahResolved, stepNext).doNext(frame);
//            }
//
//        return raiseException(frame, ahValue, ahValue);
//        }
//
//        Frame.Continuation stepNext = frameCaller ->
//            frameCaller.raiseException(xException.makeHandle(sb.toString()));
//
//        return new Utils.ArrayToString(sb, ahResolved, asName, stepNext).doNext(frame);
//        }

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

    private transient String[] m_asParts;
    }
