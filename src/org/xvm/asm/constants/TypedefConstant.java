package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.TypedefStructure;


/**
 * Represent a "typedef" constant, which identifies a specific typedef structure.
 */
public class TypedefConstant
        extends NamedConstant
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
    public TypedefConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is a typedef identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the structure that contains the typedef
     * @param sName        the typedef name
     */
    public TypedefConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);

        if (    !( constParent.getFormat() == Format.Module
                || constParent.getFormat() == Format.Package
                || constParent.getFormat() == Format.Class
                || constParent.getFormat() == Format.Method ))
            {
            throw new IllegalArgumentException("parent module, package, class, or method required");
            }
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Typedef;
        }

    @Override
    public boolean containsUnresolved()
        {
        TypedefStructure typedef = (TypedefStructure) getComponent();
        return typedef == null || typedef.getType().containsUnresolved();
        }

    @Override
    public TypedefStructure resolveNestedIdentity(ClassStructure clz)
        {
        Component parent = getNamespace().resolveNestedIdentity(clz);
        if (parent == null)
            {
            return null;
            }

        Component that = parent.getChild(this.getName());
        return that instanceof TypedefStructure
                ? (TypedefStructure) that
                : null;
        }

    @Override
    public IdentityConstant ensureNestedIdentity(IdentityConstant that)
        {
        return getConstantPool().ensureTypedefConstant(
                getParentConstant().ensureNestedIdentity(that), getName());
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "typedef name=" + getValueString();
        }
    }
