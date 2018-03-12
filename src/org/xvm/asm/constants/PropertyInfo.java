package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.MethodBody.Implementation;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * Represents the compile time and runtime information (aggregated across all contributions and
 * virtual levels) about a single property as it appears in a particular type.
 */
public class PropertyInfo
        implements Constants
    {
    public PropertyInfo(PropertyBody body)
        {
        this(new PropertyBody[] {body}, false, false, false);
        }

    protected PropertyInfo(
            PropertyBody[] aBody,
            boolean        fRequireField,
            boolean        fSuppressVar,
            boolean        fSuppressOverride)
        {
        m_aBody             = aBody;
        m_fRequireField     = fRequireField;
        m_fSuppressVar      = fSuppressVar;
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
    public PropertyInfo layerOn(PropertyInfo that, ErrorListener errs)
        {

        // TODO have to get the "tail end" of the property body chain of the sub to get the type (the "super" can be == or wider)
        // TODO if types don't match then there is an error
        // TODO if there is a super but the contrib didn't specify @Override then it is an error
        // TODO check for annotation redudancy / overlap
        // - duplicate annotations do NOT yank; they are simply logged (WARNING) and discarded
        // - make sure to check for annotations at this level that are super-classes of annotations already contributed, since they are also discarded
        // - it is possible that an annotation has a potential call chain that includes layers that are
        //   already in the property call chain; they are simply discarded (similar to retain-only)

        assert that != null;
        assert errs != null;

        PropertyConstant constId = getIdentity();
        assert constId.getName().equals(that.getName());

        if (this.isTypeParam() || that.isTypeParam())
            {
            if (this.isTypeParam() ^ that.isTypeParam())
                {
                constId.log(errs, Severity.ERROR, VE_PROPERTY_ILLEGAL,
                        constId.getValueString());
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
                constId.log(errs, Severity.ERROR, VE_PROPERTY_TYPES_INCOMPATIBLE,
                        constId.getValueString(),
                        this.getType().getValueString(),
                        that.getType().getValueString());
                return this;
                }
            }

        // it is illegal to combine anything with a constant
        if (this.isConstant() || that.isConstant())
            {
            constId.log(errs, Severity.ERROR, VE_CONST_INCOMPATIBLE,
                    constId.getValueString());
            }

        // types must match
        if (!this.getType().isA(that.getType()))
            {
            constId.log(errs, Severity.ERROR, VE_PROPERTY_TYPES_INCOMPATIBLE,
                    constId.getValueString(),
                    this.getType().getValueString(),
                    that.getType().getValueString());
            }

        // cannot combine struct with anything other than struct
        if (this.getRefAccess() == Access.STRUCT ^ that.getRefAccess() == Access.STRUCT)
            {
            constId.todoLogError("cannot combine struct with anything other than struct");
            }

        // cannot combine private with anything
        if (this.getRefAccess() == Access.PRIVATE || that.getRefAccess() == Access.PRIVATE)
            {
            constId.todoLogError("cannot combine private with anything");
            }

        // a non-abstract RO property combined with a RW property ... TODO

        // combine the two arrays of PropertyBody objects into a new PropertyInfo
        PropertyBody[] aBodyThis  = this.m_aBody;
        PropertyBody[] aBodyThat  = that.m_aBody;
        int            cThis      = aBodyThis.length;
        int            cThat      = aBodyThat.length;
        PropertyBody[] aBodyNew   = new PropertyBody[cThis + cThat];
        int            ofThis     = 0;
        int            ofThat     = 0;
        int            ofNew      = 0;

        CopyConcrete: while (ofThis < cThis)
            {
            PropertyBody body = aBodyThis[ofThis];
            switch (body.getImplementation())
                {
                case Delegating:
                case Native:
                case Explicit:
                    aBodyNew[ofNew++] = body;
                    ++ofThis;
                    break;

                default:
                    break CopyConcrete;
                }
            }

        CopyConcrete: while (ofThat < cThat)
            {
            PropertyBody body = aBodyThat[ofThat];
            switch (body.getImplementation())
                {
                case Delegating:
                case Native:
                case Explicit:
                    aBodyNew[ofNew++] = body;
                    ++ofThat;
                    break;

                default:
                    break CopyConcrete;
                }
            }

        while (ofThis < cThis && aBodyThis[ofThis].getImplementation() == Implementation.Declared)
            {
            aBodyNew[ofNew++] = aBodyThis[ofThis++];
            }

        while (ofThat < cThat && aBodyThat[ofThat].getImplementation() == Implementation.Declared)
            {
            aBodyNew[ofNew++] = aBodyThat[ofThat++];
            }

        while (ofThis < cThis && aBodyThis[ofThis].getImplementation() == Implementation.Implicit)
            {
            aBodyNew[ofNew++] = aBodyThis[ofThis++];
            }

        while (ofThat < cThat && aBodyThat[ofThat].getImplementation() == Implementation.Implicit)
            {
            aBodyNew[ofNew++] = aBodyThat[ofThat++];
            }

        return new PropertyInfo(aBodyNew,
                this.m_fRequireField | that.m_fRequireField,
                this.m_fSuppressVar | that.m_fSuppressVar,
                that.m_fSuppressOverride);
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
            boolean fRetain;
            switch (body.getImplementation())
                {
                case Implicit:
                    // "into" isn't in the call chain
                    fRetain = true;
                    break;

                case Declared:
                    // interface type
                    fRetain = setDefault.contains(constClz);
                    break;

                case Native:
                    // generic type parameters can come from either the concrete contributions, or
                    // from an interface
                    fRetain = setClass.contains(constClz) || setDefault.contains(constClz);
                    break;

                case Delegating:
                case Explicit:
                    // concrete type
                    fRetain = setClass.contains(constClz);
                    break;

                default:
                    throw new IllegalStateException();
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
                : new PropertyInfo(list.toArray(new PropertyBody[list.size()]),
                        m_fRequireField, m_fSuppressVar, m_fSuppressOverride);
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
        // determine if the resulting property would be a Var, a Ref, or absent from the type with
        // the specified access
        Access accessRef = getRefAccess();
        if (accessRef.isLessAccessibleThan(access))
            {
            return null;
            }

        Access accessVar = getVarAccess();
        if (accessVar != null && isVar() && accessVar.isLessAccessibleThan(access))
            {
            // create the Ref-only form of this property
            return new PropertyInfo(m_aBody, m_fRequireField, true, m_fSuppressOverride);
            }

        return this;
        }

    /**
     * @return this PropertyInfo, but with a requirement that a field exists
     */
    public PropertyInfo requireField()
        {
        return hasField()
                ? this
                : new PropertyInfo(m_aBody, true, m_fSuppressVar, m_fSuppressOverride);
        }

    /**
     * @return this PropertyInfo, but with the trailing "@Override" suppressed
     */
    public PropertyInfo suppressOverride()
        {
        return isOverride()
                ? new PropertyInfo(m_aBody, m_fRequireField, m_fSuppressVar, true)
                : this;
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

    /**
     * @return the array of all PropertyBody objects that compose this PropertyInfo
     */
    public PropertyBody[] getPropertyBodies()
        {
        return m_aBody;
        }

    /**
     * @return the first PropertyBody of this PropertyInfo; in a loose sense, the meaning of the
     *         term "first" corresponds to the order of the call chain
     */
    public PropertyBody getFirstBody()
        {
        return m_aBody[0];
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
     * @return true iff this property represents a constant (a static property)
     */
    public boolean isConstant()
        {
        return getFirstBody().isConstant();
        }

    /**
     * @return true iff there is an initial value for the property, either as a constant or via an
     *         initializer function
     */
    public boolean isInitialized()
        {
        for (PropertyBody body : m_aBody)
            {
            if (body.getInitialValue() != null || body.getInitializer() != null)
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return the initial value of the property as a constant, or null if there is no constant
     *         initial value
     */
    public Constant getInitialValue()
        {
        for (PropertyBody body : m_aBody)
            {
            Constant constVal = body.getInitialValue();
            if (constVal != null)
                {
                return constVal;
                }

            if (body.getInitializer() != null)
                {
                return null;
                }
            }

        return null;
        }

    /**
     * @return the function that provides the initial value of the property, or null if there is no
     *         initializer
     */
    public MethodConstant getInitializer()
        {
        for (PropertyBody body : m_aBody)
            {
            MethodConstant methodInit = body.getInitializer();
            if (methodInit != null)
                {
                return methodInit;
                }

            if (body.getInitialValue() != null)
                {
                return null;
                }
            }

        return null;
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
        return getFirstBody().getTypeParamInfo();
        }

    /**
     * @return true iff the property is a Var (or false if the property is only a Ref)
     */
    public boolean isVar()
        {
        if (m_fSuppressVar || isConstant() || isTypeParam())
            {
            return false;
            }

        if (hasField())
            {
            return true;
            }

        for (PropertyBody body : m_aBody)
            {
            if (body.isRW())
                {
                return true;
                }

            if (body.getImplementation() == Implementation.Delegating)
                {
                TypeInfo     typeThat = body.getDelegate().getRefType().ensureTypeInfo();
                PropertyInfo propThat = typeThat.findProperty(getName());
                return propThat != null && propThat.isVar();
                }
            }

        return false;
        }

    /**
     * @return the Access required for the Ref form of the property
     */
    public Access getRefAccess()
        {
        return getFirstBody().getRefAccess();
        }

    /**
     * @return the Access required for the Var form of the property, or null if not specified
     */
    public Access getVarAccess()
        {
        return getFirstBody().getVarAccess();
        }

    public boolean isSetterUnreachable()
        {
        return m_fSuppressVar;
        }

    /**
     * @return true iff this property has a field, whether or not that field is reachable
     */
    public boolean hasField()
        {
        if (m_fRequireField)
            {
            return true;
            }

        for (PropertyBody body : m_aBody)
            {
            if (body.hasField())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return an array of the non-virtual annotations on the property declaration itself
     */
    public Annotation[] getPropertyAnnotations()
        {
        return getFirstBody().getStructure().getPropertyAnnotations();
        }

    /**
     * @return true iff the property can exist across multiple "glass panes" of the type's
     *         composition; note that a property considered to be non-virtual by this test can
     *         still have a multi-level call chain, such as if it has Ref/Var annotations
     */
    public boolean isVirtual()
        {
        // it can only be virtual if it is non-private and non-constant, and if it is not contained
        // within a method or a private property
        if (isConstant() || getVarAccess() == Access.PRIVATE)
            {
            return false;
            }

        IdentityConstant id = getIdentity();
        for (int i = 1, c = id.getNestedDepth(); i < c; ++i)
            {
            id = id.getParentConstant();
            if ((id instanceof PropertyConstant && id.getComponent().getAccess() == Access.PRIVATE)
                    || id instanceof MethodConstant)
                {
                return false;
                }
            }

        return true;
        }

    /**
     * @return true iff the property contains any reference annotations
     */
    public boolean isRefAnnotated()
        {
        // TODO - efficiently
        return getRefAnnotations().length > 0;
        }

    /**
     * @return an array of the annotations that apply to the Ref/Var of the property
     */
    public Annotation[] getRefAnnotations()
        {
        List<Annotation> list   = null;
        Annotation[]     aAnnos = Annotation.NO_ANNOTATIONS;

        for (PropertyBody body : m_aBody)
            {
            Annotation[] aAdd = body.getStructure().getRefAnnotations();
            if (aAdd.length > 0)
                {
                if (list == null)
                    {
                    if (aAnnos.length == 0)
                        {
                        aAnnos = aAdd;
                        }
                    else
                        {
                        list = new ArrayList<>();
                        Collections.addAll(list, aAnnos);
                        Collections.addAll(list, aAdd);
                        }
                    }

                if (list != null)
                    {
                    Collections.addAll(list, aAdd);
                    }
                }
            }

        return list == null
                ? aAnnos
                : list.toArray(new Annotation[list.size()]);
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
        if (m_fSuppressOverride)
            {
            return false;
            }

        // get the last non-implicit body
        PropertyBody[] aBody = m_aBody;
        for (int i = aBody.length - 1; i >= 0; --i)
            {
            PropertyBody body = aBody[i];
            if (body.getImplementation() != Implementation.Implicit)
                {
                return body.isExplicitOverride();
                }
            }

        return false;
        }

    /**
     * @return true if the property is annotated by "@Inject"
     */
    public boolean isInjected()
        {
        return getFirstBody().isInjected();   // REVIEW inject is a Ref annotation
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

    /**
     * True iff this Property has been marked as requiring a field.
     */
    private final boolean m_fRequireField;

    /**
     * True iff this Property has been marked as not exposing access to the Var, such that it is
     * treated as a Ref.
     */
    private final boolean m_fSuppressVar;

    /**
     * True iff this Property has been marked as not having an override.
     */
    private final boolean m_fSuppressOverride;
    }
