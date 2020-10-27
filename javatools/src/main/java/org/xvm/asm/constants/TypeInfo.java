package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

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
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

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
     * @param aannoMixin           the mixin annotations (not the incorporate) for the type
     * @param typeExtends          the type that is extended
     * @param typeRebases          the type that is rebased onto
     * @param typeInto             for mixins, the type that is mixed into; for interfaces, Object
     * @param listProcess          the contribution list
     * @param listmapClassChain    the potential call chain of classes
     * @param listmapDefaultChain  the potential call chain of default implementations
     * @param mapProps             the properties of the type
     * @param mapMethods           the methods of the type
     * @param mapVirtProps         the virtual properties of the type, keyed by nested id
     * @param mapVirtMethods       the virtual methods of the type, keyed by nested id
     * @param mapChildren          the child types of the type, keyed by name (also by prop.name)
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
            Annotation[]                        aannoMixin,
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
            ListMap<String, ChildInfo>          mapChildren,
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
        f_aannoMixin          = validateAnnotations(aannoMixin);
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
        f_mapChildren         = mapChildren;
        f_progress            = progress;

        // pre-populate the method lookup caches
        // and determine if this type is implicitly abstract
        f_cacheById  = new ConcurrentHashMap<>(mapMethods);
        f_cacheByNid = new ConcurrentHashMap<>(mapVirtMethods);

        boolean fExplicitAbstract = fSynthetic || !isClass() || struct.isExplicitlyAbstract() ||
                TypeInfo.containsAnnotation(aannoClass, "Abstract");

        boolean fImplicitAbstract = false;
        boolean fMissingConstruct = false;
        for (Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodInfo info = entry.getValue();

            info.populateCache(entry.getKey(), f_cacheById, f_cacheByNid);

            if (info.isVirtualConstructor())
                {
                // constructors must come from "this" structure; otherwise they are un-implemented
                // virtual constructors
                fMissingConstruct |= info.isVirtualConstructorImplemented(this);
                }
            else
                {
                // unfortunately, there is no "||=" operator in Java
                fImplicitAbstract = fImplicitAbstract || info.isAbstract();
                }
            }

        fImplicitAbstract = fImplicitAbstract ||
            mapProps.values().stream().anyMatch(PropertyInfo::isExplicitlyAbstract);

        m_fExplicitAbstract = fExplicitAbstract;
        m_fImplicitAbstract = fImplicitAbstract;
        m_fMissingConstruct = fMissingConstruct;

        assert cInvalidations == 0 // necessary for TYPEINFO_PLACEHOLDER construction
            || cInvalidations <= type.getConstantPool().getInvalidationCount();
        }

    /**
     * Construct a TypeInfo for a formal type parameter.
     *
     * @param typeFormal      the TypeConstant of the formal type
     * @param infoConstraint  the TypeInfo for the constraining type
     * @param cInvalidations  the count of TypeInfo invalidations when this TypeInfo was built
     */
    public TypeInfo(TypeConstant typeFormal, TypeInfo infoConstraint, int cInvalidations)
        {
        assert infoConstraint != null;
        assert infoConstraint.f_progress == Progress.Complete;
        assert typeFormal != null && typeFormal.isFormalType();

        f_type                = typeFormal;
        f_cInvalidations      = cInvalidations;
        f_struct              = infoConstraint.f_struct;
        f_cDepth              = infoConstraint.f_cDepth;
        f_mapTypeParams       = Collections.EMPTY_MAP;
        f_aannoClass          = Annotation.NO_ANNOTATIONS;
        f_aannoMixin          = Annotation.NO_ANNOTATIONS;
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
        f_mapChildren         = infoConstraint.f_mapChildren;
        f_progress            = Progress.Complete;

        f_cacheById  = infoConstraint.f_cacheById;
        f_cacheByNid = infoConstraint.f_cacheByNid;

        m_fExplicitAbstract = true;
        m_fImplicitAbstract = infoConstraint.m_fImplicitAbstract;
        m_fMissingConstruct = false;

        assert cInvalidations <= typeFormal.getConstantPool().getInvalidationCount();
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
                        mapVirtProps.put(id.resolveNestedIdentity(pool, null), prop);
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
                    mapVirtMethods.put(id.resolveNestedIdentity(pool, null), method);
                    }
                }
            }

        ListMap<String, ChildInfo> mapChildren = new ListMap<>(f_mapChildren.size());
        for (Entry<String, ChildInfo> entry : f_mapChildren.entrySet())
            {
            String    sName = entry.getKey();
            ChildInfo child = entry.getValue();
            // note that the child is null if there has been a name collision that prevents the
            // name from being used at this level
            if (child == null || child.getAccess().isAsAccessibleAs(access))
                {
                mapChildren.put(sName, child);
                }
            }

        TypeConstant typeExtends = limitAccess(f_typeExtends, access);
        TypeConstant typeRebases = limitAccess(f_typeRebases, access);
        TypeConstant typeInto    = limitAccess(f_typeInto,    access);

        return new TypeInfo(typeNew, f_cInvalidations, f_struct, f_cDepth, false,
                f_mapTypeParams, f_aannoClass, f_aannoMixin,
                typeExtends, typeRebases, typeInto,
                f_listProcess, f_listmapClassChain, f_listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, mapChildren, f_progress);
        }

    /**
     * @return a TypeConstant that represents a more limited type for the specified type
     */
    private TypeConstant limitAccess(TypeConstant type, Access access)
        {
        return type == null || type.getAccess() == Access.PUBLIC
                ? type
                : pool().ensureAccessTypeConstant(type, access);
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
                    mapVirtProps.put(id.resolveNestedIdentity(pool, null), prop);
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
                    mapVirtMethods.put(id.resolveNestedIdentity(pool, null), method);
                    }
                }

            info = new TypeInfo(f_type, f_cInvalidations, f_struct, f_cDepth, true,
                    f_mapTypeParams, f_aannoClass, f_aannoMixin,
                    f_typeExtends, f_typeRebases, f_typeInto,
                    f_listProcess, f_listmapClassChain, f_listmapDefaultChain,
                    mapProps, mapMethods, mapVirtProps, mapVirtMethods, f_mapChildren, f_progress);

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
     * @return the "delegates" version of this TypeInfo
     */
    public TypeInfo asDelegates()
        {
        TypeInfo info = m_delegates;
        if (info == null)
            {
            ConstantPool                        pool         = pool();
            Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
            Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();
            for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
                {
                PropertyConstant id   = entry.getKey();
                PropertyInfo     prop = entry.getValue();

                // skip non-virtual properties
                if (prop.isVirtual())
                    {
                    mapProps.put(id, prop);
                    mapVirtProps.put(id.resolveNestedIdentity(pool, null), prop);
                    }
                }

            Map<MethodConstant, MethodInfo> mapMethods     = new HashMap<>();
            Map<Object        , MethodInfo> mapVirtMethods = new HashMap<>();
            for (Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
                {
                MethodConstant id     = entry.getKey();
                MethodInfo     method = entry.getValue();

                // skip non-virtual methods
                if (method.isVirtual())
                    {
                    mapMethods.put(id, method);
                    mapVirtMethods.put(id.resolveNestedIdentity(pool, null), method);
                    }
                }

            info = new TypeInfo(f_type, f_cInvalidations, f_struct, f_cDepth, true,
                    f_mapTypeParams, f_aannoClass, f_aannoMixin,
                    f_typeExtends, f_typeRebases, f_typeInto,
                    f_listProcess, f_listmapClassChain, f_listmapDefaultChain,
                    mapProps, mapMethods, mapVirtProps, mapVirtMethods, f_mapChildren, f_progress);

            if (f_progress == Progress.Complete)
                {
                // cache the result
                m_delegates = info;

                // cache the result on the result itself, so it doesn't have to build its own "into"
                info.m_delegates = info;
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
                    if (info == null)
                        {
                        // the parent property is not visible, therefore the child is not reachable
                        return false;
                        }
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
     * @return an identity of ths structure this info represents
     */
    public IdentityConstant getIdentity()
        {
        // this info may represent a property
        return f_type instanceof PropertyClassTypeConstant
            ? ((PropertyClassTypeConstant) f_type).getProperty()
            : f_struct.getIdentityConstant();
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
        return m_fImplicitAbstract || m_fExplicitAbstract || m_fMissingConstruct;
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
     * @return the mixin annotations (not incorporation)
     */
    public Annotation[] getMixinAnnotations()
        {
        return f_aannoMixin;
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
     * Calculate a child type for a given name.
     *
     * @param sName  the name of the child
     *
     * @return the type of the typedef, a virtual child or null if neither exists
     */
    public TypeConstant calculateChildType(ConstantPool pool, String sName)
        {
        ChildInfo childinfo = f_mapChildren.get(sName);
        if (childinfo == null)
            {
            return null;
            }

        // check if the child is a typedef
        Component child = childinfo.getComponent();
        if (child instanceof TypedefStructure)
            {
            // resolve the typedef in the context of the referring type
            TypeConstant typeTypedef = ((TypedefConstant) child.getIdentityConstant()).getReferredToType();
            return typeTypedef.resolveGenerics(pool, f_type);
            }

        // otherwise it must be a class
        ClassStructure clz = (ClassStructure) child;
        return clz.isVirtualChild()
                ? pool.ensureVirtualChildTypeConstant(f_type, sName)
                : clz.getIdentityConstant().getType();
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
                if (id.getNestedDepth() == 1)
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

        if (prop1 == prop2)
            {
            return prop1;
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
     * Obtain all of the properties declared within the specified method.
     *
     * @param idMethod  the identity of the method that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public synchronized Map<String, PropertyInfo> ensureNestedPropertiesByName(MethodConstant idMethod)
        {
        // since a property inside of the method cannot be virtual, we can do
        // a quick check to eliminate the full scan below for 99.9% of scenarios
        MethodStructure method = (MethodStructure) idMethod.getComponent();
        if (method != null && !method.hasChildren())
            {
            return Collections.EMPTY_MAP;
            }

        Map<IdentityConstant, Map<String, PropertyInfo>> mapProps = m_mapNestedProperties;
        if (mapProps == null)
            {
            m_mapNestedProperties = mapProps = new HashMap<>();
            }

        Map<String, PropertyInfo> map = mapProps.get(idMethod);
        if (map == null)
            {
            for (PropertyInfo prop : f_mapProps.values())
                {
                // only include the properties nested under the specified method
                if (prop.getParent().equals(idMethod))
                    {
                    if (map == null)
                        {
                        map = new HashMap<>();
                        }
                    map.put(prop.getName(), prop);
                    }
                }
            if (map == null)
                {
                map = Collections.EMPTY_MAP;
                }
            mapProps.put(idMethod, map);
            }

        return map;
        }

    /**
     * Obtain all of the properties declared within the specified property.
     * REVIEW this implementation is probably insufficient, considering possible visibility rules
     *
     * @param idProp  the identity of the property that may contain properties
     *
     * @return a map from property name to PropertyInfo
     */
    public synchronized Map<String, PropertyInfo> ensureNestedPropertiesByName(PropertyConstant idProp)
        {
        Map<IdentityConstant, Map<String, PropertyInfo>> mapProps = m_mapNestedProperties;
        if (mapProps == null)
            {
            m_mapNestedProperties = mapProps = new HashMap<>();
            }

        Map<String, PropertyInfo> map = mapProps.get(idProp);
        if (map == null)
            {
            for (Map.Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
                {
                PropertyConstant idTest = entry.getKey();
                PropertyInfo     prop   = entry.getValue();

                // only include the properties nested under the specified property
                if (idTest.getParentConstant().equals(idProp))
                    {
                    if (map == null)
                        {
                        map = new HashMap<>();
                        }
                    map.put(prop.getName(), prop);
                    }
                }
            if (map == null)
                {
                map = Collections.EMPTY_MAP;
                }
            mapProps.put(idProp, map);
            }
        return map;
        }


    /**
     * Look up any of the following (in that order):
     * <ol>
     *   <li>a property;</li>
     *   <li>a method;</li>
     *   <li>a child class;</li>
     * </ol>
     * Note: if more than one method with the specified name exists, a MultiMethodConstant is
     *       returned.
     *
     * @param pool   the ConstantPool to use
     * @param sName  the name to look for
     *
     * @return an IdentityConstant representing a component of that name or null if none found
     */
    public IdentityConstant findName(ConstantPool pool, String sName)
        {
        PropertyInfo prop = findProperty(sName);
        if (prop != null)
            {
            return prop.getIdentity();
            }

        // not a property; try a method
        Set<MethodConstant> setMethod = findMethods(sName, -1, MethodKind.Any);
        switch (setMethod.size())
            {
            case 0:
                break;

            case 1:
                return setMethod.iterator().next();

            default:
                return setMethod.iterator().next().getParentConstant();
            }

        // not a method; try a child class
        TypeConstant typeChild = calculateChildType(pool, sName);
        return typeChild != null && typeChild.isSingleDefiningConstant()
                ? typeChild.getSingleUnderlyingClass(true)
                : null;
        }

    /**
     * Look up the property by its name.
     *
     * @param sName  the property name
     *
     * @return the PropertyInfo for the specified constant, or null
     */
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
        PropertyInfo infoProp = f_mapProps.get(id);
        if (infoProp != null)
            {
            return infoProp;
            }

        IdentityConstant idParent = id.getClassIdentity();
        if (!idParent.equals(getIdentity()))
            {
            PropertyStructure prop = (PropertyStructure) id.getComponent();
            if (prop != null && prop.getAccess() == Access.PRIVATE)
                {
                // drill down to the TypeInfo for the corresponding class in the hierarchy
                // that has this non-virtual private property
                Origin origin = f_listmapClassChain.get(idParent);
                if (origin == null)
                    {
                    return null;
                    }

                TypeConstant typeParent = origin.getType();
                TypeInfo     infoParent = pool().ensureAccessTypeConstant(
                                            typeParent, Access.PRIVATE).ensureTypeInfo();
                return infoParent.findProperty(id);
                }
            }

        int cDeep = id.getNestedDepth();
        if (cDeep == 1)
            {
            infoProp = findProperty(id.getName());
            return infoProp != null && infoProp.isIdentityValid(id) ? infoProp : null;
            }

        Object nidThis = id.getNestedIdentity();
        for (Entry<PropertyConstant, PropertyInfo> entry : f_mapProps.entrySet())
            {
            PropertyConstant idThat = entry.getKey();
            if (idThat.getNestedDepth() == cDeep && nidThis.equals(idThat.getNestedIdentity()))
                {
                infoProp = entry.getValue();
                if (infoProp.isIdentityValid(id))
                    {
                    return infoProp;
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
                if (idMethod.getNestedDepth() == 2)
                    {
                    map.put(idMethod.getSignature(), entry.getValue());
                    }
                }

            m_mapMethodsBySignature = map;
            }

        return map;
        }

    /**
     * Find the MethodInfo for the specified SignatureConstant. If possible, find
     * a non-capped method; return a capped one *only* if nothing else matches.
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

        MethodInfo methodCapped = null;
        for (Map.Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
            {
            MethodConstant idTest     = entry.getKey();
            MethodInfo     methodTest = entry.getValue();

            if (!methodTest.getSignature().getName().equals(sig.getName()))
                {
                continue;
                }

            // only include non-nested methods
            if (idTest.getNestedDepth() != 2)
                {
                continue;
                }

            for (MethodBody body : methodTest.getChain())
                {
                // test the actual body signature
                SignatureConstant sigTest0 = body.getSignature();
                if (sigTest0.equals(sig) || sigTest0.isSubstitutableFor(sig, typeThis))
                    {
                    mapBySig.putIfAbsent(sig, methodTest);
                    if (methodTest.isCapped())
                        {
                        methodCapped = methodTest;
                        break;
                        }
                    return methodTest;
                    }

                // test the resolved identity signature
                SignatureConstant sigTest1 = resolveMethodConstant(body.getIdentity(), methodTest).getSignature();
                if (sigTest1.equals(sig) || sigTest1.isSubstitutableFor(sig, typeThis))
                    {
                    mapBySig.putIfAbsent(sig, methodTest);
                    if (methodTest.isCapped())
                        {
                        methodCapped = methodTest;
                        break;
                        }
                    return methodTest;
                    }

                // test the canonical identity signature
                SignatureConstant sigTest2 = body.getIdentity().getSignature();
                if (sigTest2.containsGenericTypes())
                    {
                    sigTest2 = sigTest2.resolveGenericTypes(pool(), getCanonicalResolver());

                    if (sigTest2.equals(sig) || sigTest2.isSubstitutableFor(sig, typeThis))
                        {
                        mapBySig.putIfAbsent(sig, methodTest);
                        if (methodTest.isCapped())
                            {
                            methodCapped = methodTest;
                            break;
                            }
                        return methodTest;
                        }
                    }
                }
            }

        if (methodCapped != null)
            {
            return methodCapped;
            }

        // check well-known native methods
        if (getType().isA(pool().typeFunction()))
            {
            if (sig.getName().equals("invoke"))
                {
                Set<MethodConstant> set = findMethods("invoke", 1, MethodKind.Method);
                assert set.size() == 1;
                method = getMethodById(set.iterator().next());
                mapBySig.putIfAbsent(sig, method);
                return method;
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
        MethodInfo infoMethod = f_cacheById.get(id);
        if (infoMethod != null)
            {
            return infoMethod;
            }

        // try to find a method with the same signature
        infoMethod = getMethodByNestedId(id.resolveNestedIdentity(pool(), f_type));
        if (infoMethod != null)
            {
            f_cacheById.put(id, infoMethod);
            }

        return infoMethod;
        }

    /**
     * Find the MethodInfo for the specified nested identity.
     *
     * @param nid  a nested identity, as obtained from {@link MethodConstant#getNestedIdentity}
     *             or {@link IdentityConstant#resolveNestedIdentity}
     *
     * @return the specified MethodInfo, or null if no MethodInfo could be found by the provided
     *         nested identity
     */
    public MethodInfo getMethodByNestedId(Object nid)
        {
        MethodInfo info = f_cacheByNid.get(nid);
        if (info != null)
            {
            return info;
            }

        if (nid instanceof SignatureConstant)
            {
            info = getMethodBySignature((SignatureConstant) nid);
            if (info != null)
                {
                f_cacheByNid.put(nid, info);
                return info;
                }
            }
        else
            {
            IdentityConstant id = ((NestedIdentity) nid).getIdentityConstant();
            for (MethodInfo infoTest : f_mapMethods.values())
                {
                if (infoTest.getIdentity().equals(id))
                    {
                    f_cacheByNid.put(nid, infoTest);
                    return infoTest;
                    }
                }
            }
        return null;
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
                : prop.ensureOptimizedGetChain(this, null);
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
                : prop.ensureOptimizedSetChain(this, null);
        }

    /**
     * Find a named method or function that best matches the specified requirements.
     *
     * @param sName       the name of the method or function
     * @param fMethod     true to include methods in the search
     * @param fFunction   true to include functions in the search
     * @param aRedundant  an optional array of redundant return type information (helps to clarify
     *                    which method or function to select)
     * @param aArgs       an optional array of the types of the arguments being provided (some of
     *                    which may be null to indicate "unknown" in a pre-validation stage, or
     *                    "non-binding unknown")
     *
     * @return the id of a matching method or function (null if none found)
     */
    public MethodConstant findCallable(String sName, boolean fMethod, boolean fFunction,
                                       TypeConstant[] aRedundant, TypeConstant[] aArgs)
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
            // 3) having at least as many parameters as the number of provided arguments, and no
            //    more required parameters than the number of provided arguments;
            // 4) having each argument from steps (3) and (4) be isA() or @Auto convertible to the
            //    type of each corresponding parameter; and
            // 5) matching (i.e. isA()) any specified redundant return types
            MethodConstant id   = entry.getKey();
            MethodInfo     info = entry.getValue();
            if (id.getNestedDepth() == 2
                    && id.getName().equals(sName)
                    && id.getRawParams() .length >= cArgs
                    && id.getRawReturns().length >= cRedundant
                    && (info.isConstructor()
                            ? (!fMethod && !fFunction)
                            : info.isFunction() ? fFunction : fMethod))
                {
                SignatureConstant sig      = info.getSignature();
                TypeConstant[]    aParams  = sig.getRawParams();
                TypeConstant[]    aReturns = sig.getRawReturns();
                for (int i = 0; i < cRedundant; ++i)
                    {
                    TypeConstant typeReturn    = aReturns  [i];
                    TypeConstant typeRedundant = aRedundant[i];

                    if (!typeReturn.isA(typeRedundant))
                        {
                        continue NextMethod;
                        }
                    }
                for (int i = 0; i < cArgs; ++i)
                    {
                    TypeConstant typeParam = aParams[i];
                    TypeConstant typeArg   = aArgs  [i];
                    if (typeArg != null && !typeArg.isAssignableTo(typeParam))
                        {
                        continue NextMethod;
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
     * @param aArgs  the types of the arguments being provided (some of which may be null to
     *               indicate "unknown" in a pre-validation stage, or "non-binding unknown")
     *
     * @return the matching constructor id (null if none found)
     */
    public MethodConstant findConstructor(TypeConstant[] aArgs)
        {
        return findCallable("construct", false, false, TypeConstant.NO_TYPES, aArgs);
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
     * Check if there is any method with the specified name inside of the container
     * property or method.
     *
     * @param idContainer  the id of the property or method to look inside of
     * @param sName        a method name to look for
     *
     * @return true if the property contains at least one method (or function) by the specified name
     */
    public boolean containsNestedMultiMethod(IdentityConstant idContainer, String sName)
        {
        if (idContainer instanceof MethodConstant)
            {
            // since a method inside of the method cannot be virtual, we can do
            // a quick check to eliminate the full scan below for 99.9% of scenarios
            MethodStructure method = (MethodStructure) idContainer.getComponent();
            if (method != null && method.getChild(sName) == null)
                {
                return false;
                }
            }

        return !findNestedMethods(idContainer, sName, -1).isEmpty();
        }

    /**
     * Obtain all of the methods that are annotated with "@Op".
     *
     * @return a set of zero or more method constants
     */
    public synchronized Set<MethodInfo> getOpMethodInfos()
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
    public synchronized Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams)
        {
        Map<String, Set<MethodConstant>> mapOps = m_mapOps;
        if (mapOps == null)
            {
            m_mapOps = mapOps = new HashMap<>();
            }

        String sKey = sName + sOp + cParams;
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
        SignatureConstant sigResolved = method.getSignature().resolveAutoNarrowing(pool, f_type);

        if (!sigResolved.equals(sigOrig))
            {
            idMethod = pool.ensureMethodConstant(idMethod.getNamespace(), sigResolved);
            f_cacheById.putIfAbsent(idMethod, method);
            }
        return idMethod;
        }

    /**
     * Obtain all of the matching methods for the specified name and the number of parameters.
     * <p/>
     * Note: the returned method constants could be synthetic and with auto-narrowing resolved.
     *
     * @param sName    the method name
     * @param cParams  the number of parameters (-1 for any)
     * @param kind     the kind of methods to consider
     *
     * @return a set of zero or more method constants
     */
    public synchronized Set<MethodConstant> findMethods(String sName, int cParams, MethodKind kind)
        {
        Map<String, Set<MethodConstant>> mapMethods = m_mapMethodsByName;
        if (mapMethods == null)
            {
            m_mapMethodsByName = mapMethods = new HashMap<>();
            }

        // a naked name is a key for "any method of that name"
        String sKey = cParams == -1 ? sName : sName + ';' + cParams;
        if (kind != MethodKind.Any)
            {
            sKey += kind.key;
            }

        Set<MethodConstant> setMethods = mapMethods.get(sKey);
        if (setMethods == null)
            {
            // the call to info.getTopmostMethodStructure(this) may change the content of
            // mapBySignature, so collect all the matching names first
            Map<MethodConstant, MethodInfo> mapCandidates = new HashMap<>();

            for (Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
                {
                MethodConstant idMethod = entry.getKey();

                // only include the non-nested Methods
                if (idMethod.getName().equals(sName) && idMethod.getNestedDepth() == 2)
                    {
                    mapCandidates.put(idMethod, entry.getValue());
                    }
                }

            for (Entry<MethodConstant, MethodInfo> entry : mapCandidates.entrySet())
                {
                MethodConstant  id     = entry.getKey();
                MethodInfo      info   = entry.getValue();
                MethodStructure method = info.getTopmostMethodStructure(this);

                if (!kind.matches(method))
                    {
                    continue;
                    }

                if (info.isCapped())
                    {
                    // ignore "capped" methods
                    continue;
                    }

                int cAllParams  = method.getParamCount();
                int cTypeParams = method.getTypeParamCount();
                int cDefaults   = method.getDefaultParamCount();
                int cRequired   = cAllParams - cTypeParams - cDefaults;

                if (cParams == -1 || cRequired <= cParams && cParams <= cAllParams)
                    {
                    if (setMethods == null)
                        {
                        setMethods = new HashSet<>(1);
                        }

                    MethodConstant idMethod = method.isFunction()
                            ? id
                            : resolveMethodConstant(id, info);
                    setMethods.add(idMethod);
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
     * Obtain all methods with specified name and the number of parameters inside of the container
     * property or method.
     *
     * @param idContainer  the id of the container property ir method
     * @param sName        the method name to look for
     * @param cParams      the number of parameters (-1 for any)
     *
     * @return a set of zero or more method constants
     */
    public synchronized Set<MethodConstant> findNestedMethods(IdentityConstant idContainer, String sName, int cParams)
        {
        Map<String, Set<MethodConstant>> mapMethods = m_mapMethodsByName;
        if (mapMethods == null)
            {
            m_mapMethodsByName = mapMethods = new HashMap<>();
            }

        Object nid   = idContainer.getNestedIdentity();
        String sPath = (nid instanceof SignatureConstant
                ? ((SignatureConstant) nid).getValueString()
                : nid.toString()) + '#' + sName;
        String sKey  = cParams == -1 ? sPath : sPath + ';' + cParams;

        Set<MethodConstant> setMethods = mapMethods.get(sKey);
        if (setMethods == null)
            {
            if (cParams == -1)
                {
                // any number of parameters goes
                cParams = Integer.MAX_VALUE;
                }
            int cReqDepth = idContainer.getNestedDepth() + 2;
            for (Map.Entry<MethodConstant, MethodInfo> entry : f_mapMethods.entrySet())
                {
                MethodConstant idTest = entry.getKey();

                if (idTest.getNestedDepth() == cReqDepth && idTest.getName().equals(sName)
                        && idTest.getNamespace().getNestedIdentity().equals(idContainer.getNestedIdentity()))
                    {
                    SignatureConstant sig    = idTest.getSignature();
                    MethodInfo        info   = entry.getValue();
                    MethodStructure   method = info.getTopmostMethodStructure(this);

                    assert !info.isCapped();

                    int cAllParams  = sig.getParamCount();
                    int cTypeParams = method.getTypeParamCount();
                    int cDefaults   = method.getDefaultParamCount();
                    int cRequired   = cAllParams - cTypeParams - cDefaults;

                    if (cParams >= cRequired)
                        {
                        if (setMethods == null)
                            {
                            setMethods = new HashSet<>(1);
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
     * Get the method that the specified capped method is narrowed by.
     *
     * @param methodCapped  a capped method
     *
     * @return the narrowing method (should never after the construction - see validateCapped())
     */
    public MethodInfo getNarrowingMethod(MethodInfo methodCapped)
        {
        assert methodCapped.isCapped();

        Object nidNarrowing = methodCapped.getHead().getNarrowingNestedIdentity();
        for (int i = 0; i < 32; i++)
            {
            methodCapped = getMethodByNestedId(nidNarrowing);
            if (methodCapped == null || !methodCapped.isCapped())
                {
                break;
                }
            nidNarrowing = methodCapped.getHead().getNarrowingNestedIdentity();
            }
        return methodCapped;
        }

    /**
     * Obtain all of the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public synchronized Set<MethodInfo> getAutoMethodInfos()
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
    public synchronized MethodConstant findConversion(TypeConstant typeDesired)
        {
        MethodConstant methodMatch = null;

        // check the cached result
        if (typeDesired.equals(m_typeAuto))
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
     * @return a map of information about child types of this type, keyed by name
     */
    public ListMap<String, ChildInfo> getChildInfosByName()
        {
        return f_mapChildren;
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
                if (f_mapVirtProps.containsKey(entry.getKey().resolveNestedIdentity(pool(), null)))
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
                if (f_mapVirtMethods.containsKey(entry.getKey().resolveNestedIdentity(pool(), null)))
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


    // ----- internal helpers ----------------------------------------------------------------------

    private GenericTypeResolver getCanonicalResolver()
        {
        GenericTypeResolver resolver = m_resolverCanonical;
        if (resolver == null)
            {
            m_resolverCanonical = resolver = sName ->
                {
                ParamInfo param = getTypeParams().get(sName);
                return param == null ? null : param.getConstraintType();
                };
            }
        return resolver;
        }

    private static Annotation[] validateAnnotations(Annotation[] annotations)
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

    /**
     * Helper: validate the integrity of all "capped" methods.
     *
     * @return null if all is good; a first offending method otherwise
     */
    public MethodInfo validateCapped()
        {
        for (MethodInfo method : f_mapMethods.values())
            {
            if (method.isCapped() && getNarrowingMethod(method) == null)
                {
                return method;
                }
            }
        return null;
        }

    private static Annotation[] mergeAnnotations(Annotation[] anno1, Annotation[] anno2)
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
        return list.toArray(Annotation.NO_ANNOTATIONS);
        }

    private static void appendAnnotations(ArrayList<Annotation> list, Annotation[] aAnno, Set<Constant> setPresent)
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
        // the ordinal values are significant: place-holder=1, incomplete=2, complete=3
        Absent, Building, Incomplete, Complete;

        public Progress worstOf(Progress that)
            {
            return this.ordinal() > that.ordinal() ? that : this;
            }
        }

    public enum MethodKind
        {
        Constructor("c"), Method("m"), Function("f"), Any("a");

        MethodKind(String key)
            {
            this.key = key;
            }

        public final String key;

        public boolean matches(MethodStructure method)
            {
            switch (this)
                {
                case Constructor:
                    return method.isConstructor();

                case Method:
                    return !method.isFunction() && !method.isConstructor();

                case Function:
                    return method.isFunction();

                case Any:
                    return true;

                default:
                    throw new IllegalStateException();
                }
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
     * Whether this type is abstract due to an absence of a virtual constructor.
     */
    private final boolean m_fMissingConstruct;

    /**
     * The type parameters for this TypeInfo key'ed by a String or Nid.
     */
    private final Map<Object, ParamInfo> f_mapTypeParams;

    /**
     * The class annotations.
     */
    private final Annotation[] f_aannoClass;

    /**
     * The mixin annotations.
     */
    private final Annotation[] f_aannoMixin;

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
     * The information about child types of this type, keyed by name. In the case of types nested
     * under properties, the name will be dot-delimited, such as "prop.name".
     */
    private final ListMap<String, ChildInfo> f_mapChildren;

    /**
     * The methods of the type, indexed by signature. This will not include nested methods, such
     * as those nested within a property or method. Lazily initialized
     */
    private transient Map<SignatureConstant, MethodInfo> m_mapMethodsBySignature;

    // cached query results REVIEW for thread safety
    // REVIEW is this a reasonable way to cache these?
    private final Map<MethodConstant, MethodInfo> f_cacheById;
    private final Map<Object, MethodInfo>         f_cacheByNid;

    private transient TypeInfo                         m_into;
    private transient TypeInfo                         m_delegates;
    private transient Set<MethodInfo>                  m_setAuto;
    private transient Set<MethodInfo>                  m_setOps;
    private transient TypeConstant                     m_typeAuto;
    private transient MethodConstant                   m_methodAuto;
    private transient Map<String, Set<MethodConstant>> m_mapOps;
    private transient Map<String, Set<MethodConstant>> m_mapMethodsByName;
    private transient Map<IdentityConstant, Map<String, PropertyInfo>> m_mapNestedProperties;
    private transient GenericTypeResolver              m_resolverCanonical;
    }
