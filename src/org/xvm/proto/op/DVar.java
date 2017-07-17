package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;

import org.xvm.proto.template.xRef.RefHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * DVAR CONST_REF_CLASS ; next register is an anonymous "dynamic reference" variable
 *
 * @author gg 2017.03.08
 */
public class DVar extends Op
    {
    final private int f_nClassConstId;

    public DVar(int nClassConstId)
        {
        f_nClassConstId = nClassConstId;
        }

    public DVar(DataInput in)
            throws IOException
        {
        f_nClassConstId = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_DVAR);
        out.writeInt(f_nClassConstId);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        RefHandle hRef = frame.f_context.f_heapGlobal.createRefHandle(frame, f_nClassConstId);

        frame.introduceVar(hRef.f_clazz, null, Frame.VAR_DYNAMIC_REF, hRef);

        return iPC + 1;
        }
    }
