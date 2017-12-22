package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Adapter;

import org.xvm.util.ListMap;


/**
 * An XVM Structure that represents an entire Class. This is also the base class for module and
 * package structures.
 */
public class ClassStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module, Package, or Class
     * @param condition  the optional condition for this ClassStructure
     */
    protected ClassStructure(XvmStructure xsParent, int nFlags, IdentityConstant constId,
                             ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this class is a singleton
     */
    public boolean isSingleton()
        {
        switch (getFormat())
            {
            case MODULE:
            case PACKAGE:
            case ENUMVALUE:
                // these types are always singletons
                return true;

            case INTERFACE:
            case CLASS:
            case ENUM:
            case MIXIN:
                // these types are never singletons
                return false;

            case CONST:
            case SERVICE:
                // these COULD be singletons (if they are static and NOT an inner class)
                if (isStatic())
                    {
                    Format format = getParent().getFormat();
                    return format == Format.MODULE || format == Format.PACKAGE;
                    }
                return false;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Obtain the type parameters for the class as an ordered read-only map, keyed by name and with
     * a corresponding value of the type constraint for the parameter.
     *
     * @return a read-only map of type parameter name to type
     */
    public Map<StringConstant, TypeConstant> getTypeParams()
        {
        Map<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            return Collections.EMPTY_MAP;
            }
        assert (map = Collections.unmodifiableMap(map)) != null;
        return map;
        }

    /**
     * Obtain the type parameters for the class as a list of map entries from name to type.
     *
     * @return a read-only list of map entries from type parameter name to type
     */
    public List<Map.Entry<StringConstant, TypeConstant>> getTypeParamsAsList()
        {
        final ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.isEmpty())
            {
            return Collections.EMPTY_LIST;
            }

        List<Map.Entry<StringConstant, TypeConstant>> list = map.asList();
        assert (list = Collections.unmodifiableList(list)) != null;
        return list;
        }

    /**
     * Add a type parameter.
     *
     * @param sName  the type parameter name
     * @param clz    the type parameter type
     */
    public void addTypeParam(String sName, TypeConstant clz)
        {
        ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null)
            {
            m_mapParams = map = new ListMap<>();
            }

        map.put(getConstantPool().ensureStringConstant(sName), clz);
        markModified();
        }

    /**
     * @return true if this class is parameterized (generic)
     */
    public boolean isParameterized()
        {
        return m_mapParams != null;
        }

    /**
     * @return the formal type (e.g. Map<KeyType, ValueType>)
     */
    public TypeConstant getFormalType()
        {
        TypeConstant typeFormal = m_typeFormal;
        if (typeFormal == null)
            {
            IdentityConstant constantClz = getIdentityConstant();

            ListMap<StringConstant, TypeConstant> mapParams = m_mapParams;
            if (mapParams == null)
                {
                typeFormal = constantClz.asTypeConstant();
                }
            else
                {
                ConstantPool   pool   = getConstantPool();
                TypeConstant[] aParam = new TypeConstant[mapParams.size()];
                int i = 0;
                for (StringConstant constName : mapParams.keySet())
                    {
                    aParam[i++] = pool.ensureTerminalTypeConstant(
                            pool.ensurePropertyConstant(constantClz, constName.getValue()));
                    }

                typeFormal = pool.ensureClassTypeConstant(constantClz, null, aParam);
                }
            m_typeFormal = typeFormal;
            }
        return typeFormal;
        }

    /**
     * @return the canonical type (e.g. Map<Object, Object>)
     */
    public TypeConstant getCanonicalType()
        {
        TypeConstant typeCanonical = m_typeCanonical;
        if (typeCanonical == null)
            {
            IdentityConstant constClz = getIdentityConstant();

            ListMap<StringConstant, TypeConstant> mapParams = m_mapParams;
            if (mapParams == null)
                {
                typeCanonical = constClz.asTypeConstant();
                }
            else
                {
                typeCanonical = getConstantPool().ensureClassTypeConstant(
                    constClz, null, mapParams.values().toArray(new TypeConstant[mapParams.size()]));
                }
            m_typeCanonical = typeCanonical;
            }
        return typeCanonical;
        }

    /**
     * Resolve the formal type for this class based on the specified list of actual types.
     *
     * @param  listActual  the list of actual types
     *
     * @return the resolved type
     */
    public TypeConstant resolveType(List<TypeConstant> listActual)
        {
        int cParams = m_mapParams == null ? 0 : m_mapParams.size();
        if (listActual.size() != cParams)
            {
            throw new IllegalArgumentException("Invalid actual type list size: " +
                listActual.size() + " expected: " + cParams);
            }

        return cParams > 0
            ? getFormalType().resolveGenerics(new SimpleTypeResolver(listActual))
            : getCanonicalType();
        }

    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }


    // ----- type comparison support ---------------------------------------------------------------

    /**
     * Test for sub-classing.
     *
     * @param constClass  the class to test if this type represents an extension of
     *
     * @return true if this type represents a sub-classing of the specified class
     */
    public boolean extendsClass(IdentityConstant constClass)
        {
        if (constClass.equals(getConstantPool().clzObject()))
            {
            // everything is considered to extend Object (even interfaces)
            return true;
            }

        if (getFormat() == Format.INTERFACE)
            {
            // interfaces do not extend; they implement
            return false;
            }

        if (constClass.equals(getIdentityConstant()))
            {
            // while a class cannot technically extend itself, this does satisfy the "is-a" test
            return true;
            }

        ClassStructure structCur = this;
        NextSuper: while (true)
            {
            for (Contribution contrib : structCur.getContributionsAsList())
                {
                if (contrib.getComposition() == Composition.Extends)
                    {
                    // even though this class may be id'd using a ModuleConstant or PackageConstant,
                    // the super will always be a class (because a Module and a Package cannot be
                    // extended)
                    ClassConstant constSuper = (ClassConstant) contrib.getTypeConstant().getSingleUnderlyingClass();
                    if (constClass.equals(constSuper))
                        {
                        return true;
                        }

                    structCur = (ClassStructure) constSuper.getComponent();
                    continue NextSuper;
                    }
                }

            return false;
            }
        }

    /**
     * Validate that a format can legally extend another format.
     *
     * @param fmtSub    the format of the extending class (the "sub" class)
     * @param fmtSuper  the format of the class being extended (the "super" class)
     *
     * @return true if legal; otherwise false
     */
    public static boolean isExtendsLegal(Format fmtSub, Format fmtSuper)
        {
        switch (fmtSub)
            {
            case CLASS:
                return fmtSuper == Format.CLASS;

            case CONST:
            case ENUM:
            case PACKAGE:
            case MODULE:
                return fmtSuper == Format.CONST || fmtSuper == Format.CLASS;

            case ENUMVALUE:
                return fmtSuper == Format.ENUM;

            case MIXIN:
                return fmtSuper == Format.MIXIN;

            case SERVICE:
                return fmtSuper == Format.SERVICE || fmtSuper == Format.CLASS;

            default:
                return false;
            }
        }

    /**
     * @return a type to rebase onto, if rebasing is required by this class; otherwise null
     */
    public TypeConstant getRebaseType()
        {
        switch (getFormat())
            {
            case MODULE:
                return getConstantPool().typeModule();

            case PACKAGE:
                return getConstantPool().typePackage();

            case ENUM:
                return getConstantPool().typeEnum();

            case CONST:
            case SERVICE:
                // only if the format differs from the format of the super
                ClassStructure structSuper = (ClassStructure)
                        getExtendsType().getSingleUnderlyingClass().getComponent();
                return getFormat() == structSuper.getFormat()
                        ? null
                        : getFormat() == Format.CONST
                                ? getConstantPool().typeConst()
                                : getConstantPool().typeService();

            default:
                return null;
            }
        }

    /**
     * @return the type that this ClassStructure extends, or null if this ClassStructure does not
     *         contain an Extends contribution.
     */
    public TypeConstant getExtendsType()    // TODO helper to return a structure
        {
        // mixin contributions are: optional annotations followed by optional into followed by
        // optional extends
        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Into:
                    break;

                case Extends:
                    return contrib.getTypeConstant();

                default:
                    return null;
                }
            }
        return null;
        }

    /**
     * Test for fake sub-classing (impersonation).
     *
     * @param constClass  the class to test if this type represents an impersonation of
     *
     * @return true if this type represents a fake sub-classing of the specified class
     */
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        // TODO
        return false;
        }

    /**
     * Test for real (extends) or fake (impersonation) sub-classing.
     *
     * @param constClass  the class to test if this type represents a sub-class of
     *
     * @return true if this type represents either real or fake sub-classing of the specified class
     */
    public boolean extendsOrImpersonatesClass(IdentityConstant constClass)
        {
        // TODO - this should be done in a single pass
        return extendsClass(constClass) || impersonatesClass(constClass);
        }

    /**
     * Find an index of a parameter with the specified name.
     *
     * @param sParamName  the parameter name
     *
     * @return the parameter index or -1 if not found
     */
    public int indexOfFormalParameter(String sParamName)
        {
        Iterator<Map.Entry<StringConstant, TypeConstant>> iterFormalEntry =
                getTypeParams().entrySet().iterator();
        for (int i = 0; iterFormalEntry.hasNext(); i++)
            {
            Map.Entry<StringConstant, TypeConstant> entry = iterFormalEntry.next();

            if (entry.getKey().getValue().equals(sParamName))
                {
                return i;
                }
            }
        return -1;
        }

    /**
     * Recursively find the type for the specified formal name. Note that the formal name could
     * be introduced by some contributions, rather than this class itself.
     */
    public TypeConstant getActualParamType(String sName, List<TypeConstant> listActual)
        {
        // TODO: use the type info when done
        return getActualParamTypeImpl(sName, listActual, true);
        }

    /**
     * Recursive implementation of getActualParamType method.
     *
     * @param fAllowInto  specifies whether or not the "Into" contribution is to be skipped
     */
    protected TypeConstant getActualParamTypeImpl(String sName, List<TypeConstant> listActual,
                                                  boolean fAllowInto)
        {
        int ix = indexOfFormalParameter(sName);
        if (ix >= 0)
            {
            return listActual.get(ix);
            }

        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                    // TODO:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        break;
                        }
                case Implements:
                    // TODO: what if the underlying type is relational
                case Impersonates:
                case Incorporates:
                case Extends:
                    TypeConstant typeContrib = contrib.getTypeConstant();
                    if (typeContrib.isParamsSpecified())
                        {
                        ClassStructure clzContrib = (ClassStructure)
                            ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                getComponent();
                        TypeConstant type = clzContrib.getActualParamTypeImpl(sName,
                            typeContrib.getParamTypes(), false);
                        if (type != null)
                            {
                            return type;
                            }
                        }
                    break;

                default:
                    throw new IllegalStateException();
                }
            }

        return null;
        }

    /**
     * Recursively find a contribution by the specified class id and add the corresponding
     * ContributionChain objects to the list of chains.
     *
     * @param idClz       the identity of the class to look for
     * @param listParams  the list of actual generic parameters
     * @param chains      the list of chains to add to
     * @param fAllowInto  specifies whether or not the "Into" contribution is to be skipped
     *
     * @return the resulting list of ContributionChain objects
     */
    public List<ContributionChain> collectContributions(
            ClassConstant idClz, List<TypeConstant> listParams,
            List<ContributionChain> chains, boolean fAllowInto)
        {
        if (idClz.equals(getIdentityConstant()))
            {
            chains.add(new ContributionChain(
                new Contribution(Composition.Equal, (TypeConstant) null)));
            return chains;
            }

    nextContribution:
        for (Contribution contrib : getContributionsAsList())
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            switch (contrib.getComposition())
                {
                case Into:
                    if (!fAllowInto)
                        {
                        continue nextContribution;
                        }
                case Delegates:
                case Implements:
                    break;

                case Annotation:
                case Impersonates:
                case Incorporates:
                case Extends:
                    // the identity constant for those contribution is always a class
                    assert typeContrib.isSingleDefiningConstant();
                    break;

                default:
                    throw new IllegalStateException();
                }

            List<ContributionChain> chainsContrib = null;
            if (typeContrib.isSingleDefiningConstant())
                {
                ClassConstant constContrib = (ClassConstant) typeContrib.getDefiningConstant();
                if (constContrib.equals(idClz))
                    {
                    chains.add(new ContributionChain(contrib));
                    continue;
                    }

                if (constContrib.equals(getConstantPool().clzObject()))
                    {
                    // trivial Object contribution
                    continue;
                    }

                List<TypeConstant> listContribActual =
                    contrib.transformActualTypes(this, listParams);

                if (listContribActual != null)
                    {
                    chainsContrib = ((ClassStructure) constContrib.getComponent()).
                        collectContributions(idClz, listContribActual, new LinkedList<>(), false);
                    }
                }
            else
                {
                typeContrib = contrib.resolveGenerics(new SimpleTypeResolver(listParams));

                chainsContrib = typeContrib.collectContributions(
                    idClz.asTypeConstant(), new LinkedList<>(), new LinkedList<>());
                }

            if (chainsContrib != null && !chainsContrib.isEmpty())
                {
                for (ContributionChain chain : chainsContrib)
                    {
                    chain.add(contrib);
                    }

                chains.addAll(chainsContrib);
                }
            }

        return chains;
        }

    /**
     * Determine if this template consumes a formal type with the specified name for the specified
     * access policy.
     */
    public boolean consumesFormalType(String sName, Access access, List<TypeConstant> listActual)
        {
        return consumesFormalTypeImpl(sName, access, listActual, true);
        }

    protected boolean consumesFormalTypeImpl(String sName, Access access,
                                             List<TypeConstant> listActual, boolean fAllowInto)
        {
        for (Component child : children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (!method.isStatic() && method.isAccessible(access)
                            && method.consumesFormalType(sName))
                        {
                        return true;
                        }
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) child;

                if (property.isTypeParameter())
                    {
                    // type properties don't consume
                    continue;
                    }

                TypeConstant constType = property.getType();
                if (!listActual.isEmpty())
                    {
                    constType = constType.resolveGenerics(new SimpleTypeResolver(listActual));
                    }

                // TODO: add correct access check when added to the structure
                // TODO: add @RO support

                MethodStructure methodGet = Adapter.getGetter(property);
                if ((methodGet == null || methodGet.isAccessible(access))
                        && constType.consumesFormalType(sName,
                                Access.PUBLIC, Collections.EMPTY_LIST))
                    {
                    return true;
                    }

                MethodStructure methodSet = Adapter.getSetter(property);
                if ((methodSet == null || methodSet.isAccessible(access))
                        && constType.producesFormalType(sName,
                                Access.PUBLIC, Collections.EMPTY_LIST))
                    {
                    return true;
                    }
                }
            }

        // check the contributions
        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                    // TODO:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        break;
                        }
                case Implements:
                    // TODO: what if the underlying type is relational
                case Extends:
                case Impersonates:
                case Incorporates:
                    {
                    String sGenericName = contrib.transformGenericName(this, sName);
                    if (sGenericName != null)
                        {
                        List<TypeConstant> listContribActual =
                            contrib.transformActualTypes(this, listActual);

                        if (listContribActual != null)
                            {
                            ClassStructure clzContrib = (ClassStructure)
                                ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                    getComponent();

                            if (clzContrib.consumesFormalTypeImpl(
                                    sGenericName, access, listContribActual, false))
                                {
                                return true;
                                }
                            }
                        }
                    break;
                    }

                default:
                    throw new IllegalStateException();
                }
            }
        return false;
        }

    /**
     * Determine if this template produces a formal type with the specified name for the
     * specified access policy.
     */
    public boolean producesFormalType(String sName, Access access, List<TypeConstant> listActual)
        {
        return producesFormalTypeImpl(sName, access, listActual, true);
        }

    protected boolean producesFormalTypeImpl(String sName, Access access,
                                             List<TypeConstant> listActual, boolean fAllowInto)
        {
        for (Component child : children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (!method.isStatic() && method.isAccessible(access)
                            && method.producesFormalType(sName))
                        {
                        return true;
                        }
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) child;

                if (property.isTypeParameter())
                    {
                    // type properties don't produce
                    continue;
                    }

                TypeConstant constType = property.getType();
                if (!listActual.isEmpty())
                    {
                    constType = constType.resolveGenerics(new SimpleTypeResolver(listActual));
                    }

                // TODO: add correct access check when added to the structure
                // TODO: add @RO support

                MethodStructure methodGet = Adapter.getGetter(property);
                if ((methodGet == null || methodGet.isAccessible(access)
                        && constType.producesFormalType(sName,
                                Access.PUBLIC, Collections.EMPTY_LIST)))
                    {
                    return true;
                    }

                MethodStructure methodSet = Adapter.getSetter(property);
                if ((methodSet == null || methodSet.isAccessible(access))
                        && constType.consumesFormalType(sName,
                                Access.PUBLIC, Collections.EMPTY_LIST))
                    {
                    return true;
                    }
                }
            }

        // check the contributions
        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                    // TODO:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        break;
                        }
                case Implements:
                    // TODO: what if the underlying type is relational
                case Extends:
                case Impersonates:
                case Incorporates:
                    {
                    String sGenericName = contrib.transformGenericName(this, sName);
                    if (sGenericName != null)
                        {
                        List<TypeConstant> listContribActual =
                            contrib.transformActualTypes(this, listActual);

                        if (listContribActual != null)
                            {
                            ClassStructure clzContrib = (ClassStructure)
                                ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                    getComponent();

                            if (clzContrib.producesFormalTypeImpl(
                                    sGenericName, access, listContribActual, false))
                                {
                                return true;
                                }
                            }
                        }
                    break;
                    }

                default:
                    throw new IllegalStateException();
                }
            }
        return false;
        }

    /**
     * Check recursively if all properties and methods on the interface represented by this class
     * structure have a matching (substitutable) property or method on the specified type.
     *
     * @param typeThat    the type to look for the matching methods
     * @param access      the access level to limit the check for
     * @param listParams  the actual generic parameters for this interface
     *
     * @return a set of method/property signatures that don't have a match
     */
    public Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeThat, Access access,
                                                            List<TypeConstant> listParams)
        {
        Set<SignatureConstant> setMiss = new HashSet<>();

        for (Component child : children())
            {
            if (child instanceof PropertyStructure)
                {
                PropertyStructure prop = (PropertyStructure) child;

                // TODO: check access

                SignatureConstant sig = prop.getIdentityConstant().getSignature();
                if (!listParams.isEmpty())
                    {
                    sig = sig.resolveGenericTypes(new SimpleTypeResolver(listParams));
                    }

                if (!typeThat.containsSubstitutableProperty(sig,
                        Access.PUBLIC, Collections.EMPTY_LIST))
                    {
                    setMiss.add(sig);
                    }
                }
            else if (child instanceof MultiMethodStructure)
                {
                MultiMethodStructure mms = (MultiMethodStructure) child;
                for (MethodStructure method : mms.methods())
                    {
                    if (method.isStatic() || !method.isAccessible(access))
                        {
                        continue;
                        }

                    SignatureConstant sig = method.getIdentityConstant().getSignature();
                    if (!listParams.isEmpty())
                        {
                        sig = sig.resolveGenericTypes(new SimpleTypeResolver(listParams));
                        }

                    if (!typeThat.containsSubstitutableMethod(sig,
                            Access.PUBLIC, Collections.EMPTY_LIST))
                        {
                        setMiss.add(sig);
                        }
                    }
                }
            }

        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Extends)
                {
                List<TypeConstant> listContribActual =
                    contrib.transformActualTypes(this, listParams);

                if (listContribActual != null)
                    {
                    ClassStructure clzSuper = (ClassStructure)
                        ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                            getComponent();

                    assert (clzSuper.getFormat() == Component.Format.INTERFACE);

                    setMiss.addAll(
                        clzSuper.isInterfaceAssignableFrom(typeThat, access, listContribActual));
                    }
                }
            }
        return setMiss;
        }

    /**
     * Check recursively if this class contains a matching (substitutable) method.
     *
     * @param signature   the method signature to look for the match for (resolved)
     * @param access      the access level to limit the check to
     * @param listParams  the actual generic parameters for this interface
     *
     * @return true iff all properties and methods have matches
     */
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        return containsSubstitutableMethodImpl(signature, access, listParams, true);
        }

    protected boolean containsSubstitutableMethodImpl(SignatureConstant signature, Access access,
                                                   List<TypeConstant> listParams, boolean fAllowInto)
        {
        Component child = getChild(signature.getName());

        if (child instanceof MultiMethodStructure)
            {
            MultiMethodStructure mms = (MultiMethodStructure) child;

            TypeConstant.GenericTypeResolver resolver = listParams.isEmpty() ? null :
                new SimpleTypeResolver(listParams);

            for (MethodStructure method : mms.methods())
                {
                if (!method.isStatic() && method.isAccessible(access) &&
                    method.isSubstitutableFor(signature, resolver))
                    {
                    return true;
                    }
                }
            }

        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                    // TODO:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        break;
                        }
                case Implements:
                    // TODO: what if the underlying type is relational
                case Impersonates:
                case Incorporates:
                case Extends:
                    {
                    List<TypeConstant> listContribActual =
                        contrib.transformActualTypes(this, listParams);

                    if (listContribActual != null)
                        {
                        ClassStructure clzContrib = (ClassStructure)
                            ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                getComponent();

                        if (clzContrib.containsSubstitutableMethod(signature,
                                access, listContribActual))
                            {
                            return true;
                            }
                        }
                    }
                }
            }
        return false;
        }

    /**
     * Check recursively if this class contains a matching (substitutable) property.
     *
     * @param signature   the property signature to look for the match for (resolved)
     * @param access      the access level to limit the check to
     * @param listParams  the actual generic parameters for this interface
     *
     * @return true iff all properties and methods have matches
     */
    public boolean containsSubstitutableProperty(SignatureConstant signature, Access access,
                                                 List<TypeConstant> listParams)
        {
        return containsSubstitutablePropertyImpl(signature, access, listParams, true);
        }

    protected boolean containsSubstitutablePropertyImpl(SignatureConstant signature, Access access,
                                                       List<TypeConstant> listParams, boolean fAllowInto)
        {
        Component child = getChild(signature.getName());

        if (child instanceof PropertyStructure)
            {
            PropertyStructure property = (PropertyStructure) child;

            // TODO: check access

            if (property.isSubstitutableFor(signature, listParams))
                {
                return true;
                }
            }

        for (Contribution contrib : getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                    // TODO:
                    break;

                case Into:
                    if (!fAllowInto)
                        {
                        break;
                        }
                case Implements:
                    // TODO: what if the underlying type is relational
                case Impersonates:
                case Incorporates:
                case Extends:
                    {
                    List<TypeConstant> listContribActual =
                        contrib.transformActualTypes(this, listParams);

                    if (listContribActual != null)
                        {
                        ClassStructure clzContrib = (ClassStructure)
                            ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                getComponent();

                        if (clzContrib.containsSubstitutableProperty(signature,
                                access, listContribActual))
                            {
                            return true;
                            }
                        }
                    }
                }
            }
        return false;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        // read in the type parameters
        m_mapParams = disassembleTypeParams(in);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // register the type parameters
        m_mapParams = registerTypeParams(m_mapParams);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        // write out the type parameters
        assembleTypeParams(m_mapParams, out);
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDescription())
          .append(", type-params=");

        final ListMap<StringConstant, TypeConstant> map = m_mapParams;
        if (map == null || map.size() == 0)
            {
            sb.append("none");
            }
        else
            {
            sb.append('<');
            boolean fFirst = true;
            for (Map.Entry<StringConstant, TypeConstant> entry : map.entrySet())
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }

                sb.append(entry.getKey().getValue());

                TypeConstant constType = entry.getValue();
                if (!constType.isEcstasy("Object"))
                    {
                    sb.append(" extends ")
                      .append(constType);
                    }
                }
            sb.append('>');
            }

        return sb.toString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof ClassStructure) || !super.equals(obj))
            {
            return false;
            }

        ClassStructure that = (ClassStructure) obj;

        // type parameters
        Map mapThisParams = this.m_mapParams;
        Map mapThatParams = that.m_mapParams;
        int cThisParams   = mapThisParams == null ? 0 : mapThisParams.size();
        int cThatParams   = mapThatParams == null ? 0 : mapThatParams.size();

        return cThisParams == cThatParams &&
            (cThisParams == 0 || mapThisParams.equals(mapThatParams));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The name-to-type information for type parameters. The type constant is used to specify a
     * type constraint for the parameter.
     */
    private ListMap<StringConstant, TypeConstant> m_mapParams;

    /**
     * Cached formal type.
     */
    private transient TypeConstant m_typeFormal;

    /**
     * Cached canonical type.
     */
    private transient TypeConstant m_typeCanonical;


    // ----- inner classes ------------------------------------------------------------------

    /**
     * Generic type resolver based on the actual parameter list.
     */
    public class SimpleTypeResolver
            implements TypeConstant.GenericTypeResolver
        {
        /**
         * Create a TypeResolver based on the actual type list.
         *
         * @param listActual  the actual type list
         */
        public SimpleTypeResolver(List<TypeConstant> listActual)
            {
            if (getIdentityConstant().equals(getConstantPool().clzTuple()))
                {
                m_fTuple = true;
                }
            else
                {
                assert m_mapParams == null && listActual.isEmpty() ||
                   m_mapParams.size() == listActual.size();
                }
            m_listActual = listActual;
            }

        @Override
        public TypeConstant resolveGenericType(PropertyConstant constProperty)
            {
            int ix = indexOfFormalParameter(constProperty.getName());
            if (ix < 0)
                {
                throw new IllegalStateException(
                    "Failed to find " + constProperty.getName() + " in " + this);
                }

            if (m_fTuple)
                {
                ConstantPool pool = getConstantPool();
                return pool.ensureParameterizedTypeConstant(pool.typeTuple(),
                    m_listActual.toArray(new TypeConstant[m_listActual.size()]));
                }
            return m_listActual.get(ix);
            }

        private boolean m_fTuple;
        private List<TypeConstant> m_listActual;
        }
    }
