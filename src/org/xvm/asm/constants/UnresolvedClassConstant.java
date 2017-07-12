package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant that will eventually be replaced with a real class constant.
 */
public class UnresolvedClassConstant
        extends ClassConstant
        implements ResolvableConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real ClassConstant.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param name  the name of the unresolved class
     */
    public UnresolvedClassConstant(ConstantPool pool, String name)
        {
        super(pool);
        this.m_sName = name;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        if (isClassResolved())
            {
            return m_constId.getParentConstant();
            }

        throw new IllegalStateException("unresolved: " + getName());
        }

    @Override
    public String getName()
        {
        return isClassResolved()
                ? m_constId.getName()
                : m_sName;
        }

    public boolean isClassResolved()
        {
        return m_constId != null;
        }

    @Override
    public IdentityConstant getResolvedConstant()
        {
        return m_constId;
        }

    @Override
    public void resolve(Constant constant)
        {
        assert constant instanceof ClassConstant;
        assert this.m_constId == null || this.m_constId == constant;
        this.m_constId = (ClassConstant) constant;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return isClassResolved() ? m_constId.getFormat() : Format.Unresolved;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        if (m_constId != null)
            {
            visitor.accept(m_constId);
            }
        }

    @Override
    public Object getLocator()
        {
        return isClassResolved() ? m_constId.getLocator() : null;
        }

    @Override
    public String getValueString()
        {
        return isClassResolved()
                ? m_constId.getValueString()
                : getName();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (isClassResolved())
            {
            if (that instanceof UnresolvedClassConstant && ((UnresolvedClassConstant) that).isClassResolved())
                {
                that = ((UnresolvedClassConstant) that).m_constId;
                }
            return m_constId.compareDetails(that);
            }
        else if (that instanceof UnresolvedClassConstant)
            {
            return this.m_sName.compareTo(((UnresolvedClassConstant) that).m_sName);
            }
        else
            {
            return -1;
            }
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        throw new UnsupportedOperationException();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        if (isClassResolved())
            {
            m_constId.registerConstants(pool);
            }
        else
            {
            throw new IllegalStateException("unresolved: " + getName());
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        if (isClassResolved())
            {
            m_constId.assemble(out);
            }
        else
            {
            throw new IllegalStateException("unresolved: " + getName());
            }
        }

    @Override
    public String getDescription()
        {
        return isClassResolved()
                ? m_constId.getDescription()
                : "name=" + getName();
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof UnresolvedClassConstant && ((UnresolvedClassConstant) obj).isClassResolved())
            {
            obj = ((UnresolvedClassConstant) obj).m_constId;
            }

        return isClassResolved()
                ? m_constId.equals(obj)
                : super.equals(obj);
        }

    @Override
    public int hashCode()
        {
        return isClassResolved()
                ? m_constId.hashCode()
                : m_sName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    private String           m_sName;
    private IdentityConstant m_constId;
    }
