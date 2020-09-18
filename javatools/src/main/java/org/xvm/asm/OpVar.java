package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ServiceContext;

import org.xvm.runtime.template.collections.xArray;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Support for all of the various "VAR" ops.
 */
public abstract class OpVar
        extends Op
    {
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
            m_nType = encodeArgument(getRegisterType(), registry);

            writePackedLong(out, m_nType);
            }
        }

    /**
     * @param aconst  (optional) an array of constants to retrieve constants by index from
     *
     * @return the variable name, iff the variable has a name (otherwise null)
     */
    protected String getName(Constant[] aconst)
        {
        return null;
        }

    /**
     * @return the variable name based on any of the present information
     */
    protected String getName(Constant[] aconst, StringConstant constName, int nNameId)
        {
        if (constName != null)
            {
            return constName.getValue();
            }

        if (aconst != null)
            {
            return ((StringConstant) aconst[convertId(nNameId)]).getValue();
            }

        // we cannot use Argument.toIdString(), since it returns a quoted string
        try
            {
            if (nNameId <= Op.CONSTANT_OFFSET)
                {
                ServiceContext context = ServiceContext.getCurrentContext();
                if (context != null)
                    {
                    return ((StringConstant) context.getCurrentFrame().
                            localConstants()[convertId(nNameId)]).getValue();
                    }
                }
            }
        catch (Throwable e) {}

        return "?";
        }

    /**
     * @param aconst  (optional) an array of constants to retrieve constants by index from
     *
     * @return the variable type
     */
    protected TypeConstant getType(Constant[] aconst)
        {
        return m_reg == null
                ? (TypeConstant) aconst[convertId(m_nType)]
                : m_reg.getType();
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
     * Helper method to calculate a ClassComposition for a sequence class.
     *
     * @param frame     the current frame
     * @param typeList  the sequence type
     *
     * @return the corresponding class composition
     */
    protected ClassComposition getArrayClass(Frame frame, TypeConstant typeList)
        {
        ServiceContext   context  = frame.f_context;
        ClassComposition clzArray = (ClassComposition) context.getOpInfo(this, Category.Composition);;
        TypeConstant     typePrev = (TypeConstant)     context.getOpInfo(this, Category.Type);

        if (clzArray == null || !typeList.equals(typePrev))
            {
            TypeConstant typeEl = typeList.resolveGenericType("Element");

            clzArray = xArray.INSTANCE.ensureParameterizedClass(frame.poolContext(), typeEl);

            context.setOpInfo(this, Category.Composition, clzArray);
            context.setOpInfo(this, Category.Type, typeList);
            }

        return clzArray;
        }

    /**
     * Note: Used only during compilation.
     *
     * @return the type of the register
     */
    public TypeConstant getRegisterType()
        {
        return m_reg.isDVar()
                ? m_reg.ensureRegType(!m_reg.isWritable())
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
    public void resetSimulation()
        {
        resetRegister(m_reg);
        }

    @Override
    public void simulate(Scope scope)
        {
        m_nVar = m_reg == null
                ? scope.allocVar()
                : m_reg.assignIndex(scope.allocVar());
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_reg.registerConstants(registry);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder(super.toString());

        String sName = getName(null);
        if (sName != null)
            {
            sb.append(' ')
              .append(sName)
              .append(',');
            }

        if (isTypeAware())
            {
            sb.append(' ')
              .append(Argument.toIdString(null, m_nType))
              .append(',');
            }

        sb.append(' ');
        if (m_reg == null)
            {
            sb.append('#').append(m_nVar);
            }
        else
            {
            sb.append(m_reg);
            }

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

    // categories for cached info
    enum Category {Composition, Type};
    }
