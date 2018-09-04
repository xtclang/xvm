package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;


import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * RETURN_1 rvalue
 */
public class Return_1
        extends Op
    {
    /**
     * Construct a RETURN_1 op.
     *
     * @param nValue  the value to return
     *
     * @deprecated
     */
    public Return_1(int nValue)
        {
        m_nArg = nValue;
        }

    /**
     * Construct a RETURN_1 op.
     *
     * @param arg  the value to return
     */
    public Return_1(Argument arg)
        {
        m_arg = arg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Return_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArg = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_arg != null)
            {
            m_nArg = encodeArgument(m_arg, registry);
            }

        out.writeByte(OP_RETURN_1);
        writePackedLong(out, m_nArg);
        }

    @Override
    public int getOpCode()
        {
        return OP_RETURN_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hValue = frame.getReturnValue(m_nArg);

        if (isDeferred(hValue))
            {
            ObjectHandle[] ahValue = new ObjectHandle[]{hValue};
            Frame.Continuation stepNext = frameCaller -> frameCaller.returnValue(ahValue[0], false);

            return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

        return frame.returnValue(hValue, frame.isDynamicVar(m_nArg));
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_arg = registerArgument(m_arg, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()+ ' ' + Argument.toIdString(m_arg, m_nArg);
        }

    private int m_nArg;

    private Argument m_arg;
    }
