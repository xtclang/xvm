package org.xvm.asm.op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.asm.OpProperty;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * LGET CONST_PROPERTY, lvalue ; local get (target=this)
 *
 * @author gg 2017.03.08
 */
public class LGet extends OpProperty
    {
    private final int f_nPropConstId;
    private final int f_nRetValue;

    public LGet(int nPropId, int nRet)
        {
        f_nPropConstId = nPropId;
        f_nRetValue = nRet;
        }

    public LGet(DataInput in)
            throws IOException
        {
        f_nPropConstId = in.readInt();
        f_nRetValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_L_GET);
        out.writeInt(f_nPropConstId);
        out.writeInt(f_nRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.getThis();

        PropertyConstant constProperty = (PropertyConstant)
                frame.f_context.f_pool.getConstant(f_nPropConstId);

        return hTarget.f_clazz.f_template.getPropertyValue(
                frame, hTarget, constProperty.getName(), f_nRetValue);
        }
    }
