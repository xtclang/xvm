package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.TypeSet;


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
     * @return true iff exactly the number of specified params are specified
     */
    public boolean isParamsSpecified(int n)
        {
        return isParamsSpecified() && getParamTypes().size() == n;
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
                && constThis.getParamTypes().isEmpty();
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

    protected TypeConstant unwrapForCongruence()
        {
        return this;
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

    /**
     * @return this same type, but without any typedefs in it
     */
    public abstract TypeConstant resolveTypedefs();

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
                ? getParamTypes().get(0)
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
                ? constThis.getParamTypes().size()
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

        List<TypeConstant> listParamTypes = constThis.getParamTypes();
        if (i < 0 || i >= listParamTypes.size())
            {
            throw new IllegalArgumentException("i=" + i + ", size=" + listParamTypes.size());
            }

        return listParamTypes.get(i);
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
    public Set<MethodConstant> getOpMethods(String sName, String sOp, int cParams)
        {
        Set<MethodConstant> setOps = new HashSet<>(7);
        collectOpMethods(setOps, Access.PUBLIC, sName, sOp, cParams);
        return setOps;
        }

    /**
     * For this type, find all of the ops that match the name and/or the operator string that take
     * the specified number of params.
     *
     * @param setOps   the set to contribute to
     * @param access   the minimum accessibility that matching methods must have
     * @param sName    the default op name, such as "add"
     * @param sOp      the operator string, such as "+"
     * @param cParams  the number of parameters for the operator method, such as 1
     */
    protected void collectOpMethods(Set<MethodConstant> setOps, Access access, String sName, String sOp, int cParams)
        {
        // TODO
        // Set<MethodConstant> setOps = null;
        // ConstantPool        pool   = getConstantPool();
        // ClassConstant       clzOp  = pool.clzOp();
        // for (MethodConstant constMethod : getMethods(access))
        //     {
        //     // method constant can quickly eliminate parameter count mismatches
        //     if (constMethod.getRawParams().length == cParams)
        //         {
        //         constMethod.getName().equals(sName) &&
        //                 MethodStructure structMethod = (MethodStructure) constMethod.getComponent();
        //         Annotation annotation   = structMethod.findAnnotation(clzOp);
        //         if (annotation != null)
        //         }
        //     // method must be decorated with @Op
        //     constMethod.
        //             // method name must match, OR the @Op parameter must match sOp
        //                     method.getDecoration(pool.clAu))
        //     //method.getRawParams().length == cParams
        //     //method.getName().equals(sName))
        //     }

        getUnderlyingType().collectOpMethods(setOps, access, sName, sOp, cParams);
        }

    /**
     * Obtain all of the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> getAutoMethods()
        {
        Set<MethodConstant> setAuto = new HashSet<>(7);
        collectAutoMethods(setAuto, Access.PUBLIC);
        return setAuto;
        }

    /**
     * For this type, all of the auto conversion methods.
     *
     * @param setAuto  the set to contribute to
     * @param access   the minimum accessibility that matching methods must have
     */
    protected void collectAutoMethods(Set<MethodConstant> setAuto, Access access)
        {
        getUnderlyingType().collectAutoMethods(setAuto, access);
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
        Set<MethodConstant> setMethods = getAutoMethods();
        if (setMethods.isEmpty())
            {
            return null;
            }

        MethodConstant methodMatch = null;
        for (Iterator<MethodConstant> iter = setMethods.iterator(); iter.hasNext(); )
            {
            MethodConstant method     = iter.next();
            TypeConstant   typeResult = method.getRawReturns()[0];
            if (typeResult.equals(typeDesired))
                {
                // exact match -- it's not going to get any better than this
                return method;
                }

            if (typeResult.isA(typeDesired))
                {
                if (methodMatch != null)
                    {
                    // ambiguous - there are at least two methods that match
                    return null;
                    }

                methodMatch = method;
                }
            }

        return methodMatch;
        }

    /**
     * Collect all of the methods that would be available on this
     * @param access
     * @return
     */
    public Set<MethodConstant> getMethods(Access access)
        {
        // TODO
        return Collections.EMPTY_SET;
        }



    // ----- type comparison support ---------------------------------------------------------------

    /**
     * Determine if the specified TypeConstant represents a type that is assignable to values of
     * the type represented by this TypeConstant.
     * <p/>
     * Note: a negative answer doesn't guarantee non-assignability; it's simply an indication
     *       that a "long-path" computation should be done to prove or disprove it.
     *
     * @param that   the type to match
     *
     * See Type.x # isA()
     */
    public boolean isA(TypeConstant that)
        {
        if (this.equals(that) || that.equals(getConstantPool().typeObject()))
            {
            return true;
            }

        // TODO: should be a collection of ContributionChains
        ContributionChain chainTo = this.checkAssignableTo(that);
        if (chainTo == null)
            {
            return false;
            }

        if (!that.checkAssignableFrom(this, chainTo))
            {
            return false;
            }

        if (chainTo.getOrigin().getComposition() != Component.Composition.MaybeDuckType)
            {
            return true;
            }

        // TODO: there is a "Maybe"; need to do the "duck type" check
        return false;
        }

    protected ContributionChain checkAssignableTo(TypeConstant that)
        {
        return getUnderlyingType().checkAssignableTo(that);
        }

    protected boolean checkAssignableFrom(TypeConstant that, ContributionChain chain)
        {
        return getUnderlyingType().checkAssignableFrom(that, chain);
        }

    /**
     * Determine if this type consumes a formal type with the specified name in context
     * of the given TypeComposition and access policy.
     */
    public boolean consumesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return getUnderlyingType().consumesFormalType(sTypeName, types, access);
        }

    /**
     * Determine if this type produces a formal type with the specified name in context
     * of the given TypeComposition and access policy..
     */
    public boolean producesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return getUnderlyingType().producesFormalType(sTypeName, types, access);
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
     * Test for fake sub-classing (impersonation).
     *
     * @param constClass  the class to test if this type represents an impersonation of
     *
     * @return true if this type represents a fake sub-classing of the specified class
     */
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        return getUnderlyingType().impersonatesClass(constClass);
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
        return getUnderlyingType().extendsOrImpersonatesClass(constClass);
        }

    public Set<MethodConstant> autoConverts()
        {
        // TODO this is temporary (it just finds the one @Auto that exists on Object itself)
        // TODO make sure that @Override without @Auto hides the underlying @Auto method!!! (see Function.x)
        for (MethodStructure method : getConstantPool().clzObject()
                .getComponent().ensureMultiMethodStructure("to").methods())
            {
            if (method.getReturn(0).getType().isEcstasy("Function"))
                {
                return Collections.singleton(method.getIdentityConstant());
                }
            }
        throw new IllegalStateException("no method found: \"to<function Object()>()\"");
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
     * Find an underlying TypeConstant of the specified class.
     *
     * @return the matching TypeConstant or null
     */
    public <T extends TypeConstant> T findFirst(Class<? extends TypeConstant> clz)
        {
        return clz == getClass() ? (T) this : getUnderlyingType().findFirst(clz);
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public abstract Constant.Format getFormat();

    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();
        return pool.ensureParameterizedTypeConstant(pool.typeType(), this);
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
    public String getDescription()
        {
        return "type=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public abstract int hashCode();
    }
