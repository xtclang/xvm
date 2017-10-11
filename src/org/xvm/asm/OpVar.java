package org.xvm.asm;


import java.io.DataOutput;
import java.io.IOException;
import org.xvm.asm.constants.TypeConstant;


import static org.xvm.util.Handy.writePackedLong;


/**
 * Support for all of the various "VAR" ops.
 */
public abstract class OpVar
        extends Op
    {
    /**
     * Construct a VAR op.
     *
     * @param nType  the variable type id
     *
     * @deprecated
     */
    protected OpVar(int nType)
        {
        m_nType = nType;
        }

    /**
     * Construct a variable that will hold the specified type.
     *
     * @param constType  the variable type
     */
    protected OpVar(TypeConstant constType)
        {
        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }

        m_reg = new Register(constType);
        }

    /**
     * @return the type of the variable
     */
    public TypeConstant getType()
        {
        return m_reg.getType();
        }

    /**
     * @return the Register that holds the variable's value
     */
    public Register getRegister()
        {
        return m_reg;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_reg != null)
            {
            m_nType = encodeArgument(m_reg.getType(), registry);
            }

        out.writeByte(getOpCode());
        writePackedLong(out, m_nType);
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
            registry.register(m_reg.getType());
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
