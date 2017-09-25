package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

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
    /**
     * Construct an NVAR op.
     *
     * @param nTypeConstId  the type of the var
     * @param nNameConstId  the name of the var
     */
    public NVar(int nTypeConstId, int nNameConstId)
        {
        f_nTypeConstId = nTypeConstId;
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
        f_nTypeConstId = readPackedInt(in);
        f_nNameConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
    throws IOException
        {
        out.writeByte(OP_NVAR);
        writePackedLong(out, f_nTypeConstId);
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
                f_nTypeConstId, frame.getActualTypes());

        frame.introduceVar(clazz, frame.getString(f_nNameConstId), Frame.VAR_STANDARD, null);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    private final int f_nTypeConstId;
    private final int f_nNameConstId;
    }
