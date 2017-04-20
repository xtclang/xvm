package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;

import java.util.Date;

/**
 * Debugging only.
 *
 * @author gg 2017.03.08
 */
public class X_Print extends Op
    {
    private final int f_nValue;

    public X_Print(int nValue)
        {
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int nValue = f_nValue;

        StringBuilder sb = new StringBuilder();
        sb.append(new Date()).append(" ")
          .append(frame.f_context).append(": ");

        if (nValue >= 0)
            {
            try
                {
                ObjectHandle handle = frame.getArgument(nValue);
                Frame.VarInfo info = frame.f_aInfo[nValue];
                if (info != null && info.f_sVarName != null)
                    {
                    sb.append(info.f_sVarName).append("=");
                    }
                sb.append(handle);
                }
            catch (ObjectHandle.ExceptionHandle.WrapperException e)
                {
                frame.m_hException = e.getExceptionHandle();
                return RETURN_EXCEPTION;
                }
            }
        else
            {
            sb.append(frame.f_context.f_constantPool.getConstantValue(-nValue).getValueString());
            }

        System.out.println(sb.toString());

        return iPC + 1;
        }
    }
