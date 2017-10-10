package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR TYPE ; (next register is an uninitialized anonymous variable)
 */
public class Var
        extends Op
    {
    /**
     * Construct a VAR op.
     *
     * @param nType  the r-value specifying the type of the variable
     *
     * @deprecated
     */
    public Var(int nType)
        {
        m_nType = nType;
        }

    /**
     * Construct a VAR op for the passed Register.
     *
     * @param reg  the Register object
     */
    public Var(Register reg)
        {
        if (reg == null)
            {
            throw new IllegalArgumentException("register required");
            }
        m_reg = reg;
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
        m_nType = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_reg != null)
            {
            m_nType = encodeArgument(m_reg.getType(), registry);
            }

        out.writeByte(OP_VAR);
        writePackedLong(out, m_nType);
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
                m_nType, frame.getActualTypes());

        frame.introduceVar(clazz, null, Frame.VAR_STANDARD, null);

        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        int iReg = scope.allocVar();
        if (m_reg != null)
            {
            m_reg.assignIndex(iReg);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        if (m_reg != null)
            {
            m_reg.registerConstants(registry);
            }
        }

    private int      m_nType;
    private Register m_reg;
    }
