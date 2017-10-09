package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * CAST rvalue
 */
public class CVar
        extends Op
    {
    /**
     * Construct a CAST op.
     *
     * @param arg   the argument to cast
     * @param type  the value to return
     */
    public CVar(Argument arg, TypeConstant type)
        {
        m_arg = arg;
        }

    /**
     * Construct a RETURN_1 op.
     *
     * @param nValue  the value to return
     *
     * @deprecated
     */
    public CVar(int nValue)
        {
        m_nArg = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public CVar(DataInput in, Constant[] aconst)
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
        int iRet = frame.f_iReturn;

        if (iRet >= 0 || iRet == Frame.RET_LOCAL)
            {
            return frame.returnValue(iRet, m_nArg);
            }

        switch (iRet)
            {
            case Frame.RET_UNUSED:
                return R_RETURN;

            case Frame.RET_MULTI:
                throw new IllegalStateException();

            default:
                return frame.returnTuple(-iRet - 1, new int[] {m_nArg});
            }
        }

    private Argument     m_arg;
    private TypeConstant m_type;
    private int          m_nArg;
    private int          m_nType;
    }
