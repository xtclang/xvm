package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xBoolean.BooleanHandle;


/**
 * JMP_FALSE rvalue, addr ; jump if value is Boolean.False
 */
public class JumpFalse
        extends JumpCond
    {
    /**
     * Construct a JMP_FALSE op.
     *
     * @param nValue    the Boolean value to test
     * @param nRelAddr  the relative address to jump to
     *
     * @deprecated
     */
    public JumpFalse(int nValue, int nRelAddr)
        {
        super((Argument) null, null);

        m_nArg  = nValue;
        m_ofJmp = nRelAddr;
        }

    /**
     * Construct a JMP_FALSE op.
     *
     * @param arg  the argument to test
     * @param op   the op to conditionally jump to
     */
    public JumpFalse(Argument arg, Op op)
        {
        super(arg, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpFalse(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_FALSE;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(m_nArg);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            return hTest.get() ? iPC + 1 : iPC + m_ofJmp;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
