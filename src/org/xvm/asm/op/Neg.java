package org.xvm.asm.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.asm.OpInvocable;
import org.xvm.proto.ClassTemplate;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * NEG rvalue-target, lvalue-return   ; -T -> T
 *
 * @author gg 2017.03.08
 */
public class Neg extends OpInvocable
    {
    private final int f_nArgValue;
    private final int f_nRetValue;

    public Neg(int nArg, int nRet)
        {
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    public Neg(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_NEG);
        out.writeInt(f_nArgValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nArgValue);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            ClassTemplate template = hTarget.f_clazz.f_template;

            return template.invokeNeg(frame, hTarget, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
