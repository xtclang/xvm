package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant that will eventually be replaced with a real type.
 */
public class UnresolvedTypeConstant
        extends TypeConstant
        implements ResolvableConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real constant
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param oSrc  the opaque source of the type information; must implement toString()
     */
    public UnresolvedTypeConstant(ConstantPool pool, Object oSrc)
        {
        super(pool);
        m_oSrc = oSrc;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the source of the type information, which is an opaque object that provides a {@link
     * Object#toString} implementation.
     *
     * @return the source information that is used to resolve the type constant, or null if no
     *         source information was provided
     */
    public Object getUnresolvedSource()
        {
        return m_oSrc;
        }

    /**
     * @return true iff the type has been resolved
     */
    public boolean isTypeResolved()
        {
        return getResolvedConstant() != null;
        }

    @Override
    public void resolve(Constant type)
        {
        assert type instanceof TypeConstant;
        assert !isTypeResolved();
        if (m_resolved instanceof UnresolvedTypeConstant)
            {
            ((UnresolvedTypeConstant) m_resolved).resolve((TypeConstant) type);
            }
        else
            {
            m_resolved = (TypeConstant) type;
            }
        }

    @Override
    public TypeConstant getResolvedConstant()
        {
        TypeConstant type = m_resolved;
        while (type instanceof UnresolvedTypeConstant)
            {
            type = ((UnresolvedTypeConstant) type).m_resolved;
            }
        return type;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return m_resolved == null ? Format.Unresolved : m_resolved.getFormat();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        if (m_resolved != null)
            {
            visitor.accept(m_resolved);
            }
        }

    @Override
    protected int compareDetails(Constant that)
        {
        // need to return a value that allows for stable sorts, but unless this==that, the details
        // can never be equal
        return this == that ? 0 : -1;
        }

    @Override
    public String getValueString()
        {
        return m_resolved == null
                ? m_oSrc == null ? this.getClass().getSimpleName() : (m_oSrc + " (unresolved)")
                : m_resolved.getValueString();
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
        if (m_resolved != null)
            {
            m_resolved.registerConstants(pool);
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        if (m_resolved == null)
            {
            throw new IllegalStateException();
            }
        else
            {
            m_resolved.assemble(out);
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        if (m_resolved == null)
            {
            return m_oSrc == null ? -314159265 : m_oSrc.hashCode();
            }
        else
            {
            return m_resolved.hashCode();
            }
        }

    @Override
    public boolean equals(Object obj)
        {
        return this == obj || (m_resolved != null && m_resolved.equals(obj));
        }

    @Override
    public String toString()
        {
        return m_resolved == null
                ? super.toString()
                : m_resolved.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    private TypeConstant m_resolved;
    private Object m_oSrc;
    }
