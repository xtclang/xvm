package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

/**
 * MOV rvalue-src, lvalue-dest
 *
 * @author gg 2017.03.08
 */
public class Move extends Op
    {
    final private int f_nFromValue;
    final private int f_nToValue;

    public Move(int nFrom, int nTo)
        {
        f_nToValue = nTo;
        f_nFromValue = nFrom;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        try
            {
            ObjectHandle hValue = frame.getArgument(f_nFromValue);

            // TODO: validate the source/destination compatibility?

            hException = frame.assignValue(f_nToValue, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }
