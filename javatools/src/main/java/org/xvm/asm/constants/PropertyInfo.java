package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody.Existence;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.PropertyBody.Effect;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * Represents the compile time and runtime information (aggregated across all contributions and
 * virtual levels) about a single property as it appears in a particular type.
 */
public class PropertyInfo
        implements Constants
    {
    /**
     * Create a PropertyInfo.
     *
     * @param body   a PropertyBody
     * @param nRank  the property's rank
     */
    public PropertyInfo(PropertyBody body, int nRank)
        {
        this(new PropertyBody[] {body}, body.getType(), body.hasField(), false, nRank);
        }

    /**
     * Combine the information in this PropertyInfo with the specified PropertyBody.
     *
     * @param that  a PropertyInfo to merge with
     * @param body  a PropertyBody to add
     */
    public PropertyInfo(PropertyInfo that, PropertyBody body)
        {
        this(Handy.appendHead(that.getPropertyBodies(), body),
            body.getType(), body.hasField(), body.isSetterBlockingSuper(), that.m_nRank);
        }

    protected PropertyInfo(
            PropertyBody[] aBody,
            TypeConstant   type,
            boolean        fRequireField,
            boolean        fSuppressVar,
            int            nRank)
        {
        m_aBody          = aBody;
        m_type           = type;
        m_fRequireField  = fRequireField;
        m_fSuppressVar   = fSuppressVar;
        m_nRank          = nRank;
        }

    /**
     * Combine the information in this PropertyInfo with the information from a sub type's
     * PropertyInfo.
     *
     * @param that   the "contribution" PropertyInfo to layer on top of this property
     * @param fSelf  true if the layer being added ("that") represents the "Equals" contribution of
     *               the type
     * @param fAnno  true if the layer(s) being added ("that") represents an annotation
     * @param errs   the error list to log any conflicts to
     *
     * @return a PropertyInfo representing the combined information
     */
    public PropertyInfo layerOn(PropertyInfo that, boolean fSelf, boolean fAnno, ErrorListener errs)
        {
        assert that != null && errs != null;

        PropertyConstant constId = getIdentity();
        assert constId.getName().equals(that.getName());

        if (this.isFormalType() ^ that.isFormalType())
            {
            constId.log(errs, Severity.ERROR, VE_PROPERTY_ILLEGAL,
                    constId.getValueString());
            return this;
            }

        // it is illegal to combine anything with a constant
        if (this.isConstant() || that.isConstant())
            {
            constId.log(errs, Severity.ERROR, VE_CONST_INCOMPATIBLE,
                    constId.getValueString());
            return this;
            }

        // types must match (but it is possible that an annotation is wider than the specific type
        // that it annotates)
        if (!(that.getType().isA(this.getType()) || fAnno && this.getType().isA(that.getType())))
            {
            constId.log(errs, Severity.ERROR, VE_PROPERTY_TYPES_INCOMPATIBLE,
                    constId.getValueString(),
                    this.getType().getValueString(),
                    that.getType().getValueString());
            return this;
            }

        // cannot combine struct with anything
        if (this.getRefAccess() == Access.STRUCT || that.getRefAccess() == Access.STRUCT)
            {
            throw new IllegalStateException("cannot combine struct with anything");
            }

        // cannot combine private with anything
        if (this.getRefAccess() == Access.PRIVATE || that.getRefAccess() == Access.PRIVATE)
            {
            throw new IllegalStateException("cannot combine private with anything");
            }

        // first, determine what property bodies are duplicates, if any
        PropertyBody[] aBase = this.m_aBody;
        PropertyBody[] aAdd  = that.m_aBody;
        int            cBase = aBase.length;
        int            cAdd  = aAdd.length;

        ArrayList<PropertyBody> listMerge = null;
        NextLayer: for (int iAdd = 0; iAdd < cAdd; ++iAdd)
            {
            PropertyBody bodyAdd = aAdd[iAdd];

            // unlike the MethodInfo processing, we don't need to allow duplicate interface
            // properties to survive; AAMOF if we allowed it to go on top, the "asInto" would
            // produce wrong result by using the new head (from the contributing interface) that is
            // no longer correctly represents all property aspects
            for (int iThis = 0; iThis < cBase; ++iThis)
                {
                PropertyBody bodyBase = aBase[iThis];

                // discard duplicate "into" and class properties
                if (bodyAdd.equals(bodyBase) ||
                    bodyAdd.getIdentity().equals(bodyBase.getIdentity())
                        && bodyAdd.getImplementation() == Implementation.Implicit)
                    {
                    // we found a duplicate, so we can ignore it (it'll get added when we add
                    // all of the bodies from this)
                    continue NextLayer;
                    }
                }
            if (listMerge == null)
                {
                listMerge = new ArrayList<>();
                }
            listMerge.add(bodyAdd);
            }

        if (listMerge == null)
            {
            return this;
            }

        // glue together the layers
        Collections.addAll(listMerge, aBase);
        PropertyBody[] aResult = listMerge.toArray(new PropertyBody[0]);

        // check @Override (formal types are naturally exempt)
        if (fSelf && !isFormalType())
            {
            // should only have one layer (or zero layers, in which case we wouldn't have been
            // called) of property body for the "self" layer
            assert cAdd == 1;

            // check @Override
            PropertyBody bodyAdd = aAdd[0];
            if (!bodyAdd.isExplicitOverride() && !this.containsBody(bodyAdd.getIdentity())
                    && !bodyAdd.getStructure().isSynthetic())   // synthetic might not be marked
                {
                constId.log(errs, Severity.ERROR, VE_PROPERTY_OVERRIDE_REQUIRED,
                        bodyAdd.getIdentity().getValueString(),
                        aBase[0].getIdentity().getValueString());
                }
            }

        // check the property type and determine the type of the resulting property
        TypeConstant typeResult = this.getType();
        for (int iAdd = cAdd - 1; iAdd >= 0; --iAdd)
            {
            PropertyBody bodyAdd = aAdd[iAdd];
            TypeConstant typeAdd = bodyAdd.getType();
            Existence    exAdd   = bodyAdd.getExistence();
            if (!typeAdd.equals(typeResult))
                {
                // the property type can be narrowed by a class implementation
                if (exAdd != Existence.Implied && /* TODO GG+CP exAdd.compareTo(exResult) >= 0 && */ typeAdd.isA(typeResult))
                    {
                    // type has been narrowed
                    typeResult = typeAdd;
                    }
                // the property type can only be wider if it is a read-only interface/into method
                // or the layer-on is an annotation; otherwise it is an error
                else if (!(exAdd != Existence.Class && bodyAdd.isRO() && typeResult.isA(typeAdd))
                        && !bodyAdd.getStructure().isSynthetic()    // synthetic might not be marked
                        && !fAnno)
                    {
                    constId.log(errs, Severity.ERROR, VE_PROPERTY_TYPES_INCOMPATIBLE,
                            bodyAdd.getIdentity().getValueString(),
                            typeAdd.getValueString(),
                            typeResult.getValueString());
                    }
                }
            }

        // determine whether a field is required
        boolean fRequireField = this.m_fRequireField | that.m_fRequireField;
        if (!fRequireField && Arrays.stream(aBase).allMatch(
                body -> body.getImplementation().compareTo(Implementation.Abstract) <= 0))
            {
            // one of the bodies being added could cause the resulting property to require a field
            for (int i = cAdd - 1; i >= 0; --i)
                {
                PropertyBody body = aResult[i];
                if (body.getImplementation().compareTo(Implementation.Abstract) > 0)
                    {
                    // this is the first body that is "real", so determine whether a field is
                    // required; this needs to stay in sync with TypeConstant#createPropertyInfo()
                    fRequireField = body.impliesField();
                    break;
                    }
                }
            }

        // check for annotation redundancy / overlap
        // - duplicate annotations do NOT yank; they are simply logged (WARNING) and discarded
        // - make sure to check for annotations at this level that are super-classes of annotations
        //   already contributed, since they are also discarded
        // - it is possible that an annotation has a potential call chain that includes layers that are
        //   already in the property call chain; they are simply discarded (similar to retain-only)
        // note that we do not attempt to check for duplicates within the annotations that are being
        // added (i.e. we don't accrue the ones being added onto the list of ones that we're
        // checking against), because transitivity.
        Annotation[] aAnnoBase = this.getRefAnnotations();
        int          cAnnoBase = aAnnoBase.length;
        if (cAnnoBase > 0 && this.getExistence() != Existence.Implied)
            {
            for (int iBodyAdd = cAdd - 1; iBodyAdd >= 0; --iBodyAdd)
                {
                PropertyBody bodyAdd  = aAdd[iBodyAdd];
                Annotation[] aAnnoAdd = bodyAdd.getRefAnnotations();
                for (int iAnnoAdd = aAnnoAdd.length - 1; iAnnoAdd >= 0; --iAnnoAdd)
                    {
                    Annotation   annoAdd     = aAnnoAdd[iAnnoAdd];
                    TypeConstant typeAnnoAdd = annoAdd.getAnnotationType();
                    for (int iAnnoBase = 0; iAnnoBase < cAnnoBase; ++iAnnoBase)
                        {
                        TypeConstant typeAnnoBase = aAnnoBase[iAnnoBase].getAnnotationType();
                        if (typeAnnoAdd.equals(typeAnnoBase))
                            {
                            constId.log(errs, Severity.WARNING, VE_DUP_ANNOTATION_IGNORED,
                                    constId.getParentConstant().getValueString(),
                                    getName(),
                                    annoAdd.getAnnotationClass().getValueString());
                            break;
                            }

                        if (typeAnnoAdd.isA(typeAnnoBase))
                            {
                            constId.log(errs, Severity.WARNING, VE_SUP_ANNOTATION_IGNORED,
                                    constId.getParentConstant().getValueString(),
                                    getName(),
                                    typeAnnoAdd.getValueString(),
                                    typeAnnoBase.getValueString());
                            break;
                            }
                        }
                    }
                }
            }

        // whenever property loses access because its setter is not available, it transitions from
        // being a Var to being a Ref (a read-only property). for example, when a public/private
        // is extended, the private setter is no longer accessible, so while the underlying property
        // itself is not changed, the ability to access the property as a Var disappears; this is
        // referred to as "suppressing the Var".
        boolean fSuppressVar = this.m_fSuppressVar | that.m_fSuppressVar;

        // the property extension cannot re-introduce a Var if it has been suppressed by the base
        if (this.isSetterUnreachable() && that.isVar())
            {
            constId.log(errs, Severity.ERROR, VE_PROPERTY_SET_PRIVATE_SUPER,
                    constId.getParentConstant().getValueString(),
                    getName());
            }

        // the property extension cannot reduce the accessibility of the base
        Access accessThisVar = this.calcVarAccess();
        Access accessThatVar = that.getVarAccess();     // null has additional possible meanings
        if (that.getRefAccess().isLessAccessibleThan(this.getRefAccess()) ||
                (accessThisVar != null && accessThatVar != null &&
                        accessThatVar.isLessAccessibleThan(accessThisVar)))
            {
            constId.log(errs, Severity.ERROR, VE_PROPERTY_ACCESS_LESSENED,
                    constId.getParentConstant().getValueString(),
                    getName());
            }

        return new PropertyInfo(aResult, typeResult, fRequireField, fSuppressVar, that.m_nRank);
        }

    /**
     * When a property on a class originates on an interface, it may not have been evaluated for a
     * field. This method is invoked on all of the properties of a class once all of the interfaces
     * have been applied to the class, giving the properties a chance to replace themselves with an
     * appropriate PropertyInfo.
     *
     * @param fNative  true iff the type being assembled is a native rebase class
     * @param errs     the error list to log any errors to
     *
     * @return a PropertyInfo to use in place of this
     */
    public PropertyInfo finishAdoption(boolean fNative, ErrorListener errs)
        {
        // only modify normal properties that originate from interfaces and have not been
        // subsequently "layered on"
        if (isConstant() || isFormalType() || getExistence() != Existence.Interface)
            {
            return this;
            }

        assert !hasField();

        // interface properties with a default get() and an @RO declaration become calculated
        // properties; all others become field-based properties
        PropertyBody[]    aBody  = m_aBody;
        PropertyStructure struct = null;
        boolean           fRO    = false;
        for (int i = 0, c = aBody.length; i < c; ++i)
            {
            PropertyBody body = aBody[i];
            if (body.getExistence() != Existence.Implied)
                {
                assert body.getExistence() == Existence.Interface;

                if (struct == null)
                    {
                    struct = body.getStructure();
                    }

                // can only be read-only if at least one interface property body has a default get()
                if (body.isExplicitReadOnly())
                    {
                    fRO |= body.hasGetter();
                    }
                else
                    {
                    fRO = false;
                    break;
                    }
                }
            }

        PropertyBody bodyNew = fNative
                ? new PropertyBody(struct, Implementation.Native, null, getType(), fRO, false, true,
                    Effect.BlocksSuper, fRO ? Effect.None : Effect.BlocksSuper, false, false, null, null)
                : new PropertyBody(struct, Implementation.SansCode, null, getType(), fRO, false, false,
                    Effect.None, Effect.None, !fRO, false, null, null);
        return layerOn(new PropertyInfo(bodyNew, m_nRank), false, false, errs);
        }

    /**
     * Retain only property bodies that originate from the identities specified in the passed sets.
     *
     * @param constId     the identity of the property for this operation
     * @param setClass    the set of identities that call chain bodies can come from
     * @param setClass    the set of identities that call chain bodies can come from
     * @param setDefault  the set of identities that default bodies can come from
     *
     * @return the resulting PropertyInfo, or null if nothing has been retained
     */
    public PropertyInfo retainOnly(PropertyConstant      constId,
                                   Set<IdentityConstant> setClass,
                                   Set<IdentityConstant> setDefault)
        {
        ArrayList<PropertyBody> list  = null;
        PropertyBody[]          aBody = m_aBody;
        for (int i = 0, c = aBody.length; i < c; ++i)
            {
            PropertyBody     body     = aBody[i];
            IdentityConstant constClz = constId.getClassIdentity();
            boolean fRetain;
            switch (body.getImplementation())
                {
                case Implicit:      // "into" isn't in the call chain
                case Default:       // interface type - allow multiple copies to survive
                case Declared:      // interface type - allow multiple copies to survive
                    fRetain = true;
                    break;

                case Native:
                    // generic type parameters can come from either the concrete contributions, or
                    // from an interface
                    fRetain = setClass.contains(constClz) || setDefault.contains(constClz);
                    break;

                case SansCode:
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
                : new PropertyInfo(list.toArray(new PropertyBody[0]),
                        m_type, m_fRequireField, m_fSuppressVar, m_nRank);
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
        assert access != null && access != Access.STRUCT;

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
            return new PropertyInfo(m_aBody, m_type, m_fRequireField, true, m_nRank);
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
                : new PropertyInfo(m_aBody, m_type, true, m_fSuppressVar, m_nRank);
        }

    /**
     * @return the "into" version of this PropertyInfo
     */
    public PropertyInfo asInto()
        {
        // basically, if the property is a constant or formal type, it stays as-is; otherwise, it
        // needs to be "flattened" into a single implicit entry with the right signature
        if (isConstant() || isFormalType())
            {
            return this;
            }

        boolean fRO = false;
        boolean fRW = false;
        for (PropertyBody body : m_aBody)
            {
            fRO |= body.isRO();
            fRW |= body.isRW();
            }

        PropertyBody body = new PropertyBody(getHead().getStructure(), Implementation.Implicit, null,
                getType(), fRO, fRW, false, Effect.None, Effect.None, hasField(), false, null, null);
        return new PropertyInfo(new PropertyBody[] {body},
                m_type, m_fRequireField, m_fSuppressVar, m_nRank);
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
        return getHead().getIdentity();
        }

    /**
     * @return the array of all PropertyBody objects that compose this PropertyInfo
     */
    public PropertyBody[] getPropertyBodies()
        {
        return m_aBody;
        }

    /**
     * @return true iff any body of this info has the specified property id
     */
    public boolean containsBody(PropertyConstant id)
        {
        for (PropertyBody body : m_aBody)
            {
            if (id.equals(body.getIdentity()))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * @return the first PropertyBody of this PropertyInfo; in a loose sense, the meaning of the
     *         term "first" corresponds to the order of the call chain
     */
    public PropertyBody getHead()
        {
        return m_aBody[0];
        }

    /**
     * @return the last PropertyBody of this PropertyInfo; in a loose sense, the meaning of the
     *         term "last" corresponds to the order of the call chain
     */
    public PropertyBody getTail()
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
            // there is a possibility that the property has been "duck-typeable", which is only
            // allowable for interfaces
            Component parent = constId.getNamespace().getComponent();
            if (parent != null && parent.getFormat() == Format.INTERFACE)
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return the property name
     */
    public String getName()
        {
        return getHead().getName();
        }

    /**
     * @return the property type
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return true iff this property represents a constant (a static property)
     */
    public boolean isConstant()
        {
        return getHead().isConstant();
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
     * @return true iff this property represents a formal type
     */
    public boolean isFormalType()
        {
        return getHead().isFormalType();
        }

    /**
     * @return the Existence of the property
     */
    public Existence getExistence()
        {
        return getExistence(m_aBody);
        }

    /**
     * @return the Existence implied by the passed array of property bodies
     */
    private static Existence getExistence(PropertyBody[] aBody)
        {
        Existence ex = null;
        for (PropertyBody body : aBody)
            {
            Existence exBody = body.getExistence();
            if (exBody == Existence.Class)
                {
                return exBody;
                }
            if (ex == null || exBody.compareTo(ex) > 0)
                {
                ex = exBody;
                }
            }
        return ex;
        }

    /**
     * @return true iff the property is a Var (or false if the property is only a Ref)
     */
    public boolean isVar()
        {
        if (m_fSuppressVar || isConstant() || isFormalType() || isInjected())
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
                TypeInfo     typeThat = body.getDelegate().getType().ensureTypeInfo();
                PropertyInfo propThat = typeThat.findProperty(getName());
                return propThat != null && propThat.isVar();
                }
            }

        return false;
        }

    /**
     * @return true iff the property needs the native rebase for the get()
     */
    public boolean requiresNativeRef()
        {
        return isVar() || isFormalType() || isInjected() || isRefAnnotated();
        }

    /**
     * @return the Access required for the Ref form of the property
     */
    public Access getRefAccess()
        {
        return getHead().getRefAccess();
        }

    /**
     * @return the Access required for the Var form of the property, or null if not specified
     */
    public Access getVarAccess()
        {
        return getHead().getVarAccess();
        }

    /**
     * @return the Access required for the Var form of the property, or null if the property is not
     *         a Var
     */
    public Access calcVarAccess()
        {
        Access access = getVarAccess();
        if (access == null && isVar())
            {
            access = getRefAccess();
            }
        return access;
        }

    /**
     * @return true iff the property at some base level is actually a Var, but that Var is
     *         unreachable due to insufficient access
     */
    public boolean isSetterUnreachable()
        {
        return m_fSuppressVar;
        }

    /**
     * @return true iff this property is marked as native
     */
    public boolean isNative()
        {
        NextBody:
        for (PropertyBody body : m_aBody)
            {
            switch (body.getImplementation())
                {
                case Implicit:
                    continue NextBody;

                case Native:
                    return true;

                default:
                    return false;
                }
            }
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
     * @return the identity selected for the field of the property, or null iff the property does
     *         not have a field
     */
    public PropertyConstant getFieldIdentity()
        {
        if (!hasField())
            {
            return null;
            }

        PropertyConstant idBest = null;
        for (int i = m_aBody.length - 1; i >= 0; --i)
            {
            PropertyBody body = m_aBody[i];
            if (body.hasField())
                {
                return body.getIdentity();
                }

            if (idBest == null && body.getImplementation().compareTo(Implementation.Abstract) >= 0)
                {
                idBest = body.getIdentity();
                }
            }

        assert idBest != null;
        return idBest;
        }

    /**
     * @return an array of the non-virtual annotations on the property declaration itself
     */
    public Annotation[] getPropertyAnnotations()
        {
        return getHead().getStructure().getPropertyAnnotations();
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
        if (isConstant() || getRefAccess() == Access.PRIVATE)
            {
            return false;
            }

        IdentityConstant id = getIdentity();
        for (int i = 1, c = id.getNestedDepth(); i < c; ++i)
            {
            id = id.getParentConstant();
            if (id instanceof MethodConstant)
                {
                return false;
                }

            if (id instanceof PropertyConstant)
                {
                PropertyStructure prop = (PropertyStructure) id.getComponent();

                // absence of the component indicates that the property was created as "synthetic"
                // via "PropertyConstant.appendNestedIdentity", which means it's not private
                if (prop != null && prop.getAccess() == Access.PRIVATE)
                    {
                    return false;
                    }
                }
            }

        return true;
        }

    /**
     * @return true iff the property contains any reference annotations except the "Inject"
     */
    public boolean isRefAnnotated()
        {
        // this is a bit blunt, but @Inject is a very special annotation that is incompatible with
        // any other Ref annotations, so it is split out for its own special handling
        return !isInjected() && getRefAnnotations().length > 0;
        }

    /**
     * @return an array of the annotations that apply to the Ref/Var of the property
     */
    public Annotation[] getRefAnnotations()
        {
        Annotation[] aAnnos = m_annotations;
        if (aAnnos == null)
            {
            aAnnos = Annotation.NO_ANNOTATIONS;

            List<Annotation> list = null;
            for (PropertyBody body : m_aBody)
                {
                PropertyStructure prop = body.getStructure();
                if (prop == null)
                    {
                    continue;
                    }
                Annotation[] aAdd = prop.getRefAnnotations();
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

            if (list != null)
                {
                aAnnos = list.toArray(Annotation.NO_ANNOTATIONS);
                }
            m_annotations = aAnnos;
            }
        return aAnnos;
        }

    /**
     * Obtain the TypeConstant representing the data type of the underlying "box" (Ref/Var).
     * <p/>
     * Note, that unlike the {@link PropertyConstant#getRefType}, this method returns the base
     * Ref type even for "custom" properties.
     *
     * @return the underlying Ref type
     */
    public TypeConstant getBaseRefType()
        {
        TypeConstant typeProp = getType();
        ConstantPool pool     = pool();

        TypeConstant typeRef = pool.ensureParameterizedTypeConstant(
            isVar() ? pool.typeVar() : pool.typeRef(), typeProp);

        return isRefAnnotated()
                ? pool.ensureAnnotatedTypeConstant(typeRef, getRefAnnotations())
                : typeRef;
        }

    /**
     * @return true iff the property has the LazyVar annotation
     */
    public boolean isLazy()
        {
        return containsRefAnnotation(pool().clzLazy());
        }

    /**
     * @return true iff the property has an Atomic annotation
     */
    public boolean isAtomic()
        {
        return containsRefAnnotation(pool().clzAtomic());
        }

    /**
     * @return true iff this property contains the specified annotation
     */
    public boolean containsRefAnnotation(IdentityConstant idAnno)
        {
        for (Annotation anno : getRefAnnotations())
            {
            if (anno.getAnnotationClass().equals(idAnno))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * @return true iff the property has any methods in addition to the underlying Ref or Var
     *         "rebasing" implementation, and in addition to any annotations
     */
    public boolean isCustomLogic()
        {
        if (!isNative())
            {
            for (PropertyBody body : m_aBody)
                {
                if (body.hasCustomCode())
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    /**
     * @return return true iff this property should not be implicitly initialized
     */
    public boolean isImplicitlyUnassigned()
        {
        ConstantPool pool = pool();
        return containsRefAnnotation(pool.clzUnassigned())
            || containsRefAnnotation(pool.clzLazy())
            || containsRefAnnotation(pool.clzFuture());
        }

    /**
     * @return return true iff this property is marked as "Unassigned" and has no other annotations
     */
    public boolean isSimpleUnassigned()
        {
        Annotation[] aAnnos = getRefAnnotations();
        return aAnnos.length == 1
            && (aAnnos[0].getAnnotationClass()).equals(pool().clzUnassigned());
        }

    /**
     * @return the MethodConstant that will identify the getter (but not necessarily a
     *         MethodConstant that actually exists, because there may not be a getter, but also
     *         because the fully resolved type is used in the MethodConstant)
     */
    public MethodConstant getGetterId()
        {
        PropertyConstant constId = getIdentity();
        ConstantPool     pool    = pool();
        return pool.ensureMethodConstant(constId, "get", ConstantPool.NO_TYPES, new TypeConstant[]{getType()});
        }

    /**
     * Obtain the method chain for the property getter represented by this property info.
     *
     * @param infoType  the enclosing TypeInfo
     * @param idNested  the nested id of this property info (null for "top level" properties)
     *
     * @return the method chain iff the property exists; otherwise null
     */
    public MethodBody[] ensureOptimizedGetChain(TypeInfo infoType, PropertyConstant idNested)
        {
        MethodBody[] chain = m_chainGet;
        if (chain == null)
            {
            MethodConstant idGet = getGetterId();
            if (idNested != null)
                {
                idGet = (MethodConstant) idNested.appendNestedIdentity(pool(), idGet.getSignature());
                }

            m_chainGet = chain = isDelegating()
                    ? createDelegatingChain(infoType, idGet)
                    : augmentPropertyChain(
                            infoType.getOptimizedMethodChain(idGet), infoType, idGet);
            }

        return chain;
        }

    /**
     * @return the MethodConstant that will identify the setter (but not necessarily a
     *         MethodConstant that actually exists, because there may not be a setter, but also
     *         because the fully resolved type is used in the MethodConstant)
     */
    public MethodConstant getSetterId()
        {
        PropertyConstant constId = getIdentity();
        ConstantPool     pool    = pool();
        return pool.ensureMethodConstant(constId, "set", new TypeConstant[]{getType()}, ConstantPool.NO_TYPES);
        }

    /**
     * Obtain the method chain for the property getter represented by this property info.
     *
     * @param infoType  the enclosing TypeInfo
     * @param idNested  the nested id of this property info (null for "top level" properties)
     *
     * @return the method chain iff the property exists; otherwise null
     */
    public MethodBody[] ensureOptimizedSetChain(TypeInfo infoType, PropertyConstant idNested)
        {
        MethodBody[] chain = m_chainSet;
        if (chain == null)
            {
            MethodConstant idSet = getSetterId();
            if (idNested != null)
                {
                idSet = (MethodConstant) idNested.appendNestedIdentity(pool(), idSet.getSignature());
                }

            m_chainSet = chain = isDelegating()
                    ? createDelegatingChain(infoType, idSet)
                    : augmentPropertyChain(
                            infoType.getOptimizedMethodChain(idSet), infoType, idSet);
            }

        return chain;
        }

    /**
     * @return true iff the property is abstract, which means that it comes from an interface or is
     *         annotated with "@Abstract"
     */
    public boolean isAbstract()
        {
        return getHead().isAbstract();
        }

    /**
     * @return true if the property is annotated by "@Abstract"
     */
    public boolean isExplicitlyAbstract()
        {
        return getHead().isExplicitAbstract();
        }

    /**
     * @return true if the property is annotated by "@Override"
     */
    public boolean isOverride()
        {
        return getTail().isExplicitOverride();
        }

    /**
     * @return true if the property is annotated by "@Inject"
     */
    public boolean isInjected()
        {
        Boolean FInjected = m_FInjected;
        if (FInjected == null)
            {
            m_FInjected = FInjected = getHead().isInjected();
            }
        return FInjected.booleanValue();
        }

    /**
     * @return true if the property is a delegating one
     */
    public boolean isDelegating()
        {
        return getHead().getImplementation() == Implementation.Delegating;
        }

    /**
     * @return the property that provides the reference to delegate to
     */
    public PropertyConstant getDelegate()
        {
        return getHead().getDelegate();
        }

    /**
     * @return the injected resource name
     */
    public String getInjectedResourceName()
        {
        assert isInjected();

        Constant[] aParams = getRefAnnotations()[0].getParams();
        return aParams.length > 0
                ? ((StringConstant) aParams[0]).getValue()
                : getName();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Augment the method chain for a property accessor represented by this property info.
     *
     * @param chain     the raw method chain
     * @param infoType  the enclosing TypeInfo
     * @param idMethod  the accessor method constant
     *
     * @return the method chain iff the property exists; otherwise null
     */
    protected MethodBody[] augmentPropertyChain(MethodBody[] chain, TypeInfo infoType, MethodConstant idMethod)
        {
        if (chain == null || chain.length == 0)
            {
            if (isNative())
                {
                chain = new MethodBody[]
                    {
                    new MethodBody(idMethod, idMethod.getSignature(),
                            Implementation.Native, null)
                    };
                }
            else if (hasField())
                {
                chain = new MethodBody[]
                    {
                    new MethodBody(idMethod, idMethod.getSignature(),
                            Implementation.Field, getFieldIdentity())
                    };
                }
            else if (isInjected())
                {
                // injection is currently implemented at the field access level
                // (see ClassTemplate.getFieldValue)
                chain = new MethodBody[]
                    {
                    new MethodBody(idMethod, idMethod.getSignature(),
                            Implementation.Field, getHead().getIdentity())
                    };
                }
            else
                {
                chain = MethodBody.NO_BODIES;
                }
            }
        else if (hasField() && !isNative())
            {
            if (chain[0].isNative())
                {
                // if a Ref or Var mixin overrides "get" or "set" (e.g. FutureVar), but the
                // implementation is native, we need to replace it with the field access
                chain = new MethodBody[] {new MethodBody(idMethod, idMethod.getSignature(),
                        Implementation.Field, getFieldIdentity())};
                }
            else
                {
                int cBodies = chain.length;
                int ixTail  = cBodies - 1;

                Implementation implTail = chain[ixTail].getImplementation();
                if (implTail != Implementation.Field)
                    {
                    if (implTail == Implementation.Native)
                        {
                        // replace the "native" method with a field access
                        chain = chain.clone();
                        }
                    else
                        {
                        MethodBody[] chainNew = new MethodBody[cBodies + 1];
                        System.arraycopy(chain, 0, chainNew, 0, cBodies);
                        chain = chainNew;
                        ixTail++;
                        }
                    chain[ixTail] = new MethodBody(idMethod, idMethod.getSignature(),
                            Implementation.Field, getFieldIdentity());
                    }
                }
            }
        return chain;
        }

    /**
     * Augment the method chain for a delegating property accessor represented by this property info.
     *
     * @param infoType  the enclosing TypeInfo
     * @param idMethod  the accessor method constant
     *
     * @return the delegating method chain
     */
    protected MethodBody[] createDelegatingChain(TypeInfo infoType, MethodConstant idMethod)
        {
        PropertyStructure prop   = getHead().getStructure();
        ClassStructure    clz    = infoType.getClassStructure();
        MethodStructure   method = clz.ensurePropertyDelegation(prop, idMethod.getSignature(),
                getDelegate().getName());

        MethodConstant idDelegate = method.getIdentityConstant();
        MethodBody     body       = new MethodBody(idDelegate,
                idDelegate.getSignature(), Implementation.Explicit);
        body.setMethodStructure(method);

        return new MethodBody[]{body};
        }

    /**
     * @return the current rank
     */
    public int getRank()
        {
        return m_nRank;
        }

    /**
     * @return the ConstantPool
     */
    private ConstantPool pool()
        {
        return ConstantPool.getCurrentPool();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return getHead().hashCode();
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
        return this.m_fRequireField == that.m_fRequireField
            && this.m_fSuppressVar  == that.m_fSuppressVar
            && Handy.equals(this.m_aBody, that.m_aBody);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(getType().getValueString())
          .append(' ')
          .append(getName());

        if (m_fRequireField)
            {
            sb.append(", require-field");
            }

        if (m_fSuppressVar)
            {
            sb.append(", suppress-var");
            }

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
     * The type of this Property.
     */
    private final TypeConstant m_type;

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
     * The property rank represents a relative order of property's appearance in the containing
     * class. It's used only to preserve a natural (in the order of introduction) enumeration of
     * fields by auto-generated code, such as Const.toString() and reflection API.
     */
    private final int m_nRank;

    /**
     * Cached "get" chain.
     */
    private MethodBody[] m_chainGet;

    /**
     * Cached "set" chain.
     */
    private MethodBody[] m_chainSet;

    /**
     * Cached "annotation" chain.
     */
    private Annotation[] m_annotations;

    /**
     * Cached "Injected" flag.
     */
    private Boolean m_FInjected;
    }
