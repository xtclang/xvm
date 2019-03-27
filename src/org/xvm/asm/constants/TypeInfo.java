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
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
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
     * @param cInvalidations       the count of TypeInfo invalidations when this TypeInfo was built
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
            int                                 cInvalidations,
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

        f_type                = type;
        f_cInvalidations      = cInvalidations;
        f_struct              = struct;
        f_cDepth              = cDepth;
        f_mapTypeParams       = mapTypeParams;
        f_aannoClass          = validateAnnotations(aannoClass);
        f_typeExtends         = typeExtends;
        f_typeRebases         = typeRebases;
        f_typeInto            = typeInto;
        f_listProcess         = listProcess;
        f_listmapClassChain   = listmapClassChain;
        f_listmapDefaultChain = listmapDefaultChain;
        f_mapProps            = mapProps;
        f_mapVirtProps        = mapVirtProps;
        f_mapMethods          = mapMethods;
        f_mapVirtMethods      = mapVirtMethods;
        f_progress            = progress;

        // pre-populate the method lookup caches
        // and determine if this type is implicitly abstract
        f_cacheById  = new HashMap<>(f_mapMethods);
        f_cacheByNid = new HashMap<>(f_mapVirtMethods);

        for (Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
            {
            MethodInfo info = entry.getValue();

            info.populateCache(entry.getKey(), f_cacheById, f_cacheByNid);
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
     * Construct a TypeInfo for a formal type parameter.
     *
     * @param typeFormal      the TypeConstant of the formal type
     * @param infoConstraint  the TypeInfo for the constraining type
     */
    public TypeInfo(TypeConstant typeFormal, TypeInfo infoConstraint)
        {
        assert infoConstraint != null;
        assert infoConstraint.f_progress == Progress.Complete;
        assert typeFormal != null && typeFormal.isFormalType();

        f_type                = typeFormal;
        f_cInvalidations      = infoConstraint.f_cInvalidations;
        f_struct              = infoConstraint.f_struct;
        f_cDepth              = infoConstraint.f_cDepth;
        f_mapTypeParams       = Collections.EMPTY_MAP;
        f_aannoClass          = null;
        f_typeExtends         = infoConstraint.f_type;
        f_typeRebases         = null;
        f_typeInto            = null;
        f_listProcess         = infoConstraint.f_listProcess;
        f_listmapClassChain   = infoConstraint.f_listmapClassChain;
        f_listmapDefaultChain = infoConstraint.f_listmapDefaultChain;
        f_mapProps            = infoConstraint.f_mapProps;
        f_mapVirtProps        = infoConstraint.f_mapVirtProps;
        f_mapMethods          = infoConstraint.f_mapMethods;
        f_mapVirtMethods      = infoConstraint.f_mapVirtMethods;
        f_progress            = Progress.Complete;

        f_cacheById  = infoConstraint.f_cacheById;
        f_cacheByNid = infoConstraint.f_cacheByNid;

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
        assert f_type.getAccess() == Access.PRIVATE;
        if (access == Access.PRIVATE)
            {
            return this;
            }

        assert access == Access.PROTECTED || access == Access.PUBLIC;

        ConstantPool pool    = pool();
        TypeConstant typeNew = f_type.getUnderlyingType();
        if (access == Access.PROTECTED)
            {
            typeNew = pool.ensureAccessTypeConstant(typeNew, Access.PROTECTED);
            }

        Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
        Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();
        for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
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
                        mapVirtProps.put(id.resolveNestedIdentity(pool, f_type), prop);
                        }
                    }
                }
            }

        Map<MethodConstant, MethodInfo> mapMethods     = new HashMap<>();
        Map<Object        , MethodInfo> mapVirtMethods = new HashMap<>();
        for (Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
            {
            MethodConstant id = entry.getKey();
            MethodInfo method = entry.getValue();
            if (method.getAccess().isAsAccessibleAs(access) && (id.getNestedDepth() <= 1
                    || isIdentityReachable(method.getIdentity(), method.isVirtual(), access)))
                {
                mapMethods.put(id, method);

                if (method.isVirtual())
                    {
                    mapVirtMethods.put(id.resolveNestedIdentity(pool, f_type), method);
                    }
                }
            }

        return new TypeInfo(typeNew, f_cInvalidations, f_struct, f_cDepth, false,
                f_mapTypeParams, f_aannoClass,
                f_typeExtends, f_typeRebases, f_typeInto,
                f_listProcess, f_listmapClassChain, f_listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, f_progress);
        }

    /**
     * @return the "into" version of this TypeInfo
     */
    public TypeInfo asInto()
        {
        TypeInfo info = m_into;
        if (info == null)
            {
            ConstantPool                        pool         = pool();
            Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
            Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();
            for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
                {
                PropertyConstant id   = entry.getKey();
                PropertyInfo     prop = entry.getValue();

                // convert the property into an "into" property
                prop = prop.asInto();

                mapProps.put(id, prop);
                if (prop.isVirtual())
                    {
                    mapVirtProps.put(id.resolveNestedIdentity(pool, f_type), prop);
                    }
                }

            Map<MethodConstant, MethodInfo> mapMethods     = new HashMap<>();
            Map<Object        , MethodInfo> mapVirtMethods = new HashMap<>();
            for (Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
                {
                MethodConstant id     = entry.getKey();
                MethodInfo     method = entry.getValue();

                // convert the method into an "into" method
                method = method.asInto();

                mapMethods.put(id, method);
                if (method.isVirtual())
                    {
                    mapVirtMethods.put(id.resolveNestedIdentity(pool, f_type), method);
                    }
                }

            info = new TypeInfo(f_type, f_cInvalidations, f_struct, f_cDepth, true,
                    f_mapTypeParams, f_aannoClass,
                    f_typeExtends, f_typeRebases, f_typeInto,
                    f_listProcess, f_listmapClassChain, f_listmapDefaultChain,
                    mapProps, mapMethods, mapVirtProps, mapVirtMethods, f_progress);

            if (f_progress == Progress.Complete)
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
        ConstantPool     pool     = pool();
        IdentityConstant idParent = id.getNamespace();
        while (idParent.isNested())
            {
            if (fVirtual)
                {
                // substitute a sub-class property or method if one is available
                if (idParent instanceof PropertyConstant)
                    {
                    Object       idResolved = idParent.resolveNestedIdentity(pool, f_type);
                    PropertyInfo info       = f_mapVirtProps.get(idResolved);

                    idParent = info.getIdentity();
                    }
                else if (idParent instanceof MethodConstant)
                    {
                    Object     idResolved = idParent.resolveNestedIdentity(pool, f_type);
                    MethodInfo info       = f_mapVirtMethods.get(idResolved);

                    idParent = info.getIdentity();
                    }
                }

            Component component = idParent.getComponent();
            if (component == null)
                {
                // a method cap will not have a real component because it is a fake identity
                if (idParent instanceof MethodConstant)
                    {
                    Object     idResolved = idParent.resolveNestedIdentity(pool, f_type);
                    MethodInfo method     = f_mapVirtMethods.get(idResolved);
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
        Origin originTrue  = f_type.new Origin(true);
        Origin originFalse = f_type.new Origin(false);
        if (composition != Composition.Implements && composition != Composition.Delegates)
            {
            boolean fAnnotation = composition == Composition.Annotation;
            for (Entry<IdentityConstant, Origin> entry : f_listmapClassChain.entrySet())
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
        for (IdentityConstant constId : f_listmapDefaultChain.keySet())
            {
            listmapDefaultChain.putIfAbsent(constId, originTrue);
            }
        }

    /**
     * @return the type that the TypeInfo represents
     */
    public TypeConstant getType()
        {
        return f_type;
        }

    /**
     * @return the invalidation count when this TypeInfo was built
     */
    int getInvalidationCount()
        {
        return f_cInvalidations;
        }

    /**
     * Determine if this TypeInfo is impacted by changes in the TypeInfos built for any of the
     * classes specified by the passed set of IdentityConstants.
     *
     * @param setModified  the set of class IdentityConstants whose TypeInfos may have changed
     *
     * @return true iff this TypeInfo depends on any of the specified IdentityConstants for its
     *         contents, or if the info needs to be rebuilt for another reason
     */
    public boolean needsRebuild(Set<IdentityConstant> setModified)
        {
        if (setModified == null || setModified.isEmpty())
            {
            return false;
            }

        for (Contribution contrib : f_listProcess)
            {
            if (contrib.getTypeConstant().isComposedOfAny(setModified))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return the ClassStructure, or null if none is available; a non-abstract type will always
     *         have a ClassStructure (unless it's a virtual child "projection")
     */
    public ClassStructure getClassStructure()
        {
        return f_struct;
        }

    /**
     * @return the format of the topmost structure that the TypeConstant refers to, or
     *         {@code INTERFACE} for any non-class / non-mixin type (such as a difference type)
     */
    public Format getFormat()
        {
        return f_struct == null ? Format.INTERFACE : f_struct.getFormat();
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
        return f_type.getDefiningConstant() instanceof NativeRebaseConstant;
        }

    /**
     * @return true iff this type is static (a static global type is a singleton; a static local
     *         type does not hold a reference to its parent)
     */
    public boolean isStatic()
        {
        return f_struct != null && f_struct.isStatic();
        }

    /**
     * @return true if this type represents a singleton instance of a class
     */
    public boolean isSingleton()
        {
        return f_struct != null && f_struct.isSingleton();
        }

    /**
     * @return true if this type represents a singleton instance of a class
     */
    public boolean isSynthetic()
        {
        return f_struct != null && f_struct.isSynthetic();
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
        return f_struct == null || f_struct.isTopLevel();
        }

    /**
     * @return true iff this class is a virtual child class, or an anonymous inner class, or some
     *         other inner class such as a named class inside a method
     */
    public boolean isInnerClass()
        {
        return f_struct != null && f_struct.isInnerClass();
        }

    /**
     * @return true iff this class is scoped within another class, such that it requires a parent
     *         reference in order to be instantiated
     */
    public boolean isVirtualChild()
        {
        return f_struct != null && f_struct.isVirtualChild();
        }

    /**
     * @return true iff this class is an anonymous inner class
     */
    public boolean isAnonInnerClass()
        {
        return f_struct != null && f_struct.isAnonInnerClass();
        }

    /**
     * @return true iff this is an inner class with a reference to an "outer this"
     */
    public boolean hasOuter()
        {
        return f_struct != null && f_struct.hasOuter();
        }

    /**
     * @return the type of the "outer this" for any TypeInfo that {@link #hasOuter()}
     */
    public TypeConstant getOuterType()
        {
        assert hasOuter();

        TypeConstant type = getType();
        if (type.isVirtualChild() || type.isAnonymousClass())
            {
            return type.getParentType();
            }
        // REVIEW: it's not clear what type to return: formal, canonical or naked
        return f_struct.getOuter().getIdentityConstant().getType();
        }

    /**
     * Test if this TypeInfo would represent an identifiable reference using the specified name.
     * For example, within a <tt>Map.Entry</tt>, the <tt>Map</tt> reference would be identifiable
     * as "<tt>this.Map</tt>".
     *
     * @param sName  the name to test, as used in the form "this.OuterName"
     *
     * @return true iff this TypeInfo, acting as an "outer" TypeInfo, would identify itself using
     *         the specified name
     */
    public boolean hasOuterName(String sName)
        {
        // everything is an Object, so that doesn't help identify anything in particular
        if (sName.equals("Object"))
            {
            return false;
            }

        for (IdentityConstant id : getClassChain().keySet())
            {
            if (id.getName().equals(sName))
                {
                return true;
                }
            }

        for (IdentityConstant id : getDefaultChain().keySet())
            {
            if (id.getName().equals(sName))
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return the complete set of type parameters declared within the type
     */
    public Map<Object, ParamInfo> getTypeParams()
        {
        return f_mapTypeParams;
        }

    /**
     * @return the type annotations that had an "into" clause of "Class"
     */
    public Annotation[] getClassAnnotations()
        {
        return f_aannoClass;
        }

    /**
     * @return the TypeConstant representing the "native rebase" type
     */
    public TypeConstant getRebases()
        {
        return f_typeRebases;
        }

    /**
     * @return the TypeConstant representing the super class
     */
    public TypeConstant getExtends()
        {
        return f_typeExtends;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getInto()
        {
        return f_typeInto;
        }

    /**
     * @return the list of contributions that made up this TypeInfo
     */
    public List<Contribution> getContributionList()
        {
        return f_listProcess;
        }

    /**
     * @return the potential call chain of classes
     */
    public ListMap<IdentityConstant, Origin> getClassChain()
        {
        return f_listmapClassChain;
        }

    /**
     * @return the potential default call chain of interfaces
     */
    public ListMap<IdentityConstant, Origin> getDefaultChain()
        {
        return f_listmapDefaultChain;
        }

    /**
     * Look up a nested type by its name.
     *
     * @param sName  the name of the child
     *
     * @return the type of the child iff it exists and is visible; null otherwise
     */
    public TypeConstant getChildType(String sName)
        {
        // TODO: if this info represents a virtual child by itself (f_struct == null),
        // there must be a way to confirm the child existence
        return f_struct == null || f_struct.getVirtualChild(sName) == null
                ? null
                : pool().ensureVirtualChildTypeConstant(f_type, sName);
        }

    /**
     * Look up a nested typedef by its name.
     *
     * @param sName  the name of the typedef
     *
     * @return the type of the typedef iff it exists and is visible; null otherwise
     */
    public TypeConstant getTypedefType(String sName)
        {
        // TODO
        return null;
        }

    /**
     * @return all of the properties for this type, indexed by their "flattened" property constant
     */
    public Map<PropertyConstant, PropertyInfo> getProperties()
        {
        return f_mapProps;
        }

    /**
     * @return virtual properties keyed by nested id
     */
    public Map<Object, PropertyInfo> getVirtProperties()
        {
        return f_mapVirtProps;
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
            for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
                {
                PropertyConstant id   = entry.getKey();
                PropertyInfo     prop = entry.getValue();

                // only include the non-nested properties
                if (id.getNestedDepth() == f_cDepth + 1)
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
        int of1 = indexOfClass(f_listmapClassChain, idClass1);
        int of2 = indexOfClass(f_listmapClassChain, idClass2);
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
        of1 = indexOfClass(f_listmapDefaultChain, idClass1);
        of2 = indexOfClass(f_listmapDefaultChain, idClass2);
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
        for (PropertyInfo prop : f_mapProps.values())
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
        for (PropertyInfo prop : f_mapProps.values())
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
     * @param id  the constant that identifies the property
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public PropertyInfo findProperty(PropertyConstant id)
        {
        PropertyInfo prop = f_mapProps.get(id);
        if (prop != null)
            {
            return prop;
            }

        int cDeep = id.getNestedDepth();
        if (cDeep == 1)
            {
            prop = findProperty(id.getName());
            return prop != null && prop.isIdentityValid(id) ? prop : null;
            }

        Object nidThis = id.getNestedIdentity();
        for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
            {
            PropertyConstant idThat = entry.getKey();
            if (idThat.getNestedDepth() == cDeep && nidThis.equals(idThat.getNestedIdentity()))
                {
                prop = entry.getValue();
                if (prop.isIdentityValid(id))
                    {
                    return prop;
                    }
                }
            }

        return null;
        }

    /**
     * Look up the property by its nested identity.
     * <p/>
     * Note: this lookup is not cached since the results are always cached by the caller.
     *
     * @param nid  the id (String | NestedIdentity)
     *
     * @return the PropertyInfo for the specified constant, or null
     */
    public PropertyInfo findPropertyByNid(Object nid)
        {
        if (nid instanceof String)
            {
            return findProperty((String) nid);
            }

        NestedIdentity nidThis = (NestedIdentity) nid;
        for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
            {
            PropertyConstant idThat = entry.getKey();
            if (nidThis.equals(idThat.getNestedIdentity()))
                {
                return entry.getValue();
                }
            }

        return null;
        }

    /**
     * @return all of the non-scoped methods for this type
     */
    public Map<MethodConstant, MethodInfo> getMethods()
        {
        return f_mapMethods;
        }

    /**
     * @return virtual methods keyed by nested id
     */
    public Map<Object, MethodInfo> getVirtMethods()
        {
        return f_mapVirtMethods;
        }

    // TODO this should be the "virt map" from Object to MethodInfo
    /**
     * @return all of the methods for this type that can be identified by just a signature, indexed
     *         by that signature
     */
    public Map<SignatureConstant, MethodInfo> ensureMethodsBySignature()
        {
        Map<SignatureConstant, MethodInfo> map = m_mapMethodsBySignature;

        if (map == null)
            {
            map = new HashMap<>();
            for (Map.Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
                {
                MethodConstant idMethod = entry.getKey();

                // only include the non-nested Methods
                if (idMethod.getNestedDepth() == f_cDepth + 2)
                    {
                    map.put(idMethod.getSignature(), entry.getValue());
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
        Map<SignatureConstant, MethodInfo> mapBySig = m_mapMethodsBySignature;
        if (mapBySig != null)
            {
            MethodInfo method = mapBySig.get(sig);
            if (method != null)
                {
                return method;
                }
            }

        MethodInfo method = f_mapVirtMethods.get(sig);
        if (method != null)
            {
            return method;
            }

        TypeConstant typeThis = getType();

        mapBySig = ensureMethodsBySignature();

        for (MethodInfo methodTest : f_mapMethods.values())
            {
            if (!methodTest.getSignature().getName().equals(sig.getName()))
                {
                continue;
                }

            for (MethodBody body : methodTest.getChain())
                {
                SignatureConstant sigTest;

                sigTest = body.getSignature();
                if (sigTest.equals(sig) || sigTest.isSubstitutableFor(sig, typeThis))
                    {
                    mapBySig.putIfAbsent(sig, methodTest);
                    return methodTest;
                    }

                sigTest = body.getIdentity().getSignature();
                if (sigTest.equals(sig) || sigTest.isSubstitutableFor(sig, typeThis))
                    {
                    mapBySig.putIfAbsent(sig, methodTest);
                    return methodTest;
                    }

                sigTest = resolveMethodConstant(body.getIdentity(), methodTest).getSignature();
                if (sigTest.equals(sig) || sigTest.isSubstitutableFor(sig, typeThis))
                    {
                    mapBySig.putIfAbsent(sig, methodTest);
                    return methodTest;
                    }
                }
            }

        // TODO: cache the miss
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
        MethodInfo method = f_cacheById.get(id);
        if (method != null)
            {
            return method;
            }

        // try to find a method with the same signature
        method = f_mapVirtMethods.get(id.resolveNestedIdentity(pool(), f_type));
        if (method != null)
            {
            for (MethodBody body : method.getChain())
                {
                if (body.getIdentity().equals(id))
                    {
                    f_cacheById.put(id, method);
                    f_cacheByNid.put(id.getNestedIdentity(), method);
                    return method;
                    }
                }
            }

        // it is possible that the map lookup miss is caused by the passed id's signature NOT
        // having its generic types resolved, so brute-force search
        // TODO this might no longer be necessary because the cache is now pre-populated
        String sName  = id.getName();
        int    cDepth = id.getNestedDepth();
        for (MethodInfo methodTest : f_mapVirtMethods.values())
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
                        f_cacheById.put(id, methodTest);
                        f_cacheByNid.put(id.getNestedIdentity(), methodTest);
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
     *             or {@link IdentityConstant#resolveNestedIdentity(ConstantPool, GenericTypeResolver)}
     *
     * @return the specified MethodInfo, or null if no MethodInfo could be found by the provided
     *         nested identity
     */
    public MethodInfo getMethodByNestedId(Object nid)
        {
        MethodInfo info = f_cacheByNid.get(nid);
        if (info == null)
            {
            throw new IllegalStateException("TODO: couldn't find " + nid + " at " + f_type);
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
                    if (entry.getValue().isCapped())
                        {
                        // the current entry represents a "capped" method; ignore it
                        continue;
                        }

                    if (mapMatch == null)
                        {
                        if (entryBest.getValue().isCapped())
                            {
                            // the best entry represents a "capped" method; replace it
                            entryBest = entry;
                            continue;
                            }

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
            ConstantPool pool = pool();
            for (Contribution contrib : f_listProcess)
                {
                TypeConstant typeThis = contrib.getTypeConstant();
                ClassConstant clzThis = (ClassConstant) typeThis.getDefiningConstant();
                if (clzThis instanceof NativeRebaseConstant)
                    {
                    clzThis = ((NativeRebaseConstant) clzThis).getClassConstant();
                    }

                for (Map.Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
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
            ConstantPool pool = pool();
            for (Contribution contrib : f_listProcess)
                {
                TypeConstant typeThis = contrib.getTypeConstant();
                ClassConstant clzThis = (ClassConstant) typeThis.getDefiningConstant();
                if (clzThis instanceof NativeRebaseConstant)
                    {
                    clzThis = ((NativeRebaseConstant) clzThis).getClassConstant();
                    }

                for (Map.Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
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
            !typeType.getUnderlyingType().equals(pool.typeType()))
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
        for (MethodConstant method : f_mapMethods.keySet())
            {
            if (method.getNestedDepth() == 2 && method.getName().equals(sName))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * See if any method has the specified name.
     *
     * @param idProp  the property to look inside of
     * @param sName   a method name
     *
     * @return true if the property contains at least one method (or function) by the specified name
     */
    public boolean propertyContainsMultiMethod(PropertyConstant idProp, String sName)
        {
        int cReqDepth = idProp.getNestedDepth() + 2;
        for (MethodConstant method : f_mapMethods.keySet())
            {
            if (method.getNestedDepth() == cReqDepth && method.getName().equals(sName)
                    && method.getNamespace().getNestedIdentity().equals(idProp.getNestedIdentity()))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * See if any method has the specified name.
     * REVIEW this really doesn't need to be its own method; we could combine it with the above method
     *
     * @param idMethod  the method to look inside of
     * @param sName     a method name
     *
     * @return true if the method contains at least one method (or function) by the specified name
     */
    public boolean methodContainsMultiMethod(MethodConstant idMethod, String sName)
        {
        int cReqDepth = idMethod.getNestedDepth() + 2;
        for (MethodConstant method : f_mapMethods.keySet())
            {
            if (method.getNestedDepth() == cReqDepth && method.getName().equals(sName)
                    && method.getNamespace().getNestedIdentity().equals(idMethod.getNestedIdentity()))
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
        Map<String, Set<MethodConstant>> mapOps = m_mapOps;
        if (mapOps == null)
            {
            m_mapOps = mapOps = new HashMap<>();
            }

        String sKey = String.valueOf(sName) + sOp + cParams;
        Set<MethodConstant> setOps = mapOps.get(sKey);
        if (setOps == null)
            {
            for (MethodInfo method : getOpMethodInfos())
                {
                if (method.isOp(sName, sOp, cParams))
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(resolveMethodConstant(method));
                    }
                }

            // cache the result
            if (setOps == null)
                {
                setOps = Collections.EMPTY_SET;
                }
            mapOps.put(sKey, setOps);
            }

        return setOps;
        }

    /**
     * @return resolved method constant, which may be synthetic (not pointing to a structure)
     */
    protected MethodConstant resolveMethodConstant(MethodInfo method)
        {
        return resolveMethodConstant(method.getIdentity(), method);
        }

    /**
     * @return resolved method constant, which may be synthetic (not pointing to a structure)
     */
    protected MethodConstant resolveMethodConstant(MethodConstant idMethod, MethodInfo method)
        {
        ConstantPool      pool        = pool();
        SignatureConstant sigOrig     = idMethod.getSignature();
        SignatureConstant sigResolved = sigOrig.resolveGenericTypes(pool, f_type)
                                               .resolveAutoNarrowing(pool, f_type);
        if (!sigResolved.equals(sigOrig))
            {
            idMethod = pool.ensureMethodConstant(idMethod.getNamespace(), sigResolved);
            f_cacheById.putIfAbsent(idMethod, method);
            }
        return idMethod;
        }

    /**
     * Obtain all of the matching methods for the specified name and the number of parameters.
     *
     * @param sName       the method name
     * @param cParams     the number of parameters (-1 for any)
     * @param methodType  the category of methods to consider
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> findMethods(String sName, int cParams, MethodType methodType)
        {
        Map<String, Set<MethodConstant>> mapMethods = m_mapMethodsByName;
        if (mapMethods == null)
            {
            m_mapMethodsByName = mapMethods = new HashMap<>();
            }

        String sKey = cParams == 0 ? sName : sName + ';' + cParams;
        if (methodType != MethodType.Method)
            {
            sKey += methodType.key;
            }

        Set<MethodConstant> setMethods = mapMethods.get(sKey);
        if (setMethods == null)
            {
            if (cParams == -1)
                {
                // any number of parameters goes
                cParams = Integer.MAX_VALUE;
                }
            for (Map.Entry<SignatureConstant, MethodInfo> entry : ensureMethodsBySignature().entrySet())
                {
                SignatureConstant sig = entry.getKey();

                if (sig.getName().equals(sName))
                    {
                    MethodInfo      info   = entry.getValue();
                    MethodStructure method = info.getTopmostMethodStructure(this);

                    if (!methodType.matches(method))
                        {
                        continue;
                        }

                    if (info.isCapped())
                        {
                        // ignore "capped" methods
                        continue;
                        }

                    int cAllParams  = sig.getParamCount();
                    int cTypeParams = method.getTypeParamCount();
                    int cDefaults   = method.getDefaultParamCount();
                    int cRequired   = cAllParams - cTypeParams - cDefaults;

                    if (cParams >= cRequired)
                        {
                        if (setMethods == null)
                            {
                            setMethods = new HashSet<>(7);
                            }
                        setMethods.add(resolveMethodConstant(info));
                        }
                    }
                }

            // cache the result
            if (setMethods == null)
                {
                setMethods = Collections.EMPTY_SET;
                }
            mapMethods.put(sKey, setMethods);
            }
        return setMethods;
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
        return f_type.getConstantPool();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("TypeInfo: ")
          .append(f_type)
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

        if (!f_mapTypeParams.isEmpty())
            {
            sb.append("\n- Parameters (")
              .append(f_mapTypeParams.size())
              .append(')');
            int i = 0;
            for (Entry<Object, ParamInfo> entry : f_mapTypeParams.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (f_typeInto != null)
            {
            sb.append("\n- Into: ")
              .append(f_typeInto.getValueString());
            }
        if (f_typeRebases != null)
            {
            sb.append("\n- Rebases: ")
              .append(f_typeRebases.getValueString());
            }
        if (f_typeExtends != null)
            {
            sb.append("\n- Extends: ")
              .append(f_typeExtends.getValueString());
            }

        if (!f_listmapClassChain.isEmpty())
            {
            sb.append("\n- Class Chain (")
              .append(f_listmapClassChain.size())
              .append(')');
            int i = 0;
            for (Entry<IdentityConstant, Origin> entry : f_listmapClassChain.entrySet())
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

        if (!f_listmapDefaultChain.isEmpty())
            {
            sb.append("\n- Default Chain (")
              .append(f_listmapDefaultChain.size())
              .append(')');
            int i = 0;
            for (IdentityConstant constId : f_listmapDefaultChain.keySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ")
                  .append(constId.getValueString());
                }
            }

        if (!f_mapProps.isEmpty())
            {
            sb.append("\n- Properties (")
              .append(f_mapProps.size())
              .append(')');
            int i = 0;
            for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ");
                if (f_mapVirtProps.containsKey(entry.getKey().resolveNestedIdentity(pool(), f_type)))
                    {
                    sb.append("(v) ");
                    }
                sb.append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
                }
            }

        if (!f_mapMethods.isEmpty())
            {
            sb.append("\n- Methods (")
              .append(f_mapMethods.size())
              .append(')');
            int i = 0;
            for (Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
                {
                sb.append("\n  [")
                  .append(i++)
                  .append("] ");
                if (f_mapVirtMethods.containsKey(entry.getKey().resolveNestedIdentity(pool(),
                        f_type)))
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
        return f_progress;
        }

    boolean isPlaceHolder()
        {
        return f_progress == Progress.Building;
        }

    boolean isIncomplete()
        {
        return f_progress == Progress.Incomplete;
        }

    boolean isComplete()
        {
        return f_progress == Progress.Complete;
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


    // ----- fields --------------------------------------------------------------------------------

    public enum Progress
        {
        Absent, Building, Incomplete, Complete;

        public Progress worstOf(Progress that)
            {
            return this.ordinal() < that.ordinal() ? that : this;
            }
        }

    public enum MethodType
        {
        Constructor("c"), Method("m"), Function("f"), Either("mf");

        MethodType(String key)
            {
            this.key = key;
            }

        public final String key;

        public boolean matches(MethodStructure method)
            {
            if (method.isConstructor())
                {
                return this == Constructor;
                }

            if (this == Either)
                {
                return true;
                }

            return method.isFunction() == (this == Function);
            }
        }

    /**
     * Represents the completeness of the TypeInfo.
     */
    private final Progress f_progress;

    /**
     * The data type that this TypeInfo represents.
     */
    private final TypeConstant f_type;

    /**
     * The version (invalidation count) of the constant pool that contains the data type that this
     * TypeInfo represents, at the point in time that this TypeInfo was created.
     */
    private final int f_cInvalidations;

    /**
     * The ClassStructure of the type, if the type is based on a ClassStructure.
     */
    private final ClassStructure f_struct;

    /**
     * The "depth from class" for this TypeInfo. A TypeInfo for an actual class will have a depth of
     * {@code 0}.
     */
    private final int f_cDepth;

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
    private final Map<Object, ParamInfo> f_mapTypeParams;

    /**
     * The class annotations.
     */
    private final Annotation[] f_aannoClass;

    /**
     * The type that is extended. The term "extends" has slightly different meanings for mixins and
     * other classes.
     */
    private final TypeConstant f_typeExtends;

    /**
     * The type that is rebased onto.
     */
    private final TypeConstant f_typeRebases;

    /**
     * For mixins, the type that is mixed into. For interfaces, this is always Object.
     */
    private final TypeConstant f_typeInto;

    /**
     * The list of contributions that made up this TypeInfo.
     */
    private final List<Contribution> f_listProcess;

    /**
     * The potential call chain of classes.
     */
    private final ListMap<IdentityConstant, Origin> f_listmapClassChain;

    /**
     * The potential default call chain of interfaces.
     */
    private final ListMap<IdentityConstant, Origin> f_listmapDefaultChain;

    /**
     * The properties of this type, indexed by PropertyConstant. Constants, private properties, and
     * properties declared within methods, are identified only by a single (non-virtual) property
     * constant. Other properties can show up at multiple virtual levels, and thus the same property
     * may be referred to by different PropertyConstants, although it will only show up once in this
     * map (using the identity from the highest "pane of glass" that the property shows up on.)
     */
    private final Map<PropertyConstant, PropertyInfo> f_mapProps;

    /**
     * The properties of the type, keyed by nested identity. Properties nested immediately under
     * a class are identified by their (String) name, while properties nested further below the
     * class are identified by a NestedIdentity object. In either case, the key can be obtained by
     * calling {@link PropertyConstant#getNestedIdentity()} or
     * {@link IdentityConstant#resolveNestedIdentity(ConstantPool, GenericTypeResolver)}.
     */
    private final Map<Object, PropertyInfo> f_mapVirtProps;

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
    private final Map<MethodConstant, MethodInfo> f_mapMethods;

    /**
     * The virtual methods of the type, keyed by nested identity. Methods nested immediately under
     * a class are identified by their signature, while methods nested further below the class are
     * identified by a NestedIdentity object. In either case, the key can be obtained by calling
     * calling {@link PropertyConstant#getNestedIdentity()} or
     * {@link IdentityConstant#resolveNestedIdentity(ConstantPool, GenericTypeResolver)}.
     */
    private final Map<Object, MethodInfo> f_mapVirtMethods;

    /**
     * The methods of the type, indexed by signature. This will not include nested methods, such
     * as those nested within a property or method. Lazily initialized
     */
    private transient Map<SignatureConstant, MethodInfo> m_mapMethodsBySignature;

    /**
     * Cached "equals" function.
     */
    private transient MethodStructure m_functionEquals;

    /**
     * Cached "compare" function.
     */
    private transient MethodStructure m_functionCompare;

    // cached query results REVIEW for thread safety
    // REVIEW is this a reasonable way to cache these?
    private final Map<MethodConstant, MethodInfo> f_cacheById;
    private final Map<Object, MethodInfo>         f_cacheByNid;

    private transient TypeInfo                         m_into;
    private transient Set<MethodInfo>                  m_setAuto;
    private transient Set<MethodInfo>                  m_setOps;
    private transient TypeConstant                     m_typeAuto;
    private transient MethodConstant                   m_methodAuto;
    private transient Map<String, Set<MethodConstant>> m_mapOps;
    private transient Map<String, Set<MethodConstant>> m_mapMethodsByName;
    }
