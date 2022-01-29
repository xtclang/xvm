package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.PropertyStructure;


/**
 * Represent a property constant, which identifies a particular property structure.
 */
public class PropertyConstant
        extends FormalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

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

        checkParent(constParent);
        }

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
     * Validate the parent's format.
     *
     * @param idParent  the parent's id
     */
    protected void checkParent(IdentityConstant idParent)
        {
        switch (idParent.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case NativeClass:
            case Property:
            case Method:
                break;

            default:
                throw new IllegalArgumentException("invalid parent: " + idParent.getFormat());
            }
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();
        }


    // ----- FormalConstant methods ----------------------------------------------------------------

    @Override
    public TypeConstant getConstraintType()
        {
        TypeConstant typeConstraint = m_typeConstraint;
        if (typeConstraint != null)
            {
            return typeConstraint;
            }

        assert isFormalType();

        // the type of the property must be "Type<X>", so return X
        typeConstraint = getType();

        assert typeConstraint.isTypeOfType() && typeConstraint.isParamsSpecified();

        typeConstraint = typeConstraint.getParamType(0);

        if (!typeConstraint.isParamsSpecified() && typeConstraint.isExplicitClassIdentity(true))
            {
            // create a normalized formal type
            ConstantPool   pool = getConstantPool();
            ClassStructure clz  = (ClassStructure) typeConstraint.getSingleUnderlyingClass(true).getComponent();
            if (clz == null)
                {
                // there is a possibility for this method be called before the pool is fully
                // assembled; return the naked type without caching it
                return typeConstraint;
                }

            if (clz.isParameterized())
                {
                Set<StringConstant> setFormalNames = clz.getTypeParams().keySet();
                TypeConstant[]      atypeFormal    = new TypeConstant[setFormalNames.size()];
                int ix = 0;
                for (StringConstant constName : setFormalNames)
                    {
                    Constant constant = pool.ensureFormalTypeChildConstant(this, constName.getValue());
                    atypeFormal[ix++] = constant.getType();
                    }
                typeConstraint = pool.ensureParameterizedTypeConstant(typeConstraint, atypeFormal);
                }
            }
        return m_typeConstraint = typeConstraint;
        }

    @Override
    public TypeConstant resolve(GenericTypeResolver resolver)
        {
        if (isTypeSequenceTypeParameter())
            {
            // the following block is for nothing else, but compilation of Tuple and
            // ConditionalTuple natural classes
            if (resolver instanceof TypeConstant typeResolver)
                {
                if (typeResolver.isTuple() && !typeResolver.isParamsSpecified())
                    {
                    return null;
                    }
                }
            }

        return resolver.resolveFormalType(this);
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

    /**
     * @return true iff this property is a generic type parameter
     */
    public boolean isFormalType()
        {
        PropertyStructure struct = (PropertyStructure) getComponent();
        return struct != null && struct.isGenericTypeParameter();
        }

    /**
     * @return a TypeConstant representing a formal (generic) type represented by this property,
     *         which must be a generic type parameter
     */
    public TypeConstant getFormalType()
        {
        assert isFormalType();
        return getConstantPool().ensureTerminalTypeConstant(this);
        }

    /**
     * @return true iff this property is a formal type parameter that materializes into a
     *         sequence of types
     */
    public boolean isTypeSequenceTypeParameter()
        {
        return isFormalType() && getConstraintType() instanceof TypeSequenceTypeConstant;
        }

    /**
     * @return true iff the property is a named constant value
     */
    public boolean isConstant()
        {
        PropertyStructure prop = (PropertyStructure) getComponent();
        return prop != null && prop.isConstant();
        }

    /**
     * @return true iff the property has a Future annotation
     */
    public boolean isFutureVar()
        {
        PropertyStructure prop = (PropertyStructure) getComponent();
        return prop != null && prop.isFuture();
        }

    /**
     * Obtain the TypeConstant that represents the runtime type of a Ref/Var for this property in
     * the context of the specified target.
     *
     * @param typeTarget  the target type (null if the property's {@link #getClassIdentity()
     *                    class identity} is the target)
     *
     * @return a TypeConstant
     */
    public TypeConstant getRefType(TypeConstant typeTarget)
        {
        if (typeTarget == null)
            {
            typeTarget = getConstantPool().ensureAccessTypeConstant(
                getClassIdentity().getType(), Access.PRIVATE);
            }

        Access access = getComponent().getAccess();
        if (access.isLessAccessibleThan(typeTarget.getAccess()))
            {
            typeTarget = typeTarget.getConstantPool().ensureAccessTypeConstant(typeTarget, access);
            }

        PropertyInfo infoThis = typeTarget.ensureTypeInfo().findProperty(this);

        assert infoThis != null;

        return infoThis.isCustomLogic()
                ? getConstantPool().ensurePropertyClassTypeConstant(typeTarget, this)
                : infoThis.getBaseRefType();
        }

    /**
     * @return true iff this property is nested directly inside of a class
     */
    public boolean isTopLevel()
        {
        return getParentConstant().isClass();
        }

    /**
     * Invalidate any cached information for this PropertyConstant. This method should be called
     * when there are any structural changes to the property that this constant identifies.
     */
    public void invalidateCache()
        {
        m_type           = null;
        m_constSig       = null;
        m_typeConstraint = null;
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public TypeConstant getValueType(ConstantPool pool, TypeConstant typeTarget)
        {
        if (typeTarget == null)
            {
            typeTarget = getClassIdentity().getType();
            }

        TypeConstant typePrivate  = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE);
        PropertyInfo infoProp     = typePrivate.ensureTypeInfo().findProperty(this);
        TypeConstant typeReferent = infoProp.getType();
        TypeConstant typeImpl     = pool.ensurePropertyClassTypeConstant(typePrivate, this);

        return pool.ensureParameterizedTypeConstant(pool.typeProperty(),
                typeTarget, typeReferent, typeImpl);
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
                ? getCanonicalNestedIdentity()
                : getName();
        }

    @Override
    public Object resolveNestedIdentity(ConstantPool pool, GenericTypeResolver resolver)
        {
        // property can be identified with only a name, assuming it is not recursively nested
        return getNamespace().isNested()
                ? resolver == null
                    ? getCanonicalNestedIdentity()
                    : new NestedIdentity(resolver)
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
    public PropertyConstant ensureNestedIdentity(ConstantPool pool, IdentityConstant that)
        {
        IdentityConstant idParent = getParentConstant();
        return idParent.equals(that)
            ? this
            : pool.ensurePropertyConstant(
                    idParent.ensureNestedIdentity(pool, that), getName());
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        return that.getConstantPool().ensurePropertyConstant(that, getName());
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        invalidateCache();

        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out) throws IOException
        {
        super.assemble(out);

        m_type     = null;
        m_constSig = null;
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder("property=");
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

        return sb.toString();
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

    /**
     * Cached constraint type.
     */
    protected transient TypeConstant m_typeConstraint;
    }
