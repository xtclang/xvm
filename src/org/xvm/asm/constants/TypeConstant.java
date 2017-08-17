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

    public boolean isImmutabilitySpecified()
        {
        return false;
        }

    public boolean isAccessSpecified()
        {
        return false;
        }

    public Access getAccess()
        {
        return Access.PUBLIC;
        }

    public boolean isParamsSpecified()
        {
        return false;
        }

    public boolean isSingleDefiningConstant()
        {
        return true;
        }

    public abstract Constant getDefiningConstant();

    public boolean isAnnotated()
        {
        return false;
        }

    /**
     * @return true iff this TypeConstant represents an auto-narrowing type
     */
    public boolean isAutoNarrowing()
        {
        return false;
        }

    /**
     * Determine if this TypeConstant represents a core, implicitly-imported Ecstasy type denoted
     * by the specified name.
     *
     * @param sName  the name or alias by which the Ecstasy core type is imported
     *
     * @return true iff this TypeConstant is the Ecstasy core type identified by the passed name
     */
    public boolean isEcstasy(String sName)
        {
        IdentityConstant constId = getConstantPool().getImplicitlyImportedIdentity(sName);
        if (constId == null)
            {
            throw new IllegalArgumentException("no such implicit name: " + sName);
            }

        return isSingleDefiningConstant() && getDefiningConstant().equals(constId);
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

    // ----- fields --------------------------------------------------------------------------------

    }
