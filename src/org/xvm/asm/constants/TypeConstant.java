package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * A base class for the various forms of Constants that will represent data types.
 *
 * @author cp 2017.04.24
 */
public abstract class TypeConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    protected TypeConstant(ConstantPool pool, Constant.Format format, DataInput in)
            throws IOException
        {
        super(pool);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    protected TypeConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Determine if this TypeConstant is the "Object" type.
     *
     * @return true iff this TypeConstant represents the type of the Ecstasy root Object class
     */
    public boolean isEcstasyObject()
        {
        return false;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public abstract Constant.Format getFormat();

    @Override
    protected abstract int compareDetails(Constant that);


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected abstract void registerConstants(ConstantPool pool);

    @Override
    protected abstract void assemble(DataOutput out)
            throws IOException;

    @Override
    public String getDescription()
        {
        return "type=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public abstract int hashCode();

    @Override
    public boolean equals(Object obj)
        {
        if (obj instanceof UnresolvedTypeConstant)
            {
            TypeConstant type = ((UnresolvedTypeConstant) obj).getResolvedConstant();
            if (type != null)
                {
                return equals(type);
                }
            }

        return super.equals(obj);
        }


    // ----- fields --------------------------------------------------------------------------------

    }
