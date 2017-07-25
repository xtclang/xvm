package org.xvm.proto.op;

import org.xvm.asm.constants.CharStringConstant;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xRef.RefHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * DNVAR CONST_REF_CLASS, CONST_STRING ; next register is a named "dynamic reference" variable
 *
 * @author gg 2017.03.08
 */
public class DNVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nNameConstId;

    public DNVar(int nClassConstId, int nNameConstId)
        {
        f_nClassConstId = nClassConstId;
        f_nNameConstId = nNameConstId;
        }

    public DNVar(DataInput in)
            throws IOException
        {
        f_nClassConstId = in.readInt();
        f_nNameConstId = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_DNVAR);
        out.writeInt(f_nClassConstId);
        out.writeInt(f_nNameConstId);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        CharStringConstant constName =
                (CharStringConstant) context.f_pool.getConstant(f_nNameConstId);
        String sName = constName.getValue();

        TypeComposition clz = context.f_types.ensureComposition(f_nClassConstId);

        RefHandle hRef = clz.f_template.createRefHandle(clz, sName);

        frame.introduceVar(clz, sName, Frame.VAR_DYNAMIC_REF, hRef);

        return iPC + 1;
        }
    }
