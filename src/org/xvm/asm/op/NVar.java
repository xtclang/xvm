package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVAR CONST_CLASS, CONST_STRING ; (next register is an uninitialized named variable)
 */
public class NVar
        extends Op
    {
    private final int f_nClassConstId;
    private final int f_nNameConstId;

    public NVar(int nClassConstId, int nNameConstId)
        {
        f_nClassConstId = nClassConstId;
        f_nNameConstId = nNameConstId;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public NVar(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nClassConstId = readPackedInt(in);
        f_nNameConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_NVAR);
        writePackedLong(out, f_nClassConstId);
        writePackedLong(out, f_nNameConstId);
        }

    @Override
    public int getOpCode()
        {
        return OP_NVAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        TypeComposition clazz = context.f_types.ensureComposition(
                f_nClassConstId, frame.getActualTypes());
        StringConstant constName = (StringConstant)
                context.f_pool.getConstant(f_nNameConstId);

        frame.introduceVar(clazz, constName.getValue(), Frame.VAR_STANDARD, null);

        return iPC + 1;
        }
    }
