package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.PropertyStructure;


/**
 * Represent a property constant, which identifies a particular property structure.
 */
public class PropertyConstant
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
    public PropertyConstant(ConstantPool pool, Format format, DataInput in)
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
    public PropertyConstant(ConstantPool pool, IdentityConstant constParent, String sName)
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

    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return a signature constant representing this property
     */
    public SignatureConstant getSignature()
        {
        SignatureConstant sig = m_constSig;
        if (sig == null)
            {
            // transient synthetic constant; no need to register
            sig = m_constSig = new SignatureConstant(getConstantPool(), this);
            }
        return sig;
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Property;
        }

    @Override
    public boolean isProperty()
        {
        return true;
        }

    @Override
    public TypeConstant getType()
        {
        // TODO return Property<> or Ref<>
        throw new IllegalStateException("TODO: property type?!");
        }

    @Override
    public TypeConstant getRefType()
        {
        TypeConstant type = m_type;
        if (type == null)
            {
            m_type = type = ((PropertyStructure) getComponent()).getType();
            }
        return type;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------


    @Override
    protected void disassemble(DataInput in) throws IOException
        {
        super.disassemble(in);

        m_type = null;
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_type = null;

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out) throws IOException
        {
        super.assemble(out);

        m_type = null;
        }

    @Override
    public String getDescription()
        {
        return "property=" + getValueString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Cached type.
     */
    private transient TypeConstant m_type;

    /**
     * Cached constant that represents the signature of this property.
     */
    private transient SignatureConstant m_constSig;
    }
