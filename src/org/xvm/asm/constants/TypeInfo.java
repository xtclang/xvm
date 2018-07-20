package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.TypeConstant.Origin;

import org.xvm.util.ListMap;


/**
 * Represents the "flattened" information about the type.
 */
public class TypeInfo
    {
    /**
     * Construct a TypeInfo.
     *
     * @param type                 the type that the TypeInfo represents
     * @param struct               the structure that underlies the type, or null if there is none
     * @param cDepth               the nested depth of the TypeInfo; {@code 0} for a class TypeInfo,
     *                             or {@code >0} for a TypeInfo that represents a property
     * @param fSynthetic           true if this type info is synthetic (e.g. "as into")
     * @param mapTypeParams        the collected type parameters for the type
     * @param aannoClass           the annotations for the type that mix into "Class"
     * @param typeExtends          the type that is extended
     * @param typeRebases          the type that is rebased onto
     * @param typeInto             for mixins, the type that is mixed into; for interfaces, Object
     * @param listProcess
     * @param listmapClassChain    the potential call chain of classes
     * @param listmapDefaultChain  the potential call chain of default implementations
     * @param mapProps             the properties of the type
     * @param mapMethods           the methods of the type
     * @param mapVirtProps         the virtual properties of the type, keyed by nested id
     * @param mapVirtMethods       the virtual methods of the type, keyed by nested id
     * @param progress             the Progress for this TypeInfo
     */
    public TypeInfo(
            TypeConstant                        type,
            ClassStructure                      struct,
            int                                 cDepth,
            boolean                             fSynthetic,
            Map<Object, ParamInfo>              mapTypeParams,
            Annotation[]                        aannoClass,
            TypeConstant                        typeExtends,
            TypeConstant                        typeRebases,
            TypeConstant                        typeInto,
            List<Contribution>                  listProcess,
            ListMap<IdentityConstant, Origin>   listmapClassChain,
            ListMap<IdentityConstant, Origin>   listmapDefaultChain,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant, MethodInfo>     mapMethods,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<Object, MethodInfo>             mapVirtMethods,
            Progress                            progress)
        {
        assert type                 != null;
        assert mapTypeParams        != null;
        assert listmapClassChain    != null;
        assert listmapDefaultChain  != null;
        assert mapProps             != null;
        assert mapMethods           != null;
        assert mapVirtProps         != null;
        assert mapVirtMethods       != null;
        assert progress             != null && progress != Progress.Absent;

        m_type                  = type;
        m_struct                = struct;
        m_cDepth                = cDepth;
        m_mapTypeParams         = mapTypeParams;
        m_aannoClass            = validateAnnotations(aannoClass);
        m_typeExtends           = typeExtends;
        m_typeRebases           = typeRebases;
        m_typeInto              = typeInto;
        m_listProcess           = listProcess;
        m_listmapClassChain     = listmapClassChain;
        m_listmapDefaultChain   = listmapDefaultChain;
        m_mapProps              = mapProps;
        m_mapVirtProps          = mapVirtProps;
        m_mapMethods            = mapMethods;
        m_mapVirtMethods        = mapVirtMethods;
        m_progress              = progress;

        // pre-populate the method lookup caches
        // and determine if this type is implicitly abstract
        m_cacheById  = new HashMap<>(m_mapMethods);
        m_cacheByNid = new HashMap<>(m_mapVirtMethods);

        for (Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
            {
            MethodInfo info = entry.getValue();

            info.populateCache(entry.getKey(), m_cacheById, m_cacheByNid);
            }

        // REVIEW: consider calculating the "abstract" flags lazily
        boolean fExplicitAbstract = fSynthetic || !isClass() ||
                TypeInfo.containsAnnotation(aannoClass, "Abstract");

        boolean fImplicitAbstract = fExplicitAbstract ||
                mapProps.values().stream().anyMatch(PropertyInfo::isExplicitlyAbstract) ||
                mapMethods.values().stream().anyMatch(MethodInfo::isAbstract);

        m_fExplicitAbstract = fExplicitAbstract;
        m_fImplicitAbstract = fImplicitAbstract;
        }

    /**
     * Construct a TypeInfo for a generic type parameter.
     *
     * @param typeGeneric     the TypeConstant of the generic type
     * @param infoConstraint  the TypeInfo for the constraining type
     */
    public TypeInfo(TypeConstant typeGeneric, TypeInfo infoConstraint)
        {
        assert infoConstraint != null;
        assert infoConstraint.m_progress == Progress.Complete;
        assert typeGeneric != null && typeGeneric.isGenericType();

        m_type                = typeGeneric;
        m_struct              = infoConstraint.m_struct;
        m_cDepth              = infoConstraint.m_cDepth;
        m_mapTypeParams       = Collections.EMPTY_MAP;
        m_aannoClass          = null;
        m_typeExtends         = infoConstraint.m_type;
        m_typeRebases         = null;
        m_typeInto            = null;
        m_listProcess         = infoConstraint.m_listProcess;
        m_listmapClassChain   = infoConstraint.m_listmapClassChain;
        m_listmapDefaultChain = infoConstraint.m_listmapDefaultChain;
        m_mapProps            = infoConstraint.m_mapProps;
        m_mapVirtProps        = infoConstraint.m_mapVirtProps;
        m_mapMethods          = infoConstraint.m_mapMethods;
        m_mapVirtMethods      = infoConstraint.m_mapVirtMethods;
        m_progress            = Progress.Complete;

        m_cacheById  = infoConstraint.m_cacheById;
        m_cacheByNid = infoConstraint.m_cacheByNid;

        m_fExplicitAbstract = true;
        m_fImplicitAbstract = infoConstraint.m_fImplicitAbstract;
        }

    /**
     * Create a new TypeInfo that represents a more limited (public or protected) access to the
     * members of this private type.
     *
     * @param access  the desired access, either PUBLIC or PROTECTED
     *
     * @return a new TypeInfo
     */
    public TypeInfo limitAccess(Access access)
        {
        assert m_type.getAccess() == Access.PRIVATE;
        if (access == Access.PRIVATE)
            {
            return this;
            }

        assert access == Access.PROTECTED || access == Access.PUBLIC;

        TypeConstant typeNew = m_type.getUnderlyingType();
        if (access == Access.PROTECTED)
            {
            typeNew = pool().ensureAccessTypeConstant(typeNew, Access.PROTECTED);
            }

        Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
        Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();
        for (Entry<PropertyConstant, PropertyInfo> entry : m_mapProps.entrySet())
            {
            // first, determine if the property's parent would even still exist (since everything
            // inside of it will disappear if it doesn't)
            PropertyConstant id       = entry.getKey();
            PropertyInfo     prop     = entry.getValue();
            boolean          fVirtual = prop.isVirtual();
            if (id.getNestedDepth() <= 1 || isIdentityReachable(prop.getIdentity(), fVirtual, access))
                {
                // now ask the Property itself to reduce its capabilities based on the new access level
                prop = prop.limitAccess(access);
                if (prop != null)
                    {
                    mapProps.put(id, prop);

                    if (fVirtual)
                        {
                        mapVirtProps.put(id.resolveNestedIdentity(ensureTypeResolver()), prop);
                        }
                    }
                }
            }

        Map<MethodConstant, MethodInfo> mapMethods     = new HashMap<>();
        Map<Object        , MethodInfo> mapVirtMethods = new HashMap<>();
        for (Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
            {
            MethodConstant id = entry.getKey();
            MethodInfo method = entry.getValue();
            if (method.getAccess().isAsAccessibleAs(access) && (id.getNestedDepth() <= 1
                    || isIdentityReachable(method.getIdentity(), method.isVirtual(), access)))
                {
                mapMethods.put(id, method);

                if (method.isVirtual())
                    {
                    mapVirtMethods.put(id.resolveNestedIdentity(ensureTypeResolver()), method);
                    }
                }
            }

        return new TypeInfo(typeNew, m_struct, m_cDepth, false,
                m_mapTypeParams, m_aannoClass,
                m_typeExtends, m_typeRebases, m_typeInto,
                m_listProcess, m_listmapClassChain, m_listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, m_progress);
        }

    /**
     * @return the "into" version of this TypeInfo
     */
    public TypeInfo asInto()
        {
        TypeInfo info = m_into;
        if (info == null)
            {
            Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
            Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();
            for (Entry<PropertyConstant, PropertyInfo> entry : m_mapProps.entrySet())
                {
                PropertyConstant id   = entry.getKey();
                PropertyInfo     prop = entry.getValue();

                // convert the property into an "into" property
                prop = prop.asInto();

                mapProps.put(id, prop);
                if (prop.isVirtual())
                    {
                    mapVirtProps.put(id.resolveNestedIdentity(ensureTypeResolver()), prop);
                    }
                }

            Map<MethodConstant, MethodInfo> mapMethods     = new HashMap<>();
            Map<Object        , MethodInfo> mapVirtMethods = new HashMap<>();
            for (Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
                {
                MethodConstant id     = entry.getKey();
                MethodInfo     method = entry.getValue();

                // convert the method into an "into" method
                method = method.asInto();

                mapMethods.put(id, method);
                if (method.isVirtual())
                    {
                    mapVirtMethods.put(id.resolveNestedIdentity(ensureTypeResolver()), method);
                    }
                }

            info = new TypeInfo(m_type, m_struct, m_cDepth, true,
                    m_mapTypeParams, m_aannoClass,
                    m_typeExtends, m_typeRebases, m_typeInto,
                    m_listProcess, m_listmapClassChain, m_listmapDefaultChain,
                    mapProps, mapMethods, mapVirtProps, mapVirtMethods, m_progress);

            if (m_progress == Progress.Complete)
                {
                // cache the result
                m_into = info;

                // cache the result on the result itself, so it doesn't have to build its own "into"
                info.m_into = info;
                }
            }
        return info;
        }

    /**
     * Given a specified level of access, would the specified identity still be reachable?
     *
     * @param id        a property or method identity
     * @param fVirtual  true if the identity represents a virtual member of the type
     * @param access    the access level being proposed
     *
     * @return true iff all of the properties and methods between the specified identity and the
     *         containing type would still be reachable given the proposed access level
     */
    private boolean isIdentityReachable(IdentityConstant id, boolean fVirtual, Access access)
        {
        IdentityConstant idParent = id.getNamespace();
        while (idParent.isNested())
            {
            if (fVirtual)
                {
                // substitute a sub-class property or method if one is available
                if (idParent instanceof PropertyConstant)
                    {
                    Object       idResolved = idParent.resolveNestedIdentity(ensureTypeResolver());
                    PropertyInfo info       = m_mapVirtProps.get(idResolved);

                    idParent = info.getIdentity();
                    }
                else if (idParent instanceof MethodConstant)
                    {
                    Object     idResolved = idParent.resolveNestedIdentity(ensureTypeResolver());
                    MethodInfo info       = m_mapVirtMethods.get(idResolved);

                    idParent = info.getIdentity();
                    }
                }

            Component component = idParent.getComponent();
            if (component == null)
                {
                // a method cap will not have a real component because it is a fake identity
                if (idParent instanceof MethodConstant)
                    {
                    Object       idResolved = idParent.resolveNestedIdentity(ensureTypeResolver());
                    MethodInfo method = m_mapVirtMethods.get(idResolved);
                    assert method != null;
                    assert method.getHead().getImplementation() == Implementation.Capped;

                    // look under the cap
                    idParent  = method.getChain()[1].getIdentity();
                    component = idParent.getComponent();
                    assert component != null;
                    }
                else
                    {
                    throw new IllegalStateException("missing component for id: " + idParent);
                    }
                }

            if (component.getAccess().isLessAccessibleThan(access))
                {
                return false;
                }

            idParent = idParent.getNamespace();
            }

        return true;
        }

    /**
     * Obtain a type resolver that uses the information from this type's type parameters.
     *
     * @param errs  the error list to log any errors to
     *
     * @return a GenericTypeResolver
     */
    public ParamInfo.TypeResolver ensureTypeResolver(ErrorListener errs)
        {
        assert errs != null;

        ParamInfo.TypeResolver resolver = m_resolver;
        if (resolver == null || resolver.errs != errs)
            {
            m_resolver = resolver = new ParamInfo.TypeResolver(
                    m_struct.getIdentityConstant(), m_mapTypeParams, errs);
            }
        return resolver;
        }

    /**
     * (Internal)
     *
     * @return a type resolver, creating one if necessary (using the TypeConstant's error list)
     */
    private ParamInfo.TypeResolver ensureTypeResolver()
        {
        ParamInfo.TypeResolver resolver = m_resolver;
        if (resolver == null)
            {
            resolver = ensureTypeResolver(getType().getErrorListener());
            }
        return resolver;
        }

    /**
     * Contribute this TypeInfo's knowledge of potential call chain information to another deriving
     * type's TypeInfo information.
     *
     * @param listmapClassChain    the class chain being collected for the derivative type
     * @param listmapDefaultChain  the default chain being collected for the derivative type
     * @param composition          the composition of the contribution
     */
    public void contributeChains(
            ListMap<IdentityConstant, Origin> listmapClassChain,
            ListMap<IdentityConstant, Origin> listmapDefaultChain,
            Composition composition)
        {
        Origin originTrue  = m_type.new Origin(true);
        Origin originFalse = m_type.new Origin(false);
        if (composition != Composition.Implements && composition != Composition.Delegates)
            {
            boolean fAnnotation = composition == Composition.Annotation;
            for (Entry<IdentityConstant, Origin> entry : m_listmapClassChain.entrySet())
                {
                IdentityConstant constId      = entry.getKey();
                Origin           originThis   = entry.getValue();
                Origin           originResult = originThis.isAnchored() & fAnnotation
                                              ? originTrue
                                              : originFalse;
                Origin           originThat   = listmapClassChain.get(constId);
                if (originThat == null)
                    {
                    // the identity does not already appear in the chain, so add it to the chain
                    listmapClassChain.put(constId, originResult);
                    }
                else if (!originThat.isAnchored())
                    {
                    // the identity in the chain is owned by this type, so remove it from its old
                    // location in the chain, and add it to the end
                    listmapClassChain.remove(constId);
                    listmapClassChain.put(constId, originResult);
                    }
                // else ... the identity in the chain was "yanked" from us, so we can't claim it;
                // just leave it where it is in the chain
                }
            }

        // append our defaults to the default chain (just the ones that are absent from the chain)
        for (IdentityConstant constId : m_listmapDefaultChain.keySet())
            {
            listmapDefaultChain.putIfAbsent(constId, originTrue);
            }
        }

    /**
     * @return the type that the TypeInfo represents
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return the ClassStructure, or null if none is available; a non-abstract type will always
     *         have a ClassStructure
     */
    public ClassStructure getClassStructure()
        {
        return m_struct;
        }

    /**
     * @return the format of the topmost structure that the TypeConstant refers to, or
     *         {@code INTERFACE} for any non-class / non-mixin type (such as a difference type)
     */
    public Format getFormat()
        {
        return m_struct == null ? Format.INTERFACE : m_struct.getFormat();
        }

    /**
     * @return true iff this type is explicitly abstract
     */
    public boolean isExplicitlyAbstract()
        {
        return m_fExplicitAbstract;
        }

    /**
     * @return true iff this type is abstract, which is always true for an interface, and may be
     *         true for a class or mixin
     */
    public boolean isAbstract()
        {
        return m_fImplicitAbstract || m_fExplicitAbstract;
        }

    /**
     * @return true iff this type is a native rebase
     */
    public boolean isNativeRebase()
        {
        return m_type.getDefiningConstant() instanceof NativeRebaseConstant;
        }

    /**
     * @return true iff this type is static (a static global type is a singleton; a static local
     *         type does not hold a reference to its parent)
     */
    public boolean isStatic()
        {
        return m_struct != null && m_struct.isStatic();
        }

    /**
     * @return true if this type represents a singleton instance of a class
     */
    public boolean isSingleton()
        {
        return m_struct != null && m_struct.isSingleton();
        }

    /**
     * @return true iff this is a class type, which is not an interface type or a mixin type
     */
    public boolean isClass()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case CLASS:
            case CONST:
            case ENUM:
            case ENUMVALUE:
            case SERVICE:
                return true;

            default:
                return false;
            }
        }

    /**
     * @return true iff this is a type that can be instantiated
     */
    public boolean isNewable()
        {
        return !isAbstract() && !isSingleton() && isClass();
        }

    /**
     * @return true iff this class is considered to be "top level"
     */
    public boolean isTopLevel()
        {
        return m_struct != null && m_struct.isTopLevel();
        }

    /**
     * @return true iff this class is scoped within another class, such that it requires a parent
     *         reference in order to be instantiated
     */
    public boolean isChild()
        {
        return isClass() && m_struct.isChild();
        }

    /**
     * @return the complete set of type parameters declared within the type
     */
    public Map<Object, ParamInfo> getTypeParams()
        {
        return m_mapTypeParams;
        }

    /**
     * @return the type annotations that had an "into" clause of "Class"
     */
    public Annotation[] getClassAnnotations()
        {
        return m_aannoClass;
        }

    /**
     * @return the TypeConstant representing the "native rebase" type
     */
    public TypeConstant getRebases()
        {
        return m_typeRebases;
        }

    /**
     * @return the TypeConstant representing the super class
     */
    public TypeConstant getExtends()
        {
        return m_typeExtends;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getInto()
        {
        return m_typeInto;
        }

    /**
     * @return the list of contributions that made up this TypeInfo
     */
    public List<Contribution> getContributionList()
        {
        return m_listProcess;
        }

    /**
     * @return the potential call chain of classes
     */
    public ListMap<IdentityConstant, Origin> getClassChain()
        {
        return m_listmapClassChain;
        }

    /**
     * @return the potential default call chain of interfaces
     */
    public ListMap<IdentityConstant, Origin> getDefaultChain()
        {
        return m_listmapDefaultChain;
        }

    /**
     * @return all of the properties for this type, indexed by their "flattened" property constant
     */
    public Map<PropertyConstant, PropertyInfo> getProperties()
        {
        return m_mapProps;
        }

    /**
     * @return all of the properties for this type that can be identified by a simple name, indexed
     *         by that name
     */
    public Map<String, PropertyInfo> ensurePropertiesByName()
        {
        Map<String, PropertyInfo> map = m_mapPropertiesByName;

        if (map == null)
            {
            map = new HashMap<>();
            for (Entry<PropertyConstant, PropertyInfo> entry : m_mapProps.entrySet())
                {
                PropertyConstant id   = entry.getKey();
                PropertyInfo     prop = entry.getValue();

                // only include the non-nested properties
                if (id.getNestedDepth() == m_cDepth + 1)
                    {
                    // have to pick one that is more visible than the other
                    map.compute(prop.getName(), (sName, propPrev) ->
                        propPrev == null ? prop : selectVisible(prop, propPrev));
                    }
                }

            m_mapPropertiesByName = map;
            }

        return map;
        }

    private PropertyInfo selectVisible(PropertyInfo prop1, PropertyInfo prop2)
        {
        // only one, at most, can be virtual
        if (prop1.isVirtual())
            {
            assert !prop2.isVirtual();
            return prop1;
            }
        if (prop2.isVirtual())
            {
            return prop2;
            }

        // "highest" pane of glass for a non-virtual property wins
        IdentityConstant idClass1 = prop1.getIdentity().getClassIdentity();
        IdentityConstant idClass2 = prop2.getIdentity().getClassIdentity();
        assert idClass1 != null && idClass2 != null && !idClass1.equals(idClass2);

        // first check the class call chain
        int of1 = indexOfClass(m_listmapClassChain, idClass1);
        int of2 = indexOfClass(m_listmapClassChain, idClass2);
        if (of1 >= 0)
            {
            return of2 >= 0 && of2 < of1
                    ? prop2
                    : prop1;
            }
        if (of2 >= 0)
            {
            return prop2;
            }

        // next check the interface call chain
        of1 = indexOfClass(m_listmapDefaultChain, idClass1);
        of2 = indexOfClass(m_listmapDefaultChain, idClass2);
        if (of1 >= 0)
            {
            return of2 >= 0 && of2 < of1
                    ? prop2
                    : prop1;
            }
        if (of2 >= 0)
            {
            return prop2;
            }

        throw new IllegalStateException();
        }

    private int indexOfClass(ListMap<IdentityConstant, Origin> listmap, IdentityConstant idClass)
        {
        int i = 0;
        for (IdentityConstant id : listmap.keySet())
            {
            if (id.equals(idClass))
                {
                return i;
                }
            ++i;
            }
        return -1;
        }

    /**
     * Given an identity (or nested identity) of a property, obtain a TypeInfo that represents the
     * entirety of the information about that property, including what properties and methods it
     * contains, what their call chains are, etc. The reason that this method combines the "id" and
     * the "nid" lookups is because a nested identity is not particularly type-safe, thus making it
     * relatively simple to combine the two requests into one (albeit untyped).
     *
     * @param idOrNid  either the PropertyConstant for the property, or the nested identity of the
     *                 property
     *
     * @return a TypeInfo for the property, or null if the property does not exist
     */
    public TypeInfo getPropertyAsTypeInfo(Object idOrNid)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    /**
     * Obtain all of the properties declared within the specified method.
     *
     * @param constMethod  the MethodConstant identifying the method that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public Map<String, PropertyInfo> ensureNestedPropertiesByName(MethodConstant constMethod)
        {
        Map<String, PropertyInfo> map = null;
        for (PropertyInfo prop : m_mapProps.values())
            {
            // only include the properties nested under the specified method
            if (prop.getParent().equals(constMethod))
                {
                if (map == null)
                    {
                    map = new HashMap<>();
                    }
                map.put(prop.getName(), prop);
                }
            }

        return map == null
                ? Collections.EMPTY_MAP
                : map;
        }

    /**
     * Obtain all of the properties declared within the specified property.
     * REVIEW this implementation is probably insufficient, considering possible visibility rules
     *
     * @param constProp  the PropertyConstant identifying the property that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public Map<String, PropertyInfo> ensureNestedPropertiesByName(PropertyConstant constProp)
        {
        Map<String, PropertyInfo> map = null;
        int cDepth = constProp.getNestedDepth();
        for (PropertyInfo prop : m_mapProps.values())
            {
            IdentityConstant constParent = prop.getParent();
            // only include the properties nested under the specified property
            if (constParent == constProp || constParent.trailingPathEquals(constProp, cDepth))
                {
                if (map == null)
                    {
                    map = new HashMap<>();
                    }
                map.put(prop.getName(), prop);
                }
            }

        return map == null
                ? Collections.EMPTY_MAP
                : map;
        }

    public PropertyInfo findProperty(String sName)
        {
        return ensurePropertiesByName().get(sName);
        }

    /**
     * Look up the property by its identity constant.
     *
     * @param constId  the constant that identifies the property
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public PropertyInfo findProperty(PropertyConstant constId)
        {
        PropertyInfo prop = m_mapProps.get(constId);
        if (prop != null)
            {
            return prop;
            }

        prop = m_mapVirtProps.get(constId.resolveNestedIdentity(ensureTypeResolver()));
        return prop != null && prop.isIdentityValid(constId) ? prop : null;
        }

    /**
     * @return all of the non-scoped methods for this type
     */
    public Map<MethodConstant, MethodInfo> getMethods()
        {
        return m_mapMethods;
        }

    // TODO this should be the "virt map" from Object to MethodInfo
    /**
     * @return all of the methods for this type that can be identified by just a signature, indexed
     *         by that signature
     */
    protected Map<SignatureConstant, MethodInfo> ensureMethodsBySignature()
        {
        Map<SignatureConstant, MethodInfo> map = m_mapMethodsBySignature;

        if (map == null)
            {
            map = new HashMap<>();
            for (MethodInfo method : m_mapMethods.values())
                {
                // only include the non-nested Methods
                if (method.getIdentity().getNestedDepth() == m_cDepth + 2)
                    {
                    map.put(method.getSignature(), method);
                    }
                }

            m_mapMethodsBySignature = map;
            }

        return map;
        }

    /**
     * Find the MethodInfo for the specified SignatureConstant.
     *
     * @param sig  a SignatureConstant
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public MethodInfo getMethodBySignature(SignatureConstant sig)
        {
        Map<SignatureConstant, MethodInfo> map = m_mapMethodsBySignature;
        if (map != null)
            {
            MethodInfo method = map.get(sig);
            if (method != null)
                {
                return method;
                }
            }

        MethodInfo method = m_mapVirtMethods.get(sig);
        if (method != null)
            {
            return method;
            }

        TypeConstant typeThis = getType();

        for (MethodInfo methodTest : m_mapMethods.values())
            {
            if (methodTest.getIdentity().getSignature().isSubstitutableFor(sig, typeThis))
                {
                ensureMethodsBySignature().putIfAbsent(sig, methodTest);
                return methodTest;
                }
            }

        return null;
        }

    /**
     * Find the MethodInfo for the specified MethodConstant identity.
     *
     * @param id  a MethodConstant identity
     *
     * @return the MethodInfo corresponding to the specified identity
     */
    public MethodInfo getMethodById(MethodConstant id)
        {
        MethodInfo method = m_cacheById.get(id);
        if (method != null)
            {
            return method;
            }

        // try to find a method with the same signature
        method = m_mapVirtMethods.get(id.resolveNestedIdentity(ensureTypeResolver()));
        if (method != null)
            {
            for (MethodBody body : method.getChain())
                {
                if (body.getIdentity().equals(id))
                    {
                    m_cacheById.put(id, method);
                    m_cacheByNid.put(id.getNestedIdentity(), method);
                    return method;
                    }
                }
            }

        // it is possible that the map lookup miss is caused by the passed id's signature NOT
        // having its generic types resolved, so brute-force search
        // TODO this might no longer be necessary because the cache is now pre-populated
        String sName  = id.getName();
        int    cDepth = id.getNestedDepth();
        for (MethodInfo methodTest : m_mapVirtMethods.values())
            {
            // this "if" does not prove that this is the method that we're looking for; it just
            // eliminates 99% of the potential garbage from our brute force search
            if (methodTest.getSignature().getName().equals(sName)
                    && methodTest.getIdentity().getNestedDepth() == cDepth)
                {
                for (MethodBody body : methodTest.getChain())
                    {
                    if (body.getIdentity().equals(id))
                        {
                        m_cacheById.put(id, methodTest);
                        m_cacheByNid.put(id.getNestedIdentity(), methodTest);
                        return methodTest;
                        }
                    }
                }
            }

        return null;
        }

    /**
     * Find the MethodInfo for the specified nested identity.
     *
     * @param nid  a nested identity, as obtained from {@link MethodConstant#getNestedIdentity()}
     *             or {@link MethodConstant#resolveNestedIdentity(GenericTypeResolver)}
     *
     * @return the specified MethodInfo, or null if no MethodInfo could be found by the provided
     *         nested identity
     */
    public MethodInfo getMethodByNestedId(Object nid)
        {
        MethodInfo info = m_cacheByNid.get(nid);
        if (info == null)
            {
            throw new IllegalStateException("TODO: couldn't find " + nid + " at " + m_type);
            }
        return info;
        }

    /**
     * Obtain the method chain for the specified method.
     *
     * @param id  the MethodConstant for the method
     *
     * @return the method chain iff the method exists; otherwise null
     */
    public MethodBody[] getOptimizedMethodChain(MethodConstant id)
        {
        MethodInfo info = getMethodById(id);
        return info == null
                ? null
                : info.ensureOptimizedMethodChain(this);
        }

    /**
     * Obtain the method chain for the specified method signature.
     *
     * @param sig  the SignatureConstant for the method
     *
     * @return the method chain iff the method exists; otherwise null
     */
    public MethodBody[] getOptimizedMethodChain(SignatureConstant sig)
        {
        MethodInfo info = getMethodBySignature(sig);
        return info == null
                ? null
                : info.ensureOptimizedMethodChain(this);
        }

    /**
     * Obtain the method chain for the specified method.
     *
     * @param nid  the nested id for the method
     *
     * @return the method chain iff the method exists; otherwise null
     */
    public MethodBody[] getOptimizedMethodChain(Object nid)
        {
        MethodInfo info = getMethodByNestedId(nid);
        return info == null
                ? null
                : info.ensureOptimizedMethodChain(this);
        }

    /**
     * Obtain the method chain for the property getter for the specified property id.
     *
     * @param id  the property id
     *
     * @return the method chain iff the property exists; otherwise null
     */
    public MethodBody[] getOptimizedGetChain(PropertyConstant id)
        {
        PropertyInfo prop = findProperty(id);
        return prop == null
                ? null
                : prop.ensureOptimizedGetChain(this);
        }

    /**
     * Obtain the method chain for the property setter for the specified property id.
     *
     * @param id  the property id
     *
     * @return the method chain iff the property exists and is a Var; otherwise null
     */
    public MethodBody[] getOptimizedSetChain(PropertyConstant id)
        {
        PropertyInfo prop = findProperty(id);
        return prop == null
                ? null
                : prop.ensureOptimizedSetChain(this);
        }

    /**
     * Find a named method or function that best matches the specified requirements.
     *
     * @param sName       the name of the method or function
     * @param fMethod     true to include methods in the search
     * @param fFunction   true to include functions in the search
     * @param aRedundant  the redundant return type information (helps to clarify which method or
     *                    function to select)
     * @param aArgs       the types of the arguments being provided (some of which may be null to
     *                    indicate "unknown" in a pre-validation stage, or "non-binding unknown")
     * @param asArgNames  an optional array of argument names, each (if provided) corresponding to
     *                    an element in {@code aArgs}
     *
     * @return the id of a matching method or function (null if none found)
     */
    public MethodConstant findCallable(String sName, boolean fMethod, boolean fFunction,
                                       TypeConstant[] aRedundant, TypeConstant[] aArgs, String[] asArgNames)
        {
        int cRedundant = aRedundant == null ? 0 : aRedundant.length;
        int cArgs      = aArgs      == null ? 0 : aArgs     .length;

        Map<MethodConstant, MethodInfo>       mapAll    = getMethods();
        Map.Entry<MethodConstant, MethodInfo> entryBest = null;
        Map<MethodConstant, MethodInfo>       mapMatch  = null;
        NextMethod: for (Map.Entry<MethodConstant, MethodInfo> entry : mapAll.entrySet())
            {
            // 1) including only method and/or functions as appropriate;
            // 2) matching the name;
            // 3) for each named argument, having a matching parameter name on the method/function; // TODO
            // 4) after accounting for named arguments, having at least as many parameters as the
            //    number of provided arguments, and no more required parameters than the number of
            //    provided arguments;
            // 5) having each argument from steps (3) and (4) be isA() or @Auto convertible to the
            //    type of each corresponding parameter; and
            // 6) matching (i.e. isA()) any specified redundant return types
            MethodConstant id   = entry.getKey();
            MethodInfo     info = entry.getValue();
            if (id.getNestedDepth() == 2
                    && id.getName().equals(sName)
                    && id.getRawParams() .length >= cArgs
                    && id.getRawReturns().length >= cRedundant
                    && (info.isFunction() ? fFunction : fMethod))
                {
                SignatureConstant sig      = info.getSignature();
                TypeConstant[]    aParams  = sig.getRawParams();
                TypeConstant[]    aReturns = sig.getRawReturns();
                for (int i = 0; i < cRedundant; ++i)
                    {
                    TypeConstant typeReturn    = aReturns  [i];
                    TypeConstant typeRedundant = aRedundant[i];

                    assert typeRedundant.isA(pool().typeType()) &&
                           typeRedundant.getParamsCount() == 1;

                    if (!typeReturn.isA(typeRedundant.getParamTypesArray()[0]))
                        {
                        continue NextMethod;
                        }
                    }
                for (int i = 0; i < cArgs; ++i)
                    {
                    TypeConstant typeParam = aParams[i];
                    TypeConstant typeArg   = aArgs  [i];
                    if (typeArg != null &&                // null means unbound
                            !typeArg.isAssignableTo(typeParam))
                        {
                        continue NextMethod;
                        }
                    }

                int cParams = aParams.length;
                if (cParams > cArgs)
                    {
                    // make sure that all required parameters have been satisfied;
                    // the challenge that we have here is that there are potentially a large number
                    // of MethodStructures that contribute to the resulting MethodInfo, and each can
                    // have different defaults for the parameters
                    // TODO: improve the naive implementation below
                    MethodBody      body   = info.getHead();
                    MethodStructure method = body.getMethodStructure();
                    for (int i = cArgs; i < cParams; ++i)
                        {
                        // make sure that the argument is optional
                        if (!method.getParam(i).hasDefaultValue())
                            {
                            continue NextMethod;
                            }
                        }
                    }

                if (entryBest == null && mapMatch == null)
                    {
                    // only one match so far
                    entryBest = entry;
                    }
                else
                    {
                    if (mapMatch == null)
                        {
                        // switch to "multi choice" mode
                        mapMatch = new HashMap<>();
                        mapMatch.put(entryBest.getKey(), entryBest.getValue());
                        entryBest = null;
                        }

                    mapMatch.put(entry.getKey(), entry.getValue());
                    }
                }
            }

        if (entryBest != null)
            {
            // if one method matches, then that method is selected
            return entryBest.getKey();
            }

        if (mapMatch == null)
            {
            // no matches
            return null;
            }

        // with multiple methods and/or functions matching, the _best_ one must be selected.
        // - First, the algorithm from {@link TypeConstant#selectBest(SignatureConstant[])} is
        //   used.
        // - If that algorithm results in a single selection, then that single selection is used.
        // - Otherwise, the redundant return types are used as a tie breaker; if that results in a
        //   single selection, then that single selection is used.
        // - Otherwise, the ambiguity is an error.
        // TODO - how to factor in conversions?
        return null;
        }

    /**
     * Find a constructor that best matches the specified requirements.
     *
     * @param aArgs       the types of the arguments being provided (some of which may be null to
     *                    indicate "unknown" in a pre-validation stage, or "non-binding unknown")
     * @param asArgNames  an optional array of argument names, each (if provided) corresponding to
     *                    an element in {@code aArgs}
     *
     * @return the matching constructor id (null if none found)
     */
    public MethodConstant findConstructor(TypeConstant[] aArgs, String[] asArgNames)
        {
        return findCallable("construct", false, true, TypeConstant.NO_TYPES, aArgs, asArgNames);
        }

    /**
     * @return a MethodStructure for this type's "equals" function
     */
    public MethodStructure findEqualsFunction()
        {
        MethodStructure functionEquals = m_functionEquals;
        if (functionEquals == null)
            {
            ConstantPool pool = m_type.getConstantPool();
            for (Contribution contrib : m_listProcess)
                {
                TypeConstant typeThis = contrib.getTypeConstant();
                ClassConstant clzThis = (ClassConstant) typeThis.getDefiningConstant();
                if (clzThis instanceof NativeRebaseConstant)
                    {
                    clzThis = ((NativeRebaseConstant) clzThis).getClassConstant();
                    }

                for (Map.Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
                    {
                    MethodConstant constMethod = entry.getKey();

                    // the signature we are looking for is:
                    //  static <CompileType extends [this type]> Boolean equals(CompileType v1, CompileType v2)
                    if (constMethod.getNestedDepth() != 2 ||
                        !constMethod.getName().equals("equals"))
                        {
                        continue;
                        }

                    TypeConstant[] atypeReturn = constMethod.getRawReturns();
                    if (atypeReturn.length != 1 && !atypeReturn[0].equals(pool.typeBoolean()))
                        {
                        continue;
                        }

                    if (isComparePattern(pool, constMethod.getRawParams(), clzThis))
                        {
                        functionEquals = entry.getValue().getHead().getMethodStructure();

                        if (functionEquals.isFunction())
                            {
                            return m_functionEquals = functionEquals;
                            }
                        // else TODO: should there be a warning here?
                        }
                    }
                }
            }
        return functionEquals;
        }

    /**
     * @return a MethodStructure for this type's "compare" function
     */
    public MethodStructure findCompareFunction()
        {
        MethodStructure functionCompare = m_functionCompare;
        if (functionCompare == null)
            {
            ConstantPool pool = m_type.getConstantPool();
            for (Contribution contrib : m_listProcess)
                {
                TypeConstant typeThis = contrib.getTypeConstant();
                ClassConstant clzThis = (ClassConstant) typeThis.getDefiningConstant();
                if (clzThis instanceof NativeRebaseConstant)
                    {
                    clzThis = ((NativeRebaseConstant) clzThis).getClassConstant();
                    }

                for (Map.Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
                    {
                    MethodConstant constMethod = entry.getKey();

                    // the signature we are looking for is:
                    //  static <CompileType extends [this type]> Ordered compare(CompileType v1, CompileType v2)
                    if (constMethod.getNestedDepth() != 2 ||
                        !constMethod.getName().equals("compare"))
                        {
                        continue;
                        }

                    TypeConstant[] atypeReturn = constMethod.getRawReturns();
                    if (atypeReturn.length != 1 && !atypeReturn[0].equals(pool.typeOrdered()))
                        {
                        continue;
                        }

                    if (isComparePattern(pool, constMethod.getRawParams(), clzThis))
                        {
                        functionCompare = entry.getValue().getHead().getMethodStructure();

                        if (functionCompare.isFunction())
                            {
                            return m_functionCompare = functionCompare;
                            }
                        // else TODO: should there be a warning here?
                        }
                    }
                }
            }
        return functionCompare;
        }

    /**
     * Check if the specified parameters match the "equals" or "compare" functions parameters
     * that have the following pattern:
     *
     *  <CompileType extends [this type]> [return type] equals(CompileType v1, CompileType v2)
     */
    private static boolean isComparePattern(
            ConstantPool pool, TypeConstant[] atypeParam, ClassConstant clzThis)
        {
        if (atypeParam.length != 3)
            {
            return false;
            }

        TypeConstant typeType = atypeParam[0];
        if (!typeType.isParamsSpecified() ||
            !typeType.getUnderlyingType().equals(pool.typeType()) ||
            !typeType.getParamTypesArray()[0].getDefiningConstant().
                equals(clzThis))
            {
            return false;
            }

        TypeConstant typeParam = atypeParam[1];
        if (!typeParam.equals(atypeParam[2]))
            {
            return false;
            }

        if (typeParam instanceof TerminalTypeConstant)
            {
            Constant constParam = typeParam.getDefiningConstant();
            if (constParam.getFormat() == Constant.Format.TypeParameter &&
                ((TypeParameterConstant) constParam).getRegister() == 0)
                {
                return true;
                }
            }

        return false;
        }


    // ----- compiler support ----------------------------------------------------------------------

    /**
     * See if any method has the specified name.
     *
     * @param sName  a method name
     *
     * @return true if the type contains at least one method (or function) by the specified name
     */
    public boolean containsMultiMethod(String sName)
        {
        for (MethodConstant method : m_mapMethods.keySet())
            {
            if (method.getName().equals(sName))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Obtain all of the methods that are annotated with "@Op".
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getOpMethodInfos()
        {
        Set<MethodInfo> setOps = m_setOps;
        if (setOps == null)
            {
            for (MethodInfo info : ensureMethodsBySignature().values())
                {
                if (info.isOp())
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info);
                    }
                }

            // cache the result
            m_setOps = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Obtain all of the matching op methods for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * @param sName    the default op name, such as "add" (optional)
     * @param sOp      the operator string, such as "+" (optional)
     * @param cParams  the number of parameters for the operator method, or -1 to match any
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams)
        {
        Set<MethodConstant> setOps = null;

        String sKey = String.valueOf(sName) + sOp + cParams;
        if (m_sOp != null && sKey.equals(m_sOp))
            {
            setOps = m_setOp;
            }
        else
            {
            ConstantPool pool = m_type.getConstantPool();
            for (MethodInfo info : getOpMethodInfos())
                {
                if (info.isOp(sName, sOp, cParams))
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    MethodConstant    idMethod    = info.getIdentity();
                    SignatureConstant sigOrig     = idMethod.getSignature();
                    SignatureConstant sigResolved = sigOrig.resolveGenericTypes(m_type)
                                                           .resolveAutoNarrowing(m_type);
                    if (!sigResolved.equals(sigOrig))
                        {
                        idMethod = pool.ensureMethodConstant(idMethod.getNamespace(), sigResolved);
                        m_cacheById.putIfAbsent(idMethod, info);
                        }
                    setOps.add(idMethod);
                    }
                }

            // cache the result
            m_sOp   = sKey;
            m_setOp = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Obtain all of the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getAutoMethodInfos()
        {
        Set<MethodInfo> setAuto = m_setAuto;
        if (setAuto == null)
            {
            for (MethodInfo info : ensureMethodsBySignature().values())
                {
                if (info.isAuto())
                    {
                    if (setAuto == null)
                        {
                        setAuto = new HashSet<>(7);
                        }
                    setAuto.add(info);
                    }
                }

            // cache the result
            m_setAuto = setAuto = (setAuto == null ? Collections.EMPTY_SET : setAuto);
            }

        return setAuto;
        }

    /**
     * Find a method on this type that converts an object of this type to a desired type.
     *
     * @param typeDesired  the type desired to convert to, or that the conversion result would be
     *                     assignable to ("isA" would be true)
     *
     * @return a MethodConstant representing an {@code @Auto} conversion method resulting in an
     *         object whose type is compatible with the specified (desired) type, or null if either
     *         no method matches, or more than one method matches (ambiguous)
     */
    public MethodConstant findConversion(TypeConstant typeDesired)
        {
        MethodConstant methodMatch = null;

        // check the cached result
        if (m_typeAuto != null && typeDesired.equals(m_typeAuto))
            {
            methodMatch = m_methodAuto;
            }
        else
            {
            for (MethodInfo info : getAutoMethodInfos())
                {
                MethodConstant method     = info.getIdentity();
                TypeConstant   typeResult = method.getRawReturns()[0];
                if (typeResult.equals(typeDesired))
                    {
                    // exact match -- it's not going to get any better than this
                    return method;
                    }

                if (typeResult.isA(typeDesired))
                    {
                    if (methodMatch == null)
                        {
                        methodMatch = method;
                        }
                    else
                        {
                        TypeConstant typeResultMatch = methodMatch.getRawReturns()[0];
                        boolean fSub = typeResult.isA(typeResultMatch);
                        boolean fSup = typeResultMatch.isA(typeResult);
                        if (fSub ^ fSup)
                            {
                            // use the obviously-more-specific type conversion
                            methodMatch = fSub ? method : methodMatch;
                            }
                        else
                            {
                            // ambiguous - there are at least two methods that match
                            methodMatch = null;
                            break;
                            }
                        }
                    }
                }

            // cache the result
            m_typeAuto   = typeDesired;
            m_methodAuto = methodMatch;
            }

        return methodMatch;
        }

    /**
     * Helper method that return all methods with a given name. Used for debugging.
     */
    public Map<MethodConstant, MethodInfo> filterMethods(String sName)
        {
        Map<MethodConstant, MethodInfo> map = new HashMap<>();
        for (Map.Entry<MethodConstant, MethodInfo> entry : getMethods().entrySet())
            {
            if (entry.getValue().getIdentity().getSignature().getName().equals(sName))
                {
                map.put(entry.getKey(), entry.getValue());
                }
            }
        return map;
        }

    private ConstantPool pool()
        {
        return m_type.getConstantPool();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("TypeInfo: ")
          .append(m_type)
          .append(" (format=")
          .append(getFormat());

        if (isAbstract())
            {
            sb.append(", abstract");
            }
        if (isStatic())
            {
            sb.append(", static");
            }
        if (isSingleton())
            {
            sb.append(", singleton");
            }
        if (isNewable())
            {
            sb.append(", newable");
            }

        sb.append(")");

        if (!m_mapTypeParams.isEmpty())
            {
            sb.append("\n- Parameters (")
              .append(m_mapTypeParams.size())
              .append(')');
            int i = 0;
            for (Entry<Object, ParamInfo> entry : m_mapTypeParams.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (m_typeInto != null)
            {
            sb.append("\n- Into: ")
              .append(m_typeInto.getValueString());
            }
        if (m_typeRebases != null)
            {
            sb.append("\n- Rebases: ")
              .append(m_typeRebases.getValueString());
            }
        if (m_typeExtends != null)
            {
            sb.append("\n- Extends: ")
              .append(m_typeExtends.getValueString());
            }

        if (!m_listmapClassChain.isEmpty())
            {
            sb.append("\n- Class Chain (")
              .append(m_listmapClassChain.size())
              .append(')');
            int i = 0;
            for (Entry<IdentityConstant, Origin> entry : m_listmapClassChain.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey().getValueString());

                if (entry.getValue().isAnchored())
                    {
                    sb.append(" (Anchored)");
                    }
                }
            }

        if (!m_listmapDefaultChain.isEmpty())
            {
            sb.append("\n- Default Chain (")
              .append(m_listmapDefaultChain.size())
              .append(')');
            int i = 0;
            for (IdentityConstant constId : m_listmapDefaultChain.keySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(constId.getValueString());
                }
            }

        if (!m_mapProps.isEmpty())
            {
            sb.append("\n- Properties (")
              .append(m_mapProps.size())
              .append(')');
            int i = 0;
            for (Entry<PropertyConstant, PropertyInfo> entry : m_mapProps.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ");
                if (m_mapVirtProps.containsKey(entry.getKey().resolveNestedIdentity(ensureTypeResolver())))
                    {
                    sb.append("(v) ");
                    }
                sb.append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (!m_mapMethods.isEmpty())
            {
            sb.append("\n- Methods (")
              .append(m_mapMethods.size())
              .append(')');
            int i = 0;
            for (Entry<MethodConstant, MethodInfo> entry : m_mapMethods.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ");
                if (m_mapVirtMethods.containsKey(entry.getKey().resolveNestedIdentity(ensureTypeResolver())))
                    {
                    sb.append("(v) ");
                    }
                sb.append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        return sb.toString();
        }


    // ----- deferred TypeInfo creation ------------------------------------------------------------

    Progress getProgress()
        {
        return m_progress;
        }

    boolean isPlaceHolder()
        {
        return m_progress == Progress.Building;
        }

    boolean isIncomplete()
        {
        return m_progress == Progress.Incomplete;
        }

    boolean isComplete()
        {
        return m_progress == Progress.Complete;
        }


    // ----- internal helpers ----------------------------------------------------------------------

    public static Annotation[] validateAnnotations(Annotation[] annotations)
        {
        if (annotations == null)
            {
            return Annotation.NO_ANNOTATIONS;
            }

        for (Annotation annotation : annotations)
            {
            if (annotation == null)
                {
                throw new IllegalStateException("null annotation");
                }
            }

        return annotations;
        }

    public static Annotation[] mergeAnnotations(Annotation[] anno1, Annotation[] anno2)
        {
        if (anno1.length == 0)
            {
            return anno2;
            }

        if (anno2.length == 0)
            {
            return anno1;
            }

        ArrayList<Annotation> list = new ArrayList<>();
        Set<Constant> setPresent = new HashSet<>();
        appendAnnotations(list, anno1, setPresent);
        appendAnnotations(list, anno2, setPresent);
        return list.toArray(new Annotation[list.size()]);
        }

    public static void appendAnnotations(ArrayList<Annotation> list, Annotation[] aAnno, Set<Constant> setPresent)
        {
        for (Annotation anno : aAnno)
            {
            if (setPresent.add(anno.getAnnotationClass()))
                {
                list.add(anno);
                }
            }
        }

    public static boolean containsAnnotation(Annotation[] annotations, String sName)
        {
        if (annotations == null || annotations.length == 0)
            {
            return false;
            }

        IdentityConstant clzFind = annotations[0].getConstantPool().getImplicitlyImportedIdentity(sName);
        for (Annotation annotation : annotations)
            {
            if (annotation.getAnnotationClass().equals(clzFind))
                {
                return true;
                }
            }

        return false;
        }


    // ----- inner class: TypeResolver -------------------------------------------------------------

    /**
     * A GenericTypeResolver that works from a TypeInfo's map from property name to ParamInfo.
     */
    public interface TypeResolver
            extends GenericTypeResolver
        {
        ParamInfo findParamInfo(Object nid);
        void registerParamInfo(Object nid, ParamInfo param);
        }


    // ----- fields --------------------------------------------------------------------------------

    public enum Progress {Absent, Building, Incomplete, Complete}


    /**
     * Represents the completeness of the TypeInfo.
     */
    private final Progress m_progress;

    /**
     * The data type that this TypeInfo represents.
     */
    private final TypeConstant m_type;

    /**
     * The ClassStructure of the type, if the type is based on a ClassStructure.
     */
    private final ClassStructure m_struct;

    /**
     * The "depth from class" for this TypeInfo. A TypeInfo for an actual class will have a depth of
     * {@code 0}.
     */
    private final int m_cDepth;

    /**
     * Whether this type is explicitly abstract, which is always true for an interface.
     */
    private final boolean m_fExplicitAbstract;

    /**
     * Whether this type is abstract due to a presence of abstract properties or methods.
     */
    private final boolean m_fImplicitAbstract;

    /**
     * The type parameters for this TypeInfo.
     */
    private final Map<Object, ParamInfo> m_mapTypeParams;

    /**
     * The class annotations.
     */
    private final Annotation[] m_aannoClass;

    /**
     * The type that is extended. The term "extends" has slightly different meanings for mixins and
     * other classes.
     */
    private final TypeConstant m_typeExtends;

    /**
     * The type that is rebased onto.
     */
    private final TypeConstant m_typeRebases;

    /**
     * For mixins, the type that is mixed into. For interfaces, this is always Object.
     */
    private final TypeConstant m_typeInto;

    /**
     * The list of contributions that made up this TypeInfo.
     */
    private final List<Contribution> m_listProcess;

    /**
     * The potential call chain of classes.
     */
    private final ListMap<IdentityConstant, Origin> m_listmapClassChain;

    /**
     * The potential default call chain of interfaces.
     */
    private final ListMap<IdentityConstant, Origin> m_listmapDefaultChain;

    /**
     * The properties of this type, indexed by PropertyConstant. Constants, private properties, and
     * properties declared within methods, are identified only by a single (non-virtual) property
     * constant. Other properties can show up at multiple virtual levels, and thus the same property
     * may be referred to by different PropertyConstants, although it will only show up once in this
     * map (using the identity from the highest "pane of glass" that the property shows up on.)
     */
    private final Map<PropertyConstant, PropertyInfo> m_mapProps;

    /**
     * The properties of the type, keyed by nested identity. Properties nested immediately under
     * a class are identified by their (String) name, while properties nested further below the
     * class are identified by a NestedIdentity object. In either case, the key can be obtained by
     * calling {@link PropertyConstant#getNestedIdentity()} or
     * {@link PropertyConstant#resolveNestedIdentity(GenericTypeResolver)}.
     */
    private final Map<Object, PropertyInfo> m_mapVirtProps;

    /**
     * The properties of the type, indexed by name. This will not include nested properties, such
     * as those nested within a property or method. Lazily initialized
     */
    private transient Map<String, PropertyInfo> m_mapPropertiesByName;

    /**
     * The methods of the type, indexed by MethodConstant. Functions, private methods, and other
     * non-virtual methods are identified only by a single (non-virtual) MethodConstant. Other
     * methods can show up a multiple virtual levels, and thus the same method chain may be referred
     * to by different MethodConstants, although each virtual method will show up only once in this
     * map (using the identity from the highest "pane of glass" that the method shows up on.)
     */
    private final Map<MethodConstant, MethodInfo> m_mapMethods;

    /**
     * The virtual methods of the type, keyed by nested identity. Methods nested immediately under
     * a class are identified by their signature, while methods nested further below the class are
     * identified by a NestedIdentity object. In either case, the key can be obtained by calling
     * calling {@link PropertyConstant#getNestedIdentity()} or
     * {@link PropertyConstant#resolveNestedIdentity(GenericTypeResolver)}.
     */
    private final Map<Object, MethodInfo> m_mapVirtMethods;

    /**
     * The methods of the type, indexed by signature. This will not include nested methods, such
     * as those nested within a property or method. Lazily initialized
     */
    private transient Map<SignatureConstant, MethodInfo> m_mapMethodsBySignature;

    /**
     * A cached type resolver.
     */
    private transient ParamInfo.TypeResolver m_resolver;

    /**
     * Cached "equals" function.
     */
    private MethodStructure m_functionEquals;

    /**
     * Cached "compare" function.
     */
    private MethodStructure m_functionCompare;

    // cached query results REVIEW for thread safety
    // REVIEW is this a reasonable way to cache these?
    private final Map<MethodConstant, MethodInfo> m_cacheById;
    private final Map<Object, MethodInfo>         m_cacheByNid;

    private transient TypeInfo            m_into;
    private transient Set<MethodInfo>     m_setAuto;
    private transient Set<MethodInfo>     m_setOps;
    private transient String              m_sOp;
    private transient Set<MethodConstant> m_setOp;
    private transient TypeConstant        m_typeAuto;
    private transient MethodConstant      m_methodAuto;
    }
