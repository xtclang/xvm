package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.ClassConstant;
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
 *
 * @author cp 2016.04.14
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
            case TRAIT:
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

// TODO need a more generic test that checks class assignability in general
//            switch (contrib.getComposition())
//                {
//                case Extends:
//                    // even though this class may be id'd using a ModuleConstant or PackageConstant, the
//                    // super will always be a class (because a Module and a Package cannot be extended)
//                    ClassConstant constSuper = (ClassConstant) contrib.getTypeConstant().getSingleUnderlyingClass();
//                    if (constClass.equals(constSuper))
//
//                case Enumerates:
//
//                case Annotation:
//                case Incorporates:
//
//                case Into:
//                    // this is a mixin; if the type that this mixin mixes into is a class, then any
//                    // instance of this mixin will extend the specified class
//                }

    /**
     * Test for fake sub-classing (impersonation).
     *
     * @param constClass  the class to test if this type represents an impersonation of
     *
     * @return true if this type represents a fake sub-classing of the specified class
     */
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        // TODO - in progress
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
        // TODO - in progress
        return false;
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
     * Recursively find a contribution by the specified id.
     *
     * @param constId  the identity to look for
     *
     * @return the resulting contribution or null if none found
     */
    public ContributionChain findContribution(IdentityConstant constId)
        {
        ConstantPool pool = getConstantPool();
        if (constId.equals(pool.clzObject()))
            {
            // everything is considered to extend Object (even interfaces)
            return new ContributionChain(
                new Contribution(Composition.Extends, pool.typeObject()));
            }

        if (constId.equals(getIdentityConstant()))
            {
            return new ContributionChain(
                new Contribution(Composition.Equal, (TypeConstant) null));
            }

        ClassStructure structCur = this;

        for (Contribution contrib : structCur.getContributionsAsList())
            {
            Constant constContrib = contrib.getTypeConstant().getDefiningConstant();
            if (constContrib.equals(constId))
                {
                return new ContributionChain(contrib);
                }

            if (constContrib.equals(pool.clzObject()))
                {
                // trivial Object contribution
                continue;
                }

            switch (contrib.getComposition())
                {
                case Annotation:
                case Delegates:
                    // TODO:
                    break;

                case Impersonates:
                case Implements:
                case Incorporates:
                case Into:
                case Extends:
                    // even though this class may be a ModuleConstant or PackageConstant,
                    // the super will always be a class (because a Module and a Package cannot be
                    // extended)
                    ClassConstant constSuper = (ClassConstant) constContrib;
                    ContributionChain chain = ((ClassStructure) constSuper.getComponent()).
                        findContribution(constId);
                    if (chain != null)
                        {
                        // return as soon as a match is found
                        chain.add(contrib);
                        return chain;
                        }
                    break;

                case Enumerates:
                    // TODO:
                    break;

                default:
                    throw new IllegalStateException();
                }
            }

        return null;
        }

    /**
     * Determine if this template consumes a formal type with the specified name for the specified
     * access policy.
     */
    public boolean consumesFormalType(String sName, Access access, List<TypeConstant> listActual)
        {
        for (Component child : children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (method.isAccessible(access) &&
                        method.consumesFormalType(sName))
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

                // TODO: add correct access check when added to the structure
                // TODO: add @RO support

                MethodStructure methodGet = Adapter.getGetter(property);
                if ((methodGet == null || methodGet.isAccessible(access))
                        && constType.consumesFormalType(sName, Access.PUBLIC))
                    {
                    return true;
                    }

                MethodStructure methodSet = Adapter.getSetter(property);
                if ((methodSet == null || methodSet.isAccessible(access))
                        && constType.producesFormalType(sName, Access.PUBLIC))
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

                case Impersonates:
                case Implements:
                case Incorporates:
                case Into:
                case Extends:
                    {
                    String sGenericName = contrib.transformGenericName(this, sName);
                    if (sGenericName != null)
                        {
                        List<TypeConstant> listContribActual =
                            contrib.transformActualTypes(this, listActual);

                        if (listContribActual != null)
                            {
                            ClassStructure clzSuper = (ClassStructure)
                                ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                    getComponent();
                            if (clzSuper.consumesFormalType(sGenericName, access, listContribActual))
                                {
                                return true;
                                }
                            }
                        }
                    break;
                    }

                case Enumerates:
                    // TODO:
                    break;

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
        for (Component child : children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (method.isAccessible(access) &&
                        method.producesFormalType(sName))
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

                // TODO: add correct access check when added to the structure
                // TODO: add @RO support

                MethodStructure methodGet = Adapter.getGetter(property);
                if ((methodGet == null || methodGet.isAccessible(access)
                        && constType.producesFormalType(sName, Access.PUBLIC)))
                    {
                    return true;
                    }

                MethodStructure methodSet = Adapter.getSetter(property);
                if ((methodSet == null || methodSet.isAccessible(access))
                        && constType.consumesFormalType(sName, Access.PUBLIC))
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

                case Impersonates:
                case Implements:
                case Incorporates:
                case Into:
                case Extends:
                    {
                    String sGenericName = contrib.transformGenericName(this, sName);
                    if (sGenericName != null)
                        {
                        List<TypeConstant> listContribActual =
                            contrib.transformActualTypes(this, listActual);

                        if (listContribActual != null)
                            {
                            ClassStructure clzSuper = (ClassStructure)
                                ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                    getComponent();
                            if (clzSuper.producesFormalType(sGenericName, access, listContribActual))
                                {
                                return true;
                                }
                            }
                        }
                    break;
                    }

                case Enumerates:
                    // TODO:
                    break;

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
     * @return true iff all properties and methods have matches
     */
    public boolean isInterfaceAssignableFrom(TypeConstant typeThat, Access access, List<TypeConstant> listParams)
        {
        for (Component child : children())
            {
            if (child instanceof PropertyStructure)
                {
                PropertyStructure prop = (PropertyStructure) child;
                // TODO: check access
                SignatureConstant sig = null; // TODO: signature constant for the property

                if (!typeThat.containsSubstitutableProperty(sig, listParams))
                    {
                    return false;
                    }
                }
            else if (child instanceof MultiMethodStructure)
                {
                MultiMethodStructure mms = (MultiMethodStructure) child;
                for (MethodStructure method : mms.methods())
                    {
                    if (!method.isAccessible(access))
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
                        return false;
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

                    if (!clzSuper.isInterfaceAssignableFrom(typeThat, access, listContribActual))
                        {
                        return false;
                        }
                    }
                }
            }
        return true;
        }

    /**
     * Check recursively if this class contains a matching (substitutable) method.
     *
     * @param signature   the method signature to look for the match for
     * @param access      the access level to limit the check to
     * @param listParams  the actual generic parameters for this interface
     *
     * @return true iff all properties and methods have matches
     */
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        Component child = getChild(signature.getName());

        if (child instanceof MultiMethodStructure)
            {
            MultiMethodStructure mms = (MultiMethodStructure) child;

            for (MethodStructure ms : mms.methods())
                {
                if (ms.isSubstitutableFor(signature, listParams))
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

                case Impersonates:
                case Implements:
                case Incorporates:
                case Into:
                case Extends:
                    {
                    List<TypeConstant> listContribActual =
                        contrib.transformActualTypes(this, listParams);

                    if (listContribActual != null)
                        {
                        ClassStructure clzSuper = (ClassStructure)
                            ((ClassConstant) contrib.getTypeConstant().getDefiningConstant()).
                                getComponent();

                        if (clzSuper.containsSubstitutableMethod(signature,
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
        final Map mapThisParams = this.m_mapParams;
        final Map mapThatParams = that.m_mapParams;
        final int cThisParams = mapThisParams == null ? 0 : mapThisParams.size();
        final int cThatParams = mapThatParams == null ? 0 : mapThatParams.size();
        if (cThisParams != cThatParams || (cThisParams > 0 && !mapThisParams.equals(mapThatParams)))
            {
            return  false;
            }

        return true;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The name-to-type information for type parameters. The type constant is used to specify a
     * type constraint for the parameter.
     */
    private ListMap<StringConstant, TypeConstant> m_mapParams;

    // ----- inner classes ------------------------------------------------------------------

    /**
     * Generic type resolver based on an actual parameter list.
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
            assert m_mapParams == null && listActual.isEmpty() ||
                   m_mapParams.size() == listActual.size();

            m_listActual = listActual;
            }

        @Override
        public TypeConstant resolveGenericType(String sName)
            {
            int ix = indexOfFormalParameter(sName);
            if (ix < 0)
                {
                throw new IllegalStateException(
                    "Failed to find " + sName + " in " + this);
                }
            return m_listActual.get(ix);
            }

        private List<TypeConstant> m_listActual;
        }
    }
