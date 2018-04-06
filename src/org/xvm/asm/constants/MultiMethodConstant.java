package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MultiMethodStructure;


/**
 * Represent a collection of methods or functions with the same name.
 */
public class MultiMethodConstant
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
    public MultiMethodConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Construct a constant whose value is a property identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this property
     * @param sName        the property name
     */
    public MultiMethodConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);

        if (    !( constParent.getFormat() == Format.Module
                || constParent.getFormat() == Format.Package
                || constParent.getFormat() == Format.Class
                || constParent.getFormat() == Format.NativeClass
                || constParent.getFormat() == Format.Property
                || constParent.getFormat() == Format.Method ))
            {
            throw new IllegalArgumentException("parent module, package, class, or method required");
            }
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.MultiMethod;
        }

    @Override
    public MultiMethodStructure resolveNestedIdentity(ClassStructure clz)
        {
        Component parent = getNamespace().resolveNestedIdentity(clz);
        if (parent == null)
            {
            return null;
            }

        Component that = parent.getChild(this.getName());
        return that instanceof MultiMethodStructure
                ? (MultiMethodStructure) that
                : null;
        }

    @Override
    public IdentityConstant ensureNestedIdentity(IdentityConstant that)
        {
        return that.getConstantPool().ensureMultiMethodConstant(
                getParentConstant().ensureNestedIdentity(that), getName());
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "multimethod=" + getValueString();
        }
    }
