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
        try
            {
            ObjectHandle hValue = frame.getArgument(f_nFromValue);
            if (hValue == null)
                {
                return R_WAIT;
                }

            // TODO: validate the source/destination compatibility?

            return frame.assignValue(f_nToValue, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
