package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * VAR CONST_CLASS  ; (next register is an uninitialized anonymous variable)
 *
 * @author gg 2017.03.08
 */
public class Var extends Op
    {
    private final int f_nClassConstId;

    public Var(int nClassConstId)
        {
        f_nClassConstId = nClassConstId;
        }

    public Var(DataInput in)
            throws IOException
        {
        f_nClassConstId = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_VAR);
        out.writeInt(f_nClassConstId);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clazz = frame.f_context.f_types.ensureComposition(f_nClassConstId);

        frame.introduceVar(clazz, null, Frame.VAR_STANDARD, null);

        return iPC + 1;
        }
    }
