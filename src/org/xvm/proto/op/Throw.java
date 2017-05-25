package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

/**
 * THROW rvalue
 *
 * @author gg 2017.03.08
 */
public class Throw extends Op
    {
    private final int f_nArgValue;

    public Throw(int nValue)
        {
        f_nArgValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            // there are no "const" exceptions
            frame.m_hException = (ExceptionHandle) frame.getArgument(f_nArgValue);
            if (frame.m_hException == null)
                {
                return R_REPEAT;
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            }

        return R_EXCEPTION;
        }
    }
