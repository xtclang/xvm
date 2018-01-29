package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_INT rvalue, #:(addr), addr-default ; if value equals (0,1,2,...), jump to address, otherwise default
 */
public class JumpInt
        extends Op
    {
    /**
     * Construct a JMP_INT op.
     *
     * @param arg        a value Argument
     * @param aOpCase    an array of Ops to jump to
     * @param opDefault  an Op to jump to in the "default" case
     */
    protected JumpInt(Argument arg, Op[] aOpCase, Op opDefault)
        {
        assert aOpCase != null;

        m_argVal = arg;
        m_aOpCase = aOpCase;
        m_opDefault = opDefault;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected JumpInt(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArg = readPackedInt(in);
        m_aofCase = readIntArray(in);
        m_ofDefault = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argVal != null)
            {
            m_nArg = encodeArgument(m_argVal, registry);
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nArg);
        writeIntArray(out, m_aofCase);
        writePackedLong(out, m_ofDefault);
        }

    @Override
    public void resolveAddress(MethodStructure.Code code, int iPC)
        {
        if (m_aOpCase != null && m_aofCase == null)
            {
            int c = m_aOpCase.length;
            m_aofCase = new int[c];
            for (int i = 0; i < c; i++)
                {
                m_aofCase[i] = resolveAddress(code, iPC, m_aOpCase[i]);
                }
            m_ofDefault = resolveAddress(code, iPC, m_opDefault);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nArg);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frame, iPC, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return complete(frame, iPC, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, ObjectHandle hValue)
        {
        long lValue = ((JavaLong) hValue).getValue();

        return lValue >= 0 || lValue < m_aofCase.length
            ? iPC + m_aofCase[(int) lValue]
            : iPC + m_ofDefault;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argVal, registry);
        }

    protected int   m_nArg;
    protected int[] m_aofCase;
    protected int   m_ofDefault;

    private Argument m_argVal;
    private Op[] m_aOpCase;
    private Op m_opDefault;
    }
