package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant that will eventually be replaced with a real class constant.
 */
public class UnresolvedClassConstant
        extends ClassConstant
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
        this.name = name;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        if (isClassResolved())
            {
            return constant.getParentConstant();
            }

        throw new IllegalStateException("unresolved: " + getName());
        }

    @Override
    public String getName()
        {
        return isClassResolved()
                ? constant.getName()
                : name;
        }

    public boolean isClassResolved()
        {
        return constant != null;
        }

    public void resolve(ClassConstant constant)
        {
        assert this.constant == null || this.constant == constant;
        this.constant = constant;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return isClassResolved() ? constant.getFormat() : Format.Unresolved;
        }

    @Override
    public Object getLocator()
        {
        return isClassResolved() ? constant.getLocator() : null;
        }

    @Override
    public String getValueString()
        {
        return isClassResolved()
                ? constant.getValueString()
                : getName();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (isClassResolved())
            {
            if (that instanceof UnresolvedClassConstant && ((UnresolvedClassConstant) that).isClassResolved())
                {
                that = ((UnresolvedClassConstant) that).constant;
                }
            return constant.compareDetails(that);
            }
        else if (that instanceof UnresolvedClassConstant)
            {
            return this.name.compareTo(((UnresolvedClassConstant) that).name);
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
        if (isClassResolved())
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
        return isClassResolved()
                ? constant.getDescription()
                : "name=" + getName();
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof UnresolvedClassConstant && ((UnresolvedClassConstant) obj).isClassResolved())
            {
            obj = ((UnresolvedClassConstant) obj).constant;
            }

        return isClassResolved()
                ? constant.equals(obj)
                : super.equals(obj);
        }

    @Override
    public int hashCode()
        {
        return isClassResolved()
                ? constant.hashCode()
                : name.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    private String           name;
    private IdentityConstant constant;
    }
