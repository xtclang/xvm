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
     * Construct a constant whose value is a multi-method identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, property, or method that contains this
     *                     multi-method
     * @param sName        the method name
     * @param info         optional TypeInfo
     */
    public MultiMethodConstant(ConstantPool pool, IdentityConstant constParent, String sName, TypeInfo info)
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

        m_info = info;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return a TypeInfo, if one was supplied during construction
     */
    public TypeInfo getTypeInfo()
        {
        return m_info;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.MultiMethod;
        }

    @Override
    public MultiMethodStructure relocateNestedIdentity(ClassStructure clz)
        {
        Component parent = getNamespace().relocateNestedIdentity(clz);
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
    public IdentityConstant ensureNestedIdentity(ConstantPool pool, IdentityConstant that)
        {
        return pool.ensureMultiMethodConstant(
                getParentConstant().ensureNestedIdentity(pool, that), getName());
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription()
        {
        return "multimethod=" + getValueString();
        }


    // ----- fields --------------------------------------------------------------------------------

    private transient TypeInfo m_info;
    }
