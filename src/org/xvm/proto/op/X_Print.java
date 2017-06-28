package org.xvm.proto.op;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.Utils;

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

        if (nValue >= 0)
            {
            try
                {
                Frame.VarInfo info = frame.getVarInfo(nValue);

                switch (info.m_nStyle)
                    {
                    case Frame.VAR_DYNAMIC_REF:
                        sb.append("<dynamic> ");
                        break;

                    case Frame.VAR_WAITING:
                        // must not happen
                        throw new IllegalStateException();
                    }

                if (info.f_sVarName != null)
                    {
                    sb.append(info.f_sVarName).append("=");
                    }

                ObjectHandle hValue = frame.getArgument(nValue);
                if (hValue == null)
                    {
                    sb.append("<waiting>...");
                    Utils.log(sb.toString());
                    return R_REPEAT;
                    }
                sb.append(hValue);
                }
            catch (ObjectHandle.ExceptionHandle.WrapperException e)
                {
                frame.m_hException = e.getExceptionHandle();
                return R_EXCEPTION;
                }
            }
        else
            {
            Constant constValue = frame.f_context.f_pool.getConstant(-nValue);

            if (constValue instanceof CharStringConstant)
                {
                sb.append(((CharStringConstant) constValue).getValue());
                }
            else
                {
                sb.append(constValue.getValueString());
                }
            }

        Utils.log(sb.toString());

        return iPC + 1;
        }
    }