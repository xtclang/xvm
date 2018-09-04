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
        if (isTypeAware())
            {
            m_nType = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (isTypeAware())
            {
            if (m_reg != null)
                {
                m_nType = encodeArgument(getRegisterType(ConstantPool.getCurrentPool()), registry);
                }

            writePackedLong(out, m_nType);
            }
        }

    /**
     * Specifies whether or not this op carries the type information.
     */
    protected boolean isTypeAware()
        {
        // majority of Var_* op-codes carry the type; only Var_C and Var_CN don't
        return true;
        }

    /**
     * Note: Used only during compilation.
     *
     * @return the type of the register
     */
    public TypeConstant getRegisterType(ConstantPool pool)
        {
        return m_reg.isDVar()
                ? m_reg.ensureRegType(pool, !m_reg.isWritable())
                : m_reg.getType();
        }

    /**
     * Note: Used only during compilation.
     *
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
        // TODO: remove the null check when manually compiled code is gone
        if (m_reg != null)
            {
            m_nVar = m_reg.assignIndex(iReg);
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

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder(super.toString());

        if (isTypeAware())
            {
            sb.append(' ')
              .append(Argument.toIdString(null, m_nType))
              .append(',');
            }
        sb.append(' ')
          .append(m_reg == null ? "" : m_reg.toString());

        return sb.toString();
        }

    /**
     * The register that the VAR op is responsible for creating.
     */
    protected transient Register m_reg;

    /**
     * The var index.
     */
    protected transient int m_nVar = -1;

    /**
     * The type constant id.
     */
    protected int m_nType;
    }
