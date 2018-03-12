package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;


/**
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends OpMove
    {
    /**
     * Construct a MOV op.
     *
     * @param nFrom  the source location
     * @param nTo    the destination location
     *
     * @deprecated
     */
    public Move(int nFrom, int nTo)
        {
        super((Argument) null, null);

        m_nToValue = nTo;
        m_nFromValue = nFrom;
        }

    /**
     * Construct a MOV op for the passed arguments.
     *
     * @param argFrom  the Argument to move from
     * @param regTo  the Register to move to
     */
    public Move(Argument argFrom, Register regTo)
        {
        super(argFrom, regTo);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Move(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_MOV;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            int nFrom = m_nFromValue;
            int nTo   = m_nToValue;

            ObjectHandle hValue = frame.getArgument(nFrom);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (frame.isNextRegister(nTo))
                {
                frame.introduceVarCopy(nFrom);
                }
            return frame.assignValue(nTo, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
