package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
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
                || constParent.getFormat() == Format.NativeClass
                || constParent.getFormat() == Format.Property
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
        TypeConstant type = m_typeRef;
        if (type == null)
            {
            m_typeRef = type = ((PropertyStructure) getComponent()).getRefType();
            }
        return type;
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

    @Override
    public Object getNestedIdentity()
        {
        // property can be identified with only a name, assuming it is not recursively nested
        return getNamespace().isNested()
                ? new NestedIdentity()
                : getName();
        }

    @Override
    public Object resolveNestedIdentity(GenericTypeResolver resolver)
        {
        // property can be identified with only a name, assuming it is not recursively nested
        return getNamespace().isNested()
                ? new NestedIdentity(resolver)
                : getName();
        }

    @Override
    public PropertyStructure relocateNestedIdentity(ClassStructure clz)
        {
        Component parent = getNamespace().relocateNestedIdentity(clz);
        if (parent == null)
            {
            return null;
            }

        Component that = parent.getChild(this.getName());
        return that instanceof PropertyStructure
                ? (PropertyStructure) that
                : null;
        }

    @Override
    public IdentityConstant ensureNestedIdentity(IdentityConstant that)
        {
        return that.getConstantPool().ensurePropertyConstant(
                getParentConstant().ensureNestedIdentity(that), getName());
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
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        IdentityConstant idParent = getNamespace();
        while (idParent != null)
            {
            switch (idParent.getFormat())
                {
                case Method:
                case Property:
                    sb.insert(0, idParent.getName() + '#');
                    idParent = idParent.getNamespace();
                    break;

                default:
                    idParent = null;
                }
            }

        return "property=" + sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Cached type.
     */
    private transient TypeConstant m_type;

    /**
     * Cached Ref type.
     */
    private transient TypeConstant m_typeRef;

    /**
     * Cached constant that represents the signature of this property.
     */
    private transient SignatureConstant m_constSig;
    }
