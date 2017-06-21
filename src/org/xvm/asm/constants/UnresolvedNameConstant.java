package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * Represent a constant that will eventually be replaced with a real identity constant.
 */
public class UnresolvedNameConstant
        extends IdentityConstant
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
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        if (isNameResolved())
            {
            return constant.getParentConstant();
            }

        throw new IllegalStateException("unresolved: " + getName());
        }

    @Override
    public String getName()
        {
        if (isNameResolved())
            {
            return constant.getName();
            }

        String[]      names  = this.names;
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

    public boolean isNameResolved()
        {
        return constant != null;
        }

    public void resolve(IdentityConstant constant)
        {
        assert this.constant == null || this.constant == constant;
        this.constant = constant;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return isNameResolved() ? constant.getFormat() : Format.Unresolved;
        }

    @Override
    public Object getLocator()
        {
        return isNameResolved() ? constant.getLocator() : null;
        }

    @Override
    public String getValueString()
        {
        return isNameResolved()
                ? constant.getValueString()
                : getName();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (isNameResolved())
            {
            return constant.compareDetails(that);
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
            constant.registerConstants(pool);
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
            constant.assemble(out);
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
                ? constant.getDescription()
                : "name=" + getName();
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
        public boolean equals(Object obj)
        {
        return isNameResolved()
                ? constant.equals(obj)
                : this == obj;
        }

    @Override
    public int hashCode()
        {
        if (isNameResolved())
            {
            return constant.hashCode();
            }
        else
            {
            String[] names  = this.names;
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

    private String[]         names;
    private IdentityConstant constant;
    }
