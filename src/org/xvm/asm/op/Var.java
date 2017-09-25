package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR CONST_CLASS  ; (next register is an uninitialized anonymous variable)
 */
public class Var
        extends Op
    {
    /**
     * Construct a VAR op.
     *
     * @param nType  the r-value specifying the type of the variable
     */
    public Var(int nType)
        {
        f_nType = nType;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nType = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_VAR);
        writePackedLong(out, f_nType);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clazz = frame.f_context.f_types.ensureComposition(
                f_nType, frame.getActualTypes());

        frame.introduceVar(clazz, null, Frame.VAR_STANDARD, null);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    private final int f_nType;
    }
