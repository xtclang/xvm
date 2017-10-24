package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * JMP_NZERO rvalue, addr ; jump if value is NOT zero
 */
public class JumpNotZero
        extends OpCondJump
    {
    /**
     * Construct a JMP_NZERO op.
     *
     * @param nValue    the value to test
     * @param nRelAddr  the relative address to jump to
     */
    public JumpNotZero(int nValue, int nRelAddr)
        {
        super((Argument) null, null);

        m_nArg  = nValue;
        m_ofJmp = nRelAddr;
        }

    /**
     * Construct a JMP_NZERO op.
     *
     * @param arg  the argument to test
     * @param op   the op to conditionally jump to
     */
    public JumpNotZero(Argument arg, Op op)
        {
        super(arg, op);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpNotZero(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }


    @Override
    public int getOpCode()
        {
        return OP_JMP_NZERO;
        }

    @Override
    protected int completeUnaryOp(Frame frame, int iPC, ObjectHandle hValue)
        {
        return ((JavaLong) hValue).getValue() == 0 ? iPC + 1 : iPC + m_ofJmp;
        }
    }
