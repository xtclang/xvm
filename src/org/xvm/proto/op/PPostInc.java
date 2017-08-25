package org.xvm.proto.op;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpProperty;
import org.xvm.proto.TypeComposition;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * P_POSTINC rvalue-target, CONST_PROPERTY, lvalue ; same as POSTINC for a register
 *
 * @author gg 2017.03.08
 */
public class PPostInc extends OpProperty
    {
    private final int f_nTarget;
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public PPostInc(int nTarget, int nArg, int nRet)
        {
        f_nTarget = nTarget;
        f_nPropConstId = nArg;
        f_nRetValue = nRet;
        }

    public PPostInc(DataInput in)
            throws IOException
        {
        f_nTarget = in.readInt();
        f_nPropConstId = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_P_POSTINC);
        out.writeInt(f_nTarget);
        out.writeInt(f_nPropConstId);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(f_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            TypeComposition clazz = hTarget.f_clazz;

            CallChain.PropertyCallChain chain = getPropertyCallChain(frame, clazz, f_nPropConstId, true);

            return clazz.f_template.invokePostInc(frame, chain, hTarget, f_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }