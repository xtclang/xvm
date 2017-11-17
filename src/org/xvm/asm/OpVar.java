package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Support for all of the various "VAR" ops.
 */
public abstract class OpVar
        extends Op
    {
    /**
     * @deprecated constructor used by deprecated sub-class constructors
     */
    protected OpVar()
        {
        }

    /**
     * Construct a variable that will hold the specified type.
     *
     * @param constType  the variable type
     */
    protected OpVar(TypeConstant constType)
        {
        this(new Register(constType));
        }

    /**
     * Construct a variable that corresponds to the specified register.
     *
     * @param reg  the register for the variable
     */
    protected OpVar(Register reg)
        {
        assert reg != null;
        m_reg = reg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpVar(DataInput in, Constant[] aconst)
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
            m_nType = encodeArgument(m_reg.getRefType(), registry);
            }

        out.writeByte(getOpCode());
        writePackedLong(out, m_nType);
        }

    /**
     * @return the type of the variable
     */
    public TypeConstant getType()
        {
        return m_reg.getRefType();
        }

    /**
     * @return the Register that holds the variable's value
     */
    public Register getRegister()
        {
        return m_reg;
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
            registry.register(m_reg.getRefType());
            }
        }

    /**
     * The register that the VAR op is responsible for creating.
     */
    protected Register m_reg;

    /**
     * The type constant id.
     */
    protected int m_nType;
    }
