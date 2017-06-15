package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.template.xBoolean;

/**
 * IS_ZERO rvalue-int, lvalue-return ; T == 0 -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsZero extends Op
    {
    private final int f_nValue;
    private final int f_nRetValue;

    public IsZero(int nValue1, int nRet)
        {
        f_nValue = nValue1;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            JavaLong hValue = (JavaLong) frame.getArgument(f_nValue);

            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(f_nRetValue, hValue.getValue() == 0 ?
                            xBoolean.TRUE : xBoolean.FALSE);
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
