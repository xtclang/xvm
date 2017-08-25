package org.xvm.proto.op;

import org.xvm.proto.CallChain;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.OpProperty;
import org.xvm.proto.TypeComposition;

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

        TypeComposition clazz = hTarget.f_clazz;

        CallChain.PropertyCallChain chain = getPropertyCallChain(frame, clazz, f_nPropConstId, true);

        return clazz.f_template.getPropertyValue(frame, chain, hTarget, f_nRetValue);
        }
    }
