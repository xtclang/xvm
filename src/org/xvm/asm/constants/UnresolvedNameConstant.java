package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * Represent a constant that will eventually be replaced with a real name. Because the constant is
 * actually a CharStringConstant, it "completes" itself (by eventually becoming a "real" resolved
 * CharStringConstant.)
 */
public class UnresolvedNameConstant
        extends CharStringConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real constant
     *
     * @param pool         the ConstantPool that will contain this Constant
     */
    public UnresolvedNameConstant(ConstantPool pool)
        {
        super(pool, UNRESOLVED);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    public boolean isNameResolved()
        {
        return getValue() != UNRESOLVED;
        }

    @Override
    public void resolve(String value)
        {
        super.resolve(value);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Object getLocator()
        {
        return isNameResolved() ? super.getLocator() : null;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (isNameResolved())
            {
            return super.compareDetails(that);
            }
        else
            {
            // need to return a value that allows for stable sorts, but unless this==that, the details
            // can never be equal
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
            super.registerConstants(pool);
            }
        else
            {
            throw new IllegalStateException();
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        if (isNameResolved())
            {
            super.assemble(out);
            }
        else
            {
            throw new IllegalStateException();
            }
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
        public boolean equals(Object obj)
        {
        if (isNameResolved())
            {
            return super.equals(obj);
            }
        else
            {
            return this == obj;
            }
        }

    @Override
    public int hashCode()
        {
        if (isNameResolved())
            {
            return super.hashCode();
            }
        else
            {
            return -271828182;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    }
