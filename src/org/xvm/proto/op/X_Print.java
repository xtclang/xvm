package org.xvm.proto.op;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xRef.RefHandle;

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

                switch (info.f_nStyle)
                    {
                    case VAR_DYNAMIC:
                        sb.append("<dynamic> ");
                        break;

                    case VAR_DEFERRABLE:
                        if (frame.f_ahVar[nValue] instanceof RefHandle)
                            {
                            sb.append("<deferred> ");
                            }
                        break;
                    }

                if (info.f_sVarName != null)
                    {
                    sb.append(info.f_sVarName).append("=");
                    }

                sb.append(frame.getArgument(nValue));
                }
            catch (ObjectHandle.ExceptionHandle.WrapperException e)
                {
                frame.m_hException = e.getExceptionHandle();
                return RETURN_EXCEPTION;
                }
            }
        else
            {
            Constant constValue = frame.f_context.f_constantPool.getConstantValue(-nValue);

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