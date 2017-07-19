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
     * @param oSrc  the source information that will be used to resolve the class identity; not null
     */
    public UnresolvedClassConstant(ConstantPool pool, Object oSrc)
        {
        super(pool);

        assert oSrc != null;
        this.m_oSrc = oSrc;
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
                : m_oSrc.toString() + " (unresolved)";
        }

    /**
     * Obtain the source of the class information, which is an opaque object that provides a {@link
     * Object#toString} implementation.
     *
     * @return the source information that is used to resolve the type constant; never null
     */
    public Object getUnresolvedSource()
        {
        return m_oSrc;
        }

    /**
     * @return true iff the UnresolvedClassConstant has been resolved
     */
    public boolean isClassResolved()
        {
        return m_constId != null && (!(m_constId instanceof UnresolvedClassConstant)
                || ((UnresolvedClassConstant) m_constId).isResolved());
        }

    @Override
    public IdentityConstant getResolvedConstant()
        {
        IdentityConstant constId = m_constId;
        while (constId instanceof UnresolvedClassConstant)
            {
            constId = ((UnresolvedClassConstant) constId).m_constId;
            }
        return constId;
        }

    @Override
    public void resolve(Constant constant)
        {
        if (!(constant instanceof ClassConstant))
            {
            throw new IllegalArgumentException("constant must be ClassConstant (" + constant + ")");
            }

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
        if (this == that)
            {
            return 0;
            }

        if (isClassResolved())
            {
            if (that instanceof UnresolvedClassConstant)
                {
                if (((UnresolvedClassConstant) that).isClassResolved())
                    {
                    that = ((UnresolvedClassConstant) that).getResolvedConstant();
                    }
                else
                    {
                    return -1;
                    }
                }
            return m_constId.compareDetails(that);
            }

        return -1;
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
                : m_oSrc.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    private Object           m_oSrc;
    private IdentityConstant m_constId;
    }
