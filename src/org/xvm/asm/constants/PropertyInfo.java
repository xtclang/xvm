package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;

import org.xvm.util.Handy;


/**
 * Represents the compile time and runtime information (aggregated across all contributions and
 * virtual levels) about a single property as it appears in a particular type.
 *
 *
 * public       public/public
 *              public/protected
 *              public/private
 * protected    protected/protected
 *              protected/private
 * private      private/private
 */
public class PropertyInfo
        implements Constants
    {
    public PropertyInfo(PropertyBody body)
        {
        this(new PropertyBody[] {body}, body.hasField(), body.isExplicitOverride());
        }

    protected PropertyInfo(PropertyBody[] aBody, boolean fRequireField, boolean fSuppressOverride)
        {
        m_aBody             = aBody;
        m_fRequireField     = fRequireField;
        m_fSuppressOverride = fSuppressOverride;
        }

    /**
     * Combine the information in this PropertyInfo with the information from a super type's
     * PropertyInfo.
     *
     * @param that  a super-type's PropertyInfo
     * @param errs  the error list to log any conflicts to
     *
     * @return a PropertyInfo representing the combined information
     */
    public PropertyInfo append(PropertyInfo that, ErrorListener errs)
        {
        assert that != null;
        assert errs != null;
        assert this.getName().equals(that.getName());

        /* TODO
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

        return new PropertyInfo(
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
        */

        PropertyBody[] aBodyThis = this.m_aBody;
        PropertyBody[] aBodyThat = that.m_aBody;
        int            cBodyThis = aBodyThis.length;
        int            cBodyThat = aBodyThat.length;
        PropertyBody[] aBodyNew  = new PropertyBody[cBodyThis + cBodyThat];
        System.arraycopy(aBodyThis, 0, aBodyNew, 0, cBodyThis);
        System.arraycopy(aBodyThat, 0, aBodyNew, cBodyThis, cBodyThat);
        return new PropertyInfo(aBodyNew, this.hasField() | that.hasField(), that.isOverride());
        }

    /**
     * Retain only property bodies that originate from the identities specified in the passed sets.
     *
     * @param setClass    the set of identities that call chain bodies can come from
     * @param setDefault  the set of identities that default bodies can come from
     *
     * @return the resulting PropertyInfo, or null if nothing has been retained
     */
    public PropertyInfo retainOnly(Set<IdentityConstant> setClass, Set<IdentityConstant> setDefault)
        {
        ArrayList<PropertyBody> list  = null;
        PropertyBody[]          aBody = m_aBody;
        for (int i = 0, c = aBody.length; i < c; ++i)
            {
            PropertyBody     body     = aBody[i];
            IdentityConstant constClz = body.getIdentity().getClassIdentity();
            boolean fRetain = setClass.contains(constClz) || setDefault.contains(constClz);
            switch (body.getImplementation())
                {
                case Implicit:
                case Declared:
                    fRetain
                    break;

                case Default:
                    fRetain = setDefault.contains(constClz);
                    break;

                default:
                    fRetain = setClass.contains(constClz);
                    break;
                }
            if (fRetain)
                {
                if (list != null)
                    {
                    list.add(body);
                    }
                }
            else if (list == null)
                {
                list = new ArrayList<>();
                for (int iCopy = 0; iCopy < i; ++iCopy)
                    {
                    list.add(aBody[iCopy]);
                    }
                }
            }

        if (list == null)
            {
            return this;
            }

        return list.isEmpty()
                ? null
                : new PropertyInfo(list.toArray(new PropertyBody[list.size()]));
        }


    /**
     * Create a new PropertyInfo that represents a more limited (public or protected) access to the
     * members of this property that is on the private type.
     *
     * @param access  the desired access, either PUBLIC or PROTECTED
     *
     * @return a PropertyInfo to use, or null if the PropertyInfo would not be present on the type
     *         with the specified access
     */
    public PropertyInfo limitAccess(Access access)
        {
        // TODO this property is either a Var or a Ref on the private type (i.e. this PropertyInfo)
        //      determine if the same property would be a Var, a Ref, or absent from the type with the specified access
        //      - if absent, return null
        //      - if the same as on the private type, then return this
        //      - otherwise private type must be Var and we need to create a Ref (@RO) of the same
        return this;
        }

    /**
     * @return this PropertyInfo, but with the trailing "@Override" suppressed
     */
    public PropertyInfo suppressOverride()
        {
        return new PropertyInfo(m_aBody, m_fRequireField, false);
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
        return getFirstBody().getIdentity();
        }

    public PropertyBody[] getPropertyBodies()
        {
        return m_aBody;
        }

    public PropertyBody getFirstBody()
        {
        return m_aBody[0];
        }

    public PropertyBody getLastBody()
        {
        return m_aBody[m_aBody.length-1];
        }

    /**
     * A single property can be identified by multiple PropertyConstants if the property results
     * from multiple contributions to the type. Each contribution would use a different
     * PropertyConstant to identify the property.
     *
     * @param constId  a PropertyConstant that may or may not identify this property
     *
     * @return true iff the specified PropertyConstant refers to this PropertyInfo
     */
    public boolean isIdentityValid(PropertyConstant constId)
        {
        if (constId.getName().equals(this.getName()))
            {
            for (PropertyBody body : m_aBody)
                {
                if (body.getIdentity().equals(constId))
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    /**
     * @return the property name
     */
    public String getName()
        {
        return getFirstBody().getName();
        }

    /**
     * @return the property type
     */
    public TypeConstant getType()
        {
        return getFirstBody().getType();
        }

    /**
     * @return true iff this property represents a type parameter type
     */
    public boolean isTypeParam()
        {
        return getFirstBody().isTypeParam();
        }

    /**
     * @return the type param info
     */
    public ParamInfo getParamInfo()
        {
        return getFirstBody().getParamInfo();
        }

    /**
     * @return the Access required for the Ref form of the property
     */
    public Access getRefAccess()
        {
        // TODO
        return getFirstBody().getRefAccess();
        }

    /**
     * @return the Access required for the Var form of the property, or null if not specified
     */
    public Access getVarAccess()
        {
        // TODO
        return getFirstBody().getVarAccess();
        }

    public boolean hasUnreachableSet()
        {
        // TODO
        return false;
        }

    /**
     * @return true iff this property has a field, whether or not that field is reachable
     */
    public boolean hasField()
        {
        return m_fRequireField;
        }

    /**
     * @return an array of the non-virtual annotations on the property declaration itself
     */
    public Annotation[] getPropertyAnnotations()
        {
        return getFirstBody().getStructure().getPropertyAnnotations();
        }

    /**
     * @return an array of the annotations that apply to the Ref/Var of the property
     */
    public Annotation[] getRefAnnotations()
        {
        // TODO combine?
        return getFirstBody().getStructure().getRefAnnotations();
        }

    /**
     * @return true iff the property has any methods in addition to the underlying Ref or Var
     *         "rebasing" implementation, and in addition to any annotations
     */
    public boolean isCustomLogic()
        {
        for (PropertyBody body : m_aBody)
            {
            if (body.hasCustomCode())
                {
                return true;
                }
            }
        return false;
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
        return pool.ensureMethodConstant(constId, "get", ConstantPool.NO_TYPES, new TypeConstant[]{getType()});
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
        return pool.ensureMethodConstant(constId, "set", new TypeConstant[]{getType()}, ConstantPool.NO_TYPES);
        }

    /**
     * @return true iff the property is abstract, which means that it comes from an interface or is
     *         annotated with "@Abstract"
     */
    public boolean isAbstract()
        {
        return getFirstBody().isAbstract();
        }

    /**
     * @return true if the property is annotated by "@Abstract"
     */
    public boolean isExplicitlyAbstract()
        {
        return getFirstBody().isExplicitAbstract();
        }

    /**
     * @return true if the property is annotated by "@Override"
     */
    public boolean isOverride()
        {
        return !m_fSuppressOverride && getLastBody().isExplicitOverride();
        }

    /**
     * @return true if the property is annotated by "@Inject"
     */
    public boolean isInjected()
        {
        return getFirstBody().isInjected();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return getFirstBody().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof PropertyInfo))
            {
            return false;
            }

        PropertyInfo that = (PropertyInfo) obj;
        return this.m_fRequireField     == that.m_fRequireField
            && this.m_fSuppressOverride == that.m_fSuppressOverride
            && Handy.equals(this.m_aBody, that.m_aBody);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(getType().getValueString() + ' ' + getName());

        int i = 0;
        for (PropertyBody body : m_aBody)
            {
            sb.append("\n    [")
                    .append(i++)
                    .append("] ")
              .append(body);
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The PropertyBody objects that provide the data represented by this PropertyInfo.
     */
    private final PropertyBody[] m_aBody;

    private final int m_c

    /**
     * True iff this Property has been marked as requiring a field.
     */
    private final boolean m_fRequireField;

    /**
     * True iff this Property has been marked as not having an override.
     */
    private final boolean m_fSuppressOverride;
    }
