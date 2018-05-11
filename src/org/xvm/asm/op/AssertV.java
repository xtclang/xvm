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
import org.xvm.runtime.template.types.xProperty.DeferredPropertyHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * ASSERT_V rvalue, STRING, #vals(rvalue)
 */
public class AssertV
        extends Op
    {
    /**
     * Construct an ASSERT_V op.
     *
     * @param nTest    the r-value to test
     * @param nMsgId   the text to print on assertion failure
     * @param anValue  the values to print on assertion failure
     */
    public AssertV(int nTest, int nMsgId, int[] anValue)
        {
        m_nTest = nTest;
        m_nMsgConstId = nMsgId;
        m_anValue = anValue;
        }

    /**
     * Construct an ASSERT_T op based on the specified arguments.
     *
     * @param argTest    the test Argument
     * @param constMsg   the message StringConstant
     * @param aArgValue  the value Arguments
     */
    public AssertV(Argument argTest, StringConstant constMsg, Argument[] aArgValue)
        {
        m_argTest = argTest;
        m_constMsg = constMsg;
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
        m_nTest = readPackedInt(in);
        m_nMsgConstId = readPackedInt(in);
        m_anValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTest != null)
            {
            m_nTest = encodeArgument(m_argTest, registry);
            m_nMsgConstId = encodeArgument(m_constMsg, registry);
            m_anValue = encodeArguments(m_aArgValue, registry);
            }

        out.writeByte(OP_ASSERT_V);
        writePackedLong(out, m_nTest);
        writePackedLong(out, m_nMsgConstId);
        writeIntArray(out, m_anValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_ASSERT_V;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest = frame.getArgument(m_nTest);
            ObjectHandle[] ahValue = frame.getArguments(m_anValue, m_anValue.length);
            if (hTest == null || ahValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hTest))
                {
                ObjectHandle[] ahTest = new ObjectHandle[] {hTest};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, iPC, (BooleanHandle) ahTest[0], ahValue);

                return new Utils.GetArguments(ahTest, stepNext).doNext(frame);
                }

            return complete(frame, iPC, (BooleanHandle) hTest, ahValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, BooleanHandle hTest, ObjectHandle[] ahValue)
        {
        if (hTest.get())
            {
            return iPC + 1;
            }

        if (anyDeferred(ahValue))
            {
            int cValues = ahValue.length;

            ObjectHandle[] ahResolved = new ObjectHandle[cValues];
            System.arraycopy(ahValue, 0, ahResolved, 0, cValues);

            // ahValue still holds original PropertyHandles
            Frame.Continuation stepNext = frameCaller ->
                raiseException(frameCaller, ahResolved, ahValue);

            return new Utils.GetArguments(ahResolved, stepNext).doNext(frame);
            }

        return raiseException(frame, ahValue, ahValue);
        }

    protected int raiseException(Frame frame, ObjectHandle[] ahResolved, ObjectHandle[] ahOrig)
        {
        StringBuilder sb = new StringBuilder("Assertion failed: \"");
        sb.append(frame.getString(m_nMsgConstId))
          .append("\" (");

        // get the variable/local property names

        int[] anValue = m_anValue;
        int   cValues = anValue.length;

        String[] asName = new String[cValues];
        for (int i = 0; i < cValues; i++)
            {
            if (i > 0)
                {
                sb.append(", ");
                }

            int nValue = anValue[i];
            if (nValue >= 0)
                {
                // nValue points to a register
                asName[i] = frame.getVarInfo(nValue).getName();
                }
            else
                {
                // nValue points to a local property, deferred call or a constant;
                // in the local property case the property handle must be in the ahOrig array
                ObjectHandle hValueOrig = ahOrig[i];
                if (hValueOrig instanceof DeferredPropertyHandle)
                    {
                    asName[i] = ((DeferredPropertyHandle) hValueOrig).m_property.getName();
                    }
                else // simply a constant; no label
                    {
                    asName[i] = "";
                    }
                }
            }

        Frame.Continuation stepNext = frameCaller ->
            frameCaller.raiseException(xException.makeHandle(sb.toString()));

        return new Utils.ArrayToString(sb, ahResolved, asName, stepNext).doNext(frame);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argTest, registry);
        registerArgument(m_constMsg, registry);
        registerArguments(m_aArgValue, registry);
        }

    private int m_nTest;
    private int m_nMsgConstId;
    private int[] m_anValue;

    private Argument m_argTest;
    private StringConstant m_constMsg;
    private Argument[] m_aArgValue;
    }
