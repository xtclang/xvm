package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

/**
 * RETURN_1 rvalue
 *
 * @author gg 2017.03.08
 */
public class Return_1 extends Op
    {
    private final int f_nArgValue;

    public Return_1(int nValue)
        {
        f_nArgValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            frame.f_ahReturn[0] = frame.getArgument(f_nArgValue);
            return RETURN_NORMAL;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return RETURN_EXCEPTION;
            }
        }
    }
