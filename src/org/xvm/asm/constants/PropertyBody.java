package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * Represents the information about a single property at a single virtual level.
 */
public class PropertyBody
        implements Constants
    {
    /**
     * Construct a PropertyBody from the passed information.
     *
     * @param constId            the property constant
     * @param type               the type of the property, including any type annotations (required)
     * @param fRO                true iff the property has any of a number of indicators that would
     *                           indicate that the property is read-only
     * @param fRW                true iff the property has any of a number of indicators that would
     *                           indicate that the property is read-write
     * @param accessRef          the access required to obtain the property's Ref
     * @param accessVar          the access required to obtain the property's Var (or null)
     * @param aPropAnno          an array of non-virtual annotations on the property declaration
     *                           itself
     * @param aRefAnno           an array of annotations that apply to the Ref/Var of the property
     * @param fCustomCode        true to indicate that the property has custom code that overrides
     *                           the underlying Ref/Var implementation
     * @param fReqField          true iff the property requires the presence of a field
     * @param fAbstract          true iff the property is from an interface declaration, or is
     *                           declared explicitly as abstract
     * @param fConstant          true iff the property represents a named constant value
     * @param fTrailingOverride  true iff the property is still waiting to be combined with some
     *                           "super"
     * @param constInitVal       the initial value for the property
     * @param constInitFunc      the function that will provide an initial value for the property
     */
    public PropertyBody(
            PropertyConstant constId,
            TypeConstant type,
            boolean fRO,
            boolean fRW,
            Access accessRef,
            Access accessVar,
            Annotation[] aPropAnno,
            Annotation[] aRefAnno,
            boolean fCustomCode,
            boolean fReqField,
            boolean fAbstract,
            boolean fConstant,
            boolean fTrailingOverride,
            Constant constInitVal,
            MethodConstant constInitFunc)
        {
        assert constId != null;
        assert type    != null;

        m_constId       = constId;
        m_type          = type;
        m_paraminfo     = null;
        m_fRO           = fRO;
        m_fRW           = fRW;
        m_accessRef     = accessRef;
        m_accessVar     = accessVar;
        m_aPropAnno     = TypeInfo.validateAnnotations(aPropAnno);
        m_aRefAnno      = TypeInfo.validateAnnotations(aRefAnno);
        m_fCustom       = fCustomCode;
        m_fField        = fReqField;
        m_fAbstract     = fAbstract;
        m_fConstant     = fConstant;
        m_fOverride     = fTrailingOverride;
        m_constInitVal  = constInitVal;
        m_constInitFunc = constInitFunc;
        }

    /**
     * Construct a PropertyBody that represents the specified type parameter.
     *
     * @param constId  the identity of this property
     * @param param    the type parameter information
     */
    public PropertyBody(PropertyConstant constId, ParamInfo param)
        {
        ConstantPool pool = constId.getConstantPool();

        m_constId       = constId;
        m_type          = pool.ensureParameterizedTypeConstant(pool.typeType(), param.getConstraintType());
        m_paraminfo     = param;
        m_fRO           = true;
        m_fRW           = false;
        m_accessRef     = Access.PUBLIC;
        m_accessVar     = null;
        m_aPropAnno     = Annotation.NO_ANNOTATIONS;
        m_aRefAnno      = Annotation.NO_ANNOTATIONS;
        m_fCustom       = false;
        m_fField        = false;
        m_fAbstract     = false;
        m_fConstant     = false;
        m_fOverride     = false;
        m_constInitVal  = null;
        m_constInitFunc = null;
        }

    /**
     * Combine the information in this PropertyBody with the information from a super type's
     * PropertyBody.
     *
     * @param that  a super-type's PropertyBody
     * @param errs  the error list to log any conflicts to
     *
     * @return a PropertyBody representing the combined information
     */
    public PropertyBody combineWithSuper(PropertyBody that, ErrorListener errs)
        {
        assert that != null;
        assert errs != null;
        assert this.getName().equals(that.getName());

        if (this.isTypeParam() || that.isTypeParam())
            {
            if (this.isTypeParam() ^ that.isTypeParam())
                {
                m_constId.log(errs, Severity.ERROR, VE_PROPERTY_ILLEGAL,
                        m_constId.getValueString());
                return this;
                }

            if (this.getType().isA(that.getType()))
                {
                return this;
                }
            else if (that.getType().isA(this.getType()))
                {
                return that;
                }
            else
                {
                // right now, this is treated as an error; theoretically, we could "merge" or union
                // the types
                m_constId.log(errs, Severity.ERROR, VE_PROPERTY_TYPES_INCOMPATIBLE,
                        m_constId.getValueString(),
                        this.getType().getValueString(),
                        that.getType().getValueString());
                return this;
                }
            }

        // it is illegal to combine anything with a constant
        if (this.m_fConstant || that.m_fConstant)
            {
            m_constId.log(errs, Severity.ERROR, VE_CONST_INCOMPATIBLE,
                    m_constId.getValueString());
            }

        // types must match
        if (!this.m_type.isA(that.m_type))
            {
            m_constId.log(errs, Severity.ERROR, VE_PROPERTY_TYPES_INCOMPATIBLE,
                    m_constId.getValueString(),
                    this.getType().getValueString(),
                    that.getType().getValueString());
            }

        // cannot combine struct with anything other than struct
        if (this.m_accessRef == Access.STRUCT ^ that.m_accessRef == Access.STRUCT)
            {
            // error
            }

        // cannot combine private with anything
        if (this.m_accessRef == Access.PRIVATE || that.m_accessRef == Access.PRIVATE)
            {
            // error
            }

        // a non-abstract RO property combined with a RW property ... TODO

        boolean fThisInit = this.m_constInitVal != null || this.m_constInitFunc != null;

        return new PropertyBody(
                this.m_constId,
                this.m_type,
                this.m_fRO & that.m_fRO,                // read-only Ref if both are read-only
                this.m_fRW | that.m_fRW,                // read-write Ref if either is read-write
                this.m_aPropAnno,                       // property annotations NOT inherited
                TypeInfo.mergeAnnotations(this.m_aRefAnno, that.m_aRefAnno),
                this.m_fCustom | that.m_fCustom,        // custom logic if either is custom
                this.m_fField | that.m_fField,          // field present if either has field
                this.m_fAbstract,                       // abstract if the top one is abstract
                this.m_fConstant,                       // constant if the top one is constant (err)
                that.m_fOverride,                       // override if the bottom one is override
                fThisInit ? this.m_constInitVal  : that.m_constInitVal,
                fThisInit ? this.m_constInitFunc : that.m_constInitFunc);
        }

    /**
     * Create a new PropertyBody that represents a more limited (public or protected) access to the
     * members of this property that is on the private type.
     *
     * @param access  the desired access, either PUBLIC or PROTECTED
     *
     * @return a PropertyBody to use, or null if the PropertyBody would not be present on the type
     *         with the specified access
     */
    public PropertyBody limitAccess(Access access)
        {
        if (access.is)
        // TODO this property is either a Var or a Ref on the private type (i.e. this PropertyBody)
        //      determine if the same property would be a Var, a Ref, or absent from the type with the specified access
        //      - if absent, return null
        //      - if the same as on the private type, then return this
        //      - otherwise private type must be Var and we need to create a Ref (@RO) of the same
        return this;
        }

    /**
     * Specifies a value for whether or not the property has a field, and unsets the abstract flag.
     *
     * @param fField  true to indicate the presence of a field
     *
     * @return the PropertyBody reflecting the changes
     */
    public PropertyBody specifyField(boolean fField)
        {
        return new PropertyBody(m_constId, m_type, m_fRO, m_fRW, m_aPropAnno, m_aRefAnno,
                m_fCustom, fField, false, m_fConstant, m_fOverride, m_constInitVal, m_constInitFunc);
        }

    /**
     * Specifies a value for whether or not the property overrides some unknown super property.
     *
     * @param fOverride  specifies whether the resulting PropertyBody is overriding
     *
     * @return the PropertyBody reflecting the changes
     */
    public PropertyBody specifyOverride(boolean fOverride)
        {
        return new PropertyBody(m_constId, m_type, m_fRO, m_fRW, m_aPropAnno, m_aRefAnno,
                m_fCustom, m_fField, m_fAbstract, m_fConstant, fOverride, m_constInitVal, m_constInitFunc);
        }

    /**
     * @return the container of the property
     */
    public IdentityConstant getParent()
        {
        return m_constId.getParentConstant();
        }

    /**
     * @return the identity of the property
     */
    public PropertyConstant getIdentity()
        {
        return m_constId;
        }

    /**
     * A single property can be identified by multiple PropertyConstants if the property results
     * from multiple contributions to the type. Each contribution would use a different
     * PropertyConstant to identify the property.
     *
     * @param constId  a PropertyConstant that may or may not identify this property
     *
     * @return true iff the specified PropertyConstant refers to this PropertyBody
     */
    public boolean isIdentityValid(PropertyConstant constId)
        {
        return constId.equals(m_constId)
                || m_infoSuper != null && m_infoSuper.isIdentityValid(constId);
        }

    /**
     * @return the property name
     */
    public String getName()
        {
        return m_constId.getName();
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
     * @return an array of the non-virtual annotations on the property declaration itself
     */
    public Annotation[] getPropertyAnnotations()
        {
        return m_aPropAnno;
        }

    /**
     * @return an array of the annotations that apply to the Ref/Var of the property
     */
    public Annotation[] getRefAnnotations()
        {
        return m_aRefAnno;
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
        ConstantPool pool = m_constId.getConstantPool();
        return pool.ensureMethodConstant(m_constId, "get", ConstantPool.NO_TYPES, new TypeConstant[]{m_type});
        }

    /**
     * @return the MethodConstant that will identify the setter (but not necessarily a
     *         MethodConstant that actually exists, because there may not be a setter, but also
     *         because the fully resolved type is used in the MethodConstant)
     */
    public MethodConstant getSetterId()
        {
        ConstantPool pool = m_constId.getConstantPool();
        return pool.ensureMethodConstant(m_constId, "set", new TypeConstant[]{m_type}, ConstantPool.NO_TYPES);
        }

    /**
     * @return true iff the property is abstract, which means that it comes from an interface
     */
    public boolean isAbstract()
        {
        return m_fAbstract;
        }

    /**
     * @return true iff the property TODO
     */
    public boolean isOverride()
        {
        // TODO
        // return m_infoSuper == null
        //         ? isExplicitOverride()
        //         : m_infoSuper.isOverride();
        return m_fOverride;
        }

    /**
     * @return true if the property is annotated by "@Abstract"
     */
    public boolean isExplicitAbstract()
        {
        return TypeInfo.containsAnnotation(m_aPropAnno, "Abstract");
        }

    /**
     * @return true if the property is annotated by "@Override"
     */
    public boolean isExplicitOverride()
        {
        return TypeInfo.containsAnnotation(m_aPropAnno, "Override");
        }

    /**
     * @return true if the property is annotated by "@RO"
     */
    public boolean isExplicitReadOnly()
        {
        return TypeInfo.containsAnnotation(m_aPropAnno, "RO");
        }

    /**
     * @return true if the property is annotated by "@Inject"
     */
    public boolean isExplicitInject()
        {
        return TypeInfo.containsAnnotation(m_aRefAnno, "Inject");
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constId.hashCode();
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
        return this.m_constId.equals(that.m_constId)
            && this.m_type   .equals(that.m_type)
            && m_fRO       == m_fRO
            && m_fRW       == m_fRW
            && m_fCustom   == m_fCustom
            && m_fField    == m_fField
            && m_fAbstract == m_fAbstract
            && m_fConstant == m_fConstant
            && Handy.equals(this.m_paraminfo    , that.m_paraminfo    )
            && Handy.equals(this.m_constInitVal , that.m_constInitVal )
            && Handy.equals(this.m_constInitFunc, that.m_constInitFunc)
            && Handy.equals(this.m_aPropAnno    , that.m_aPropAnno    )
            && Handy.equals(this.m_aRefAnno     , that.m_aRefAnno     );
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        for (Annotation annotation : m_aPropAnno)
            {
            sb.append(annotation)
              .append(' ');
            }

        for (Annotation annotation : m_aRefAnno)
            {
            sb.append(annotation)
              .append(' ');
            }

        sb.append(m_type.getValueString())
          .append(' ')
          .append(getName())
          .append(';');

        StringBuilder sb2 = new StringBuilder();
        if (m_paraminfo != null)
            {
            sb2.append(", type-param");
            }
        if (m_fConstant)
            {
            sb2.append(", constant");
            }
        if (m_fField)
            {
            sb2.append(", has-field");
            }
        if (m_fCustom)
            {
            sb2.append(", has-code");
            }
        if (m_fRO)
            {
            sb2.append(", RO");
            }
        if (m_fRW)
            {
            sb2.append(", RW");
            }
        if (m_fAbstract)
            {
            sb2.append(", abstract");
            }
        if (m_fOverride)
            {
            sb2.append(", override");
            }
        if (sb2.length() > 0)
            {
            sb.append(" (")
              .append(sb2.substring(2))
              .append(')');
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The property's identity constant.
     */
    private final PropertyConstant m_constId;

    /**
     * The PropertyBody that was used as the "super" to create this PropertyBody.
     */
    private final PropertyBody m_infoSuper;

    /**
     * Type of the property, including any annotations on the type.
     */
    private final TypeConstant m_type;

    /**
     * Type parameter information.
     */
    private final ParamInfo m_paraminfo;

    /**
     * True iff the property is explicitly a Ref and not a Var.
     */
    private final boolean m_fRO;

    /**
     * True iff the property is explicitly a Var and not a Ref.
     */
    private final boolean m_fRW;

    /**
     * Access required for the Ref.
     */
    private final Access m_accessRef;

    /**
     * Access required for the Var (if the property is R/W).
     */
    private final Access m_accessVar;

    /**
     * An array of non-virtual annotations on the property declaration itself
     */
    private final Annotation[] m_aPropAnno;

    /**
     * An array of annotations that apply to the Ref/Var of the property.
     */
    private final Annotation[] m_aRefAnno;

    /**
     * True to indicate that the property has custom code that overrides the underlying Ref/Var
     * implementation.
     */
    private final boolean m_fCustom;

    /**
     * True iff the property requires a field.
     */
    private final boolean m_fField;

    /**
     * True iff the property is abstract, such as when it is on an interface.
     */
    private final boolean m_fAbstract;

    /**
     * True iff the property is a constant value.
     */
    private final boolean m_fConstant;

    /**
     * True iff the property's last contribution specifies "@Override".
     */
    private final boolean m_fOverride;

    private final Constant       m_constInitVal;
    private final MethodConstant m_constInitFunc;
    }
