package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.PropertyStructure;

import org.xvm.util.Handy;


/**
 * Represents the information about a single property at a single virtual level.
 */
public class PropertyBody
        implements Constants
    {
    /**
     * Construct a PropertyBody from the passed information.
     *
     * @param struct         the property structure that this body is derived from
     * @param type           the type of the property, including any type annotations (required)
     * @param fRO            true iff the property has any of a number of indicators that would
     *                       indicate that the property is read-only
     * @param fRW            true iff the property has any of a number of indicators that would
     *                       indicate that the property is read-write
     * @param accessRef      the access required to obtain the property's Ref
     * @param accessVar      the access required to obtain the property's Var (or null)
     * @param fCustomCode    true to indicate that the property has custom code that overrides
     *                       the underlying Ref/Var implementation
     * @param fReqField      true iff the property requires the presence of a field
     * @param fAbstract      true iff the property is from an interface declaration, or is
     *                       declared explicitly as abstract
     * @param fConstant      true iff the property represents a named constant value
     * @param constInitVal   the initial value for the property
     * @param constInitFunc  the function that will provide an initial value for the property
     */
    public PropertyBody(
            PropertyStructure struct,
            TypeConstant      type,
            boolean           fRO,
            boolean           fRW,
            Access            accessRef,
            Access            accessVar,
            boolean           fCustomCode,
            boolean           fReqField,
            boolean           fAbstract,
            boolean           fConstant,
            Constant          constInitVal,
            MethodConstant    constInitFunc)
        {
        assert struct    != null;
        assert type      != null;
        assert accessRef != null;

        m_structProp    = struct;
        m_paraminfo     = null;
        m_type          = type;
        m_fRO           = fRO;
        m_fRW           = fRW;
        m_accessRef     = accessRef;
        m_accessVar     = accessVar;
        m_fCustom       = fCustomCode;
        m_fField        = fReqField;
        m_fAbstract     = fAbstract;
        m_fConstant     = fConstant;
        m_constInitVal  = constInitVal;
        m_constInitFunc = constInitFunc;
        }

    /**
     * Construct a PropertyBody that represents the specified type parameter.
     *
     * @param struct  the property structure that this body is derived from
     * @param param   the type parameter information
     */
    public PropertyBody(PropertyStructure struct, ParamInfo param)
        {
        assert struct != null;
        assert param  != null;

        ConstantPool pool = struct.getConstantPool();

        m_structProp    = struct;
        m_paraminfo     = param;
        m_type          = pool.ensureParameterizedTypeConstant(pool.typeType(), param.getConstraintType());
        m_fRO           = true;
        m_fRW           = false;
        m_accessRef     = Access.PUBLIC;
        m_accessVar     = null;
        m_fCustom       = false;
        m_fField        = false;
        m_fAbstract     = false;
        m_fConstant     = false;
        m_constInitVal  = null;
        m_constInitFunc = null;
        }

    /**
     * @return the container of the property
     */
    public IdentityConstant getParent()
        {
        return getIdentity().getParentConstant();
        }

    /**
     * @return the identity of the property
     */
    public PropertyConstant getIdentity()
        {
        return m_structProp.getIdentityConstant();
        }

    /**
     * @return the PropertyStructure for the property body
     */
    public PropertyStructure getStructure()
        {
        return m_structProp;
        }

    /**
     * @return the property name
     */
    public String getName()
        {
        return m_structProp.getName();
        }

    /**
     * @return the property type
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return true iff this property represents a type parameter type
     */
    public boolean isTypeParam()
        {
        return m_paraminfo != null;
        }

    /**
     * @return the type param info
     */
    public ParamInfo getParamInfo()
        {
        return m_paraminfo;
        }

    /**
     * @return the Access required for the Ref form of the property
     */
    public Access getRefAccess()
        {
        return m_accessRef;
        }

    /**
     * @return the Access required for the Var form of the property, or null if not specified
     */
    public Access getVarAccess()
        {
        return m_accessVar;
        }

    /**
     * @return true iff this property is a Ref; false iff this property is a Var
     */
    public boolean isRO()
        {
        return m_fRO;
        }

    /**
     * @return true iff this property has a field, whether or not that field is reachable
     */
    public boolean hasField()
        {
        return m_fField;
        }

    /**
     * @return true iff the property has any methods in addition to the underlying Ref or Var
     *         "rebasing" implementation, and in addition to any annotations
     */
    public boolean isCustomLogic()
        {
        return m_fCustom;
        }

    /**
     * @return the MethodConstant that will identify the getter (but not necessarily a
     *         MethodConstant that actually exists, because there may not be a getter, but also
     *         because the fully resolved type is used in the MethodConstant)
     */
    public MethodConstant getGetterId()
        {
        PropertyConstant constId = getIdentity();
        ConstantPool     pool    = constId.getConstantPool();
        return pool.ensureMethodConstant(constId, "get", ConstantPool.NO_TYPES, new TypeConstant[]{m_type});
        }

    /**
     * @return the MethodConstant that will identify the setter (but not necessarily a
     *         MethodConstant that actually exists, because there may not be a setter, but also
     *         because the fully resolved type is used in the MethodConstant)
     */
    public MethodConstant getSetterId()
        {
        PropertyConstant constId = getIdentity();
        ConstantPool     pool    = constId.getConstantPool();
        return pool.ensureMethodConstant(constId, "set", new TypeConstant[]{m_type}, ConstantPool.NO_TYPES);
        }

    /**
     * @return true iff the property is abstract, which means that it comes from an interface
     */
    public boolean isAbstract()
        {
        return m_fAbstract;
        }

    /**
     * @return true if the property is annotated by "@Abstract"
     */
    public boolean isExplicitAbstract()
        {
        return TypeInfo.containsAnnotation(m_structProp.getPropertyAnnotations(), "Abstract");
        }

    /**
     * @return true if the property is annotated by "@Override"
     */
    public boolean isExplicitOverride()
        {
        return TypeInfo.containsAnnotation(m_structProp.getPropertyAnnotations(), "Override");
        }

    /**
     * @return true if the property is annotated by "@RO"
     */
    public boolean isExplicitReadOnly()
        {
        return TypeInfo.containsAnnotation(m_structProp.getPropertyAnnotations(), "RO");
        }

    /**
     * @return true if the property is annotated by "@Inject"
     */
    public boolean isInjected()
        {
        return TypeInfo.containsAnnotation(m_structProp.getRefAnnotations(), "Inject");
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return getIdentity().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof PropertyBody))
            {
            return false;
            }

        PropertyBody that = (PropertyBody) obj;
        return this.m_structProp.equals(that.m_structProp)
            && this.m_type      .equals(that.m_type)
            && this.m_fRO       == that.m_fRO
            && this.m_fRW       == that.m_fRW
            && this.m_accessRef == that.m_accessRef
            && this.m_accessVar == that.m_accessVar
            && this.m_fCustom   == that.m_fCustom
            && this.m_fField    == that.m_fField
            && this.m_fAbstract == that.m_fAbstract
            && this.m_fConstant == that.m_fConstant
            && Handy.equals(this.m_paraminfo    , that.m_paraminfo    )
            && Handy.equals(this.m_constInitVal , that.m_constInitVal )
            && Handy.equals(this.m_constInitFunc, that.m_constInitFunc);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (m_accessRef != Access.STRUCT)
            {
            sb.append(m_accessRef);
            if (m_accessVar != null)
                {
                sb.append('/')
                  .append(m_accessVar);
                }
            sb.append(' ');
            }

        sb.append(m_type.getValueString())
          .append(' ')
          .append(getName())
          .append("; (id=")
          .append(getIdentity().getValueString());

        if (m_paraminfo != null)
            {
            sb.append(", type-param");
            }
        if (m_fRO)
            {
            sb.append(", RO");
            }
        if (m_fRW)
            {
            sb.append(", RW");
            }
        if (m_fAbstract)
            {
            sb.append(", abstract");
            }
        if (m_fConstant)
            {
            sb.append(", constant");
            }
        if (m_fField)
            {
            sb.append(", has-field");
            }
        if (m_fCustom)
            {
            sb.append(", has-code");
            }
        if (m_constInitVal != null)
            {
            sb.append(", has-init-value");
            }
        if (m_constInitFunc != null)
            {
            sb.append(", has-init-fn");
            }

        return sb.append(')').toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The property's underlying structure.
     */
    private final PropertyStructure m_structProp;

    /**
     * Type parameter information.
     */
    private final ParamInfo m_paraminfo;

    /**
     * Type of the property, including any annotations on the type.
     */
    private final TypeConstant m_type;

    /**
     * True iff the property is explicitly a Ref and not a Var.
     */
    private final boolean m_fRO;

    /**
     * True iff the property is explicitly a Var and not a Ref.
     */
    private final boolean m_fRW;

    /**
     * Access for the property.
     */
    private final Access m_accessRef;

    /**
     * A second access specifically for the Var (write access), or null.
     */
    private final Access m_accessVar;

    /**
     * True to indicate that the property has custom code that overrides the underlying Ref/Var
     * implementation.
     */
    private final boolean m_fCustom;

    /**
     * True iff the property requires a field. A field is assumed to exist iff the property is a
     * non-constant, non-interface property, @Abstract is not specified, @Inject is not specified,
     * @RO is not specified, @Override is not specified, and the Ref.get() doesn't block its super.
     */
    private final boolean m_fField;

    /**
     * True iff the property is abstract, such as when it is on an interface, or when it is
     * explicitly declared as abstract.
     */
    private final boolean m_fAbstract;

    /**
     * True iff the property is a constant value.
     */
    private final boolean m_fConstant;

    /**
     * The initial value of the property, as a constant.
     */
    private final Constant m_constInitVal;

    /**
     * The function that provides the initial value of the property.
     */
    private final MethodConstant m_constInitFunc;
    }
