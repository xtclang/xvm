package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;


/**
 * JMP_NZERO rvalue, addr ; jump if value is NOT zero
 */
public class JumpNotZero
        extends JumpCond
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
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nArg);
            if (hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hArg))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
                Frame.Continuation stepNext = frameCaller ->
                    ((JavaLong) ahArg[0]).getValue() == 0 ? iPC + 1 : iPC + m_ofJmp;

                return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
                }

            return ((JavaLong) hArg).getValue() == 0 ? iPC + 1 : iPC + m_ofJmp;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
