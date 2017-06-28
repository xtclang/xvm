package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.template.xBoolean.BooleanHandle;
import org.xvm.proto.template.xException;

/**
 * ASSERT_V rvalue, CONST_STRING, #vals(rvalue)
 *
 * @author gg 2017.03.08
 */
public class AssertV extends OpCallable
    {
    private final int f_nValue;
    private final int f_nTextConstId;
    private final int[] f_anValue;

    public AssertV(int nValue, int nTextId, int[] anValue)
        {
        f_nValue = nValue;
        f_nTextConstId = nTextId;
        f_anValue = anValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        try
            {
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(f_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            if (hTest.get())
                {
                return iPC + 1;
                }

            CharStringConstant constText = (CharStringConstant)
                    frame.f_context.f_heapGlobal.f_pool.getConstant(f_nTextConstId);

            StringBuilder sb = new StringBuilder("Assertion failed: ");
            sb.append(constText.getValueString())
              .append(" (");

            for (int i = 0, c = f_anValue.length; i < c; i++)
                {
                if (i > 0)
                    {
                    sb.append(", ");
                    }
                int nValue = f_anValue[i];

                Frame.VarInfo info = frame.getVarInfo(nValue);
                ObjectHandle hValue = frame.getArgument(nValue);

                sb.append(info.f_sVarName)
                  .append('=')
                  .append(hValue);
                }
            sb.append(')');

            hException = xException.makeHandle(sb.toString());
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        frame.m_hException = hException;
        return R_EXCEPTION;
        }
    }
