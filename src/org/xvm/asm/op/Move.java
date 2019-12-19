package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpMove;

import org.xvm.asm.Register;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;


/**
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends OpMove
    {
    /**
     * Construct a MOV op for the passed arguments.
     *
     * @param argFrom  the Argument to move from
     * @param argTo    the Argument to move to
     */
    public Move(Argument argFrom, Argument argTo)
        {
        super(argFrom, argTo);
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
                frame.introduceVarCopy(nTo, nFrom);
                }

            if (isDeferred(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller ->
                    frame.assignValue(nTo, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }
            return frame.assignValue(nTo, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public boolean checkRedundant(Op[] aop)
        {
        if (m_argFrom instanceof Register && m_argFrom.equals(m_argTo))
            {
            markRedundant();
            return true;
            }
        return false;
        }
    }
