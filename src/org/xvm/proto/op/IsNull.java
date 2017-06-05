package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;

import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.xNullable;

/**
 * IS_NULL rvalue, lvalue-return ; T == null -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsNull extends Op
    {
    private final int f_nValue;
    private final int f_nRetValue;

    public IsNull(int nValue1, int nRet)
        {
        f_nValue = nValue1;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(f_nValue);

            if (hValue == null)
                {
                return R_REPEAT;
                }

            frame.assignValue(f_nRetValue, hValue == xNullable.NULL ?
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
