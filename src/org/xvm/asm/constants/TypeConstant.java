package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.runtime.template.xType;
import org.xvm.runtime.template.xType.TypeHandle;


/**
 * A base class for the various forms of Constants that will represent data types.
 * <p/>
 * Each type has 0, 1, or 2 underlying types:
 * <ul>
 * <li>A {@link TerminalTypeConstant} has no underlying type(s); it is a terminal;</li>
 * <li>Type constants that modify a single underlying type include {@link
 *     ImmutableTypeConstant}, {@link AccessTypeConstant}, {@link ParameterizedTypeConstant},
 *     and {@link AnnotatedTypeConstant}; and</li>
 * <li>Type constants that relate two underlying types include {@link IntersectionTypeConstant},
 *     {@link UnionTypeConstant}, and {@link DifferenceTypeConstant}.</li>
 * </ul>
 */
public abstract class TypeConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    protected TypeConstant(ConstantPool pool, Constant.Format format, DataInput in)
            throws IOException
        {
        super(pool);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool  the ConstantPool that will contain this Constant
     */
    protected TypeConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Determine if the type has exactly one underlying type that it modifies the meaning of.
     * An underlying type is a type whose definition is modified by this type constant.
     * <p/>
     * <ul>
     * <li>{@link ImmutableTypeConstant}</li>
     * <li>{@link AccessTypeConstant}</li>
     * <li>{@link ParameterizedTypeConstant}</li>
     * <li>{@link AnnotatedTypeConstant}</li>
     * </ul>
     *
     * @return true iff this is a modifying type constant
     */
    public boolean isModifyingType()
        {
        return false;
        }

    /**
     * Determine if the type represents a relation between two underlying types.
     * <p/>
     * <ul>
     * <li>{@link IntersectionTypeConstant}</li>
     * <li>{@link UnionTypeConstant}</li>
     * <li>{@link DifferenceTypeConstant}</li>
     * </ul>
     * <p/>
     *
     * @return true iff this is a relational type constant
     */
    public boolean isRelationalType()
        {
        return false;
        }

    /**
     * Obtain the underlying type, or the first of two underlying types if the type constant has
     * two underlying types.
     *
     * @return the underlying type constant
     *
     * @throws UnsupportedOperationException if there is no underlying type
     */
    public TypeConstant getUnderlyingType()
        {
        throw new UnsupportedOperationException();
        }

    /**
     * Obtain the second underlying type if the type constant has two underlying types.
     *
     * @return the second underlying type constant
     *
     * @throws UnsupportedOperationException if there is no second underlying type
     */
    public TypeConstant getUnderlyingType2()
        {
        throw new UnsupportedOperationException();
        }

    /**
     * @return true iff the type specifies immutability
     */
    public boolean isImmutabilitySpecified()
        {
        return getUnderlyingType().isImmutabilitySpecified();
        }

    /**
     * @return true iff the type specifies accessibility
     */
    public boolean isAccessSpecified()
        {
        return getUnderlyingType().isAccessSpecified();
        }

    /**
     * @return the access, if it is specified, otherwise public
     *
     * @throws UnsupportedOperationException if the type is relational and contains conflicting
     *         access specifiers
     */
    public Access getAccess()
        {
        return getUnderlyingType().getAccess();
        }

    /**
     * @return true iff type parameters for the type are specified
     */
    public boolean isParamsSpecified()
        {
        return isModifyingType() && getUnderlyingType().isParamsSpecified();
        }

    /**
     * @return the number of parameters specified
     */
    public int getParamsCount()
        {
        return getParamTypesArray().length;
        }

    /**
     * @return the actual number of type parameters declared by the underlying defining class
     */
    public int getMaxParamsCount()
        {
        return isModifyingType() ? getUnderlyingType().getMaxParamsCount() : 0;
        }

    /**
     * @return the type parameters, iff the type has parameters specified
     *
     * @throws UnsupportedOperationException if there are no type parameters specified, or if the
     *         type is a relational type
     */
    public List<TypeConstant> getParamTypes()
        {
        return isModifyingType()
                ? getUnderlyingType().getParamTypes()
                : Collections.EMPTY_LIST;
        }

    /**
     * @return type type parameters as an array, iff the type has parameters specified
     *
     * @throws UnsupportedOperationException if there are no type parameters specified, or if the
     *         type is a relational type
     */
    public TypeConstant[] getParamTypesArray()
        {
        List<TypeConstant> list = getParamTypes();
        return list == null || list.isEmpty()
                ? ConstantPool.NO_TYPES
                : list.toArray(new TypeConstant[list.size()]);
        }

    /**
     * @return true iff annotations of the type are specified
     */
    public boolean isAnnotated()
        {
        return isModifyingType() && getUnderlyingType().isAnnotated();
        }

    /**
     * @return true iff there is a single defining constant, which means that the type does not
     *         contain any relational type constants
     */
    public boolean isSingleDefiningConstant()
        {
        return isModifyingType() && getUnderlyingType().isSingleDefiningConstant();
        }

    /**
     * @return the defining constant, iff there is a single defining constant
     *
     * @throws UnsupportedOperationException if there is not a single defining constant
     */
    public Constant getDefiningConstant()
        {
        return getUnderlyingType().getDefiningConstant();
        }

    /**
     * @return true iff this TypeConstant represents an auto-narrowing type
     */
    public boolean isAutoNarrowing()
        {
        return getUnderlyingType().isAutoNarrowing();
        }

    /**
     * @return true iff this TypeConstant is <b>not</b> auto-narrowing, and is not a reference to a
     *         type parameter, and its type parameters, if any, are also each a constant type
     */
    public boolean isConstant()
        {
        return getUnderlyingType().isConstant();
        }

    /**
     * Determine if this TypeConstant represents the public type from the core Ecstasy module.
     *
     * @return true iff this TypeConstant is a public type from the Ecstasy core module
     */
    public boolean isPublicEcstasyType()
        {
        return isSingleDefiningConstant()
                && getDefiningConstant() instanceof ClassConstant
                && ((ClassConstant) this.getDefiningConstant()).getModuleConstant().isEcstasyModule()
                && getAccess() == Access.PUBLIC;
        }

    /**
     * Determine if this TypeConstant represents a core, implicitly-imported Ecstasy type denoted
     * by the specified name.
     *
     * @param sName  the name or alias by which the Ecstasy core type is imported
     *
     * @return true iff this TypeConstant is the Ecstasy core type identified by the passed name
     */
    public boolean isEcstasy(String sName)
        {
        IdentityConstant constId = getConstantPool().getImplicitlyImportedIdentity(sName);
        if (constId == null)
            {
            // TODO could just take the name as is (including qualified notation) and assume it's an Ecstasy class
            throw new IllegalArgumentException("no such implicit name: " + sName);
            }

        return isSingleDefiningConstant() && getDefiningConstant().equals(constId);
        }

    /**
     * @return the Ecstasy class name, including package name(s), otherwise "?"
     */
    public String getEcstasyClassName()
        {
        // TODO might require additional checks
        return isSingleDefiningConstant()
                    && getDefiningConstant() instanceof ClassConstant
                    && ((ClassConstant) getDefiningConstant()).getModuleConstant().isEcstasyModule()
                    && getAccess() == Access.PUBLIC
                ? ((ClassConstant) getDefiningConstant()).getPathString()
                : "?";
        }

    /**
     * Determine if this is "Void".
     *
     * @return true iff this is provably "Void"
     */
    public boolean isVoid()
        {
        if (isEcstasy("Void"))
            {
            return true;
            }

        TypeConstant constThis = (TypeConstant) this.simplify();
        return !constThis.containsUnresolved()
                && constThis.isEcstasy("Tuple")
                && constThis.isParamsSpecified()
                && constThis.getParamTypesArray().length == 0;
        }

    /**
     * @return true iff this type is a nullable type
     */
    public boolean isNullable()
        {
        // a type is only considered nullable if it is a "(nullable | type)"
        return false;
        }

    /**
     * @return true iff the type is the Nullable type itself, or a simple modification of the same
     */
    public boolean isOnlyNullable()
        {
        // a type is considered only nullable if it is the Nullable type itself, or a simple
        // modification of the same
        return getUnderlyingType().isOnlyNullable();
        }

    /**
     * Determine if this type can be compared with another type, because the types are identical, or
     * because they differ only in irrelevant ways, such as one being immutable.
     *
     * @param that  another type
     *
     * @return true iff the two types are compatible for purposes of value comparison
     */
    public boolean isCongruentWith(TypeConstant that)
        {
        return this == that || this.unwrapForCongruence().equals(that.unwrapForCongruence());
        }

    /**
     * If this type is a nullable type, calculate the type without the nullability.
     *
     * @return a TypeConstant without
     */
    public TypeConstant nonNullable()
        {
        return this;
        }

    protected TypeConstant unwrapForCongruence()
        {
        return this;
        }

    /**
     * @return clone this single defining type based on the underlying type
     */
    protected TypeConstant cloneSingle(TypeConstant type)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * @return this same type, but without any typedefs in it
     */
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        return constResolved == constOriginal
            ? this
            : cloneSingle(constResolved);
        }

    /**
     * @return this same type, but without any generic types in it
     */
    public TypeConstant resolveGenerics(GenericTypeResolver resolver)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveGenerics(resolver);

        return constResolved == constOriginal
                ? this
                : cloneSingle(constResolved);
        }

    /**
     * @return this same type, but with the number of parameters equal to the number of
     *         formal parameters for every parameterized type
     */
    public TypeConstant normalizeParameters()
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.normalizeParameters();

        return constResolved == constOriginal
                ? this
                : cloneSingle(constResolved);
        }

    /**
     * @return this same type, but with a specified access
     */
    public TypeConstant modifyAccess(Access access)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.modifyAccess(access);

        return constResolved == constOriginal
                ? this
                : cloneSingle(constResolved);
        }

    /**
     * Type parameters are compiled as the "Type" type; assuming that this type is the type
     * {@code Type<T>}, determine what {@code T} is.
     *
     * @return the type that the type parameter (whose type is this) refers to
     */
    public TypeConstant getTypeParameterType()
        {
        if (!isEcstasy("Type"))
            {
            throw new IllegalStateException("not a type parameter type: " + this);
            }

        return isParamsSpecified()
                ? getParamTypesArray()[0]
                : getConstantPool().typeObject();
        }

    /**
     * @return true iff the type is a tuple type
     */
    public boolean isTuple()
        {
        TypeConstant constThis = (TypeConstant) this.simplify();
        assert !constThis.containsUnresolved();
        return constThis.isEcstasy("Tuple");
        }

    /**
     * @return true iff the type is a tuple type
     */
    public boolean isArray()
        {
        TypeConstant constThis = (TypeConstant) this.simplify();
        assert !constThis.containsUnresolved();
        return constThis.isA(getConstantPool().typeArray());
        }

    /**
     * @return true iff the type is a tuple type
     */
    public boolean isSequence()
        {
        TypeConstant constThis = (TypeConstant) this.simplify();
        assert !constThis.containsUnresolved();
        return     constThis.isEcstasy("String")
                || constThis.isEcstasy("Array")
                || constThis.isEcstasy("List")
                || constThis.isEcstasy("Sequence")
                || constThis.isA(getConstantPool().typeSequence());
        }

    /**
     * @return the number of tuple iff the type is a tuple type; otherwise -1
     */
    public int getTupleFieldCount()
        {
        TypeConstant constThis = (TypeConstant) this.simplify();
        if (constThis.containsUnresolved()
                || !constThis.isEcstasy("Tuple"))
            {
            throw new IllegalStateException();
            }

        return constThis.isParamsSpecified()
                ? constThis.getParamTypesArray().length
                : 0;
        }

    /**
     * Obtain the type of the specified tuple field.
     *
     * @param i  the 0-based tuple field index
     *
     * @return the type of the specified field
     */
    public TypeConstant getTupleFieldType(int i)
        {
        TypeConstant constThis = (TypeConstant) this.simplify();
        if (constThis.containsUnresolved()
                || !constThis.isEcstasy("Tuple")
                || !constThis.isParamsSpecified())
            {
            throw new IllegalStateException();
            }

        TypeConstant[] atypeParam = constThis.getParamTypesArray();
        if (i < 0 || i >= atypeParam.length)
            {
            throw new IllegalArgumentException("i=" + i + ", size=" + atypeParam.length);
            }

        return atypeParam[i];
        }

    /**
     * Obtain all of the information about this type, resolved from its recursive composition.
     *
     * @return the flattened TypeInfo that represents the resolved type of this TypeConstant
     */
    public TypeInfo getTypeInfo()
        {
        return ensureTypeInfo(ErrorListener.RUNTIME);
        }

    /**
     * Obtain all of the information about this type, resolved from its recursive composition.
     *
     * @return the flattened TypeInfo that represents the resolved type of this TypeConstant
     */
    public TypeInfo ensureTypeInfo(ErrorListener errs)
        {
        if (m_typeinfo == null)
            {
            validate(errs);
            m_typeinfo = buildTypeInfo(errs);
            }

        return m_typeinfo;
        }

    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // TODO
        }

    /**
     * Accumulate any information for the type represented by this TypeConstant into the passed
     * {@link TypeInfo}, logging any errors that are detected in the composition of the type.
     *
     * @param typeinfo     the type info to contribute to
     * @param chain        the chain of contributions that led here from the type specified in the
     *                     TypeInfo
     * @param access       the desired accessibility into the current type
     * @param atypeParams  the types for the type parameters of this class, if any (may be null)
     * @param errs         the error list to log any errors to
     *
     * @return true if the resolution process was halted before it completed, for example if the
     *         error list reached its size limit
     */
    protected boolean resolveStructure(TypeInfo typeinfo, ContributionChain chain,
            Access access, TypeConstant[] atypeParams, ErrorListener errs)
        {
        assert isModifyingType();
        return getUnderlyingType().resolveStructure(typeinfo, chain, access, atypeParams, errs);
        }


    // ----- type comparison support ---------------------------------------------------------------

    /**
     * Determine if the specified TypeConstant (L-value) represents a type that is assignable to
     * values of the type represented by this TypeConstant (R-Value).
     *
     * @param thatLeft  the type to match (L-value)
     *
     * See Type.x # isA()
     */
    public boolean isA(TypeConstant thatLeft)
        {
        return calculateRelation(thatLeft) != Relation.INCOMPATIBLE;
        }

    /**
     * Calculate the type relationship between the specified TypeConstant (L-value) and the type
     * this TypeConstant (R-Value).
     *
     * @param thatLeft  the type to match (L-value)
     *
     * See Type.x # isA()
     */
    public Relation calculateRelation(TypeConstant thatLeft)
        {
        if (this.equals(thatLeft) || thatLeft.equals(getConstantPool().typeObject()))
            {
            return Relation.IS_A;
            }

        Map<TypeConstant, Relation> mapRelations = m_mapRelations;
        Relation relation;
        if (mapRelations == null)
            {
            // TODO: this is not thread safe
            mapRelations = m_mapRelations = new HashMap<>();
            relation = null;
            }
        else
            {
            relation = mapRelations.get(thatLeft);
            }

        if (relation == null)
            {
            mapRelations.put(thatLeft, Relation.IN_PROGRESS);
            }
        else
            {
            if (relation == Relation.IN_PROGRESS)
                {
                // we are in recursion; the answer is "no"
                mapRelations.put(thatLeft, Relation.INCOMPATIBLE);
                }
            return relation;
            }

        try
            {
            List<ContributionChain> chains = this.collectContributions(thatLeft,
                new ArrayList<>(), new ArrayList<>());
            if (chains.isEmpty())
                {
                mapRelations.put(thatLeft, relation = Relation.INCOMPATIBLE);
                }
            else
                {
                relation = validate(this, thatLeft, chains);
                mapRelations.put(thatLeft, relation);
                }
            return relation;
            }
        catch (RuntimeException | Error e)
            {
            mapRelations.remove(thatLeft);
            throw e;
            }
        }

    /**
     * Validate the list of chains that were collected by the collectContribution() method, but
     * now from the L-value's perspective. The chains that deemed to be non-fitting will be
     * deleted from the chain list.
     */
    protected static Relation validate(TypeConstant typeRight, TypeConstant typeLeft,
                                       List<ContributionChain> chains)
        {
        for (Iterator<ContributionChain> iter = chains.iterator(); iter.hasNext();)
            {
            ContributionChain chain = iter.next();

            if (!typeLeft.validateContributionFrom(typeRight, Access.PUBLIC, chain))
                {
                // rejected
                iter.remove();
                continue;
                }

            Contribution contrib = chain.first();
            if (contrib.getComposition() == Composition.MaybeDuckType)
                {
                TypeConstant typeIface = contrib.getTypeConstant();
                if (typeIface == null)
                    {
                    typeIface = typeLeft;
                    }

                if (!typeIface.isInterfaceAssignableFrom(
                        typeRight, Access.PUBLIC, Collections.EMPTY_LIST).isEmpty())
                    {
                    iter.remove();
                    }
                // TODO: how to mark the contribution as "duck type checked"
                }
            else
                {
                return chain.isWeakMatch() ? Relation.IS_A_WEAK : Relation.IS_A;
                }
            }

        return chains.isEmpty() ? Relation.INCOMPATIBLE : Relation.IS_A;
        }

    /**
     * Check if the specified TypeConstant (L-value) represents a type that is assignable to
     * values of the type represented by this TypeConstant (R-Value).
     *
     * @param thatLeft   the type to match (L-value)
     * @param listRight  the list of actual generic parameters for this type
     * @param chains     the list of chains to modify
     *
     * @return a list of ContributionChain objects that describe how "that" type could be found in
     *         the contribution tree of "this" type; empty if the types are incompatible
     */
    public List<ContributionChain> collectContributions(
            TypeConstant thatLeft, List<TypeConstant> listRight, List<ContributionChain> chains)
        {
        return getUnderlyingType().collectContributions(thatLeft, listRight, chains);
        }

    /**
     * Collect the contributions for the specified class that match this type (L-value).
     * Note that the parameter list is for the passed-in class rather than this type.
     *
     * @param clzRight   the class to check for a contribution
     * @param listRight  the list of actual generic parameters applicable to clzThat
     * @param chains     the list of chains to modify
     *
     * @return a list of ContributionChain objects that describe how this type could be found in
     *         the contribution tree of the specified class; empty if none is found
     */
    protected List<ContributionChain> collectClassContributions(
            ClassStructure clzRight, List<TypeConstant> listRight, List<ContributionChain> chains)
        {
        return getUnderlyingType().collectClassContributions(clzRight, listRight, chains);
        }

    /**
     * Check if this TypeConstant (L-value) represents a type that is assignable to
     * values of the type represented by the specified TypeConstant (R-Value) due to
     * the specified contribution chain.
     */
    protected boolean validateContributionFrom(TypeConstant thatRight, Access accessLeft,
                                               ContributionChain chain)
        {
        return getUnderlyingType().validateContributionFrom(thatRight, accessLeft, chain);
        }

    /**
     * Check if this TypeConstant (L-value), which is know to be an interface, represents a type
     * that is assignable to values of the type represented by the specified TypeConstant (R-Value).
     *
     * @param thatRight   the type to check the assignability from (R-value)
     * @param accessLeft  the access level to limit the checks to
     * @param listLeft    the list of actual generic parameters
     *
     * @return a set of method/property signatures from this type that don't have a match
     *         in the specified type
     */
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant thatRight,
                                                               Access accessLeft, List<TypeConstant> listLeft)
        {
        return getUnderlyingType().isInterfaceAssignableFrom(thatRight, accessLeft, listLeft);
        }

    /**
     * Check if this type contains a method or a property substitutable for the specified one.
     *
     * @param signature   the signature to check the substitutability for (resolved formal types)
     * @param access      the access level to limit the check to
     * @param listParams  the list of actual generic parameters
     *
     *  @return true iff the specified type could be assigned to this interface type
     */
    public boolean containsSubstitutableMethod(SignatureConstant signature,
                                               Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, listParams);
        }

    /**
     * Determine if this type consumes a formal type with the specified name in context
     * of the given TypeComposition and access policy.
     *
     * @param sTypeName   the formal type name
     * @param access      the access level to limit the check to
     * @param listParams  the list of actual generic parameters
     *
     * @return true iff this type is a consumer of the specified formal type
     */
    public boolean consumesFormalType(String sTypeName, Access access,
                                      List<TypeConstant> listParams)
        {
        return getUnderlyingType().consumesFormalType(sTypeName, access, listParams);
        }

    /**
     * Determine if this type produces a formal type with the specified name in context
     * of the given TypeComposition and access policy.
     *
     * @param sTypeName   the formal type name
     * @param access      the access level to limit the check to
     * @param listParams  the list of actual generic parameters
     *
     * @return true iff this type is a producer of the specified formal type
     */
    public boolean producesFormalType(String sTypeName, Access access,
                                      List<TypeConstant> listParams)
        {
        return getUnderlyingType().producesFormalType(sTypeName, access, listParams);
        }

    /**
     * Determine if this type can be directly assigned to or automatically converted to a specified
     * type automatically by the compiler.
     *
     * @param that  the type to convert to
     *
     * @return true iff the compiler can either directly assign the one type to the other, or can
     *         automatically convert the one type to something that is assignable to the other
     */
    public boolean isAssignableTo(TypeConstant that)
        {
        return isA(that) || getConverterTo(that) != null;
        }

    public MethodConstant getConverterTo(TypeConstant that)
        {
        return this.getTypeInfo().findConversion(that);
        }

    /**
     * Test for sub-classing.
     *
     * @param constClass  the class to test if this type represents an extension of
     *
     * @return true if this type represents a sub-classing of the specified class
     */
    public boolean extendsClass(IdentityConstant constClass)
        {
        return getUnderlyingType().extendsClass(constClass);
        }

    /**
     * @return true iff the TypeConstant represents a "class type", which is any type that is not an
     *         "interface type"
     */
    public boolean isClassType()
        {
        // generally, a type is a class type if any of the underlying types is a class type
        return getUnderlyingType().isClassType();
        }

    /**
     * @return true iff there is exactly one underlying class that makes this a class type
     */
    public boolean isSingleUnderlyingClass()
        {
        return getUnderlyingType().isSingleUnderlyingClass();
        }

    /**
     * Note: Only use this method if {@link #isSingleUnderlyingClass()} returns true.
     *
     * @return the one underlying class that makes this a class type
     */
    public IdentityConstant getSingleUnderlyingClass()
        {
        assert isClassType() && isSingleUnderlyingClass();

        return getUnderlyingType().getSingleUnderlyingClass();
        }

    /**
     * @return the set of constants representing the classes that make this type a class type
     */
    public Set<IdentityConstant> underlyingClasses()
        {
        return getUnderlyingType().underlyingClasses();
        }

    /**
     * Determine if this type refers to a class that can be used in an annotation, an extends
     * clause, an incorporates clause, or an implements clause.
     *
     * @param fAllowParams     true if type parameters are acceptable
     *
     * @return true iff this type is just a class identity, and the class identity refers to a
     *         class structure
     */
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return false;
        }

    /**
     * Determine the format of the explicit class, iff the type is an explicit class identity.
     *
     * @return a {@link Component.Format Component Format} value
     */
    public Component.Format getExplicitClassFormat()
        {
        throw new IllegalStateException();
        }

    /**
     * Find an underlying TypeConstant of the specified class.
     *
     * @return the matching TypeConstant or null
     */
    public <T extends TypeConstant> T findFirst(Class<? extends TypeConstant> clz)
        {
        return clz == getClass() ? (T) this : getUnderlyingType().findFirst(clz);
        }


    // ----- run-time support ----------------------------------------------------------------------

    /**
     * @return a handle for the Type object represented by this TypeConstant
     */
    public TypeHandle getHandle()
        {
        TypeHandle hType = m_handle;
        if (hType == null)
            {
            hType = m_handle = xType.makeHandle(this);
            }
        return hType;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public abstract Constant.Format getFormat();

    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();
        return isExplicitClassIdentity(true)
            ? pool.ensureParameterizedTypeConstant(pool.typeClass(), this)
            : pool.ensureParameterizedTypeConstant(pool.typeType(), this);
        }

    @Override
    protected abstract int compareDetails(Constant that);


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected abstract void registerConstants(ConstantPool pool);

    @Override
    protected abstract void assemble(DataOutput out)
            throws IOException;

    @Override
    public boolean validate(ErrorListener errlist)
        {
        boolean fHalt = false;

        if (!m_fValidated)
            {
            fHalt       |= super.validate(errlist);
            m_fValidated = true;
            }

        return fHalt;
        }

    protected boolean isValidated()
        {
        return m_fValidated;
        }

    @Override
    public String getDescription()
        {
        return "type=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public abstract int hashCode();


    // ----- inner classes ------------------------------------------------------------------

    /**
     * Resolves a generic type name into a type.
     */
    public interface GenericTypeResolver
        {
        /**
         * Resolve the generic type based on the PropertyConstant representing a formal parameter.
         *
         * @param constProperty  the PropertyConstant for the formal parameter
         *
         * @return a resolved type
         */
        TypeConstant resolveGenericType(PropertyConstant constProperty);
        }


    // -----inner classes --------------------------------------------------------------------------

    /**
     * Represents the "flattened" information about the type.
     */
    public static class TypeInfo
        {
        public TypeInfo(TypeConstant type)
            {
            assert type != null;
            this.type = type;
            }

        /**
         * @return the format of the topmost structure that the TypeConstant refers to
         */
        public Component.Format getFormat()
            {
            return m_formatActual;
            }

        void setFormat(Component.Format format)
            {
            assert format != null;
            assert m_formatActual == null;
            this.m_formatActual = format;
            }

        public GenericTypeResolver ensureTypeResolver(ErrorListener errs)
            {
            if (errs == m_errsResolver)
                {
                return m_typeresolver;
                }

            GenericTypeResolver resolver = new GenericTypeResolver()
                {
                @Override
                public TypeConstant resolveGenericType(PropertyConstant constProperty)
                    {
                    ParamInfo info = parameters.get(constProperty.getName());
                    if (info == null)
                        {
                        return constProperty.asTypeConstant();
//                        m_errsResolver.log(Severity.ERROR, VE_FORMAL_NAME_UNKNOWN,
//                                new Object[] {sName, type.getValueString()}, type);
//                        return type.getConstantPool().typeObject();
                        }

                    TypeConstant typeResolved = info.getActualType();
                    return typeResolved == null ? type.getConstantPool().typeObject() : typeResolved;
                    }
                };

            m_errsResolver = errs;
            m_typeresolver = resolver;

            return resolver;
            }

        /**
         * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
         *         not a mixin
         */
        public TypeConstant getInto()
            {
            return m_typeInto;
            }

        void setInto(TypeConstant type)
            {
            assert type != null;
            assert m_typeInto == null;
            this.m_typeInto = type;
            }

        public boolean isSingleton()
            {
            // TODO
            return false;
            }

        public boolean isAbstract()
            {
            // TODO
            return false;
            }

        public boolean isImmutable()
            {
            // TODO
            return false;
            }

        public boolean isService()
            {
            // TODO
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
                for (MethodInfo info : methods.values())
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
         * Given the specified method signature, find the most appropriate method that matches that
         * signature, and return that method. If there is no matching method, then return null. If
         * there are multiple methods that match, but it is ambiguous which method is "the best"
         * match, then log an error to the error list, and return null.
         *
         * @param constSig  the method signature to search for
         * @param errs      the error list to log errors to
         *
         * @return the MethodInfo for the method that is the "best match" for the signature, or null
         *         if no method is a best match (including the case in which more than one method
         *         matches, but no one of those methods is a provable unambiguous "best match")
         */
        public MethodInfo findMethod(SignatureConstant constSig, ErrorListener errs)
            {
            // TODO
            return null;
            }

        /**
         * Obtain all of the matching op methods for the specified name and/or the operator string, that
         * take the specified number of params.
         *
         * @param sName    the default op name, such as "add"
         * @param sOp      the operator string, such as "+"
         * @param cParams  the number of parameters for the operator method, such as 1
         *
         * @return a set of zero or more method constants
         */
        public Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams)
            {
            Set<MethodConstant> setOps = null;

            String sKey = sName + sOp + cParams;
            if (m_sOp != null && sKey.equals(m_sOp))
                {
                setOps = m_setOp;
                }
            else
                {
                for (MethodInfo info : getOpMethodInfos())
                    {
                    if (info.isOp(sName, sOp, cParams))
                        {
                        if (setOps == null)
                            {
                            setOps = new HashSet<>(7);
                            }
                        setOps.add(info.getMethodConstant());
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
                for (MethodInfo info : methods.values())
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
                    MethodConstant method = info.getMethodConstant();
                    TypeConstant typeResult = method.getRawReturns()[0];
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

        // data members
        public final TypeConstant type;
        public final Map<String, ParamInfo>             parameters = new HashMap<>();
        public final Map<String, PropertyInfo>          properties = new HashMap<>();
        public final Map<SignatureConstant, MethodInfo> methods    = new HashMap<>();

        /**
         * The Format of the topmost class structure.
         * TODO what about relational types?
         */
        private Component.Format m_formatActual;

        /**
         * This is one of {@link Component.Format#CLASS}, {@link Component.Format#INTERFACE}, and
         * {@link Component.Format#MIXIN}. It identifies how this type is actually used:
         * <ul>
         * <li>Class - this is a type that requires a specific class identity, either by being an
         * instance of that class, or by being a sub-class of that class;</li>
         * <li>Interface - this is an interface type, and ;</li>
         * <li>Mixin - this is an instantiable (or abstract or singleton) class type;</li>
         * </ul>
         */
        private Component.Format m_formatUsage;

        public final Set<TypeConstant> extended     = new HashSet<>();
        public final Set<TypeConstant> implemented  = new HashSet<>();
        public final Set<TypeConstant> incorporated = new HashSet<>();
        public final Set<TypeConstant> implicit     = new HashSet<>();

        private TypeConstant m_typeInto;

        // cached results
        private transient Set<MethodInfo>     m_setAuto;
        private transient Set<MethodInfo>     m_setOps;
        private transient String              m_sOp;
        private transient Set<MethodConstant> m_setOp;
        private transient TypeConstant        m_typeAuto;
        private transient MethodConstant      m_methodAuto;

        // cached resolver
        private transient ErrorListener       m_errsResolver;
        private transient GenericTypeResolver m_typeresolver;
        }


    /**
     * Represents a single type parameter.
     */
    public static class ParamInfo
        {
        public ParamInfo(String sName)
            {
            name = sName;
            }

        public String getName()
            {
            return name;
            }

        public TypeConstant getActualType()
            {
            return typeActual;
            }

        void setActualType(TypeConstant type)
            {
            typeActual = type;
            }

        public TypeConstant getConstraintType()
            {
            return typeConstraint;
            }

        void setConstraintType(TypeConstant type)
            {
            typeConstraint = type;
            }

        private String       name;
        private TypeConstant typeActual;
        private TypeConstant typeConstraint;

        // TODO - not sure if this is needed
        // private Set<Constant> setUsedBy = new HashSet<>(7);
        }


    /**
     * Represents a single property.
     */
    public static class PropertyInfo
        {
        public PropertyInfo(Constant constDeclares, PropertyStructure struct)
            {
            // TypeConstant typeProp, String sName
            constDeclLevel = constDeclares;
            type           = struct.getType();
            name           = struct.getName();
            access         = struct.getAccess();
            }

        public String getName()
            {
            return name;
            }

        public TypeConstant getType()
            {
            return type;
            }

        public Access getAccess()
            {
            return access;
            }

        // TODO need a way to widen access from protected to public

        public boolean isReadOnly()
            {
            return fRO;
            }

        void markReadOnly()
            {
            fRO = true;
            }

        public boolean isFieldRequired()
            {
            return fField;
            }

        void markFieldRequired()
            {
            fField = true;
            }

        public boolean isCustomLogic()
            {
            return fLogic;
            }

        void markCustomLogic()
            {
            fLogic = true;
            }

        public boolean isAnnotated()
            {
            return fAnnotated;
            }

        void markAnnotated()
            {
            fAnnotated = true;
            }

        public Constant getDeclarationLevel()
            {
            return constDeclLevel;
            }

        public String toString()
            {
            StringBuilder sb = new StringBuilder();

            if (fRO)
                {
                sb.append("@RO ");
                }

            sb.append(type.getValueString())
                    .append(' ')
                    .append(name);

            return sb.toString();
            }

        private String                             name;
        private TypeConstant                       type;
        private Access                             access;
        private boolean                            fRO;
        private boolean                            fField;
        private boolean                            fLogic;
        private boolean                            fAnnotated;
        private Constant                           constDeclLevel;
        private Map<SignatureConstant, MethodInfo> methods;
        }


    /**
     * Represents a single method (or function).
     */
    public static class MethodInfo
        {
        /**
         * Construct a MethodInfo.
         *
         * @param constSig  the signature for the new MethodInfo
         */
        public MethodInfo(SignatureConstant constSig)
            {
            m_constSig = constSig;
            }

        /**
         * Construct a MethodInfo that is a copy of another MethodInfo, but with a different
         * signature.
         *
         * @param constSig  the signature for the new MethodInfo
         * @param that      the old MethodInfo to copy state from
         */
        public MethodInfo(SignatureConstant constSig, MethodInfo that)
            {
            this(constSig);
            this.m_bodyPrimary = that.m_bodyPrimary;
            this.m_bodyDefault = that.m_bodyDefault;
            }

        public SignatureConstant getSignature()
            {
            return m_constSig;
            }

        /**
         * @return the first method body in the chain to call
         */
        public MethodBody[] ensureChain()
            {
            // check if we've already cached the chain
            MethodBody[] abody = m_abodyChain;
            if (abody == null)
                {
                ArrayList<MethodBody> list = new ArrayList<>();
                MethodBody body = m_bodyPrimary;
                while (body != null)
                    {
                    list.add(body);
                    body = body.getSuper();
                    }
                if (m_bodyDefault != null)
                    {
                    list.add(m_bodyDefault);
                    }
                assert !list.isEmpty();
                m_abodyChain = abody = list.toArray(new MethodBody[list.size()]);
                }

            return abody;
            }

        public MethodBody getFirstMethodBody()
            {
            return ensureChain()[0];
            }

        void prependBody(MethodBody body)
            {
            body.setSuper(m_bodyPrimary);
            m_bodyPrimary = body;
            m_abodyChain  = null;
            }

        void setDefault(MethodBody body)
            {
            m_bodyDefault = body;
            m_abodyChain  = null;
            }

        /**
         * @return the constant to use to invoke the method
         */
        public MethodConstant getMethodConstant()
            {
            // TODO is this correct (GG: review)
            return getFirstMethodBody().getMethodConstant();
            }

        /**
         * @return the access of the first method in the chain
         */
        public Access getAccess()
            {
            return getFirstMethodBody().getMethodStructure().getAccess();
            }

        /**
         * @return true iff this MethodInfo represents an "@Auto" auto-conversion method
         */
        public boolean isAuto()
            {
            return getFirstMethodBody().isAuto();
            }

        /**
         * @return true iff this MethodInfo represents an "@Op" operator method
         */
        public boolean isOp()
            {
            return getFirstMethodBody().findAnnotation(m_constSig.getConstantPool().clzOp()) != null;
            }

        /**
         * @return true iff this MethodInfo represents the specified "@Op" operator method
         */
        public boolean isOp(String sName, String sOp, int cParams)
            {
            return getFirstMethodBody().isOp(sName, sOp, cParams);
            }

        private SignatureConstant m_constSig;
        private MethodBody        m_bodyPrimary;
        private MethodBody        m_bodyDefault;
        private MethodBody[]      m_abodyChain;
        }


    /**
     * Represents a single method (or function) implementation body.
     */
    public static class MethodBody
        {
        public MethodBody(MethodConstant constMethod)
            {
            m_constMethod = constMethod;
            }

        /**
         * @return the MethodConstant that this MethodBody represents
         */
        public MethodConstant getMethodConstant()
            {
            return m_constMethod;
            }

        /**
         * @return the MethodStructure that this MethodBody represents
         */
        public MethodStructure getMethodStructure()
            {
            return (MethodStructure) m_constMethod.getComponent();
            }

        /**
         * Determine if this method is annotated with the specified annotation.
         *
         * @param clzAnno  the annotation class to look for
         *
         * @return the annotation, or null
         */
        public Annotation findAnnotation(ClassConstant clzAnno)
            {
            MethodStructure struct = getMethodStructure();
            if (struct.getAnnotationCount() > 0)
                {
                for (Annotation annotation : struct.getAnnotations())
                    {
                    if (((ClassConstant) annotation.getAnnotationClass()).extendsClass(clzAnno))
                        {
                        return annotation;
                        }
                    }
                }

            return null;
            }

        /**
         * @return true iff this is an auto converting method
         */
        public boolean isAuto()
            {
            // all @Auto methods must have no params and a single return value
            return  m_constMethod.getRawParams().length == 0 &&
                    m_constMethod.getRawReturns().length == 1 &&
                    findAnnotation(m_constMethod.getConstantPool().clzAuto()) != null;
            }

        /**
         * Determine if this is a matching "@Op" method.
         *
         * @param sName    the default name of the method
         * @param sOp      the operator text
         * @param cParams  the number of required method parameters
         *
         * @return true iff this is an "@Op" method that matches the specified attributes
         */
        public boolean isOp(String sName, String sOp, int cParams)
            {
            // the number of parameters must match
            if (m_constMethod.getRawParams().length != cParams)
                {
                return false;
                }

            // there has to be an @Op annotation
            // if the method name matches the default method name for the op, then we're ok;
            // otherwise we need to get the operator text from the operator annotation
            // (it's the first of the @Op annotation parameters)
            Annotation annotation = findAnnotation(m_constMethod.getConstantPool().clzOp());
            return annotation != null &&
                    (m_constMethod.getName().equals(sName) ||
                     annotation.getParams().length >= 1 && sOp.equals(annotation.getParams()[0]));
            }

        /**
         * @return the signature of the MethodConstant associated with this MethodBody
         */
        public SignatureConstant getSignature()
            {
            return getMethodConstant().getSignature();
            }

        /**
         * @return the Implementation form of this MethodBody
         */
        public Implementation getImplementationForm()
            {
            return m_impl;
            }

        void setImplementationForm(Implementation impl)
            {
            assert impl != null;
            m_impl = impl;
            }

        /**
         * @return the PropertyConstant of the property that provides the reference to delegate this
         *         method to
         */
        public PropertyConstant getDelegationProperty()
            {
            return m_constDelegProp;
            }

        void setDelegationProperty(PropertyConstant constProp)
            {
            m_impl           = Implementation.Delegating;
            m_constDelegProp = constProp;
            }

        /**
         * @return the MethodBody that represents the "super" of this MethodBody
         */
        public MethodBody getSuper()
            {
            return m_super;
            }

        void setSuper(MethodBody bodySuper)
            {
            m_super = bodySuper;
            }

        public enum Implementation {Implicit, Abstract, Delegating, Native, ActualCode}

        private MethodConstant   m_constMethod;
        private Implementation   m_impl = Implementation.ActualCode;
        private PropertyConstant m_constDelegProp;
        private MethodBody       m_super;
        }


    // -----fields ---------------------------------------------------------------------------------

    /**
     * Relationship options.
     */
    public enum Relation {IN_PROGRESS, IS_A, IS_A_WEAK, INCOMPATIBLE};

    /**
     * Keeps track of whether the TypeConstant has been validated.
     */
    private boolean m_fValidated;

    /**
     * The resolved information about the type, its properties, and its methods.
     */
    private transient TypeInfo m_typeinfo;

    /**
     * A cache of "isA" responses.
     */
    private Map<TypeConstant, Relation> m_mapRelations;

    /**
     * Cached TypeHandle.
     */
    private xType.TypeHandle m_handle;
    }