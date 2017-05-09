package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * Represent a constant that will eventually be replaced with a real type.
 */
public class UnresolvedTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real constant
     *
     * @param pool         the ConstantPool that will contain this Constant
     */
    public UnresolvedTypeConstant(ConstantPool pool, String sType)
        {
        super(pool);
        m_sType = sType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return true iff the type has been resolved
     */
    public boolean isTypeResolved()
        {
        return getResolvedType() != null;
        }

    public void resolve(TypeConstant type)
        {
        assert !isTypeResolved();
        if (m_resolved instanceof UnresolvedTypeConstant)
            {
            ((UnresolvedTypeConstant) m_resolved).resolve(type);
            }
        else
            {
            m_resolved = type;
            }
        }

    /**
     * @return the TypeConstant that this has been resolved to, or null if this is still unresolved
     */
    public TypeConstant getResolvedType()
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
    protected int compareDetails(Constant that)
        {
        // need to return a value that allows for stable sorts, but unless this==that, the details
        // can never be equal
        return this == that ? 0 : -1;
        }

    @Override
    public String getValueString()
        {
        return m_sType == null ? "<unresolved-type>" : m_sType + " (unresolved)";
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
            return m_sType == null ? -314159265 : m_sType.hashCode();
            }
        else
            {
            return m_resolved.hashCode();
            }
        }

    @Override
    public boolean equals(Object obj)
        {
        if (m_resolved == null)
            {
            return this == obj;
            }
        else
            {
            return m_resolved.equals(obj);
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private TypeConstant m_resolved;
    private String m_sType;
    }
