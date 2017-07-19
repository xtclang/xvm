package org.xvm.proto.op;

import org.xvm.asm.PropertyStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpProperty;
import org.xvm.proto.TypeComposition;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * PREINC lvalue-target, lvalue-return  ; ++T -> T
 *
 * @author gg 2017.03.08
 */
public class PreInc extends OpProperty
    {
    private final int f_nArgValue;
    private final int f_nRetValue;

    public PreInc(int nArg, int nRet)
        {
        f_nArgValue = nArg;
        f_nRetValue = nRet;
        }

    public PreInc(DataInput in)
            throws IOException
        {
        f_nArgValue = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_PREINC);
        out.writeInt(f_nArgValue);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            if (f_nArgValue >= 0)
                {
                // operation on a register
                ObjectHandle hTarget = frame.getArgument(f_nArgValue);
                if (hTarget == null)
                    {
                    return R_REPEAT;
                    }

                return hTarget.f_clazz.f_template.
                        invokePreInc(frame, hTarget, null, f_nRetValue);
                }
            else
                {
                // operation on a local property
                ObjectHandle hTarget = frame.getThis();
                TypeComposition clazz = hTarget.f_clazz;

                PropertyStructure property = getPropertyStructure(frame, clazz, -f_nArgValue);

                return hTarget.f_clazz.f_template.
                        invokePreInc(frame, hTarget, property, f_nRetValue);
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }