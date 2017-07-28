package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant that will eventually be replaced with a real identity constant.
 */
public class UnresolvedNameConstant
        extends IdentityConstant
        implements ResolvableConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real constant
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    public UnresolvedNameConstant(ConstantPool pool, String[] names)
        {
        super(pool);
        this.m_asName = names;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        if (isNameResolved())
            {
            return m_constId.getParentConstant();
            }

        throw new IllegalStateException("unresolved: " + getName());
        }

    @Override
    public String getName()
        {
        if (isNameResolved())
            {
            return m_constId.getName();
            }

        String[]      names  = this.m_asName;
        StringBuilder sb     = new StringBuilder();
        for (int i = 0, c = names.length; i < c; ++i)
            {
            if (i > 0)
                {
                sb.append('.');
                }
            sb.append(names[i]);
            }
        return sb.toString();
        }

    public int getNameCount()
        {
        return m_asName.length;
        }

    public String getName(int i)
        {
        return m_asName[i];
        }

    public boolean isNameResolved()
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
        assert constant instanceof IdentityConstant;
        assert this.m_constId == null || this.m_constId == constant;
        this.m_constId = (IdentityConstant) constant;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return isNameResolved() ? m_constId.getFormat() : Format.Unresolved;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constId);
        }

    @Override
    public Object getLocator()
        {
        return isNameResolved() ? m_constId.getLocator() : null;
        }

    @Override
    public String getValueString()
        {
        return isNameResolved()
                ? m_constId.getValueString()
                : getName();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (isNameResolved())
            {
            if (that instanceof UnresolvedNameConstant && ((UnresolvedNameConstant) that).isNameResolved())
                {
                that = ((UnresolvedNameConstant) that).m_constId;
                }
            return m_constId.compareDetails(that);
            }
        else if (that instanceof UnresolvedNameConstant)
            {
            String[] asThis = this.m_asName;
            String[] asThat = ((UnresolvedNameConstant) that).m_asName;
            int      cThis  = asThis.length;
            int      cThat  = asThat.length;
            for (int i = 0, c = Math.min(cThis, cThat); i < c; ++i)
                {
                int n = asThis[i].compareTo(asThat[i]);
                if (n != 0)
                    {
                    return n;
                    }
                }
            return cThis - cThat;
            }
        else
            {
            // need to return a value that allows for stable sorts, but unless this==that, the
            // details can never be equal
            return this == that ? 0 : -1;
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
        if (isNameResolved())
            {
            m_constId = (IdentityConstant) pool.register(m_constId);
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
        if (isNameResolved())
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
        return isNameResolved()
                ? m_constId.getDescription()
                : "name=" + getName();
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        if (isNameResolved())
            {
            return m_constId.hashCode();
            }
        else
            {
            String[] names  = this.m_asName;
            int      cNames = names.length;
            int      nHash  = cNames ^ names[0].hashCode();
            if (cNames > 1)
                {
                nHash ^= names[cNames-1].hashCode();
                }
            return nHash;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private String[]         m_asName;
    private IdentityConstant m_constId;
    }
