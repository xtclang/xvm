package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.Ref.RefHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_D TYPE ; next register is an anonymous "dynamic reference" variable
 */
public class Var_D
        extends Op
    {
    /**
     * Construct a VAR_D.
     *
     * @param nTypeConstId   the index of the constant containing the type of the variable
     */
    public Var_D(int nTypeConstId)
        {
        f_nTypeConstId = nTypeConstId;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_D(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nTypeConstId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
    throws IOException
        {
        out.writeByte(OP_VAR_D);
        writePackedLong(out, f_nTypeConstId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_D;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clz = frame.f_context.f_types.ensureComposition(
                f_nTypeConstId, frame.getActualTypes());

        RefHandle hRef = clz.f_template.createRefHandle(clz, null);

        frame.introduceVar(clz, null, Frame.VAR_DYNAMIC_REF, hRef);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.allocVar();
        }

    final private int f_nTypeConstId;
    }
