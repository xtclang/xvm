package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import java.util.function.Function;

import org.xvm.asm.Annotation;
import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;
import org.xvm.asm.XvmStructure;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.PropertyBody.Effect;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.compiler.Compiler;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template._native.reflect.xRTType;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


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
 *
 * There are number of TypeConstant transformation APIs. If a transformation is based only on the
 * state of the TypeConstant itself, it doesn't require passing a target ConstantPool, since
 * the transformed constant doesn't create any new cross-pool dependencies and be can safely placed
 * into the same constant pool.
 */
public abstract class TypeConstant
        extends Constant
        implements GenericTypeResolver
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     */
    protected TypeConstant(ConstantPool pool, Constant.Format format, DataInput in)
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


    // ----- GenericTypeResolver -------------------------------------------------------------------

    @Override
    public TypeConstant resolveGenericType(String sFormalName)
        {
        return getGenericParamType(sFormalName, Collections.EMPTY_LIST);
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
     * Determine if this TypeConstant is composed of any of the specified identities.
     *
     * @param setIds the set of class IdentityConstants
     *
     * @return true iff this TypeConstant references any of the specified IdentityConstants for its
     *         composition
     */
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        return isModifyingType() && getUnderlyingType().isComposedOfAny(setIds);
        }

    /**
     * @return true iff the type specifies immutability
     */
    public boolean isImmutabilitySpecified()
        {
        return getUnderlyingType().isImmutabilitySpecified();
        }

    /**
     * @return true iff this TypeConstant refers to an immutable type
     */
    public boolean isImmutable()
        {
        return getUnderlyingType().isImmutable();
        }

    /**
     * Create a potentially new type that represents "this" immutable type.
     *
     * @return a type constant that represents an immutable type of this type constant
     */
    public TypeConstant freeze()
        {
        return isImmutable()
                ? this
                : getConstantPool().ensureImmutableTypeConstant(this);
        }

    /**
     * If this type is an Immutable type, calculate the type without the immutability.
     *
     * @return this TypeConstant without immutability
     */
    public TypeConstant removeImmutable()
        {
        if (!isImmutabilitySpecified())
            {
            return this;
            }

        ConstantPool pool = getConstantPool();

        // replace the TerminalType of the typeActual with the inception type
        Function<TypeConstant, TypeConstant> transformer = new Function<>()
            {
            public TypeConstant apply(TypeConstant type)
                {
                return type instanceof TerminalTypeConstant
                        ? type
                        : type instanceof ImmutableTypeConstant
                            ? type.getUnderlyingType()
                            : type.replaceUnderlying(pool, this);
                }
            };
        return transformer.apply(this);
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
     * If this type has access specified, calculate the type without an access modifier.
     *
     * @return this TypeConstant without an access modifier
     */
    public TypeConstant removeAccess()
        {
        if (!isAccessSpecified())
            {
            return this;
            }

        ConstantPool pool = getConstantPool();

        // replace the TerminalType of the typeActual with the inception type
        Function<TypeConstant, TypeConstant> transformer = new Function<>()
            {
            public TypeConstant apply(TypeConstant type)
                {
                return type instanceof TerminalTypeConstant
                        ? type
                        : type instanceof AccessTypeConstant
                            ? type.getUnderlyingType()
                            : type.replaceUnderlying(pool, this);
                }
            };
        return transformer.apply(this);
        }

    /**
     * @return true iff type parameters for the type are specified
     */
    public boolean isParamsSpecified()
        {
        return isModifyingType() && getUnderlyingType().isParamsSpecified();
        }

    /**
     * @return true iff type parameters for the type are specified at this level or any virtual
     *         parent
     */
    public boolean isParameterizedDeep()
        {
        return isParamsSpecified() ||
                isVirtualChild() && getParentType().isParameterizedDeep();
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
     * The {@link ParameterizedTypeConstant} is the only TypeConstant that allows a recursion,
     * e.g. {@code Array<Doc | Array<Doc>>}. This method measures the depth of the recursion.
     *
     * @return the depth of the {@link ParameterizedTypeConstant} recursion
     */
    protected int getParameterDepth()
        {
        return isModifyingType()
                ? getUnderlyingType().getParameterDepth()
                : 0;
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
        return isModifyingType()
                ? getUnderlyingType().getParamTypesArray()
                : ConstantPool.NO_TYPES;
        }

    /**
     * @return the type parameter at the specified index (Object if there are none)
     */
    public TypeConstant getParamType(int i)
        {
        return getParamsCount() == 0
                ? getConstantPool().typeObject()
                : getParamTypesArray()[i];
        }

    /**
     * @return true iff this type has a formal type parameter with the specified name
     */
    public boolean containsGenericParam(String sName)
        {
        return isModifyingType() && getUnderlyingType().containsGenericParam(sName);
        }

    /**
     * Find the type of the specified formal parameter for this type.
     *
     * Note, that this method is used to find a non-contradictory potential compile-time resolution
     * rather than a guaranteed run time one.
     *
     * For example: given a type: String[]? it's natural to decide at compile time that the type
     * for the "Element" formal name is String. However, at run time the answer may differ.
     *
     * @param sName      the formal parameter name
     * @param listParams the list of actual generic parameters
     *
     * @return the corresponding actual type or null if there is no matching formal type
     */
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        return isModifyingType()
                ? getUnderlyingType().getGenericParamType(sName, listParams)
                : null;
        }

    /**
     * @return true iff annotations of the type are specified
     */
    public boolean isAnnotated()
        {
        return isModifyingType() && getUnderlyingType().isAnnotated();
        }

    /**
     * @return true iff this type is annotated by the specified annotation class
     */
    public boolean containsAnnotation(ClassConstant idAnno)
        {
        return isAnnotated() && getUnderlyingType().containsAnnotation(idAnno);
        }

    /**
     * @return true iff this type is annotated by the specified annotation class
     */
    public Annotation[] getAnnotations()
        {
        return isAnnotated()
                ? getUnderlyingType().getAnnotations()
                : Annotation.NO_ANNOTATIONS;
        }

    /**
     * @return true iff this type represents a virtual child type
     */
    public boolean isVirtualChild()
        {
        return isModifyingType() && getUnderlyingType().isVirtualChild();
        }

    /**
     * @return true iff this type represents an anonymous class type
     */
    public boolean isAnonymousClass()
        {
        return isModifyingType() && getUnderlyingType().isAnonymousClass();
        }

    /**
     * A virtual child type is said to be a "phantom" if it represents a child with a non-existing
     * structure. For example, let's say we have classes:
     *
     * <pre><code>
     *   class B
     *       {
     *       class C // child of B
     *            {
     *            }
     *       }
     *   class D
     *       extends B
     *       {
     *       }
     * </code></pre>
     *
     * In this case virtual child type VCT(D, "C") is a phantom virtual child type, but VCT(B, "C")
     * is not.
     *
     * @return whether or not this type represents a phantom virtual child
     */
    public boolean isPhantom()
        {
        assert isVirtualChild();
        return getUnderlyingType().isPhantom();
        }

    /**
     * @return true iff this type is the type of a decorated class constant
     */
    public boolean isDecoratedClass()
        {
        if (!isExplicitClassIdentity(true))
            {
            return false;
            }

        if (isAnnotated() || getParamsCount() > 0)
            {
            return true;
            }

        return (isVirtualChild() || isAnonymousClass()) && getParentType().isDecoratedClass();
        }

    /**
     * @return return the virtual child type's or anonymous class type's parent type
     */
    public TypeConstant getParentType()
        {
        assert isVirtualChild() || isAnonymousClass();
        return getUnderlyingType().getParentType();
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
     * Check if this type contains any auto-narrowing portion.
     *
     * @param fAllowVirtChild  if false, the virtual child constant should not be considered
     *                         as auto-narrowing
     *
     * @return true iff this TypeConstant represents an auto-narrowing type
     */
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return getUnderlyingType().isAutoNarrowing(fAllowVirtChild);
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
     * @return true iff this type constant is a non-relational type constant for the Ecstasy Type
     *         type
     */
    public boolean isTypeOfType()
        {
        return getUnderlyingType().isTypeOfType();
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
            throw new IllegalArgumentException("no such implicit name: " + sName);
            }

        return isSingleDefiningConstant() && getDefiningConstant().equals(constId);
        }

    /**
     * @return the Ecstasy class name, including package name(s), otherwise "?"
     */
    public String getEcstasyClassName()
        {
        return isSingleDefiningConstant()
                    && getDefiningConstant() instanceof ClassConstant
                    && ((ClassConstant) getDefiningConstant()).getModuleConstant().isEcstasyModule()
                    && getAccess() == Access.PUBLIC
                ? ((ClassConstant) getDefiningConstant()).getPathString()
                : "?";
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
     * If null cannot be assigned to this type, then create a new type that minimally encompasses
     * this type and the Null value.
     *
     * @return the type, modified if necessary to allow it to support Null values
     */
    public TypeConstant ensureNullable()
        {
        return getConstantPool().ensureNullableTypeConstant(this);
        }

    /**
     * If this type is a nullable type, calculate the type without the nullability.
     *
     * @return this TypeConstant without Nullable
     */
    public TypeConstant removeNullable()
        {
        return this;
        }

    /**
     * Produce a minimal representation of a type that is known to be assignable to both this
     * and the specified type. The resulting type is guaranteed to be the same or narrower than
     * this type.
     *
     * @return a reduction for the union of two types
     */
    public TypeConstant combine(ConstantPool pool, TypeConstant that)
        {
        TypeConstant thisResolved = this.resolveTypedefs();
        TypeConstant thatResolved = that.resolveTypedefs();
        if (thisResolved != this || thatResolved != that)
            {
            return thisResolved.combine(pool, thatResolved);
            }

        if (this.isA(that))
            {
            return this;
            }
        if (that.isA(this))
            {
            return that;
            }

        // since Enum values cannot be extended, an Enum value type cannot be combined with any
        // other type; this obviously applies to Nullable
        if (this.isExplicitClassIdentity(false) &&
            this.getExplicitClassFormat() == Component.Format.ENUMVALUE
            || this.isOnlyNullable())
            {
            return this;
            }

        // theoretically speaking, we could have done the same Enum value check for "that" type
        // as above; the problem is, however, that this method is expected to return a type that is
        // equal to or narrower than "this" and returning "that" might may break that expectation

        // type Type is known to have a distributive property:
        // Type<X> + Type<Y> == Type<X + Y>
        return this.isTypeOfType() && this.getParamsCount() > 0 &&
               that.isTypeOfType() && that.getParamsCount() > 0
                ? this.getParamType(0).combine(pool, that.getParamType(0)).getType()
                : pool.ensureUnionTypeConstant(this, that);
        }

    /**
     * Produce a minimal representation of a type that both this type and the specified type are
     * known to be assignable to.
     *
     * @return a reduction for the intersection of two types
     */
    public TypeConstant intersect(ConstantPool pool, TypeConstant that)
        {
        TypeConstant thisResolved = this.resolveTypedefs();
        TypeConstant thatResolved = that.resolveTypedefs();
        if (thisResolved != this || thatResolved != that)
            {
            return thisResolved.intersect(pool, thatResolved);
            }

        if (this.isA(that))
            {
            return that;
            }
        if (that.isA(this))
            {
            return this;
            }

        // type Type is known to have a distributive property:
        // Type<X> | Type<Y> == Type<X | Y>
        return this.isTypeOfType() && this.getParamsCount() > 0 &&
               that.isTypeOfType() && that.getParamsCount() > 0
                ? this.getParamType(0).intersect(pool, that.getParamType(0)).getType()
                : pool.ensureIntersectionTypeConstant(this, that);
        }

    /**
     * Produce a minimal representation the type that is known to be assignable to this type but
     * is also known not to be assignable to the specified type. The resulting type is guaranteed
     * to be the same or narrower than this type.
     * <p/>
     * Unless this type is relational, the "not assignable to the specified type" part of the
     * contract may not be achievable.
     *
     * @return a type that is equal or narrower than this type
     */
    public TypeConstant andNot(ConstantPool pool, TypeConstant that)
        {
        TypeConstant thisResolved = this.resolveTypedefs();
        TypeConstant thatResolved = that.resolveTypedefs();
        if (thisResolved != this || thatResolved != that)
            {
            return thisResolved.andNot(pool, thatResolved);
            }

        // type Type is known to have a distributive property:
        // Type<X> - Type<Y> == Type<X - Y>
        return this.isTypeOfType() && this.getParamsCount() > 0 &&
               that.isTypeOfType() && that.getParamsCount() > 0
                ? this.getParamType(0).andNot(pool, that.getParamType(0)).getType()
                : this;
        }

    /**
     * Create a copy of this single defining type that is based on the specified underlying type.
     *
     * @param pool  the ConstantPool to place a potentially created new constant into
     *
     * @return clone this type based on the underlying type
     */
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        throw new UnsupportedOperationException();
        }

    /**
     * Determine if the specified name is referring to a name introduced by any of the contributions
     * for this type.
     *
     * @param sName      the name to resolve
     * @param access     the required access
     * @param collector  the collector to which the potential name matches will be reported
     *
     * @return the resolution result
     */
    public ResolutionResult resolveContributedName(String sName, Access access, ResolutionCollector collector)
        {
        return getUnderlyingType().resolveContributedName(sName, access, collector);
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        return constResolved == constOriginal
            ? this
            : cloneSingle(getConstantPool(), constResolved);
        }

    /**
     * Bind any {@link TypeParameterConstant} that happens to be referred by this TypeConstant
     * to the specified {@link MethodConstant}.
     *
     * This method is used to resolve a circular dependency between the MethodConstant and
     * TypeParameterConstant.
     *
     * @see {@link TypeParameterConstant#resolveTypedefs}
     */
    public void bindTypeParameters(MethodConstant idMethod)
        {
        getUnderlyingType().bindTypeParameters(idMethod);
        }

    /**
     * Create a semantically equivalent type that resolves the formal type parameters
     * based on the specified resolver.
     *
     * Note: the resolved parameters could in turn also be formal parameters.
     *
     * @param pool  the ConstantPool to place a potentially created new constant into
     *
     * @return a semantically equivalent type with resolved formal parameters
     */
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveGenerics(pool, resolver);

        return constResolved == constOriginal
                ? this
                : cloneSingle(pool, constResolved);
        }

    /**
     * If this type contains any formal type, replace that formal type with its constraint type.
     *
     * @return the resulting type
     */
    public TypeConstant resolveConstraints()
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveConstraints();
        return constResolved == constOriginal
                ? this
                : cloneSingle(getConstantPool(), constResolved);
        }

    /**
     * @return this same type, but with the number of parameters equal to the number of
     *         formal parameters for the the underlying terminal type, assigning missing
     *         type parameters to the corresponding canonical types
     */
    public TypeConstant normalizeParameters()
        {
        TypeConstant typeNormalized = m_typeNormalized;
        if (typeNormalized == null)
            {
            m_typeNormalized = typeNormalized =
                    adoptParameters(getConstantPool(), (TypeConstant[]) null);
            }
        return typeNormalized;
        }

    /**
     * Create a semantically equivalent type that is parameterized by the parameters of the
     * specified type and normalized (the total number of parameters equal to the number of
     * formal parameters for the underlying terminal type, where missing parameters are assigned
     * to the resolved canonical types).
     *
     * @param pool      the ConstantPool to place a potentially created new constant into
     * @param typeFrom  the type to adopt type parameters from
     *
     * @return potentially new normalized type parameterized by the specified type parameters
     */
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant typeFrom)
        {
        assert !this.isParamsSpecified();

        TypeConstant typeSansParams = typeFrom;
        if (typeFrom.isParamsSpecified())
            {
            do
                {
                typeSansParams = typeSansParams.getUnderlyingType();
                }
            while (typeSansParams.isParamsSpecified());
            }

        if (this.isA(typeSansParams))
            {
            return adoptParameters(pool, typeFrom.getParamTypesArray());
            }

        if (!this.isVirtualChild() && typeSansParams.isVirtualChild())
            {
            // adopt from the first parameterized parent
            TypeConstant typeParent = typeSansParams.getParentType();
            while (true)
                {
                if (typeParent.isParamsSpecified())
                    {
                    return adoptParameters(pool, typeParent.getParamTypesArray());
                    }
                if (!typeParent.isVirtualChild())
                    {
                    break;
                    }
                typeParent = typeParent.getParentType();
                }
            }

        return this;
        }

    /**
     * Create a semantically equivalent type that is parameterized by the specified type parameters,
     * and normalized (the total number of parameters equal to the number of formal parameters
     * for the underlying terminal type, where missing parameters are assigned to the resolved
     * canonical types).
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param atypeParams the parameters to adopt or null if the parameters of this type are
     *                    simply to be normalized
     *
     * @return potentially new normalized type that is parameterized by the specified types
     */
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.adoptParameters(pool, atypeParams);

        return constResolved == constOriginal
                ? this
                : cloneSingle(pool, constResolved);
        }

    /**
     * Collect an array of generic type parameters for this "formalizable" type.
     * <p/>
     * A type that has a {@link #isExplicitClassIdentity explicit class identity} is formalizable
     * iff it is not already parameterized.
     * <br>
     * A relational type is formalizable if its both "legs" are formalizable and have identical
     * or empty arrays of generic type parameters.
     *
     * @return null if the type is not formalizable; an empty array if the type doesn't have
     *         generic type parameters; and a non-empty array otherwise
     */
    public TypeConstant[] collectGenericParameters()
        {
        return getUnderlyingType().collectGenericParameters();
        }

    /**
     * If this type is auto-narrowing (or has any references to auto-narrowing types), replace any
     * auto-narrowing portion with an explicit class identity in the context of the specified target.
     *
     * Note that the target identity must be a sub-type of this type.
     *
     * @param pool           the ConstantPool to place a potentially created new constant into
     * @param fRetainParams  if true, don't attempt to resolve the type parameters
     * @param typeTarget     the context target type
     *
     * @return the TypeConstant with explicit identities swapped in for any auto-narrowing
     *         identities
     */
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveAutoNarrowing(pool, fRetainParams, typeTarget);

        return constResolved == constOriginal
            ? this
            : cloneSingle(pool, constResolved);
        }

    /**
     * Helper method that replaces an auto-narrowing "base" portion of the type, but doesn't
     * resolve any of the type parameters.
     *
     * @return the TypeConstant with explicit base identity swapped in for an auto-narrowing
     *         base identity
     */
    public TypeConstant resolveAutoNarrowingBase()
        {
        return resolveAutoNarrowing(getConstantPool(), true, null);
        }

    /**
     * Create a new type by replacing the underlying type for this one according to the specified
     * function.
     *
     * Note, that a TerminalTypeConstant doesn't have an underlying type and is not "transformable".
     *
     * @param pool         the ConstantPool to place a potentially created new constant into
     * @param transformer  the transformation function
     *
     * @return potentially transformed type
     */
    public TypeConstant replaceUnderlying(ConstantPool pool, Function<TypeConstant, TypeConstant> transformer)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = transformer.apply(constOriginal);

        return constResolved == constOriginal
                ? this
                : cloneSingle(pool, constResolved);
        }

    /**
     * Given this (formal) type A<B<C>, <D<R>> that may contain a type parameter "R" and an actual
     * type A<X<Y>, w<Z>>, find out what is the actual type of the type parameter "R".
     *
     * @return the resolved actual type or null if there is no matching type parameter or
     *         tha actual type topology is not the same as this type and doesn't provide enough
     *         fidelity (e.g.: formal is Array<R> and actual is Object)
     */
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        return getUnderlyingType().resolveTypeParameter(typeActual, sFormalName);
        }

    /**
     * @return true iff the type is a Tuple type
     */
    public boolean isTuple()
        {
        return isSingleDefiningConstant() && getUnderlyingType().isTuple();
        }

    /**
     * @return the list of type parameters for this Tuple type
     */
    public List<TypeConstant> getTupleParamTypes()
        {
        assert isTuple();

        IdentityConstant idTuple  = getSingleUnderlyingClass(true);
        ClassStructure   clzTuple = (ClassStructure) idTuple.getComponent();

        return clzTuple.getTupleParamTypes(getParamTypes());
        }

    /**
     * @return true iff the type is an Array type
     */
    public boolean isArray()
        {
        TypeConstant constThis = resolveTypedefs();
        assert !constThis.containsUnresolved();
        return constThis.isA(getConstantPool().typeArray());
        }

    /**
     * Determine compatibility for purposes of comparing equality.
     *
     * @param that             another type
     * @param fThatIsConstant  if the value of the other type is a constant
     *
     * @return true iff a value of this type can be compared with a value of the other type for
     *         equality
     */
    public boolean supportsEquals(TypeConstant that, boolean fThatIsConstant)
        {
        assert that != null;

        ConstantPool pool = getConstantPool();

        TypeConstant typeThis = this.resolveAutoNarrowing(pool, false, null);
        TypeConstant typeThat = that.resolveAutoNarrowing(that.getConstantPool(), false, null);

        if (typeThis.getParamsCount() != typeThat.getParamsCount())
            {
            typeThis = typeThis.normalizeParameters();
            typeThat = typeThat.normalizeParameters();
            }

        // when the types are the same, then the values are comparable; in the case that the value
        // is a constant, it's allowed to be of a wider type; for example:
        //   Constant c = ...
        //   if (c == "hello") // a valid comparison
        //
        // Additionally, any Ref objects are comparable, since it means the actual Ref-equality
        if (typeThis.equals(typeThat) || fThatIsConstant && typeThat.isA(typeThis)
         || typeThis.isA(pool.typeRef()) && typeThat.isA(pool.typeRef()))
            {
            return true;
            }

        if (this.isTypeOfType() && that.isTypeOfType())
            {
            // Type types are always comparable
            return true;
            }

        if (this.isTuple() && that.isTuple())
            {
            // tuples can be comparable if the types are not explicitly specified
            TypeConstant[] atypeThis = this.getParamTypesArray();
            TypeConstant[] atypeThat = that.getParamTypesArray();
            for (int i = 0, c = Math.min(atypeThis.length, atypeThat.length); i < c; i++)
                {
                if (!atypeThis[i].supportsEquals(atypeThat[i], false))
                    {
                    return false;
                    }
                }
            return true;
            }

        // we also allow a comparison of a nullable type to the base type; for example:
        // String? s1 = ...
        // String  s2 = ...
        // if (s1 == s2) // is allowed despite the types are not equal
        // TODO: consider allowing this for any IntersectionType: (T1 | T2) == T1

        if (typeThis.isNullable() || typeThat.isNullable())
            {
            return typeThis.removeNullable().equals(typeThat.removeNullable());
            }

        return false;
        }

    /**
     * Determine compatibility for purposes of comparing order.
     *
     * @param that             another type
     * @param fThatIsConstant  if the value of the other type is a constant
     *
     * @return true iff a value of this type can be compared with a value of the other type for
     *         order
     */
    public boolean supportsCompare(TypeConstant that, boolean fThatIsConstant)
        {
        assert that != null;

        ConstantPool pool = getConstantPool();

        TypeConstant typeThis = this.resolveAutoNarrowing(pool, false, null);
        TypeConstant typeThat = that.resolveAutoNarrowing(that.getConstantPool(), false, null);

        if (typeThis.equals(typeThat) || fThatIsConstant && typeThat.isA(typeThis))
            {
            return findFunctionInfo(pool.sigCompare()) != null;
            }

        if (this.isTypeOfType() && that.isTypeOfType())
            {
            // Type types are always comparable
            return true;
            }

        return false;
        }

    /**
     * Return an argument for this type constant. For all concrete types, this simply returns a
     * type of this type. The only TypeConstant that treats it differently is a formal type
     * parameter, which would return a corresponding register
     *
     * @return the argument
     */
    public Argument getTypeArgument()
        {
        return getType();
        }

    /**
     * Check whether or not this type represents a "nest mate" of the specified class.
     *
     * @param idClass  the identity of the class
     */
    public boolean isNestMateOf(IdentityConstant idClass)
        {
        return !isFormalType() &&
                isExplicitClassIdentity(true) &&
                isSingleUnderlyingClass(false) &&
                getSingleUnderlyingClass(false).isNestMateOf(idClass);
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    /**
     * Obtain all of the information about this type, resolved from its recursive composition.
     *
     * @return the flattened TypeInfo that represents the resolved type of this TypeConstant
     */
    public TypeInfo ensureTypeInfo()
        {
        return ensureTypeInfo(getErrorListener());
        }

    /**
     * Obtain all of the information about this type, while being used in a context of the
     * specified class.
     *
     * @param idClass  the identity of the class
     * @param errs     the error listener to log errors to
     *
     * @return the flattened TypeInfo that represents all aspects of this type when used in
     *         a given class context
     */
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs)
        {
        return (isNestMateOf(idClass)
                ? getConstantPool().ensureAccessTypeConstant(this, Access.PRIVATE)
                : this).
            ensureTypeInfo(errs);
        }

    /**
     * Obtain all of the information about this type, resolved from its recursive composition.
     *
     * @param errs  the error listener to log errors to
     *
     * @return the flattened TypeInfo that represents the resolved type of this TypeConstant
     */
    public TypeInfo ensureTypeInfo(ErrorListener errs)
        {
        TypeInfo info = getTypeInfo();
        if (isComplete(info) && isUpToDate(info))
            {
            return info;
            }

        ConstantPool pool = getConstantPool();
        if (info == null)
            {
            // validate this TypeConstant (necessary before we build the TypeInfo)
            if (validate(errs) || containsUnresolved())
                {
                log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, getValueString());
                return pool.typeObject().ensureTypeInfo(errs);
                }
            }

        // since we're producing a lot of information for the TypeInfo, there is no reason to do
        // it unless the type is registered (which resolves typedefs)
        TypeConstant typeResolved = (TypeConstant) pool.register(this);

        // Additionally:
        // - resolve the auto-narrowing;
        // - normalize the type to make sure that all formal parameters are filled in
        typeResolved = typeResolved.
                            resolveAutoNarrowing(pool, false, null).
                            normalizeParameters();
        if (typeResolved != this)
            {
            info = typeResolved.ensureTypeInfo(errs);
            setTypeInfo(info);
            return info;
            }

        // this is where things get very, very complicated. this method is responsible for returning
        // a "completed" TypeInfo, but there are (theoretically) lots of threads trying to do the
        // same or similar thing at the same time, and any one thread can end up in a recursive
        // situation in which to complete the TypeInfo for type X, it has to get the TypeInfo for
        // type Y, and do build that, it has to get the TypeInfo for type X. this is a catch-22!
        // so what we do to avoid this is to have two layers of requests:
        // 1) the requests from the outside (naive) world come to ensureTypeInfo(), and those
        //    requests *must* be responded to with a "completed" TypeInfo
        // 2) internal requests, like the ones causing the catch-22, can be responded to with an
        //    incomplete TypeInfo, which is sufficient to build the dependent TypeInfo, but which
        //    in turn must be completed once the dependent (which is also a depended-upon) TypeInfo
        //    is complete

        // there is a place-holder that signifies that a type is busy building a TypeInfo;
        // mark the type as having its TypeInfo building "in progress"
        setTypeInfo(pool.infoPlaceholder());

        // since this can only be used "from the outside", there should be no deferred TypeInfo
        // objects at this point
        if (hasDeferredTypeInfo())
            {
            throw new IllegalStateException("Infinite loop while producing a TypeInfo for "
                    + this + "; deferred types=" + takeDeferredTypeInfo());
            }

        errs = errs.branch();

        try
            {
            // build the TypeInfo for this type
            info = buildTypeInfo(errs);
            if (info != null)
                {
                setTypeInfo(info);
                }

            for (int cDeferredPrev = 0, iTry = 0; hasDeferredTypeInfo(); iTry++)
                {
                // any downstream TypeInfo that could not be completed during the building of
                // this TypeInfo is considered to be "deferred", but now that we've built
                // something (even if it isn't complete), we should be able to complete the
                // deferred TypeInfo building in a couple of iterations
                List<TypeConstant> listDeferred = takeDeferredTypeInfo();
                int                cDeferred    = listDeferred.size();
                if (iTry > 2 && cDeferred >= cDeferredPrev)
                    {
                    throw new IllegalStateException("Failure to make progress on a TypeInfo for "
                            + this + "; deferred types=" + listDeferred);
                    }
                cDeferredPrev = cDeferred;

                for (TypeConstant typeDeferred : listDeferred)
                    {
                    if (typeDeferred != this)
                        {
                        if (typeDeferred.getConstantPool() != pool)
                            {
                            typeDeferred = (TypeConstant) pool.register(typeDeferred);
                            }

                        TypeInfo infoDeferred = typeDeferred.getTypeInfo();
                        if (!isComplete(infoDeferred))
                            {
                            // if there's something wrong with this logic, we'll end up with infinite
                            // recursion, so be very careful about what can allow a TypeInfo to be built
                            // "incomplete" (it needs to be impossible to rebuild a TypeInfo and have it
                            // be incomplete for the second time)
                            if (++m_cRecursiveDepth > 2)
                                {
                                // an infinite loop
                                throw new IllegalStateException("Infinite loop while producing a TypeInfo for "
                                        + this + "; deferred type=" + typeDeferred);
                                }
                            infoDeferred = typeDeferred.buildTypeInfo(errs);
                            --m_cRecursiveDepth;

                            if (isComplete(infoDeferred) && !errs.hasSeriousErrors())
                                {
                                typeDeferred.setTypeInfo(infoDeferred);
                                }
                            }
                        }
                    }

                // now that all deferred types are done building, rebuild this if necessary
                if (!isComplete(info))
                    {
                    info = buildTypeInfo(errs);
                    if (info != null)
                        {
                        setTypeInfo(info);
                        }
                    }
                }
            }
        catch (Exception | Error e)
            {
            // clean up the deferred types
            takeDeferredTypeInfo();
            throw e;
            }

        if (errs.hasSeriousErrors())
            {
            // we need to return what we've got, but don't cache it
            invalidateTypeInfo();
            }
        errs.merge();
        return info;
        }

    /**
     * Build the TypeInfo, but if necessary, return an incomplete TypeInfo, or even worse, null.
     *
     * @param errs  the error list to log to
     *
     * @return a TypeInfo that may or may not be complete, or may be null if it's impossible to
     *         build the TypeInfo at this point due to recursion
     */
    protected TypeInfo ensureTypeInfoInternal(ErrorListener errs)
        {
        TypeInfo info = getTypeInfo();
        if (info != null && info.isPlaceHolder())
            {
            // the TypeInfo is already being built, so we're in the catch-22 situation; note that it
            // is even more complicated, because it could be being built by a different thread, so
            // always add it to the deferred list _on this thread_ so that we will force the rebuild
            // of the TypeInfo if necessary (imagine that the other thread is super slow, so we need
            // to preemptively duplicate its work on this thread, so we don't have to "wait" for
            // the other thread)
            addDeferredTypeInfo(this);
            return null;
            }

        if (!isComplete(info) || !isUpToDate(info))
            {
            setTypeInfo(getConstantPool().infoPlaceholder());
            info = buildTypeInfo(errs);
            if (info != null)
                {
                setTypeInfo(info);
                }
            if (!isComplete(info))
                {
                addDeferredTypeInfo(this);
                }
            }

        return info;
        }

    /**
     * Obtain the TypeInfo associated with this type.
     *
     * @return one of: null, a place-holder TypeInfo (if the TypeInfo is currently being built), an
     *         "incomplete" TypeInfo, or a finished TypeInfo
     */
    protected TypeInfo getTypeInfo()
        {
        return s_typeinfo.get(this);
        }

    /**
     * Store the specified TypeInfo for this type. Note that this is a "one way" setter, in that
     * the setter only stores the value if it is "better than" the existing value.
     *
     * @param info  the new TypeInfo
     */
    protected void setTypeInfo(TypeInfo info)
        {
        TypeInfo infoOld;
        while (rankTypeInfo(info) > rankTypeInfo(infoOld = s_typeinfo.get(this))
                || info.isPlaceHolder())
            {
            // update the TypeInfo
            if (s_typeinfo.compareAndSet(this, infoOld, info))
                {
                // update the invalidation count that we have caught up to at this point
                setInvalidationCount(info.getInvalidationCount());
                break;
                }
            }
        }

    /**
     * @return the invalidation count that this TypeConstant has already processed
     */
    protected int getInvalidationCount()
        {
        return s_cInvalidations.get(this);
        }

    /**
     * Modify the invalidation count (but don't ever regress it).
     *
     * @param cNew  the new invalidation count
     */
    protected void setInvalidationCount(int cNew)
        {
        int cOld;
        while ((cOld = s_cInvalidations.get(this)) < cNew)
            {
            s_cInvalidations.compareAndSet(this, cOld, cNew);
            }
        }

    /**
     * Specify that the TypeInfo held by this type is no longer valid, as is any other TypeInfo
     * built from the underlying class of this type.
     */
    public void invalidateTypeInfo()
        {
        clearTypeInfo();

        if (isSingleUnderlyingClass(true))
            {
            getConstantPool().invalidateTypeInfos(getSingleUnderlyingClass(true));
            }
        }

    /**
     * Clear out any cached TypeInfo for this one specific TypeConstant.
     */
    public void clearTypeInfo()
        {
        s_typeinfo.set(this, null);
        }

    /**
     * Rank is null, place-holder, incomplete, complete.
     *
     * @param info  a TypeInfo
     *
     * @return the rank of the TypeInfo
     */
    private static int rankTypeInfo(TypeInfo info)
        {
        return info == null
                ? 0
                : info.getProgress().ordinal();
        }

    /**
     * @param info  the TypeInfo to evaluate
     *
     * @return true iff the passed TypeInfo is non-null, not the place-holder, and not incomplete
     */
    public static boolean isComplete(TypeInfo info)
        {
        return rankTypeInfo(info) == 3;
        }

    /**
     * Determine if the passed TypeInfo is up-to-date for this type.
     *
     * @param info  the TypeInfo
     *
     * @return true iff the TypeInfo can be used as-is
     */
    protected boolean isUpToDate(TypeInfo info)
        {
        ConstantPool pool       = getConstantPool();
        int          cOldInvals = getInvalidationCount();
        int          cNewInvals = pool.getInvalidationCount();
        if (cNewInvals == cOldInvals)
            {
            return true;
            }

        if (info.needsRebuild(pool.invalidationsSince(cOldInvals)))
            {
            return false;
            }

        setInvalidationCount(cNewInvals);
        return true;
        }

    /**
     * Create a TypeInfo for this type.
     *
     * @param errs  the error list to log any errors to
     *
     * @return a new TypeInfo representing this TypeConstant, or null iff building a type info for
     *         this type is currently impossible because it requires a different TypeInfo that is
     *         already in the process of being built
     */
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // any newly created derivative types and various constants should be placed into the same
        // pool where this type comes from
        try (var x = ConstantPool.withPool(getConstantPool()))
            {
            return buildTypeInfoImpl(errs);
            }
        }

    /**
     * Actual buildTypeInfo implementation.
     */
    private synchronized TypeInfo buildTypeInfoImpl(ErrorListener errs)
        {
        // the raw type-info has to be built as either ":private" or ":struct", so delegate the
        // building for ":public" to ":private", and then strip out the non-accessible members
        switch (getAccess())
            {
            case STRUCT:
                return buildStructInfo(errs);

            case PRIVATE:
                // this is the one type that actually gets built by this method
                break;

            case PROTECTED:
                // this should have been handled by the AccessTypeConstant
                throw new IllegalStateException();

            case PUBLIC:
                assert !isAccessSpecified();
                TypeInfo info = getConstantPool().ensureAccessTypeConstant(this, Access.PRIVATE)
                        .ensureTypeInfoInternal(errs);
                return info == null
                        ? null
                        : info.limitAccess(Access.PUBLIC);
            }

        // annotated types require special handling
        if (isAnnotated())
            {
            TypeConstant typeUnderlying = getUnderlyingType(); // remove "Access:PRIVATE" piece
            if (typeUnderlying instanceof AnnotatedTypeConstant)
                {
                return ((AnnotatedTypeConstant) typeUnderlying).buildPrivateInfo(errs);
                }

            TypeConstant typeAnno = typeUnderlying;
            while (!(typeAnno instanceof AnnotatedTypeConstant))
                {
                typeAnno = typeAnno.getUnderlyingType();
                }
            log(errs, Severity.ERROR, VE_ANNOTATION_UNEXPECTED,
                    ((AnnotatedTypeConstant) typeAnno).getAnnotation(),
                    typeUnderlying.getValueString());
            return null;
            }

        // this implementation only deals with modifying (not including immutable) and terminal type
        // constants (not including typedefs, type parameters, auto-narrowing types, and unresolved
        // names); in other words, there must be an identity constant and a component structure
        // available for the type
        IdentityConstant constId;
        ClassStructure   struct;
        try
            {
            constId = (IdentityConstant) getDefiningConstant();
            struct  = (ClassStructure)   constId.getComponent();
            }
        catch (RuntimeException e)
            {
            throw new IllegalStateException("Unable to determine class for " + getValueString(), e);
            }

        // get a snapshot of the current invalidation count BEFORE building the TypeInfo
        ConstantPool pool    = getConstantPool();
        int          cInvals = pool.getInvalidationCount();

        Annotation[] aAnnoMixin = struct.collectAnnotations(false);
        Annotation[] aAnnoClass = struct.collectAnnotations(true);
        if (aAnnoMixin.length > 0)
            {
            // build a partial info without the annotations
            TypeInfo infoBase = buildBaseTypeInfoImpl(constId, struct, Annotation.NO_ANNOTATIONS,
                    cInvals, /*fComplete*/ false, errs);

            return layerOnAnnotations(constId, struct, infoBase, aAnnoMixin, aAnnoClass, cInvals, errs);
            }

        return buildBaseTypeInfoImpl(constId, struct, aAnnoClass, cInvals, /*fComplete*/ true, errs);
        }

    /**
     * Actual buildTypeInfo implementation for this type.
     *
     * @param constId         the identity constant of the class that this type is based on
     * @param struct          the structure of the class that this type is based on
     * @param aAnnoClass      an array of annotations for this type that mix into "Class"
     * @param cInvalidations  the count of TypeInfo invalidations before staring building the info
     * @param fComplete       if false, the "mixin" annotations have not yet been applied
     * @param errs            the error list to log to
     *
     * @return the resulting TypeInfo
     */
    private TypeInfo buildBaseTypeInfoImpl(IdentityConstant constId, ClassStructure struct,
                                   Annotation[] aAnnoClass, int cInvalidations,
                                   boolean fComplete, ErrorListener errs)
        {
        List<Contribution> listContribs = struct.getContributionsAsList();
        TypeConstant[]     atypeContrib = resolveContributionTypes(listContribs);
        TypeConstant[]     atypeCondInc = extractConditionalContributes(
                                            constId, struct, listContribs, atypeContrib, errs);
        // walk through each of the contributions, starting from the implied contributions that are
        // represented by annotations in this type constant itself, followed by the annotations in
        // the class structure, followed by the class structure (as its own pseudo-contribution),
        // followed by the remaining contributions
        List<Contribution> listProcess  = new ArrayList<>();
        TypeConstant[]     atypeSpecial = createContributionList(
                                            constId, struct, atypeContrib, listProcess, errs);
        TypeConstant typeInto    = atypeSpecial[0];
        TypeConstant typeExtends = atypeSpecial[1];
        TypeConstant typeRebase  = atypeSpecial[2];

        // we're going to build a map from name to param info, including whatever parameters are
        // specified by this class/interface, but also each of the contributing classes/interfaces
        Map<Object, ParamInfo> mapTypeParams = collectTypeParameters(constId, struct, errs);

        // 1) build the "potential call chains" (basically, the order in which we would search for
        //    methods to call in a virtual manner)
        // 2) collect all of the type parameter data from the various contributions
        ListMap<IdentityConstant, Origin> listmapClassChain   = new ListMap<>();
        ListMap<IdentityConstant, Origin> listmapDefaultChain = new ListMap<>();

        fComplete &= createCallChains(constId, struct, mapTypeParams,
                listProcess, listmapClassChain, listmapDefaultChain, errs);

        // next, we need to process the list of contributions in order, asking each for its
        // properties and methods, and collecting all of them
        Map<PropertyConstant , PropertyInfo> mapProps       = new HashMap<>();
        Map<MethodConstant   , MethodInfo  > mapMethods     = new HashMap<>();
        Map<Object           , PropertyInfo> mapVirtProps   = new HashMap<>(); // keyed by nested id
        Map<Object           , MethodInfo  > mapVirtMethods = new HashMap<>(); // keyed by nested id
        ListMap<String       , ChildInfo   > mapChildren    = new ListMap<>(); // keyed by name
        // note that the mapChildren keys may be '.' delim'd in the case of a "prop.class"

        fComplete &= collectMemberInfo(constId, struct, mapTypeParams,
                listProcess, listmapClassChain, listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, mapChildren, errs);

        // validate the type parameters against the properties
        checkTypeParameterProperties(mapTypeParams, mapVirtProps,
            fComplete ? errs : ErrorListener.BLACKHOLE);

        TypeInfo info = new TypeInfo(this, cInvalidations, struct, 0, false, mapTypeParams,
            aAnnoClass, typeExtends, typeRebase, typeInto,
                listProcess, listmapClassChain, listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, mapChildren,
                fComplete ? Progress.Complete : Progress.Incomplete);

        if (fComplete)
            {
            MethodInfo methodInvalid = info.validateCapped();
            if (methodInvalid != null)
                {
                MethodConstant id = methodInvalid.getIdentity();
                // TODO GG create a dedicated error
                id.log(errs, Severity.ERROR, VE_METHOD_NARROWING_AMBIGUOUS,
                    getValueString(),
                    id.getValueString(),
                    methodInvalid.getHead().getNarrowingNestedIdentity()
                    );
                }
            }

        return atypeCondInc == null || !fComplete
                ? info
                : mergeConditionalIncorporates(cInvalidations, constId, info, atypeCondInc, errs);
        }

    /**
     * Layer on the specified annotations on top of the base TypeInfo using this type as a target.
     *
     * @param constId         the identity constant of the class that this type is based on
     * @param struct          the structure of the class that this type is based on
     * @param aAnnoMixin      an array of "mixin" annotations for this type
     * @param aAnnoClass      an array of annotations for this type that mix into "Class"
     * @param cInvalidations  the count of TypeInfo invalidations before staring building the info
     * @param errs            the error list to log to
     */
    protected TypeInfo layerOnAnnotations(IdentityConstant constId, ClassStructure struct,
                                          TypeInfo infoBase,
                                          Annotation[] aAnnoMixin, Annotation[] aAnnoClass,
                                          int cInvalidations, ErrorListener errs)
        {
        ConstantPool pool     = getConstantPool();
        TypeInfo     infoNext = infoBase;
        TypeConstant typeNext = infoBase.getType();

        for (int c = aAnnoMixin.length, i = c-1; i >= 0; --i)
            {
            AnnotatedTypeConstant typeAnno = pool.ensureAnnotatedTypeConstant(typeNext, aAnnoMixin[i]);

            TypeConstant typeMixin        = typeAnno.getAnnotationType();
            TypeConstant typeMixinPrivate = pool.ensureAccessTypeConstant(typeMixin, Access.PRIVATE);
            TypeInfo     infoMixin        = typeMixinPrivate.ensureTypeInfoInternal(errs);

            if (infoMixin == null)
                {
                // we are always called with an incomplete infoBase when building an annotated class
                // (e.g. @M1 @M2 class TestM {}), rather than a run-time annotated type
                // (e.g. new @M1 @M2 Test()), so it's permissible to return it if the mixin info
                // cannot yet be calculated at this time
                return isComplete(infoBase) ? null : infoBase;
                }

            // even if infoMixin is incomplete, it's only incomplete because it lacks information
            // about the "into" type (which is probably this type), so the assumption is that
            // it has enough information about itself to be used for layering logic

            infoNext = typeNext.mergeMixinTypeInfo(this, cInvalidations, constId,
                    struct, infoNext, infoMixin,
                    i == 0 ? aAnnoClass : Annotation.NO_ANNOTATIONS, /*fAddProcess*/ true, errs);
            typeNext = typeAnno;
            }

        assert infoNext.getType().equals(this);
        return infoNext;
        }

    /**
     * Create a TypeInfo for this struct of this type.
     *
     * @param errs  the error list to log any errors to
     *
     * @return a new TypeInfo representing the struct of this TypeConstant
     */
    private TypeInfo buildStructInfo(ErrorListener errs)
        {
        // this is a helper method that only supports being called on AccessTypeConstant of STRUCT
        assert this instanceof AccessTypeConstant && getAccess() == Access.STRUCT;

        // start by copying all the fields and functions from the private type of this
        Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
        Map<MethodConstant  , MethodInfo  > mapMethods   = new HashMap<>();
        Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();

        ConstantPool pool    = getConstantPool();
        int          cInvals = pool.getInvalidationCount();
        TypeInfo     infoPri = pool.ensureAccessTypeConstant(getUnderlyingType(), Access.PRIVATE)
                               .ensureTypeInfoInternal(errs);
        if (!isComplete(infoPri))
            {
            return infoPri;
            }

        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoPri.getProperties().entrySet())
            {
            // the properties that show up in structure types are those that have a field; however,
            // we also need to retain both type params and constants, even though they technically
            // are not "in" the structure itself
            PropertyInfo prop = entry.getValue();
            if (prop.isFormalType() || prop.isConstant() || prop.hasField())
                {
                PropertyConstant id = entry.getKey();
                if (prop.isVirtual())
                    {
                    mapVirtProps.put(id.resolveNestedIdentity(pool, null), prop);
                    }
                mapProps.put(id, prop);
                }
            }

        for (Map.Entry<MethodConstant, MethodInfo> entry : infoPri.getMethods().entrySet())
            {
            MethodInfo method = entry.getValue();
            if (method.isFunction() || method.isConstructor())
                {
                mapMethods.put(entry.getKey(), method);
                }
            }

        // now go through all of the contributions and "vacuum" any fields from those contributions
        // that were not visible to (i.e. from within) the private form of this type
        boolean fIncomplete = false;
        for (Contribution contrib : infoPri.getContributionList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                case Incorporates:
                case Extends:
                case RebasesOnto:
                    {
                    // obtain the struct type of the contribution and copy any missing fields from it
                    TypeConstant typeContrib = contrib.getTypeConstant();
                    typeContrib = typeContrib.removeAccess();
                    typeContrib = pool.ensureAccessTypeConstant(typeContrib, Access.STRUCT);

                    TypeInfo infoContrib = typeContrib.ensureTypeInfoInternal(errs);
                    if (isComplete(infoContrib))
                        {
                        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoContrib.getProperties().entrySet())
                            {
                            PropertyInfo prop = entry.getValue();

                            if (prop.hasField() && prop.getRefAccess() == Access.PRIVATE)
                                {
                                mapProps.putIfAbsent(entry.getKey(), prop);
                                }
                            }
                        }
                    else
                        {
                        fIncomplete = true;
                        }
                    break;
                    }
                }
            }

        // add Object.toString() method
        MethodInfo infoToString = pool.typeObject().ensureTypeInfo(errs).
                getMethodBySignature(pool.sigToString());
        mapMethods.putIfAbsent(infoToString.getIdentity(), infoToString);

        return new TypeInfo(this, cInvals, infoPri.getClassStructure(), 0,
                false, infoPri.getTypeParams(), infoPri.getClassAnnotations(),
                infoPri.getExtends(), infoPri.getRebases(), infoPri.getInto(),
                infoPri.getContributionList(), infoPri.getClassChain(), infoPri.getDefaultChain(),
                mapProps, mapMethods, mapVirtProps, Collections.EMPTY_MAP, ListMap.EMPTY,   // TODO mapChildren
                fIncomplete ? Progress.Incomplete : Progress.Complete);
        }

    /**
     * Populate the type parameter map with the type parameters of this type (not counting any
     * further contributions), and create a GenericTypeResolver based on that type parameter map.
     *
     * @param constId  the identity constant of the class that the type is based on
     * @param struct   the structure of the class that the type is based on
     * @param errs     the error list to log to
     *
     * @return the map of type parameters
     */
    private Map<Object, ParamInfo> collectTypeParameters(
            IdentityConstant constId,
            ClassStructure   struct,
            ErrorListener    errs)
        {
        ConstantPool           pool          = getConstantPool();
        Map<Object, ParamInfo> mapTypeParams = new HashMap<>();

        if (isTuple())
            {
            // warning: turtles
            TypeConstant typeConstraint = pool.ensureTypeSequenceTypeConstant();

            ParamInfo param = new ParamInfo("ElementTypes", typeConstraint, null);
            mapTypeParams.put(param.getName(), param);
            }
        else
            {
            // obtain the type parameters encoded in this type constant
            TypeConstant[] atypeParams = getParamTypesArray();
            int            cTypeParams = atypeParams.length;

            // obtain the type parameters declared by the class
            List<Entry<StringConstant, TypeConstant>> listClassParams = struct.getTypeParamsAsList();
            int                                       cClassParams    = listClassParams.size();

            if (cTypeParams > cClassParams)
                {
                if (cClassParams == 0)
                    {
                    log(errs, Severity.ERROR, VE_TYPE_PARAMS_UNEXPECTED,
                            constId.getPathString());
                    }
                else
                    {
                    log(errs, Severity.ERROR, VE_TYPE_PARAMS_WRONG_NUMBER,
                            constId.getPathString(), cClassParams, cTypeParams);
                    }
                }

            TypeConstant typeNormalized = this.normalizeParameters();

            for (int i = 0; i < cClassParams; ++i)
                {
                Entry<StringConstant, TypeConstant> entryClassParam = listClassParams.get(i);
                String                              sName           = entryClassParam.getKey().getValue();
                TypeConstant                        typeConstraint  = entryClassParam.getValue();
                TypeConstant                        typeActual      = null;

                // resolve any generic dependencies in the type constraint
                if (!typeConstraint.isFormalTypeSequence())
                    {
                    typeConstraint = typeConstraint.resolveGenerics(pool, typeNormalized);
                    }

                // validate the actual type, if there is one
                if (i < cTypeParams)
                    {
                    typeActual = atypeParams[i];
                    assert typeActual != null;

                    if (!typeActual.isA(typeConstraint))
                        {
                        log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_TYPE,
                                constId.getPathString(), sName,
                                typeConstraint.getValueString(),
                                typeActual.getValueString(), this.getValueString());
                        }
                    }

                if (mapTypeParams.containsKey(sName))
                    {
                    log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_COLLISION,
                            struct.getIdentityConstant().getValueString(), sName);
                    }
                else
                    {
                    mapTypeParams.put(sName, new ParamInfo(sName, typeConstraint, typeActual));
                    }
                }
            }

        return mapTypeParams;
        }

    /**
     * Fill in the passed list of contributions to process, and also collect a list of all the
     * annotations.
     *
     * @param constId      the identity constant of the class that the type is based on
     * @param struct       the structure of the class that the type is based on
     * @param listProcess  a list of contributions, which will be filled by this method in the
     *                     order that they should be processed
     * @param errs         the error list to log to
     *
     * @return an array containing the "into", "extends", "rebase" and the first conditional
     *         incorporate type
     */
    private TypeConstant[] createContributionList(
            IdentityConstant    constId,
            ClassStructure      struct,
            TypeConstant[]      aContribType,
            List<Contribution>  listProcess,
            ErrorListener       errs)
        {
        ConstantPool       pool         = getConstantPool();
        List<Contribution> listContribs = struct.getContributionsAsList();
        int                cContribs    = listContribs.size();
        int                iContrib     = 0;

        // add a marker into the list of contributions at this point to indicate that this class
        // structure's contents need to be processed next
        listProcess.add(new Contribution(Composition.Equal, this));  // place-holder for "this"

        // error check the "into" and "extends" clauses, plus rebasing (they'll get processed later)
        TypeConstant typeInto    = null;
        TypeConstant typeExtends = null;
        TypeConstant typeRebase  = null;
        switch (struct.getFormat())
            {
            case PACKAGE:
                if (cContribs == 0)
                    {
                    assert ((PackageStructure) struct).isModuleImport();
                    // module import is still a Package and needs to be "re-based"
                    }
                // fall through
            case MODULE:
            case ENUMVALUE:
            case ENUM:
            case CLASS:
            case CONST:
            case SERVICE:
                {
                // check for re-basing; this occurs when a class format changes and the system has
                // to insert a layer of code between this class and the class being extended, such
                // as when a service (which is a Service format) extends Object (which is a Class
                // format)
                typeRebase = (TypeConstant) pool.register(struct.getRebaseType());

                // next up, for any class type (other than Object itself), there may be an "extends"
                // contribution that specifies another class
                Contribution contrib = iContrib < cContribs ? listContribs.get(iContrib) : null;
                boolean fExtends = contrib != null && contrib.getComposition() == Composition.Extends;

                if (!fExtends)
                    {
                    if (struct.getFormat() == Component.Format.ENUMVALUE)
                        {
                        log(errs, Severity.ERROR, VE_EXTENDS_EXPECTED, constId.getPathString());
                        }
                    break;
                    }

                typeExtends = aContribType[iContrib];
                ++iContrib;

                // the "extends" clause must specify a class identity
                if (!typeExtends.isExplicitClassIdentity(true))
                    {
                    log(errs, Severity.ERROR, VE_EXTENDS_NOT_CLASS, constId.getPathString(),
                            typeExtends.getValueString());
                    break;
                    }

                if (typeExtends.extendsClass(constId))
                    {
                    // some sort of circular loop
                    log(errs, Severity.ERROR, VE_CYCLICAL_CONTRIBUTION, constId.getPathString(),
                            "extends");
                    break;
                    }

                // the class structure will have to verify its "extends" clause in more detail, but
                // for now perform a quick sanity check
                IdentityConstant constExtends  = typeExtends.getSingleUnderlyingClass(true);
                ClassStructure   structExtends = (ClassStructure) constExtends.getComponent();
                if (!struct.getFormat().isExtendsLegal(structExtends.getFormat()))
                    {
                    log(errs, Severity.ERROR, VE_EXTENDS_INCOMPATIBLE,
                            constId.getPathString(), struct.getFormat(),
                            constExtends.getPathString(), structExtends.getFormat());
                    break;
                    }
                }
                break;

            case MIXIN:
                {
                // a mixin can extend another mixin, and it can specify an "into" that defines a
                // base type that defines the environment that it will be working within. if neither
                // is present, then there is an implicit "into Object"
                Contribution contrib = iContrib < cContribs ? listContribs.get(iContrib) : null;

                // check "into"
                boolean fInto = contrib != null && contrib.getComposition() == Composition.Into;
                if (fInto)
                    {
                    typeInto = aContribType[iContrib];

                    // load the next contribution
                    ++iContrib;
                    contrib = iContrib < cContribs ? listContribs.get(iContrib) : null;
                    }

                // check "extends"
                boolean fExtends = contrib != null && contrib.getComposition() == Composition.Extends;
                if (fExtends)
                    {
                    typeExtends = aContribType[iContrib];
                    ++iContrib;

                    if (!typeExtends.isExplicitClassIdentity(true))
                        {
                        log(errs, Severity.ERROR, VE_EXTENDS_NOT_CLASS,
                                constId.getPathString(),
                                typeExtends.getValueString());
                        break;
                        }

                    // verify that it is a mixin
                    if (typeExtends.getExplicitClassFormat() != Component.Format.MIXIN)
                        {
                        log(errs, Severity.ERROR, VE_EXTENDS_NOT_MIXIN, typeExtends.getValueString(),
                                constId.getPathString());
                        break;
                        }

                    if (typeExtends.extendsClass(constId))
                        {
                        // some sort of circular loop
                        log(errs, Severity.ERROR, VE_CYCLICAL_CONTRIBUTION, constId.getPathString(),
                                "extends");
                        break;
                        }
                    }
                else if (!fInto)
                    {
                    // add fake "into Object"
                    typeInto = pool.typeObject();
                    }
                }
                break;

            case INTERFACE:
                if (constId instanceof NativeRebaseConstant)
                    {
                    // for a native rebase, the interface becomes a class, and that class implements
                    // the original interface and Object
                    TypeConstant typeNatural = (TypeConstant) pool.register(
                            ((NativeRebaseConstant) constId).getClassConstant().getType());
                    if (isParamsSpecified())
                        {
                        typeNatural = pool.ensureParameterizedTypeConstant(typeNatural, getParamTypesArray());
                        }
                    listProcess.add(new Contribution(Composition.Implements, typeNatural));
                    listProcess.add(new Contribution(Composition.Implements, pool.typeObject()));
                    }
                else
                    {
                    // Object does not (and must not) implement anything
                    if (!constId.equals(pool.clzObject()))
                        {
                        // an interface implies the set of methods present in Object
                        // (use the "Into" composition to make the Object methods implicit-only, as
                        // opposed to explicitly being present in this interface)
                        typeInto = pool.typeObject();
                        }
                    }
                break;

            default:
                throw new IllegalStateException(getValueString() + "=" + struct.getFormat());
            }

        // go through the rest of the contributions, and add the ones that need to be processed to
        // the list to do
        for ( ; iContrib < cContribs; ++iContrib)
            {
            Contribution contrib     = listContribs.get(iContrib);
            TypeConstant typeContrib = aContribType[iContrib];

            switch (contrib.getComposition())
                {
                case Annotation:
                    // call processMixins() for validation only; the "mixin" annotations will be
                    // processed separately at the end of buildTypeInfoImpl() method
                    processMixins(constId, typeContrib, true, struct, new ArrayList<>(), errs);
                    break;

                case Into:
                    // only applicable on a mixin, only one allowed, and it should have been earlier
                    // in the list of contributions
                    log(errs, Severity.ERROR, VE_INTO_UNEXPECTED,
                            typeContrib.getValueString(), constId.getPathString());
                    break;

                case Extends:
                    // not applicable on an interface, only one allowed, and it should have been
                    // earlier in the list of contributions
                    log(errs, Severity.ERROR, VE_EXTENDS_UNEXPECTED,
                            typeContrib.getValueString(), constId.getPathString());
                    break;

                case Incorporates:
                    if (contrib.getTypeParams() != null)
                        {
                        // conditional incorporates have already been processed
                        break;
                        }

                    processMixins(constId, typeContrib, false, struct, listProcess, errs);
                    break;

                case Delegates:
                    processDelegates(constId, typeContrib, contrib, struct, listProcess, errs);
                    break;

                case Implements:
                    processImplements(constId, typeContrib, listProcess, errs);
                    break;

                default:
                    throw new IllegalStateException(constId.getPathString()
                            + ", contribution=" + contrib);
                }
            }

        // virtual children implement an implicit "Inner" interface, and are contained inside a
        // container class that implements an implicit "Outer" interface
        if (struct.containsVirtualChild() && !constId.equals(pool.clzOuter()))
            {
            listProcess.add(new Contribution(Composition.Implements, pool.typeOuter()));
            }
        if (isVirtualChild() && !constId.equals(pool.clzInner()))
            {
            listProcess.add(new Contribution(Composition.Implements, pool.typeInner()));
            }

        // the last three contributions to get processed are the "re-basing", the "extends" and the
        // "into" (which we also use for filling out the implied methods under interfaces, i.e.
        // "into Object")
        if (typeRebase != null)
            {
            listProcess.add(new Contribution(Composition.RebasesOnto,
                    pool.ensureAccessTypeConstant(typeRebase, Access.PROTECTED)));
            }
        if (typeExtends != null)
            {
            listProcess.add(new Contribution(Composition.Extends,
                    pool.ensureAccessTypeConstant(typeExtends, Access.PROTECTED)));
            }
        if (typeInto != null)
            {
            if (!typeInto.isAccessSpecified() && typeInto.isSingleDefiningConstant())
                {
                typeInto = pool.ensureAccessTypeConstant(typeInto, Access.PROTECTED);
                }
            listProcess.add(new Contribution(Composition.Into, typeInto));
            }

        return new TypeConstant[] {typeInto, typeExtends, typeRebase};
        }

    /**
     * Extract the types of conditional incorporates.
     *
     * @param constId       the identity constant of the class that the type is based on
     * @param struct        the structure of the class that the type is based on
     * @param listContribs  the contribution list
     * @param aContribType  the contribution types that correspond the contributions in the list
     * @param errs          the error listener
     *
     * @return the conditionally incorporated types
     */
    private TypeConstant[] extractConditionalContributes(
            IdentityConstant   constId,
            ClassStructure     struct,
            List<Contribution> listContribs,
            TypeConstant[]     aContribType,
            ErrorListener      errs)
        {
        List<TypeConstant> listCondContribs = null;

        // process the annotations and conditional incorporates at the front of the contribution list
        for (int iContrib = 0, cContribs = listContribs.size(); iContrib < cContribs; ++iContrib)
            {
            // only process conditional incorporates
            Contribution contrib   = listContribs.get(iContrib);
            TypeConstant typeMixin = aContribType[iContrib];
            Composition  compose   = contrib.getComposition();

            if (compose == Composition.Incorporates)
                {
                if (contrib.getTypeParams() == null)
                    {
                    // a regular "incorporates"; process later
                    assert typeMixin != null;
                    continue;
                    }
                else if (typeMixin == null)
                    {
                    // the conditional incorporates does not apply to "this" type
                    continue;
                    }
                // process now
                }
            else
                {
                // not now
                continue;
                }

            // has to be an explicit class identity
            if (!typeMixin.isExplicitClassIdentity(compose == Composition.Incorporates))
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_NOT_CLASS,
                        constId.getPathString(), typeMixin.getValueString());
                continue;
                }

            // has to be a mixin
            if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_NOT_MIXIN,
                        typeMixin.getValueString());
                continue;
                }

            if (listCondContribs == null)
                {
                listCondContribs = new ArrayList<>();
                }
            listCondContribs.add(typeMixin);

            // call processMixins() for validation only
            processMixins(constId, typeMixin, false, struct, new ArrayList<>(), errs);
            }

        return listCondContribs == null
                ? null
                : listCondContribs.toArray(TypeConstant.NO_TYPES);
        }

    /**
     * Resolve and collect contribution types for the specified list if contributions.
     *
     * @param listContribs  the contribution list
     *
     * @return an array of resolved TypeConstants where some elements could be null for conditional
     *         incorporates
     */
    private TypeConstant[] resolveContributionTypes(List<Contribution> listContribs)
        {
        ConstantPool   pool         = getConstantPool();
        int            cContribs    = listContribs.size();
        TypeConstant[] aContribType = new TypeConstant[cContribs];

        for (int iContrib = 0; iContrib < cContribs; ++iContrib)
            {
            aContribType[iContrib] = listContribs.get(iContrib).resolveGenerics(pool, this);
            }
        return aContribType;
        }

    /**
     * Process the "incorporates" contribution for annotations or mixins.
     */
    private void processMixins(IdentityConstant constId, TypeConstant typeContrib,
                               boolean fAnno, ClassStructure struct,
                               List<Contribution> listProcess, ErrorListener errs)
        {
        ConstantPool pool = getConstantPool();

        if (struct.getFormat() == Component.Format.INTERFACE && !fAnno)
            {
            log(errs, Severity.ERROR, VE_INCORPORATES_UNEXPECTED,
                typeContrib.getValueString(),
                constId.getPathString());
            return;
            }

        if (!typeContrib.isExplicitClassIdentity(true))
            {
            log(errs, Severity.ERROR, VE_INCORPORATES_NOT_CLASS,
                typeContrib.getValueString(),
                constId.getPathString());
            return;
            }

        // validate that the class is a mixin
        if (typeContrib.getExplicitClassFormat() != Component.Format.MIXIN)
            {
            log(errs, Severity.ERROR, VE_INCORPORATES_NOT_MIXIN,
                typeContrib.getValueString(),
                constId.getPathString());
            return;
            }

        TypeConstant typeRequire = typeContrib.getExplicitClassInto();
        // mixins into Class are "synthetic" (e.g. Abstract, Override); the only
        // exception is Enumeration, which needs to be processed naturally
        if (!typeRequire.isIntoClassType() || typeRequire.isA(pool.typeEnumeration()))
            {
            // the mixin must be compatible with this type, as specified by its "into"
            // clause; note: not 100% correct because the presence of this mixin may affect
            // the answer, so this requires an eventual fix
            if (typeRequire != null && !this.isA(typeRequire))
                {
                log(errs, Severity.ERROR, VE_INCORPORATES_INCOMPATIBLE,
                    constId.getPathString(),
                    typeContrib.getValueString(),
                    this.getValueString(),
                    typeRequire.getValueString());
                return;
                }

            // check for duplicate mixin
            if (listProcess.stream().anyMatch(contribPrev ->
                    contribPrev.getTypeConstant().equals(typeContrib)))
                {
                log(errs, Severity.ERROR, VE_DUP_INCORPORATES,
                    constId.getPathString(), typeContrib.getValueString());
                return;
                }

            listProcess.add(new Contribution(Composition.Incorporates,
                pool.ensureAccessTypeConstant(typeContrib, Access.PROTECTED)));
            }
        }

    /**
     * Process the "delegates" contribution.
     */
    private void processDelegates(IdentityConstant constId, TypeConstant typeContrib,
                                  Contribution contrib, ClassStructure struct,
                                  List<Contribution> listProcess, ErrorListener errs)
        {
        // not applicable on an interface
        if (struct.getFormat() == Component.Format.INTERFACE)
            {
            log(errs, Severity.ERROR, VE_DELEGATES_UNEXPECTED,
                typeContrib.getValueString(),
                constId.getPathString());
            return;
            }

        // must be an "interface type" (not a class type)
        if (typeContrib.isExplicitClassIdentity(true)
            && typeContrib.getExplicitClassFormat() != Component.Format.INTERFACE)
            {
            log(errs, Severity.ERROR, VE_DELEGATES_NOT_INTERFACE,
                typeContrib.getValueString(),
                constId.getPathString());
            return;
            }

        // check for duplicate delegates
        if (listProcess.stream().anyMatch(contribPrev ->
            contribPrev.getComposition() == Composition.Delegates &&
                contribPrev.getTypeConstant().equals(typeContrib)))
            {
            log(errs, Severity.ERROR, VE_DUP_DELEGATES,
                constId.getPathString(), typeContrib.getValueString());
            }
        else
            {
            listProcess.add(new Contribution(typeContrib,
                contrib.getDelegatePropertyConstant()));
            }
        }

    /**
     * Process the "implements" contribution.
     */
    private void processImplements(IdentityConstant constId, TypeConstant typeContrib,
                                   List<Contribution> listProcess, ErrorListener errs)
        {
        // must be an "interface type" (not a class type)
        if (typeContrib.isExplicitClassIdentity(true)
                && typeContrib.getExplicitClassFormat() != Component.Format.INTERFACE)
            {
            log(errs, Severity.ERROR, VE_IMPLEMENTS_NOT_INTERFACE,
                    typeContrib.getValueString(),
                    constId.getPathString());
            return;
            }

        // check for duplicate implements
        if (listProcess.stream().anyMatch(contribPrev ->
                contribPrev.getComposition() == Composition.Implements &&
                contribPrev.getTypeConstant().equals(typeContrib)))
            {
            log(errs, Severity.ERROR, VE_DUP_IMPLEMENTS,
                    constId.getPathString(), typeContrib.getValueString());
            }
        else
            {
            listProcess.add(new Contribution(Composition.Implements, typeContrib));
            }
        }

    /**
     * Build the "potential call chain" from the list of contributions.
     *
     * @param constId              the identity constant of the class that the type is based on
     * @param struct               the structure of the class that the type is based on
     * @param mapTypeParams        the type parameters for the type, further added to by this method
     * @param listProcess          the list of contributions, in the order that they are intended to
     *                             be processed
     * @param listmapClassChain    the potential call chain
     * @param listmapDefaultChain  the potential default call chain
     * @param errs                 the error list to log errors to
     */
    private boolean createCallChains(
            IdentityConstant                  constId,
            ClassStructure                    struct,
            Map<Object, ParamInfo>            mapTypeParams,
            List<Contribution>                listProcess,
            ListMap<IdentityConstant, Origin> listmapClassChain,
            ListMap<IdentityConstant, Origin> listmapDefaultChain,
            ErrorListener                     errs)
        {
        boolean fIncomplete = false;

        for (Contribution contrib : listProcess)
            {
            Composition compContrib = contrib.getComposition();
            switch (compContrib)
                {
                case Equal: // i.e. "this" type
                    {
                    assert !listmapClassChain.containsKey(constId);
                    assert !listmapDefaultChain.containsKey(constId);

                    // append self to the call chain
                    (isInterface(constId, struct)
                            ? listmapDefaultChain
                            : listmapClassChain
                        ).put(constId, new Origin(true));

                    // this type's type parameters were already collected
                    }
                    break;

                case Implements:
                case Incorporates:
                case Delegates:
                case Extends:
                case RebasesOnto:
                // "into" contains only implicit methods, so it is not part of a call chain;
                // however, it may contribute type parameters
                case Into:
                    {
                    // append to the call chain
                    TypeConstant typeContrib = contrib.getTypeConstant(); // already resolved
                    TypeInfo     infoContrib = typeContrib.ensureTypeInfoInternal(errs);

                    if (!isComplete(infoContrib))
                        {
                        fIncomplete = true;
                        errs        = ErrorListener.BLACKHOLE;
                        break;
                        }

                    if (compContrib != Composition.Into)
                        {
                        infoContrib.contributeChains(listmapClassChain, listmapDefaultChain, compContrib);
                        }

                    // collect type parameters
                    for (ParamInfo paramContrib : infoContrib.getTypeParams().values())
                        {
                        Object    nid       = paramContrib.getNestedIdentity();
                        ParamInfo paramCurr = mapTypeParams.get(nid);
                        if (paramCurr == null)
                            {
                            mapTypeParams.put(nid, paramContrib);
                            }
                        else
                            {
                            // check that everything matches between the current and contributed parameter
                            if (paramContrib.isActualTypeSpecified() != paramCurr.isActualTypeSpecified())
                                {
                                if (paramContrib.isFormalType() &&
                                    paramContrib.getFormalTypeName().equals(paramCurr.getName()))
                                    {
                                    // TODO both the current and contributed parameters have a constraint type;
                                    //      if those types are different, then keep the narrower of the two; if
                                    //      there is no "narrower of the two", then keep the union of the two;
                                    //      if there is no union of the two (e.g. 2 class types), then it's an error
                                    continue;
                                    }

                                if (paramCurr.isActualTypeSpecified())
                                    {
                                    log(errs, Severity.ERROR, VE_TYPE_PARAM_CONTRIB_NO_SPEC,
                                            this.getValueString(), nid,
                                            paramCurr.getActualType().getValueString(),
                                            typeContrib.getValueString());
                                    }
                                else
                                    {
                                    log(errs, Severity.ERROR, VE_TYPE_PARAM_CONTRIB_HAS_SPEC,
                                            this.getValueString(), nid,
                                            typeContrib.getValueString(),
                                            paramContrib.getActualType().getValueString());
                                    }
                                }
                            else if (!paramCurr.getActualType().isA(paramContrib.getActualType()))
                                {
                                if (isVirtualChild())
                                    {
                                    // TODO: how to validate that we can safely override?
                                    mapTypeParams.put(nid, paramContrib);
                                    continue;
                                    }
                                log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_CONTRIB,
                                        this.getValueString(), nid,
                                        paramCurr.getActualType().getValueString(),
                                        typeContrib.getValueString(),
                                        paramContrib.getActualType().getValueString());
                                }
                            }
                        }
                    break;
                    }

                default:
                    throw new IllegalStateException("composition=" + compContrib);
                }
            }

        return !fIncomplete;
        }

    /**
     * @return true iff the type defined by the constId and corresponding struct refers to an
     *         interface type
     */
    private static boolean isInterface(IdentityConstant constId, ClassStructure struct)
        {
        return struct.getFormat() == Component.Format.INTERFACE
                && !(constId instanceof NativeRebaseConstant);
        }

    /**
     * Collect the properties and methods (including scoped properties and method) for this type.
     *
     * @param constId              identity of the class
     * @param struct               the class structure
     * @param mapTypeParams        the map of type parameters
     * @param listProcess          list of contributions in the order that they should be processed
     * @param listmapClassChain    potential call chain
     * @param listmapDefaultChain  potential default call chain
     * @param mapProps             properties of the class
     * @param mapMethods           methods of the class
     * @param mapVirtProps         the virtual properties of the type, keyed by nested id
     * @param mapVirtMethods       the virtual methods of the type, keyed by nested id
     * @param mapChildren          the child types of the class
     * @param errs                 the error list to log any errors to
     *
     * @return true iff the processing was able to obtain all of its dependencies
     */
    private boolean collectMemberInfo(
            IdentityConstant                    constId,
            ClassStructure                      struct,
            Map<Object, ParamInfo>              mapTypeParams,
            List<Contribution>                  listProcess,
            ListMap<IdentityConstant, Origin>   listmapClassChain,
            ListMap<IdentityConstant, Origin>   listmapDefaultChain,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo  > mapMethods,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<Object, MethodInfo  >           mapVirtMethods,
            Map<String, ChildInfo   >           mapChildren,
            ErrorListener                       errs)
        {
        ConstantPool pool        = getConstantPool();
        boolean      fIncomplete = false;
        boolean      fNative     = constId instanceof NativeRebaseConstant;

        for (int i = listProcess.size()-1; i >= 0; --i)
            {
            Contribution contrib = listProcess.get(i);

            Map<PropertyConstant, PropertyInfo> mapContribProps;
            Map<MethodConstant  , MethodInfo  > mapContribMethods;
            ListMap<String      , ChildInfo   > mapContribChildren;

            TypeConstant     typeContrib = contrib.getTypeConstant();
            Composition      composition = contrib.getComposition();
            PropertyConstant idDelegate  = contrib.getDelegatePropertyConstant();
            boolean          fSelf       = composition == Composition.Equal;

            if (fSelf)
                {
                mapContribProps    = new HashMap<>();
                mapContribMethods  = new HashMap<>();
                mapContribChildren = new ListMap<>();

                int nBaseRank = mapProps.size();

                collectSelfTypeParameters(struct, mapTypeParams, mapContribProps, nBaseRank, errs);

                ArrayList<PropertyConstant> listExplode = new ArrayList<>();
                if (!collectChildInfo(constId, isInterface(constId, struct), struct, mapTypeParams,
                        mapContribProps, mapContribMethods, mapContribChildren, listExplode, nBaseRank, errs))
                    {
                    fIncomplete = true;
                    errs        = ErrorListener.BLACKHOLE;
                    }

                // the order in which the properties are layered on and exploded is extremely
                // important in order for (a) the result to be correct and (b) errors to be
                // correctly identified. in general, we work from the top of the hierarchy (the
                // containing class) down (the nested properties), so that the "explosion" never
                // can occur before we layer on the property, but also that the "explosion" must
                // always occur before we layer on any properties nested thereunder. since the
                // createMembers() method recurses, it provides an ideal order for us in the
                // listExplode, and since any properties that remain in the contribution when we're
                // done with this will naturally layer on top of any artifacts from the explosion,
                // we only have to process the specific properties that explode here, and make sure
                // that they don't get processed later when we process the rest of the properties
                for (PropertyConstant idProp : listExplode)
                    {
                    // remove the property from the contrib map (so that we can process it now)
                    PropertyInfo prop = mapContribProps.remove(idProp);
                    assert prop != null;

                    // layer on the property so its information is all correct before we have to
                    // make any decisions about how to process the property
                    layerOnProp(constId, true, null, mapProps, mapVirtProps,
                            typeContrib, idProp, prop, errs);

                    if (!fNative)
                        {
                        // now that the necessary data is in place, explode the property
                        if (!explodeProperty(constId, struct, idProp, prop,
                                mapProps, mapVirtProps, mapMethods, mapVirtMethods, errs))
                            {
                            fIncomplete = true;
                            errs        = ErrorListener.BLACKHOLE;
                            }
                        }
                    }
                }
            else
                {
                TypeInfo infoContrib = typeContrib.ensureTypeInfoInternal(errs);
                if (!isComplete(infoContrib))
                    {
                    fIncomplete = true;
                    errs        = ErrorListener.BLACKHOLE;
                    continue;
                    }

                switch (composition)
                    {
                    case Into:
                        infoContrib = infoContrib.asInto();
                        break;

                    case Delegates:
                        infoContrib = infoContrib.asDelegates();
                        break;
                    }

                mapContribProps    = infoContrib.getProperties();
                mapContribMethods  = infoContrib.getMethods();
                mapContribChildren = infoContrib.getChildInfosByName();

                if (composition != Composition.Into)
                    {
                    // collect all of the IdentityConstants in the potential call chain that map to
                    // this particular contribution
                    HashSet<IdentityConstant> setClass = new HashSet<>();
                    for (Entry<IdentityConstant, Origin> entry : listmapClassChain.entrySet())
                        {
                        if (entry.getValue().getType().equals(typeContrib))
                            {
                            setClass.add(entry.getKey());
                            }
                        }
                    HashSet<IdentityConstant> setDefault = new HashSet<>();
                    for (Entry<IdentityConstant, Origin> entry : listmapDefaultChain.entrySet())
                        {
                        if (entry.getValue().getType().equals(typeContrib))
                            {
                            setDefault.add(entry.getKey());
                            }
                        }

                    // reduce the TypeInfo to only contain methods appropriate to the reduced call
                    // chain for the contribution
                    if (setClass.size() < infoContrib.getClassChain().size()
                            || setDefault.size() < infoContrib.getDefaultChain().size())
                        {
                        Map<PropertyConstant, PropertyInfo> mapReducedProps = new HashMap<>();
                        for (Entry<PropertyConstant, PropertyInfo> entry : mapContribProps.entrySet())
                            {
                            // REVIEW: consider removing the "retainOnly" call with a simple check:
                            //
                            // IdentityConstant idProp = entry.getKey().getClassIdentity();
                            // if (!setClass.contains(idProp) && !setDefault.contains(idProp))
                            //    {
                            //    iter.remove();
                            //    }
                            PropertyInfo infoReduced = entry.getValue().
                                    retainOnly(entry.getKey(), setClass, setDefault);
                            if (infoReduced != null)
                                {
                                mapReducedProps.put(entry.getKey(), infoReduced);
                                }
                            }
                        mapContribProps = mapReducedProps;

                        Map<MethodConstant, MethodInfo> mapReducedMethods = new HashMap<>();
                        for (Entry<MethodConstant, MethodInfo> entry : mapContribMethods.entrySet())
                            {
                            // REVIEW: ditto
                            MethodInfo infoReduced = entry.getValue()
                                    .retainOnly(entry.getKey(), setClass, setDefault);
                            if (infoReduced != null)
                                {
                                mapReducedMethods.put(entry.getKey(), infoReduced);
                                }
                            }
                        mapContribMethods = mapReducedMethods;
                        }
                    }
                }

            // basically, we're building from the bottom up, in columns. if we build from the top
            // down, we may make the wrong "narrowing" choice, because the right choice might not
            // yet be introduced (it comes in at a lower level), and thus we'll narrow when there
            // is an exact match (or at least a better match) available. additionally, we have to
            // build the property call chains for any properties that have custom logic and/or
            // Ref/Var annotations, because those properties are basically "little classes", whose
            // state just happens to be embedded within this larger type. the call chains for these
            // properties are _based on_ the call chains of this type, but due to annotations on
            // the Ref/Var aspect of the property, the call chains can "bloom" at any level within
            // this type's call chain. fortunately, the property call chains are simpler in one
            // particular aspect vis-a-vis the type's call chains: property call chains do not
            // having the "yanking" aspect, since what we refer to as Ref/Var _annotations_ are
            // treated more like "incorporated" mix-ins, in that the custom code on the property
            // at a given virtual level in the type's call chain will overlay the annotations from
            // that same level.

            // process properties
            layerOnProps(constId, fSelf, idDelegate, mapProps, mapVirtProps,
                    typeContrib, mapContribProps, errs);

            // if there are any remaining declared-but-not-overridden properties originating from
            // an interface on a class once the "self" layer is applied, then those need to be
            // analyzed to determine if they require fields, etc.
            if (fSelf && !isInterface(constId, struct) && !struct.isExplicitlyAbstract())
                {
                for (Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
                    {
                    PropertyInfo infoOld = entry.getValue();
                    PropertyInfo infoNew = infoOld.finishAdoption(fNative, errs);
                    if (infoNew != infoOld)
                        {
                        entry.setValue(infoNew);
                        if (infoNew.isVirtual())
                            {
                            assert infoOld.isVirtual();
                            Object       nid       = entry.getKey().resolveNestedIdentity(pool, this);
                            PropertyInfo infoCheck = mapVirtProps.put(nid, infoNew);
                            assert infoOld == infoCheck;
                            }
                        }
                    }
                }

            // process methods
            if (!mapContribMethods.isEmpty())
                {
                layerOnMethods(constId, fSelf, false, idDelegate, mapMethods, mapVirtMethods,
                        typeContrib, mapContribMethods, errs);
                }

            // process children
            if (!mapContribChildren.isEmpty())
                {
                for (Entry<String, ChildInfo> entry : mapContribChildren.entrySet())
                    {
                    String    sName       = entry.getKey();
                    ChildInfo infoContrib = entry.getValue();
                    ChildInfo infoPrev    = mapChildren.putIfAbsent(sName, infoContrib);
                    if (infoPrev != null)
                        {
                        ChildInfo infoNew = infoPrev.layerOn(infoContrib);
                        if (infoNew == null)
                            {
                            log(errs, Severity.ERROR, VE_CHILD_COLLISION,
                                    constId,
                                    sName,
                                    contrib.getTypeConstant(),
                                    infoPrev.getIdentity());
                            }
                        else
                            {
                            mapChildren.put(sName, infoNew);
                            }
                        }
                    }
                }

            if (fSelf && fNative)
                {
                // the type info that we are creating is a "native rebase"; it may have already
                // accumulated declared methods from interfaces that it implements, so they need
                // to be processed by "finishAdoption"
                for (Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
                    {
                    MethodInfo infoOld = entry.getValue();
                    MethodInfo infoNew = infoOld.finishAdoption(fNative, errs);
                    if (infoNew != infoOld)
                        {
                        entry.setValue(infoNew);
                        if (infoNew.isVirtual())
                            {
                            assert infoOld.isVirtual();
                            Object     nid       = entry.getKey().getNestedIdentity();
                            MethodInfo infoCheck = mapVirtMethods.put(nid, infoNew);
                            assert infoOld == infoCheck;
                            }
                        }
                    }
                }
            }

        return !fIncomplete;
        }

    /**
     * Explode a single property that could be composed of (1) an "into Ref" or "into Var", (2) a
     * sequence of annotations, and (3) custom code. Basically, a property is a "class within a
     * class", and we are working through multiple contributions embedded in a single contribution
     * of the containing class.
     *
     * @param constId         identity of the class
     * @param idProp          the identity of the property being exploded
     * @param info            the PropertyInfo for the property being exploded
     * @param mapProps        properties of the class
     * @param mapVirtProps    the virtual properties of the type, keyed by nested id
     * @param mapMethods      methods of the class
     * @param mapVirtMethods  the virtual methods of the type, keyed by nested id
     * @param errs            the error list to log any errors to
     *
     * @return true iff the process was able to obtain all of the necessary TypeInfo information
     *         required to explode the property
     */
    protected boolean explodeProperty(
            IdentityConstant                    constId,
            ClassStructure                      struct,
            PropertyConstant                    idProp,
            PropertyInfo                        info,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<MethodConstant, MethodInfo>     mapMethods,
            Map<Object, MethodInfo>             mapVirtMethods,
            ErrorListener                       errs)
        {
        boolean fComplete = true;

        // layer on an "into" of either "into Ref" or "into Var"
        ConstantPool pool     = getConstantPool();
        TypeConstant typeProp = info.getType();
        TypeConstant typeBase = info.isVar()
                                    ? pool.typeVarRB()
                                    : info.requiresNativeRef()
                                        ? pool.typeRefRB()
                                        : null;

        TypeConstant typeInto;
        TypeInfo     infoInto;
        if (typeBase == null)
            {
            // NakedRef belongs to a different pool
            infoInto = pool.getNakedRefInfo(typeProp);
            typeInto = infoInto.getType();
            }
        else
            {
            typeInto = pool.ensureAccessTypeConstant(
                        pool.ensureParameterizedTypeConstant(typeBase, typeProp), Access.PROTECTED);
            infoInto = typeInto.ensureTypeInfoInternal(errs);
            }

        if (isComplete(infoInto))
            {
            nestAndLayerOn(constId, idProp, mapProps, mapVirtProps, mapMethods,
                mapVirtMethods, typeInto, infoInto, errs);
            }
        else
            {
            fComplete = false;
            }

        // layer on any annotations, if any
        Annotation[] aAnnos = info.getRefAnnotations();
        int          cAnnos = aAnnos.length;
        for (int i = cAnnos - 1; i >= 0; --i)
            {
            Annotation     anno     = aAnnos[i];
            TypeConstant   typeAnno = anno.getAnnotationType();
            ClassStructure clzAnno  = (ClassStructure) ((IdentityConstant) anno.getAnnotationClass()).getComponent();
            if (typeAnno.isIntoPropertyType() && clzAnno.isParameterized())
                {
                typeAnno = pool.ensureParameterizedTypeConstant(typeAnno, typeProp);
                }
            typeAnno = pool.ensureAccessTypeConstant(typeAnno, Access.PROTECTED);

            TypeInfo infoAnno = typeAnno.ensureTypeInfoInternal(errs);
            if (infoAnno == null)
                {
                fComplete = false;
                }
            else
                {
                nestAndLayerOn(constId, idProp, mapProps, mapVirtProps, mapMethods,
                    mapVirtMethods, typeAnno, infoAnno, errs);
                }
            }

        // the custom logic will get overlaid later by layerOnMethods(); in the case of a native
        // getter for otherwise natural classes, it needs to be added (ensured) at this point so
        // that it will get picked up in that layer-on processing
        if (struct.getFormat() != Component.Format.INTERFACE)
            {
            PropertyStructure prop = (PropertyStructure) idProp.getComponent();
            if (prop != null && prop.isNative())
                {
                MethodConstant idGet   = info.getGetterId();
                MethodBody     bodyGet = new MethodBody(idGet, idGet.getSignature(), Implementation.Native);
                MethodInfo     infoGet = new MethodInfo(bodyGet);

                mapMethods.put(idGet, infoGet);
                mapVirtMethods.put(idGet.resolveNestedIdentity(pool, this), infoGet);
                }
            }

        return fComplete;
        }

    /**
     * Take information being contributed to a property from a class, and "indent" that information
     * so that it can apply to the property (which itself is _nested_ under a class). Then layer
     * that properly indented (nested) information onto the property.
     *
     * @param constId         identity of the class
     * @param idProp          the property being contributed to
     * @param mapProps        properties of the class
     * @param mapVirtProps    the virtual properties of the type, keyed by nested id
     * @param mapMethods      methods of the class
     * @param mapVirtMethods  the virtual methods of the type, keyed by nested id
     * @param typeContrib     the type whose members are being contributed
     * @param infoContrib     the information to add to the specified property
     * @param errs            the error list to log any errors to
     */
    protected void nestAndLayerOn(
            IdentityConstant                    constId,
            PropertyConstant                    idProp,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<MethodConstant, MethodInfo>     mapMethods,
            Map<Object, MethodInfo>             mapVirtMethods,
            TypeConstant                        typeContrib,
            TypeInfo                            infoContrib,
            ErrorListener                       errs)
        {
        ConstantPool pool = getConstantPool();

        // basically, everything in infoContrib needs to be "indented" (nested) within the nested
        // identity of the property *without* resolving generic types (to avoid double-dipping)
        Map<PropertyConstant, PropertyInfo> mapContribProps = new HashMap<>();
        for (Entry<PropertyConstant, PropertyInfo> entry : infoContrib.getProperties().entrySet())
            {
            Object           nidContrib = entry.getKey().resolveNestedIdentity(pool, null);
            PropertyConstant idContrib  = (PropertyConstant) idProp.appendNestedIdentity(pool, nidContrib);
            mapContribProps.put(idContrib, entry.getValue());
            }
        layerOnProps(constId, false, null, mapProps, mapVirtProps, typeContrib, mapContribProps, errs);

        Map<MethodConstant, MethodInfo> mapContribMethods = new HashMap<>();
        for (Entry<MethodConstant, MethodInfo> entry : infoContrib.getMethods().entrySet())
            {
            Object         nidContrib = entry.getKey().resolveNestedIdentity(pool, null);
            MethodConstant idContrib  = (MethodConstant) idProp.appendNestedIdentity(pool, nidContrib);
            MethodInfo     infoMethod = entry.getValue();
            if (infoMethod.isCapped())
                {
                infoMethod = infoMethod.nestNarrowingIdentity(pool, idProp);
                }
            mapContribMethods.put(idContrib, infoMethod);
            }
        layerOnMethods(constId, false, false, null, mapMethods, mapVirtMethods, typeContrib, mapContribMethods, errs);
        }

    /**
     * Layer on the passed property contributions onto the property information already collected.
     *
     * @param constId          identity of the class
     * @param fSelf            true if the layer being added represents the "Equals" contribution of
     * @param idDelegate       the property constant that provides the reference to delegate to
     * @param mapProps         properties of the class
     * @param mapVirtProps     the virtual properties of the type, keyed by nested id
     * @param typeContrib      the type whose members are being contributed
     * @param mapContribProps  the property information to add to the existing properties
     *                         the type
     * @param errs             the error list to log any errors to
     */
    protected void layerOnProps(
            IdentityConstant                    constId,
            boolean                             fSelf,
            PropertyConstant                    idDelegate,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            TypeConstant                        typeContrib,
            Map<PropertyConstant, PropertyInfo> mapContribProps,
            ErrorListener                       errs)
        {
        for (Entry<PropertyConstant, PropertyInfo> entry : mapContribProps.entrySet())
            {
            layerOnProp(constId, fSelf, idDelegate, mapProps, mapVirtProps,
                    typeContrib, entry.getKey(), entry.getValue(), errs);
            }
        }

    /**
     * Layer on the passed property contribution onto the property information already collected.
     *
     * @param constId       identity of the class
     * @param fSelf         true if the layer being added represents the "Equals" contribution of
     *                      the type
     * @param idDelegate    the property constant that provides the reference to delegate to
     * @param mapProps      properties of the class
     * @param mapVirtProps  the virtual properties of the type, keyed by nested id
     * @param typeContrib   the type whose members are being contributed
     * @param idContrib     the identity of the property contribution
     * @param propContrib   the PropertyInfo for the property contribution to layer on
     * @param errs          the error list to log any errors to
     */
    protected void layerOnProp(
            IdentityConstant                    constId,
            boolean                             fSelf,
            PropertyConstant                    idDelegate,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            TypeConstant                        typeContrib,
            PropertyConstant                    idContrib,
            PropertyInfo                        propContrib,
            ErrorListener                       errs)
        {
        ConstantPool     pool       = getConstantPool();
        Object           nidContrib = idContrib.resolveNestedIdentity(pool, this);
        PropertyConstant idResult   = (PropertyConstant) constId.appendNestedIdentity(pool, nidContrib);

        // the property is not virtual if it is a constant, if it is private/private, or if
        // it is inside a method (which coincidentally must be private/private). in this
        // case, the properties are always "fully scoped" (they have only one identity), so
        // there is no chance of a collision
        boolean fVirtual = propContrib.isVirtual();

        // look for a property of the same name (using its nested identity); only virtually
        // composable properties are registered using their nested identities
        PropertyInfo propBase = fVirtual
                ? mapVirtProps.get(nidContrib)
                : null;

        PropertyInfo propResult = propBase == null
                ? propContrib
                : propContrib.getIdentity().equals(propBase.getIdentity())
                        ? propBase
                        : propBase.layerOn(propContrib, fSelf, false, errs);

        // formal properties don't delegate
        if (idDelegate != null && !propResult.isFormalType())
            {
            PropertyBody head = propResult.getHead();
            TypeConstant type = propResult.getType();

            PropertyBody bodyDelegate = new PropertyBody(head.getStructure(),
                    Implementation.Delegating, idDelegate, type,
                    head.isRO(), head.isRW(), /*fCustom*/ false,
                    Effect.BlocksSuper, Effect.BlocksSuper,
                    /*fReqField*/ false, /*fConstant*/ false, null, null);
            propResult = new PropertyInfo(propResult, bodyDelegate);
            }

        // check if there's supposed to be a property by this same identity
        if (propBase == null && propContrib.isOverride())
            {
            log(errs, Severity.ERROR, VE_PROPERTY_OVERRIDE_NO_SPEC,
                    typeContrib.getValueString(), propContrib.getName());
            }

        // the property is stored both by its absolute (fully qualified) ID and its nested
        // ID, which is useful for example when trying to find it when building the actual
        // call chains
        mapProps.put(idResult, propResult);
        if (fVirtual)
            {
            mapVirtProps.put(nidContrib, propResult);
            }
        }

    /**
     * Layer on the passed method contributions onto the method information already collected.
     *
     * @param constId            identity of the class
     * @param fSelf              true if the layer being added represents the "Equals" contribution of
     *                           the type
     * @param fAnnoMixin         true if the contribution represents an annotation mixin
     * @param idDelegate         the property constant that provides the reference to delegate to
     * @param mapMethods         methods of the class
     * @param mapVirtMethods     the virtual methods of the type, keyed by nested id
     * @param typeContrib        the type whose members are being contributed
     * @param mapContribMethods  the method information to add to the existing methods
     * @param errs               the error list to log any errors to
     */
    protected void layerOnMethods(
            IdentityConstant                constId,
            boolean                         fSelf,
            boolean                         fAnnoMixin,
            PropertyConstant                idDelegate,
            Map<MethodConstant, MethodInfo> mapMethods,
            Map<Object, MethodInfo>         mapVirtMethods,
            TypeConstant                    typeContrib,
            Map<MethodConstant, MethodInfo> mapContribMethods,
            ErrorListener                   errs)
        {
        // the challenge here is that the methods being contributed may @Override a method that
        // does not have the same exact signature, in which case the method signature is
        // _narrowed_. there are a few different possible outcomes when this occurs:
        // 1) there is only one method in the contribution that narrows the method signature,
        //    and no method in the contribution that has the same signature: this is the
        //    typical case, in which the method signature is truly narrowed, but the resulting
        //    data structure carries a record of that choice. first, the method that is being
        //    narrowed is *capped*, which is to say that it can no longer be extended (although
        //    it still exists and can be found by the un-narrowed signature, since it is
        //    necessary for the system to be able to find the method chain that corresponds to
        //    that un-narrowed signature, because that is the signature that will appear in any
        //    code that was compiled against the base type). Further, the cap indicates what
        //    signature it was narrowed to, and its runtime behavior is to virtually invoke that
        //    narrowed signature, which in turn will be able to walk up its super chain to the
        //    bottom-most narrowing method, which then supers to the method chain that is under
        //    the cap.
        // 2) there are one or more methods in the contribution that narrow the method
        //    signature, and there is also a method in the contribution that has the same
        //    exact non-narrowed signature: this is a less common case, but it is one that is
        //    expected to occur whenever the loss of the non-narrowed method is undesirable.
        //    the result is that, instead of a "cap" on the un-narrowed method chain, the method
        //    from the contribution with the exact same signature is placed onto the top of that
        //    un-narrowed method chain, as one would expect. additionally, any method that
        //    selects the un-narrowed method chain as its super will super to the un-narrowed
        //    method chain, starting with the method that was on top of that chain *before*
        //    this contribution was added.
        // 3) if there is more than one method in the contribution that narrow the method
        //    signature, and no method in the contribution that has the same signature: this is
        //    a compiler and verifier error, because there is no single signature that is doing
        //    the narrowing, and thus there is ambiguity in terms of which signature the cap
        //    should virtually invoke.
        // to accurately collect this information, including sufficient information to report
        // any errors, all changes to virtual method chains are recorded in a separate map, so
        // that the "pre-contribution" view is not modified until all of the information has
        // been collected. additionally, if any method signatures are narrowed, the un-narrowed
        // signatures are recorded in a separate set, so that it is possible to determine if
        // they should be capped (and to identify any errors).

        ConstantPool             pool            = getConstantPool();
        Map<Object, MethodInfo>  mapVirtMods     = new HashMap<>();
        Map<Object, Set<Object>> mapNarrowedNids = null;
        for (Entry<MethodConstant, MethodInfo> entry : mapContribMethods.entrySet())
            {
            MethodConstant    idContrib     = entry.getKey();
            MethodInfo        methodContrib = entry.getValue();
            SignatureConstant sigContrib    = methodContrib.getSignature();

            // the method is not virtual if it is a function, if it is private, or if it is
            // contained inside a method or property that is non-virtual;
            // however, the processing for property accessors is the same as for virtual methods
            if (!methodContrib.isVirtual() && !methodContrib.isPotentialPropertyOverlay())
                {
                // TODO check for collision, because a function could theoretically replace a virtual method
                // TODO (e.g. 2 modules, 1 introduces a virtual method in a new version that collides with a function in the other)
                // TODO we'll also have to check similar conditions below

                boolean fKeep = true;
                if (methodContrib.isConstructor())
                    {
                    idContrib = (MethodConstant) constId.appendNestedIdentity(
                                                    pool, idContrib.getSignature());
                    // for all other purposes validators are treated as constructors, except we need
                    // to chain them (based on the same resolved id)
                    if (methodContrib.isValidator())
                        {
                        MethodInfo methodBase = mapMethods.get(idContrib);
                        if (methodBase != null)
                            {
                            methodContrib = methodBase.layerOnValidator(methodContrib);
                            }
                        }
                    else
                        {
                        // In general constructors are not virtual, unless a class or mixin implement
                        // an interface that declares a virtual constructor.
                        // When a real constructor matches a virtual one, the "virtual origin"
                        // information is retained on the non-virtual (real) constructor.
                        // We keep constructors in the map of methods only for "self" and not
                        // any "super" contributions, except for virtual constructors that are
                        // retained to enforce the corresponding constructor contracts.
                        fKeep = fSelf || methodContrib.isVirtualConstructor();

                        List<MethodConstant> listMatches =
                                collectVirtualConstructors(methodContrib, mapMethods);

                        switch (listMatches.size())
                            {
                            case 0:
                                break;

                            case 1:
                                {
                                MethodConstant idBase     = listMatches.get(0);
                                MethodInfo     methodBase = mapMethods.get(idBase);

                                methodContrib = methodBase.layerOnVirtualConstructor(methodContrib);
                                fKeep         = true;

                                if (!idBase.equals(idContrib))
                                    {
                                    // TODO: removing the base constructor is a bit ham-handed;
                                    //       consider capping it, similarly to regular methods
                                    mapMethods.remove(idBase);
                                    }
                                break;
                                }

                            default:
                                log(errs, Severity.ERROR, VE_METHOD_NARROWING_AMBIGUOUS,
                                        typeContrib.getValueString(),
                                        idContrib.getValueString(),
                                        listMatches.get(0).getValueString());
                                break;
                            }
                        }
                    }
                else if (idContrib.getNestedDepth() == 2)
                    {
                    List<MethodConstant> listMatches =
                            collectCoveredFunctions(sigContrib, mapMethods);
                    for (MethodConstant idMethod : listMatches)
                        {
                        methodContrib = methodContrib.subsumeFunction(mapMethods.remove(idMethod));
                        }
                    }
                else
                    {
                    // don't collect any abstract functions on nested structures
                    fKeep = fSelf && !methodContrib.isAbstract();
                    }

                if (fKeep)
                    {
                    // unlike the virtual methods, we don't re-resolve nested identity
                    // (via constId.appendNestedIdentity(pool, nidContrib)
                    // and instead keep all functions keyed by their "original" id
                    mapMethods.put(idContrib, methodContrib);
                    }
                continue;
                }

            // look for a method of the same signature (using its nested identity); only
            // virtual methods are registered using their nested identities
            // TODO: explain why only "fSelf" idContrib should be resolved
            MethodInfo  methodResult = methodContrib;
            Object      nidContrib   = fSelf
                                        ? idContrib.resolveNestedIdentity(pool, this)
                                        : idContrib.getNestedIdentity();

            if (fAnnoMixin)
                {
                if (methodContrib.getHead().getImplementation() == Implementation.Implicit)
                    {
                    // this was added synthetically by "asInto" processing; ignore
                    continue;
                    }
                if (methodContrib.isCapped())
                    {
                    // the cap was introduced by the mixin itself; keep it as is
                    mapVirtMods.put(nidContrib, methodContrib);
                    continue;
                    }
                }

            List<Object> listMatches = collectPotentialSuperMethods(
                    methodContrib.getHead().getMethodStructure(),
                    nidContrib, sigContrib, mapVirtMethods);

            if (methodContrib.getTail().isOverride() ||
                    fAnnoMixin && methodContrib.getHead().isOverride())
                {
                // the @Override tag gives us permission to look for a method with a
                // different signature that can be narrowed to the signature of the
                // contribution (because @Override means there MUST be a super method)
                if (listMatches.isEmpty())
                    {
                    if (fAnnoMixin || methodContrib.getTail().isNative())
                        {
                        // take it as is
                        mapVirtMods.put(nidContrib, methodResult);
                        }
                    else
                        {
                        log(errs, Severity.ERROR, VE_SUPER_MISSING,
                                methodContrib.getIdentity().getPathString(), getValueString());
                        }
                    }
                else
                    {
                    Object     nidBase = null;
                    MethodInfo methodBase;

                    if (listMatches.size() == 1)
                        {
                        nidBase      = listMatches.get(0);
                        methodBase   = mapVirtMethods.get(nidBase);
                        if (methodBase.isCapped())
                            {
                            if (fAnnoMixin)
                                {
                                // the "super" method we found is capped, but the cap itself apparently
                                // didn't match; this can happen for "into (A | B)" mixins;
                                // replace the capped method with the narrowing one
                                nidBase = methodBase.getNarrowingMethod(mapVirtMethods);

                                assert nidBase != null;
                                methodResult = mapVirtMethods.get(nidBase);
                                }
                            else
                                {
                                // the "super" method we found is capped, but the cap itself apparently
                                // didn't match; this means that the attempted override is illegal
                                MethodConstant id = methodBase.getIdentity();
                                id.log(errs, Severity.ERROR, VE_METHOD_OVERRIDE_ILLEGAL,
                                        idContrib.getNamespace().getValueString(),
                                        id.getSignature().getValueString(),
                                        id.getNamespace().getValueString());
                                nidBase = null;
                                }
                            }
                        else
                            {
                            methodResult = methodBase.layerOn(methodContrib, fSelf, errs);
                            }
                        }
                    else
                        {
                        // now we need find a method that would be the unambiguously best choice;
                        // collect the signatures into an array and a lookup map (sig->nid)
                        int                            cMatches = listMatches.size();
                        SignatureConstant[]            aSig     = new SignatureConstant[cMatches];
                        Map<SignatureConstant, Object> mapNids  = new HashMap<>(cMatches);
                        for (int i = 0; i < cMatches; i++)
                            {
                            Object            nid = listMatches.get(i);
                            SignatureConstant sig = mapVirtMethods.get(nid).getSignature();

                            aSig[i] = sig;
                            mapNids.put(sig, nid);
                            }

                        SignatureConstant sigBest = selectBest(aSig);
                        if (sigBest == null)
                            {
                            log(errs, Severity.ERROR, VE_SUPER_AMBIGUOUS,
                                    methodContrib.getIdentity().getPathString());
                            }
                        else
                            {
                            nidBase    = mapNids.get(sigBest);
                            methodBase = mapVirtMethods.get(nidBase);
                            if (methodBase.isCapped())
                                {
                                // replace the capped method with the narrowing (non-capped)
                                nidBase    = methodBase.getNarrowingMethod(mapVirtMethods);
                                methodBase = mapVirtMethods.get(nidBase);

                                assert nidBase != null;
                                listMatches.remove(sigBest);
                                cMatches--;
                                }
                            methodResult = methodBase.layerOn(methodContrib, fSelf, errs);

                            // there are multiple non-ambiguous "super" methods;
                            for (int i = 0; i < cMatches; i++)
                                {
                                Object nid = listMatches.get(i);
                                if (nid.equals(nidBase))
                                    {
                                    continue;
                                    }

                                MethodInfo method = mapVirtMethods.get(nid);
                                if (method.isAbstract())
                                    {
                                    // we have an abstract method on the super that is covered
                                    // by this method; let's reflect this fact
                                    method = method.layerOn(methodContrib, fSelf, errs);
                                    mapVirtMods.put(nid, method);
                                    }
                                }
                            }
                        }

                    if (nidBase != null)
                        {
                        if (nidBase.equals(nidContrib))
                            {
                            // while the ids are "equal", they are not the same;
                            // one of them may have a resolver and the other may not;
                            // as a result, the call to
                            //      constId.appendNestedIdentity(pool, nid)
                            // below may produce different results
                            nidContrib = nidBase;
                            }
                        else if (!mapVirtMods.containsKey(nidBase))
                            {
                            // there exists a method that this method will narrow that did *not*
                            // receive a contribution of its own, so add this method to the set of
                            // methods that are narrowing the super method
                            if (mapNarrowedNids == null)
                                {
                                mapNarrowedNids = new HashMap<>();
                                }
                            Set<Object> setNarrowing = mapNarrowedNids.computeIfAbsent(
                                nidBase, key_ -> new HashSet<>());
                            setNarrowing.add(nidContrib);
                            }
                        }
                    }
                }
            else
                {
                // override is not specified
                if (fSelf)
                    {
                    // report "override required" if necessary
                    for (Object nid : listMatches)
                        {
                        MethodInfo methodMatch = mapVirtMethods.get(nid);
                        if (methodMatch != null && !methodMatch.getIdentity().equals(methodContrib.getIdentity()))
                            {
                            log(errs, Severity.ERROR, VE_METHOD_OVERRIDE_REQUIRED,
                                getValueString(),
                                methodMatch.getIdentity(),
                                methodContrib.getIdentity().getPathString()
                                );
                            }
                        }
                    }

                // find the best base method to layer on; use a capped base method only if nothing
                // else matches
                MethodInfo methodBase = mapVirtMethods.get(nidContrib);
                if (methodBase == null || methodBase.isCapped())
                    {
                    for (Object nid : listMatches)
                        {
                        MethodInfo methodMatch = mapVirtMethods.get(nid);
                        if (methodMatch != null && !nid.equals(nidContrib))
                            {
                            if (methodMatch.isCapped())
                                {
                                if (methodBase == null)
                                    {
                                    // take a possible match, but keep looking
                                    methodBase = methodMatch;
                                    }
                                }
                            else
                                {
                                methodBase = methodMatch;
                                break;
                                }
                            }
                        }
                    }
                else
                    {
                    // nidContrib directly points to a "super" method, so it must be in the list
                    assert listMatches.contains(nidContrib);
                    }

                if (methodBase != null)
                    {
                    methodResult = methodBase.layerOn(methodContrib, fSelf, errs);
                    }

                if (idDelegate != null)
                    {
                    // ensure that the delegating body "belongs" to this layer in the chain
                    MethodBody     head         = methodResult.getHead();
                    MethodConstant idMethod     = head.getIdentity().ensureNestedIdentity(pool, constId);
                    MethodBody     bodyDelegate = new MethodBody(
                        idMethod, head.getSignature(), Implementation.Delegating, idDelegate);

                    methodResult = new MethodInfo(Handy.appendHead(methodResult.getChain(), bodyDelegate));
                    }
                }

            mapVirtMods.put(nidContrib, methodResult);
            }

        if (mapNarrowedNids != null)
            {
            // find every narrowed method signature that did *not* receive a contribution of its
            // own (i.e. same method signature), because any that did receive a contribution at
            // this level can be safely ignored
            mapNarrowedNids.keySet().removeAll(mapVirtMods.keySet());

            // for each remaining nid that was narrowed, if it was narrowed by exactly one
            // method, then cap the nid by redirecting to the narrowed method, otherwise it is
            // an error
            for (Entry<Object, Set<Object>> entry : mapNarrowedNids.entrySet())
                {
                Object      nidNarrowed  = entry.getKey();
                Set<Object> setNarrowing = entry.getValue();
                if (setNarrowing.size() == 1)
                    {
                    // cap the method
                    Object     nidNarrowing  = setNarrowing.iterator().next();
                    MethodInfo infoNarrowing = mapVirtMods.get(nidNarrowing);
                    MethodInfo infoNarrowed  = mapVirtMethods.get(nidNarrowed);
                    mapVirtMods.put(nidNarrowed, infoNarrowed.capWith(this, infoNarrowing));
                    }
                else
                    {
                    for (Object nidNarrowing : setNarrowing)
                        {
                        log(errs, Severity.ERROR, VE_METHOD_NARROWING_AMBIGUOUS,
                                typeContrib.getValueString(),
                                mapVirtMethods.get(nidNarrowed).getIdentity().getValueString(),
                                mapVirtMods.get(nidNarrowing).getIdentity().getSignature().getValueString());
                        }
                    }
                }
            }

        // the method is stored both by its absolute (fully qualified) ID and its nested
        // ID, which is useful for example when trying to find it when building the actual
        // call chains
        for (Entry<Object, MethodInfo> entry : mapVirtMods.entrySet())
            {
            Object         nid  = entry.getKey();
            MethodInfo     info = entry.getValue();
            MethodConstant id   = (MethodConstant) constId.appendNestedIdentity(pool, nid);

            mapMethods.put(id, info);
            mapVirtMethods.put(nid, info);
            }
        }

    /**
     * Collect all functions from the base that would be "hidden" by the specified function.
     *
     * @param sigSub   the signature of the function that can "hide" base functions
     * @param mapBase  the map of all base methods to select from
     *
     * @return a list of all matching function identities
     */
    protected List<MethodConstant> collectCoveredFunctions(
            SignatureConstant               sigSub,
            Map<MethodConstant, MethodInfo> mapBase)

        {
        List<MethodConstant> listMatch = new ArrayList<>();
        for (Entry<MethodConstant, MethodInfo> entry : mapBase.entrySet())
            {
            MethodConstant id   = entry.getKey();
            MethodInfo     info = entry.getValue();

            if (info.isFunction()
                    && id.getName().equals(sigSub.getName()) && id.getNestedDepth() == 2)
                {
                if (sigSub.isSubstitutableFor(id.getSignature(), this))
                    {
                    listMatch.add(id);
                    }
                }
            }
        return listMatch;
        }

    /**
     * Collect all methods that could be the "super" of the specified method signature.
     *
     * @param method     the method structure for the method that is searching for a super (optional)
     * @param nidSub     the nested identity of the method
     * @param sigSub     the signature of the method that is searching for a super
     * @param mapSupers  map of super methods to select from
     *
     * @return a list of all matching nested identities
     */
    protected List<Object> collectPotentialSuperMethods(
            MethodStructure         method,
            Object                  nidSub,
            SignatureConstant       sigSub,
            Map<Object, MethodInfo> mapSupers)
        {
        List<Object> listMatch = null;
        boolean      fExact    = true;
        for (Entry<Object, MethodInfo> entry : mapSupers.entrySet())
            {
            Object nidCandidate = entry.getKey();
            if (IdentityConstant.isNestedSibling(nidSub, nidCandidate))
                {
                SignatureConstant sigCandidate = entry.getValue().getSignature(); // resolved
                if (sigCandidate.getName().equals(sigSub.getName()))
                    {
                    if (sigSub.isSubstitutableFor(sigCandidate, this))
                        {
                        if (!fExact && listMatch != null)
                            {
                            // we found an exact match; get rid of non-exact ones
                            listMatch.clear();
                            }
                        if (listMatch == null)
                            {
                            listMatch = new ArrayList<>();
                            }
                        listMatch.add(nidCandidate);
                        }
                    else if (method != null && (listMatch == null || !fExact))
                        {
                        // allow default parameters (but only if there is no "exact" match)
                        int cDefault = method.getDefaultParamCount();
                        if (cDefault > 0)
                            {
                            int cParamsReq = sigCandidate.getParamCount();
                            int cParamsSub = sigSub.getParamCount();
                            if (cParamsSub > cParamsReq && cParamsSub - cDefault <= cParamsReq)
                                {
                                SignatureConstant sigSubReq = sigSub.truncateParams(0, cParamsReq);
                                if (sigSubReq.isSubstitutableFor(sigCandidate, this))
                                    {
                                    if (listMatch == null)
                                        {
                                        listMatch = new ArrayList<>();
                                        }
                                    listMatch.add(nidCandidate);
                                    fExact = false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        return listMatch == null ? Collections.EMPTY_LIST : listMatch;
        }

    /**
     * Collect all virtual constructors that may serve as a base contract for the specified
     * contributing constructor.
     *
     * This methods is very similar, but simpler then "collectPotentialSuperMethods" above.
     *
     * @param infoConstruct  the contributing constructor at the "sub" level
     * @param mapMethods     the map of all super methods

     * @return a list of all matching virtual constructors
     */
    protected List<MethodConstant> collectVirtualConstructors(
                MethodInfo                      infoConstruct,
                Map<MethodConstant, MethodInfo> mapMethods)
        {
        MethodStructure      method    = infoConstruct.getHead().getMethodStructure();
        SignatureConstant    sigSub    = infoConstruct.getIdentity().getSignature();
        List<MethodConstant> listMatch = null;
        boolean              fExact    = true;

        for (Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodConstant idCandidate = entry.getKey();
            if (idCandidate.getNestedDepth() != 2)
                {
                continue;
                }

            MethodInfo        infoCandidate = entry.getValue();
            SignatureConstant sigCandidate  = infoCandidate.getSignature(); // resolved

            if (sigCandidate.getName().equals(sigSub.getName()) &&
                    infoCandidate.isVirtualConstructor())
                {
                if (sigSub.isSubstitutableFor(sigCandidate, this))
                    {
                    if (!fExact && listMatch != null)
                        {
                        // we found an exact match; get rid of non-exact ones
                        listMatch.clear();
                        }
                    if (listMatch == null)
                        {
                        listMatch = new ArrayList<>();
                        }
                    listMatch.add(idCandidate);
                    }
                else if (method != null && (listMatch == null || !fExact))
                    {
                    // allow default parameters (but only if there is no "exact" match)
                    int cDefault = method.getDefaultParamCount();
                    if (cDefault > 0)
                        {
                        int cParamsReq = sigCandidate.getParamCount();
                        int cParamsSub = sigSub.getParamCount();
                        if (cParamsSub > cParamsReq && cParamsSub - cDefault <= cParamsReq)
                            {
                            SignatureConstant sigSubReq = sigSub.truncateParams(0, cParamsReq);
                            if (sigSubReq.isSubstitutableFor(sigCandidate, this))
                                {
                                if (listMatch == null)
                                    {
                                    listMatch = new ArrayList<>();
                                    }
                                listMatch.add(idCandidate);
                                fExact = false;
                                }
                            }
                        }
                    }
                }
            }
        return listMatch == null ? Collections.EMPTY_LIST : listMatch;
        }

    /**
     * Helper to select the "best" signature from an array of signatures; in other words, choose
     * the one that any other signature could "super" to.
     *
     * @param aSig  an array of signatures
     *
     * @return the "best" signature to use
     */
    public SignatureConstant selectBest(SignatureConstant[] aSig)
        {
        SignatureConstant sigBest     = null;
        int               cCandidates = aSig.length;
        nextCandidate: for (int iCandidate = 0; iCandidate < cCandidates; ++iCandidate)
            {
            SignatureConstant sigCandidate = aSig[iCandidate];
            if (sigBest == null) // that means that "best" is ambiguous thus far
                {
                // have to back-test all the ones in front of us to make sure that
                for (int iPrev = 0; iPrev < iCandidate; ++iPrev)
                    {
                    SignatureConstant sigPrev = aSig[iPrev];
                    if (!sigPrev.isSubstitutableFor(sigCandidate, this))
                        {
                        // still ambiguous
                        continue nextCandidate;
                        }
                    }

                // so far, this candidate is the best
                sigBest = sigCandidate;
                }
            else if (sigBest.isSubstitutableFor(sigCandidate, this))
                {
                // this assumes that "best" is a transitive concept, i.e. we're not going to back-
                // test all of the other candidates
                sigBest = sigCandidate;
                }
            else if (!sigCandidate.isSubstitutableFor(sigBest, this))
                {
                sigBest = null;
                }
            }

        return sigBest;
        }

    /**
     * Collect type parameters for "this" class of "this" type and all its instance parents.
     *
     * @param struct         the class structure
     * @param mapTypeParams  the map of type parameters
     * @param mapProps       the properties of the class
     * @param nBaseRank      the base rank for any properties added by "this" class
     */
    private void collectSelfTypeParameters(
            ClassStructure                      struct,
            Map<Object, ParamInfo>              mapTypeParams,
            Map<PropertyConstant, PropertyInfo> mapProps,
            int                                 nBaseRank,
            ErrorListener                       errs)
        {
        ConstantPool pool = getConstantPool();

        if (isVirtualChild())
            {
            // virtual child has access to the parent's type parameters
            TypeInfo infoParent = getParentType().ensureTypeInfo(errs);

            for (ParamInfo param : infoParent.getTypeParams().values())
                {
                if (!(param.getNestedIdentity() instanceof NestedIdentity))
                    {
                    String sParam = param.getName();

                    mapTypeParams.put(sParam, param);

                    PropertyInfo infoProp = infoParent.findProperty(sParam);
                    if (infoProp == null)
                        {
                        log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_MISSING,
                                getParentType().getValueString(), sParam);
                        }
                    else
                        {
                        mapProps.put(infoProp.getIdentity(), infoProp);
                        }
                    }
                }
            }

        for (Component child : struct.children())
            {
            if (child instanceof PropertyStructure)
                {
                PropertyStructure prop = (PropertyStructure) child;
                if (prop.isGenericTypeParameter())
                    {
                    PropertyConstant id    = prop.getIdentityConstant();
                    int              nRank = nBaseRank + mapProps.size() + 1;

                    mapProps.put(id, new PropertyInfo(new PropertyBody(pool, prop,
                                        mapTypeParams.get(id.getName())), nRank));
                    }
                }
            }
        }

    /**
     * Generate the info for the child structures of the specified contributed structure.
     *
     * @param constId        the identity of the class (used for logging error information)
     * @param fInterface     if the class is an interface type
     * @param structContrib  the class structure, property structure, or method structure or typedef
     * @param mapTypeParams  the map of type parameters
     * @param mapProps       the properties of the class
     * @param mapMethods     the methods of the class
     * @param mapChildren    the child types of the class
     * @param listExplode    used to collect the list of properties that must be "exploded"
     * @param nBaseRank      the base rank for any properties added by "this" class
     * @param errs           the error list to log any errors to
     *
     * @return true iff the processing was able to obtain all of its dependencies
     */
    protected boolean collectChildInfo(
            IdentityConstant                    constId,
            boolean                             fInterface,
            Component                           structContrib,
            Map<Object          , ParamInfo>    mapTypeParams,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo>   mapMethods,
            ListMap<String, ChildInfo>          mapChildren,
            List<PropertyConstant>              listExplode,
            int                                 nBaseRank,
            ErrorListener                       errs)
        {
        boolean fComplete = true;

        // recurse through children
        for (Component child : structContrib.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (!method.getIdentityConstant().isLambda())
                        {
                        fComplete &= createMemberInfo(constId, fInterface, method, mapTypeParams,
                            mapProps, mapMethods, mapChildren, listExplode, nBaseRank, errs);
                        }
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                fComplete &= createMemberInfo(constId, fInterface, child, mapTypeParams,
                    mapProps, mapMethods, mapChildren, listExplode, nBaseRank, errs);
                }
            else if (child instanceof ClassStructure || child instanceof TypedefStructure)
                {
                String sName = child.getIdentityConstant().getNestedName();
                if (sName != null)
                    {
                    // it should not be possible for there to be more than one at this level with
                    // the same name
                    if (mapChildren.containsKey(sName))
                        {
                        log(errs, Severity.ERROR, VE_NAME_COLLISION, constId, sName);
                        }
                    mapChildren.put(sName, new ChildInfo(child));
                    }
                }
            }
        return fComplete;
        }

    /**
     * Generate the members of the "this" class of "this" type.
     *
     * @param constId        the identity of the class (used for logging error information)
     * @param fInterface     if the class is an interface type
     * @param structContrib  the class structure, property structure, or method structure REVIEW or typedef?
     * @param mapTypeParams  the map of type parameters
     * @param mapProps       the properties of the class
     * @param mapMethods     the methods of the class
     * @param mapChildren    the child types of the class
     * @param listExplode    used to collect the list of properties that must be "exploded"
     * @param nBaseRank      the base rank for any properties added by "this" class
     * @param errs           the error list to log any errors to
     *
     * @return true iff the processing was able to obtain all of its dependencies
     */
    private boolean createMemberInfo(
            IdentityConstant                    constId,
            boolean                             fInterface,
            Component                           structContrib,
            Map<Object          , ParamInfo>    mapTypeParams,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo>   mapMethods,
            ListMap<String, ChildInfo>          mapChildren,
            List<PropertyConstant>              listExplode,
            int                                 nBaseRank,
            ErrorListener                       errs)
        {
        ConstantPool pool    = getConstantPool();
        boolean      fRebase = constId instanceof NativeRebaseConstant;

        if (structContrib instanceof MethodStructure)
            {
            MethodStructure   method       = (MethodStructure) structContrib;
            boolean           fHasNoCode   = !method.hasCode();
            boolean           fNative      = method.isNative();
            boolean           fHasAbstract = method.findAnnotation(pool.clzAbstract()) != null;
            MethodConstant    id           = method.getIdentityConstant();
            SignatureConstant sig          = id.getSignature().resolveGenericTypes(pool,
                                                    method.isFunction() ? null : this);
            if (fRebase && fHasNoCode && !fNative)
                {
                // align the structure with the info (may be used by the runtime)
                fNative = true;
                method.markNative();
                }

            MethodBody body = new MethodBody(id, sig,
                    fNative                  ? Implementation.Native   :
                    fInterface && fHasNoCode ? Implementation.Declared :
                    fInterface               ? Implementation.Default  :
                    fHasAbstract             ? Implementation.Abstract :
                    fHasNoCode               ? Implementation.SansCode :
                                               Implementation.Explicit);
            MethodInfo infoNew = new MethodInfo(body);
            mapMethods.put(id, infoNew);
            }
        else if (structContrib instanceof PropertyStructure)
            {
            PropertyStructure prop = (PropertyStructure) structContrib;
            if (prop.isGenericTypeParameter())
                {
                // type parameters have been processed by collectSelfTypeParameters()
                return true;
                }

            assert !(fRebase && fInterface); // cannot be native and interface at the same time

            PropertyConstant  id    = prop.getIdentityConstant();
            int               nRank = nBaseRank + mapProps.size() + 1;
            PropertyInfo      info  = createPropertyInfo(prop, constId, fRebase, fInterface, nRank, errs);
            mapProps.put(id, info);

            if (info.isCustomLogic() || info.isRefAnnotated())
                {
                // this property needs to be "exploded"
                listExplode.add(id);

                // create a ParamInfo and a type-param PropertyInfo for the Referent type parameter
                // note: while this is very hard-coded and dense and inelegant, it basically is
                //       compensating for the fact that we're about to treat the property (id/info)
                //       as it's own ***class***, just like the type for which we are currently
                //       producing a TypeInfo. however, unlike the top level class & TypeInfo, the
                //       property doesn't have a chance to go through the collectTypeParameters()
                //       method, so lacking that, this "jams in" the additional type parameters that
                //       the property relies on (as if they had been correctly populated by going
                //       through collectTypeParameters)
                PropertyConstant idParam   = pool.ensurePropertyConstant(id, "Referent");
                Object           nidParam  = idParam.resolveNestedIdentity(pool, this);
                ParamInfo        param     = new ParamInfo(nidParam, "Referent", pool.typeObject(), info.getType());
                PropertyInfo     propParam = new PropertyInfo(new PropertyBody(pool, null, param), nRank + 1);
                mapTypeParams.put(nidParam, param);
                mapProps.put(idParam, propParam);
                }
            }
        return collectChildInfo(constId, fInterface, structContrib,
                mapTypeParams, mapProps, mapMethods, mapChildren, listExplode, nBaseRank, errs);
        }

    /**
     * Create the PropertyInfo for the specified property.
     *
     * @param prop        the PropertyStructure
     * @param constId     the identity of the containing structure (used only for error messages)
     * @param fNative     true if the type is a native rebase
     * @param fInterface  true if the type is an interface, not a class or mixin (only if not native)
     * @param nRank       the property rank
     * @param errs        the error list to log any errors to
     *
     * @return a new PropertyInfo for the passed PropertyStructure
     */
    private PropertyInfo createPropertyInfo(
            PropertyStructure prop,
            IdentityConstant  constId,
            boolean           fNative,
            boolean           fInterface,
            int               nRank,
            ErrorListener     errs)
        {
        ConstantPool pool  = getConstantPool();
        String       sName = prop.getName();

        // scan the Property annotations
        Annotation[] aPropAnno    = prop.getPropertyAnnotations();
        boolean      fHasRO       = false;
        boolean      fHasAbstract = false;
        boolean      fHasOverride = false;
        boolean      fHasInject   = false;
        for (int i = 0, c = aPropAnno.length; i < c; ++i)
            {
            Annotation annotation = aPropAnno[i];
            Constant   constMixin = annotation.getAnnotationClass();
            if (scanForDups(aPropAnno, i, constMixin))
                {
                log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                        prop.getIdentityConstant().getValueString(),
                        constMixin.getValueString());
                }

            fHasRO       |= constMixin.equals(pool.clzRO());
            fHasAbstract |= constMixin.equals(pool.clzAbstract());
            fHasOverride |= constMixin.equals(pool.clzOverride());
            }

        // check the non-Property annotations (including checking for verifier errors, since the
        // property dumps anything that isn't a well-formed "into Property" annotation into this
        // bucket)
        Annotation[] aRefAnno    = prop.getRefAnnotations();
        boolean      fHasRefAnno = false;
        boolean      fHasVarAnno = false;
        for (int i = 0, c = aRefAnno.length; i < c; ++i)
            {
            Annotation   annotation = aRefAnno[i];
            Constant     constMixin = annotation.getAnnotationClass();
            TypeConstant typeMixin  = pool.ensureTerminalTypeConstant(constMixin);

            if (!typeMixin.isExplicitClassIdentity(true)
                    || typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_NOT_MIXIN,
                        typeMixin.getValueString());
                continue;
                }

            TypeConstant typeInto    = typeMixin.getExplicitClassInto();
            TypeConstant typeIntoCat = typeInto.getIntoPropertyType();
            if (typeIntoCat == null || typeIntoCat.equals(pool.typeProperty()))
                {
                log(errs, Severity.ERROR, VE_PROPERTY_ANNOTATION_INCOMPATIBLE,
                        sName, constId.getPathString(), typeMixin.getValueString());
                continue;
                }

            // we've already processed the "into Property" annotations, so this has to be an
            // "into Ref" (or some sub-class of Ref, e.g. Var) annotation
            assert typeInto.isA(pool.typeRef());

// TODO verify that the mixin has one and only one type parameter, and it is named Referent, i.e. "mixin M<Referent> into Var<Referent>"
// TODO does the annotation class provide a hard-coded value for Referent? because if it does, we need to "isA()" test it against the type of the property

            if (scanForDups(aRefAnno, i, constMixin))
                {
                log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                        prop.getIdentityConstant().getValueString(),
                        constMixin.getValueString());
                }

            if (constMixin.equals(pool.clzInject()))
                {
                fHasInject = true;
                }
            else
                {
                fHasRefAnno = true;
                fHasVarAnno |= typeInto.isA(pool.typeVar());
                }
            }

        // functions and constants cannot have properties; methods cannot have constants
        IdentityConstant constParent = prop.getIdentityConstant().getParentConstant();
        boolean          fConstant   = prop.isStatic();
        switch (constParent.getFormat())
            {
            case Property:
                if (!fConstant && prop.getParent().isStatic())
                    {
                    log(errs, Severity.ERROR, VE_CONST_CODE_ILLEGAL,
                            constParent.getValueString(), sName);
                    }
                break;

            case Method:
                if (!fConstant && prop.getParent().isStatic())
                    {
                    // a function cannot contain properties
                    log(errs, Severity.ERROR, VE_FUNCTION_CONTAINS_PROPERTY,
                            constParent.getValueString(), sName);
                    }
                break;

            case Module:
            case Package:
            case Class:
                break;

            default:
                throw new IllegalStateException("a property (" + sName
                        + ") cannot be nested under a " + constParent.getFormat()
                        + " (on " + constId.getValueString() + ")");
            }

        // check the methods to see if get() and set() call super
        MethodStructure  methodInit     = null;
        MethodStructure  methodGet      = null;
        MethodStructure  methodSet      = null;
        MethodStructure  methodBadGet   = null;
        MethodStructure  methodBadSet   = null;
        int              cCustomMethods = 0;
        for (Component child : prop.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (method.isPotentialInitializer())
                        {
                        if (methodInit == null && method.isInitializer(prop.getType(), this))
                            {
                            methodInit = method;
                            }
                        else
                            {
                            // there can only be one initializer function, and it must exactly match a very
                            // specific signature
                            log(errs, Severity.ERROR, VE_DUP_INITIALIZER,
                                    getValueString(), sName);
                            }

                        // an initializer is not counted as custom code
                        continue;
                        }

                    if (fConstant)
                        {
                        // the only method allowed under a static property is the initializer
                        log(errs, Severity.ERROR, VE_CONST_CODE_ILLEGAL,
                                getValueString(), sName);
                        continue;
                        }

                    if (method.isPotentialGetter())
                        {
                        if (method.isGetter(prop.getType(), this))
                            {
                            if (methodGet != null)
                                {
                                log(errs, Severity.ERROR, VE_PROPERTY_GET_AMBIGUOUS,
                                        getValueString(), sName);
                                }
                            methodGet = method;
                            }
                        else
                            {
                            methodBadGet = method;
                            }
                        }
                    else if (method.isPotentialSetter())
                        {
                        if (method.isSetter(prop.getType(), this))
                            {
                            if (methodSet != null)
                                {
                                log(errs, Severity.ERROR, VE_PROPERTY_SET_AMBIGUOUS,
                                        getValueString(), sName);
                                }
                            methodSet = method;
                            }
                        else
                            {
                            methodBadSet = method;
                            }
                        }

                    // regardless of what the code does, there is custom code in the property
                    // Note: any code in the native interfaces(Ref, Enum, etc.) is for show only
                    if (!method.isAbstract() && !method.isStatic() && !fNative)
                        {
                        ++cCustomMethods;
                        }
                    }
                }
            }

        // check for incorrect get/set method declarations
        if (methodBadGet != null && methodGet == null)
            {
            log(errs, Severity.ERROR, VE_PROPERTY_GET_INCOMPATIBLE,
                    getValueString(), sName);
            }
        if (methodBadSet != null && methodSet == null)
            {
            log(errs, Severity.ERROR, VE_PROPERTY_SET_INCOMPATIBLE,
                    getValueString(), sName);
            }

        // check access flags
        Access accessRef = prop.getAccess();
        Access accessVar = prop.getVarAccess();
        if (accessRef == Access.STRUCT | accessVar == Access.STRUCT)
            {
            log(errs, Severity.ERROR, VE_PROPERTY_ACCESS_STRUCT,
                    getValueString(), sName);
            }
        else  if (accessVar != null && accessRef.isLessAccessibleThan(accessVar))
            {
            log(errs, Severity.ERROR, VE_PROPERTY_ACCESS_ILLEGAL,
                    getValueString(), sName);
            }

        boolean         fRW       = false;
        boolean         fRO       = false;
        boolean         fField    = false;
        Effect          effectGet = Effect.None;
        Effect          effectSet = Effect.None;
        Implementation  impl;
        if (fConstant)
            {
            impl = Implementation.Native;

            // static properties of a type are language-level constant values, e.g. "Int KB = 1024;"
            if (!prop.hasInitialValue() && (prop.getInitialValue() == null) == (methodInit == null))
                {
                if (methodInit == null)
                    {
                    // it is an error for a static property to not have an initial value
                    log(errs, Severity.ERROR, VE_CONST_VALUE_REQUIRED,
                            getValueString(), sName);
                    }
                else
                    {
                    // it is an error for a static property to have both an initial value that is
                    // specified by a constant and by an initializer function
                    log(errs, Severity.ERROR, VE_CONST_VALUE_REDUNDANT,
                            getValueString(), sName);
                    }
                }

            if (fHasAbstract)
                {
                // it is an error for a constant to be annotated by "@Abstract"
                log(errs, Severity.ERROR, VE_CONST_ABSTRACT_ILLEGAL,
                        getValueString(), sName);
                }

            if (fHasOverride)
                {
                // it is an error for a constant to be annotated by "@Override"
                log(errs, Severity.ERROR, VE_CONST_OVERRIDE_ILLEGAL,
                        getValueString(), sName);
                }

            if (fHasRefAnno)
                {
                // it is an error for a constant to be annotated in a manner that affects the Ref
                log(errs, Severity.ERROR, VE_CONST_ANNOTATION_ILLEGAL,
                        getValueString(), sName);
                }

            if (accessVar != null)
                {
                // it is an error for a static property to have both reader and writer access
                // specified, e.g. "public/private"
                log(errs, Severity.ERROR, VE_CONST_READWRITE_ILLEGAL,
                        getValueString(), sName);
                }
            }
        else if (fInterface)
            {
            impl   = Implementation.Declared;
            fRO   |= fHasRO;
            fRW   |= !fRO | accessVar != null;
            fField = false;

            if (cCustomMethods > 0)
                {
                // interface is not allowed to implement a property, other than it may have a
                // default implementation of get()
                if (cCustomMethods == 1 && methodGet != null)
                    {
                    // the @RO annotation is required in this case
                    if (fHasRO)
                        {
                        effectGet = Effect.BlocksSuper;
                        }
                    else
                        {
                        log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_GET_REQUIRES_RO,
                                getValueString(), sName);
                        }
                    }
                else
                    {
                    log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_IMPLEMENTED,
                            getValueString(), sName);
                    }
                }

            if (fHasRefAnno)
                {
                // interface is not allowed to specify ref/var annotations
                log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_ANNOTATED,
                        getValueString(), sName);
                }

            if (fHasInject)
                {
                // interface is not allowed to use @Inject
                log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_INJECTED,
                        getValueString(), sName);
                }

            if (fHasAbstract && prop.getParent().getFormat() == Component.Format.INTERFACE)
                {
                // it is an error for a interface property to be annotated by "@Abstract"
                log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_ABSTRACT_ILLEGAL,
                        getValueString(), sName);
                }
            }
        else
            {
            fNative |= prop.isNative();
            impl     = fNative ? Implementation.Native : Implementation.Explicit;

            // determine if the get explicitly calls super, or explicitly blocks super
            boolean fGetSupers      = methodGet != null && methodGet.usesSuper();
            boolean fSetSupers      = methodSet != null && methodSet.usesSuper();
            boolean fGetBlocksSuper = methodGet != null && !methodGet.isAbstract() && !fGetSupers;
            boolean fSetBlocksSuper = methodSet != null && !methodSet.isAbstract() && !fSetSupers;

            if (fNative)
                {
                // native property:
                // - if there is a natural getter, it never calls super;
                // - also, the natural code may pretend there is a field, in which case there is no
                //   natural getter;
                // - if the native property has a Var annotation (e.g. @Lazy), the field needs to
                //   be there
                fGetSupers      = false;
                fGetBlocksSuper = true;
                fField          = fHasVarAnno;
                fRO             = fHasRO;
                fRW             = !fHasRO;
                }
            else
                {
                if (fHasRO && (fSetSupers || fHasVarAnno))
                    {
                    // the @RO conflicts with the annotations that require a Var
                    log(errs, Severity.ERROR, VE_PROPERTY_READONLY_NOT_VAR,
                            getValueString(), sName);
                    }

                if (fHasRO && !(fHasAbstract || fHasOverride || fHasInject || methodGet != null))
                    {
                    log(errs, Severity.ERROR, VE_PROPERTY_READONLY_NO_SPEC,
                            getValueString(), sName);
                    }

                // @Inject should not have ANY other Ref/Var annotations, and shouldn't override get/set
                if (fHasInject && (methodGet != null || methodSet != null || fHasRefAnno))
                    {
                    log(errs, Severity.ERROR, VE_PROPERTY_INJECT_NOT_OVERRIDEABLE,
                            getValueString(), sName);
                    }

                // we assume a field if @Inject is not specified, @RO is not specified,
                // @Override is not specified, and get() doesn't block going to its super
                fField = !fHasInject & !fHasRO & !fHasAbstract & !fHasOverride & !fGetBlocksSuper;

                // we assume Ref-not-Var if @RO is specified, or if there is a get() with no
                // super and no set() (or Var-implying annotations)
                fRO |= !fHasVarAnno && (fHasRO || (fGetBlocksSuper && methodSet == null));

                fRW |= fHasVarAnno | accessVar != null | methodSet != null;
                }

            effectGet = effectOf(fGetSupers, fGetBlocksSuper);
            effectSet = effectOf(fSetSupers, fSetBlocksSuper);
            }

        if (fRO && fRW)
            {
            log(errs, Severity.ERROR, VE_PROPERTY_READWRITE_READONLY,
                    getValueString(), sName);
            fRO = false;
            }

        TypeConstant typeProp = prop.getType().resolveGenerics(pool, this);

        return new PropertyInfo(new PropertyBody(prop, impl, null,
                typeProp, fRO, fRW, cCustomMethods > 0,
                effectGet, effectSet,  fField, fConstant, prop.getInitialValue(),
                methodInit == null ? null : methodInit.getIdentityConstant()), nRank);
        }

    private Effect effectOf(boolean fSupers, boolean fBlocks)
        {
        return fSupers ? Effect.MayUseSuper :
               fBlocks ? Effect.BlocksSuper :
                         Effect.None;
        }

    /**
     * Scan the array of annotations for a duplicate annotation.
     *
     * @param aAnno         the array of annotations
     * @param cScan         the number of annotations in the array to scan
     * @param constScanFor  the Constant specifying the annotation to scan for
     *
     * @return true iff a duplicate was found
     */
    private boolean scanForDups(Annotation[] aAnno, int cScan, Constant constScanFor)
        {
        for (int i = 0; i < cScan; ++i)
            {
            if (aAnno[i].getAnnotationClass().equals(constScanFor))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Verify that properties exist for each of the type parameters.
     *
     * @param mapTypeParams  the map containing all of the type parameters
     * @param mapProps       the public and protected properties of the class
     * @param errs           the error list to log any errors to
     */
    private void checkTypeParameterProperties(
            Map<Object, ParamInfo>    mapTypeParams,
            Map<Object, PropertyInfo> mapProps,
            ErrorListener             errs)
        {
        for (ParamInfo param : mapTypeParams.values())
            {
            if (param.getNestedIdentity() instanceof NestedIdentity)
                {
                continue;
                }

            String       sParam = param.getName();
            PropertyInfo info   = mapProps.get(sParam);
            if (info == null)
                {
                log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_MISSING,
                        this.getValueString(), sParam);
                }
            else if (!info.isFormalType() ||
                     !info.getType().getParamType(0).isA(param.getConstraintType()))
                {
                log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_INCOMPATIBLE,
                        this.getValueString(), sParam);
                }
            }
        }


    // ----- type info for mixins ---------------------------------------------------------------

    /**
     * For this type representing a base for conditional incorporates, merge the TypeInfo
     * built without those incorporates with the corresponding mixins.
     *
     * @param cInvalidations  the count of TypeInfo invalidations before staring building the info
     * @param idBase          the identity constant of the class that this type is based on
     * @param infoBase        the TypeInfo for this type without the conditional incorporates
     * @param atypeCondInc    the conditional incorporates to merge into the TypeInfo
     * @param errs            the error list to log into
     *
     * @return a resulting TypeInfo
     */
    private TypeInfo mergeConditionalIncorporates(
            int              cInvalidations,
            IdentityConstant idBase,
            TypeInfo         infoBase,
            TypeConstant[]   atypeCondInc,
            ErrorListener    errs)
        {
        ConstantPool pool = getConstantPool();
        TypeInfo     info = infoBase;
        for (int i = 0, c = atypeCondInc.length; i < c; i++)
            {
            TypeConstant typeMixin   = atypeCondInc[i];
            TypeConstant typePrivate = pool.ensureAccessTypeConstant(typeMixin, Access.PRIVATE);
            TypeInfo     infoMixin   = typePrivate.ensureTypeInfoInternal(errs);
            if (infoMixin == null)
                {
                // return the incomplete info of for we've got so far
                return new TypeInfo(this, cInvalidations, infoBase.getClassStructure(), 0, false,
                    info.getTypeParams(), null,
                    info.getExtends(), info.getRebases(), info.getInto(),
                    info.getContributionList(), info.getClassChain(), info.getDefaultChain(),
                    info.getProperties(), info.getMethods(),
                    info.getVirtProperties(), info.getVirtMethods(), info.getChildInfosByName(),
                    Progress.Incomplete);
                }

            info = mergeMixinTypeInfo(this, cInvalidations, idBase, infoBase.getClassStructure(),
                    info, infoMixin, Annotation.NO_ANNOTATIONS, /*fAddProcess*/ false, errs);
            }
        return info;
        }

    /**
     * For this type representing a base for an annotated type or a conditional incorporation,
     * merge the "source" TypeInfo with the TypeInfo of a mixin.
     *
     * @param typeTarget     the type to build the TypeInfo for
     * @param cInvalidations the count of TypeInfo invalidations before staring building the info
     * @param idBase         the identity constant of the class that this type is based on
     * @param structBase     the ClassStructure for the base type
     * @param infoSource     the TypeInfo containing all previous incorporates
     * @param infoMixin      the TypeInfo for the mixin to be merged with the source TypeInfo
     * @param aAnnoClass     an array of annotations for the type that mix into "Class"
     * @param fAnnoMixin     if true, the mixin represents an annotation; otherwise an incorporation
     * @param errs           the error listener to log into
     *
     * @return the resulting TypeInfo
     */
    protected TypeInfo mergeMixinTypeInfo(
            TypeConstant     typeTarget,
            int              cInvalidations,
            IdentityConstant idBase,
            ClassStructure   structBase,
            TypeInfo         infoSource,
            TypeInfo         infoMixin,
            Annotation[]     aAnnoClass,
            boolean fAnnoMixin,
            ErrorListener    errs)
        {
        ConstantPool pool = getConstantPool();

        // merge the private view of the annotation on top if the specified view of the underlying type
        Map<PropertyConstant, PropertyInfo> mapMixinProps   = infoMixin.getProperties();
        Map<MethodConstant  , MethodInfo>   mapMixinMethods = infoMixin.getMethods();
        Map<Object          , ParamInfo>    mapMixinParams  = infoMixin.getTypeParams();

        Map<PropertyConstant, PropertyInfo> mapProps       = new HashMap<>(infoSource.getProperties());
        Map<MethodConstant  , MethodInfo  > mapMethods     = new HashMap<>(infoSource.getMethods());
        Map<Object          , PropertyInfo> mapVirtProps   = new HashMap<>(infoSource.getVirtProperties());
        Map<Object          , MethodInfo  > mapVirtMethods = new HashMap<>(infoSource.getVirtMethods());
        ListMap<String      , ChildInfo   > mapChildren    = new ListMap<>(infoSource.getChildInfosByName());

        // TODO GG: replace with a call to layerOnProps
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapMixinProps.entrySet())
            {
            layerOnMixinProp(pool, infoSource, idBase, mapProps, mapVirtProps,
                    entry.getKey(), entry.getValue(), errs);
            }

        typeTarget.layerOnMethods(idBase, false, fAnnoMixin, null, mapMethods, mapVirtMethods,
                infoMixin.getType(), mapMixinMethods, errs);

        List<Contribution> listProcess = infoSource.getContributionList();
        if (fAnnoMixin)
            {
            listProcess = new ArrayList<>(listProcess);
            listProcess.add(new Contribution(Composition.Annotation,
                    pool.ensureAccessTypeConstant(infoMixin.getType(), Access.PROTECTED)));
            }

        // TODO handle mapChildren
        return new TypeInfo(typeTarget, cInvalidations, structBase, 0, false, mapMixinParams, aAnnoClass,
                infoSource.getExtends(), infoSource.getRebases(), infoSource.getInto(),
                listProcess, infoSource.getClassChain(), infoSource.getDefaultChain(),
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, mapChildren,
                Progress.Complete);
        }

    /**
     * Layer on the passed mixin's property contributions onto the base properties.
     *
     * @param pool          the constant pool to use
     * @param infoBase      the TypeInfo for the type that is being mixed into
     * @param idBaseClass   the identity of the class (etc) that is being mixed into
     * @param mapProps      properties already collected from the base
     * @param mapVirtProps  virtual properties already collected from the base
     * @param idMixinProp   the identity of the property at the mixin
     * @param infoProp      the property info from the mixin
     * @param errs          the error listener
     */
    protected void layerOnMixinProp(
            ConstantPool                        pool,
            TypeInfo                            infoBase,
            IdentityConstant                    idBaseClass,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            PropertyConstant                    idMixinProp,
            PropertyInfo                        infoProp,
            ErrorListener                       errs)
        {
        if (!infoProp.isVirtual())
            {
            mapProps.put(idMixinProp, infoProp);
            return;
            }

        Object           nidContrib = idMixinProp.getNestedIdentity(); // resolved
        PropertyConstant idResult   = (PropertyConstant) idBaseClass.appendNestedIdentity(pool, nidContrib);
        PropertyInfo     propBase   = infoBase.findPropertyByNid(nidContrib);
        if (propBase != null && infoProp.getIdentity().equals(propBase.getIdentity()))
            {
            // keep whatever the base has got
            return;
            }

        PropertyInfo propResult;
        if (propBase == null)
            {
            propResult = infoProp;
            }
        else
            {
            propResult = propBase.layerOn(infoProp, false, true, errs);
            }

        mapProps.put(idResult, propResult);
        mapVirtProps.put(nidContrib, propResult);
        }


    // ----- type comparison support ---------------------------------------------------------------

    /**
     * Determine if the specified TypeConstant (L-value) represents a type that is assignable to
     * values of the type represented by this TypeConstant (R-Value).
     *
     * @param typeLeft  the type to match (L-value)
     *
     * See Type.x # isA()
     */
    public boolean isA(TypeConstant typeLeft)
        {
        return calculateRelation(typeLeft) != Relation.INCOMPATIBLE;
        }

    /**
     * Calculate the type relationship between the specified TypeConstant (L-value) and the type
     * this TypeConstant (R-Value).
     *
     * @param typeLeft  the type to match (L-value)
     *
     * See Type.x # isA()
     */
    public Relation calculateRelation(TypeConstant typeLeft)
        {
        ConstantPool pool = getConstantPool();
        if (this.equals(typeLeft) || typeLeft.equals(pool.typeObject()))
            {
            return Relation.IS_A;
            }

        // since we're caching the relations on the constant itself, there is no reason to do it
        // unless it's registered
        TypeConstant typeRight        = (TypeConstant) pool.register(this);
        TypeConstant typeLeftResolved = typeLeft.resolveTypedefs();

        if (typeRight != this || typeLeftResolved != typeLeft)
            {
            return typeRight.calculateRelation(typeLeftResolved);
            }

        if (typeLeft instanceof RecursiveTypeConstant)
            {
            return calculateRelation(((RecursiveTypeConstant) typeLeft).getReferredToType());
            }

        Map<TypeConstant, Relation> mapRelations = ensureRelationMap();

        Relation relation = mapRelations.get(typeLeft);
        if (relation != null)
            {
            return relation;
            }

        Set<TypeConstant> setInProgress = m_tloInProgress.get();

        if (setInProgress != null && setInProgress.contains(typeLeft))
            {
            // we are in recursion; this can only happen for duck-typing, for example:
            //
            //    interface I { I! foo(); }
            //    class C { C! foo(); }
            //
            // the check on whether C is assignable to I depends on whether the return value of
            // C.foo() is assignable to the return value of I.foo(), which causes a recursion
            //
            // The soft assertion below assumes that a recursion for a given type always involves
            // the same ConstantPool. However, there is a possibility that we cycled in to the same
            // interface type on a different pool and called isInterfaceAssignableFrom again,
            // checking all the methods and circled back for a non-interface comparison.
            // Leaving the logging in for now, but no matter what, the answer should be negative.
            //
            // Quite naturally, a similar recursion may occur with recursive types.
            if (!typeLeft.isInterfaceType() &&
                    !typeLeft.containsRecursiveType() && !typeRight.containsRecursiveType())
                {
                System.err.println("rejecting isA() due to a recursion:" +
                    " left=" + typeLeft.getValueString() + "; right=" + typeRight.getValueString());
                }
            mapRelations.put(typeLeft, Relation.INCOMPATIBLE);
            return Relation.INCOMPATIBLE;
            }

        // check the access modifier first
        if (typeLeft.isAccessSpecified() || typeRight.isAccessSpecified())
            {
            Access accessLeft  = typeLeft.getAccess();
            Access accessRight = typeRight.getAccess();
            switch (accessRight)
                {
                case STRUCT:
                    if (typeLeft.equals(getConstantPool().typeStruct()))
                        {
                        relation = Relation.IS_A;
                        }
                    else if (accessLeft != Access.STRUCT)
                        {
                        // struct is not assignable to anything but a struct
                        relation = Relation.INCOMPATIBLE;
                        }
                    break;

                case PUBLIC:
                case PROTECTED:
                case PRIVATE:
                    if (accessLeft == Access.STRUCT ||
                            accessLeft.isLessAccessibleThan(accessRight))
                        {
                        // for now, disallow any access widening
                        relation = Relation.INCOMPATIBLE;
                        }
                    break;
                }

            if (relation == null)
                {
                relation = typeRight.removeAccess().
                        calculateRelation(typeLeft.removeAccess());
                }
            mapRelations.put(typeLeft, relation);
            return relation;
            }

        // then check immutability modifiers
        if (typeLeft.isImmutabilitySpecified())
            {
            relation = typeRight.isImmutable()
                ? typeRight.calculateRelation(typeLeft.removeImmutable())
                : Relation.INCOMPATIBLE;

            mapRelations.put(typeLeft, relation);
            return relation;
            }

        if (typeRight.isImmutabilitySpecified())
            {
            relation = typeRight.removeImmutable().calculateRelation(typeLeft);

            mapRelations.put(typeLeft, relation);
            return relation;
            }

        // then check various "reserved" scenarios
        relation = checkReservedCompatibility(typeLeft, typeRight);
        if (relation != null)
            {
            mapRelations.put(typeLeft, relation);
            return relation;
            }

        // now - a long journey
        if (setInProgress == null)
            {
            m_tloInProgress.set(setInProgress = new HashSet<>());
            }
        setInProgress.add(typeLeft);
        try
            {
            relation = typeRight.calculateRelationToLeft(typeLeft);

            if (relation == Relation.INCOMPATIBLE)
                {
                TypeConstant typeLeftN  = typeLeft.normalizeParameters();
                TypeConstant typeRightN = typeRight.normalizeParameters();
                if (typeLeftN.isDuckTypeAbleFrom(typeRightN))
                    {
                    // left is an interface; check the duck-typing
                    relation = typeLeftN.isInterfaceAssignableFrom(
                                    typeRightN, Access.PUBLIC, Collections.EMPTY_LIST).isEmpty()
                            ? Relation.IS_A : Relation.INCOMPATIBLE;
                    }
                }

            mapRelations.put(typeLeft, relation);
            }
        catch (RuntimeException | Error e)
            {
            mapRelations.remove(typeLeft);
            throw e;
            }
        finally
            {
            setInProgress.remove(typeLeft);
            if (setInProgress.isEmpty())
                {
                m_tloInProgress.remove();
                }
            }
        return relation;
        }

    /**
     * @return a relation between this (R-Value) and specified (L-Value) types
     */
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        return typeLeft.calculateRelationToRight(this);
        }

    /**
     * @return a relation between this (L-Value) and specified (R-Value) types
     */
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        return typeRight.calculateRelationToContribution(this);
        }

    /**
     * Find any contribution for this (R-Value) type that is assignable to the specified (L-Value)
     * type. Both types must be non-relational and single defining constant.
     *
     * @return a relation between this and the specified types
     */
    protected Relation calculateRelationToContribution(TypeConstant typeLeft)
        {
        TypeConstant typeRight = this;

        assert !typeLeft.isRelationalType() && typeLeft.isSingleDefiningConstant();
        assert !typeRight.isRelationalType() && typeRight.isSingleDefiningConstant();

        Constant constIdLeft  = typeLeft.getDefiningConstant();
        Constant constIdRight = typeRight.getDefiningConstant();

        Format format;
        switch (format = constIdRight.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case NativeClass:
                {
                ClassStructure clzRight = (ClassStructure)
                    ((IdentityConstant) constIdRight).getComponent();

                // continue recursively with the right side analysis
                return clzRight.calculateRelation(typeLeft, typeRight, true);
                }

            case Property:
                {
                // scenarios we can handle here are:
                // 1. r-value (this) = T (formal parameter type)
                //    l-value (that) = T (formal parameter type, equal by name only)

                // 2. r-value (this) = T (formal parameter type), constrained by U (other formal type)
                //    l-value (that) = U (formal parameter type)
                //
                // 3. r-value (this) = T (formal parameter type), constrained by U (real type)
                //    l-value (that) = V (real type), where U "is a" V
                PropertyConstant idRight = (PropertyConstant) constIdRight;
                if (constIdLeft.getFormat() == format &&
                    (((PropertyConstant) constIdLeft).getName().equals(idRight.getName())))
                    {
                    return Relation.IS_A;
                    }

                // the typeRight is a generic type and cannot have any modifiers
                assert typeRight instanceof TerminalTypeConstant;
                return idRight.getConstraintType().calculateRelation(typeLeft);
                }

            case TypeParameter:
                {
                // scenarios we can handle here are:
                // 1. r-value (this) = T (type parameter type)
                //    l-value (that) = T (type parameter type, equal by register only)

                // 2. r-value (this) = T (type parameter type), constrained by U (other type parameter type)
                //    l-value (that) = U (type parameter type)
                //
                // 3. r-value (this) = T (type parameter type), constrained by U (real type)
                //    l-value (that) = V (real type), where U "is a" V
                TypeParameterConstant idRight = (TypeParameterConstant) constIdRight;
                if (constIdLeft.getFormat() == format &&
                    (((TypeParameterConstant) constIdLeft).getRegister() == idRight.getRegister()))
                    {
                    // Note: it's quite opportunistic to assume that type parameters with the same
                    // register are compatible regardless of the enclosing method, but we need to
                    // assume that the caller has already (or will have) checked for the compatibility
                    // all other elements of the containing method and the only thing left is the
                    // register itself
                    return Relation.IS_A;
                    }

                // the typeRight is a type parameter and cannot have any modifiers
                assert typeRight instanceof TerminalTypeConstant;
                return idRight.getConstraintType().calculateRelation(typeLeft);
                }

            case FormalTypeChild:
                {
                // scenarios we can handle here are:
                // 1. r-value (this) = T.X (formal child type)
                //    l-value (that) = T.X (formal child type, equal by name only)

                // 2. r-value (this) = T.X (formal child type), constrained by U (other formal type)
                //    l-value (that) = U (type parameter type)
                //
                // 3. r-value (this) = T.X (formal child type), constrained by U (real type)
                //    l-value (that) = V (real type), where U "is a" V
                FormalTypeChildConstant idRight = (FormalTypeChildConstant) constIdRight;
                if (constIdLeft.getFormat() == format &&
                    (((FormalTypeChildConstant) constIdLeft).getName().equals(idRight.getName())))
                    {
                    // Note: it's quite opportunistic to assume that formal type parameters with the same
                    // name are compatible regardless of the enclosing parent type, but we need to
                    // assume that the caller has already (or will have) checked for the compatibility
                    // all other elements of the containing type and the only thing left is the
                    // name itself
                    return Relation.IS_A;
                    }

                // the typeRight is a type parameter and cannot have any modifiers
                assert typeRight instanceof TerminalTypeConstant;
                return idRight.getConstraintType().calculateRelation(typeLeft);
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                PseudoConstant idRight = (PseudoConstant) constIdRight;
                if (constIdLeft.getFormat() == format
                        && idRight.isCongruentWith((PseudoConstant) constIdLeft))
                    {
                    // without any additional context, it should be assignable in some direction
                    typeRight = typeRight.resolveAutoNarrowing(typeRight.getConstantPool(), false, null);
                    typeLeft  = typeLeft.resolveAutoNarrowing(typeLeft.getConstantPool(), false, null);

                    Relation relRightIsLeft = typeRight.calculateRelation(typeLeft);
                    return relRightIsLeft == Relation.INCOMPATIBLE
                        ? typeLeft.calculateRelation(typeRight)
                        : relRightIsLeft;
                    }

                ClassStructure clzRight = (ClassStructure)
                        idRight.getDeclarationLevelClass().getComponent();
                return clzRight.calculateRelation(typeLeft, typeRight, true);
                }

            case UnresolvedName:
                return Relation.INCOMPATIBLE;

            default:
                throw new IllegalStateException("unexpected constant: " + constIdRight);
            }
        }

    /**
     * Let's assume the following:
     *
     * <ul><li>
     *   there is a method M2 on a class C2 represented by type "typeCtx" that has a return value
     *   of *this* type;
     * </li><li>
     *   there is another method M1 from a class C1 that is present as a contribution to C2, that
     *   has the same name, same number of return values and parameters as M2 and a corresponding
     *   return value of the "typeBase" (C1).
     * </li></ul>
     *
     * Determine whether M2 could be invoked via a signature of M1, and M2 could then "super" to M1.
     *
     * @param typeBase  the type to determine the covariance with
     * @param typeCtx   the type within which context the covariance is to be determined
     */
    public boolean isCovariantReturn(TypeConstant typeBase, TypeConstant typeCtx)
        {
        if (this.isA(typeBase))
            {
            return true;
            }

        if (typeBase.isAutoNarrowing())
            {
            ConstantPool pool = ConstantPool.getCurrentPool();

            TypeConstant typeThisR = this    .resolveAutoNarrowing(pool, false, typeCtx);
            TypeConstant typeBaseR = typeBase.resolveAutoNarrowing(pool, false, typeCtx);
            return typeThisR.isA(typeBaseR);
            }

        return false;
        }

    /**
     * Let's assume the following:
     *
     * <ul><li>
     *   there is a method M2 on a class C2 represented by type "typeCtx" that has a parameter
     *   of *this* type;
     * </li><li>
     *   there is another method M1 from a class C1 that is present as a contribution to C2, that
     *   has the same name, same number of return values and parameters as M2 and a corresponding
     *   parameter of the "typeBase" (C1).
     * </li></ul>
     *
     * Determine whether M2 could be invoked via a signature of M1, and M2 could then "super" to M1.
     * <p/>
     * Note: despite the name this method also handling the auto-narrowing covariance.
     *
     * @param typeBase  the type to determine the contravariance with
     * @param typeCtx   the type within which context the covariance is to be determined
     */
    public boolean isContravariantParameter(TypeConstant typeBase, TypeConstant typeCtx)
        {
        // types may be equivalent, but not equal, for examples if some parameters are not
        // specified: ("Array" and "Array<Object>")
        if (typeBase.isA(this) && this.isA(typeBase))
            {
            return true;
            }

        if (typeBase.isAutoNarrowing() || this.isAutoNarrowing())
            {
            ConstantPool pool = ConstantPool.getCurrentPool();

            TypeConstant typeThisR = this    .resolveAutoNarrowing(pool, false, typeCtx);
            TypeConstant typeBaseR = typeBase.resolveAutoNarrowing(pool, false, typeCtx);

            return typeBaseR.isA(typeThisR) && typeThisR.isA(typeBaseR);
            }
        return false;
        }

    /**
     * Find any contribution for this (R-Value) type that is assignable to the specified (L-Value)
     * intersection type. This type must not be an intersection type.
     *
     * @return a relation between this and the specified types
     */
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        assert !(this instanceof IntersectionTypeConstant);

        TypeConstant typeRight = this;

        // this method is overridden by relational types, so by the time we come here.
        // typeRight must be a non-relational one
        assert !typeRight.isRelationalType() && typeRight.isSingleDefiningConstant();

        Constant constIdRight = typeRight.getDefiningConstant();
        switch (constIdRight.getFormat())
            {
            case Module:
            case Package:
            case Property:
            case TypeParameter:
                return Relation.INCOMPATIBLE;

            case Class:
            case NativeClass:
                {
                ClassStructure clzRight = (ClassStructure)
                    ((IdentityConstant) constIdRight).getComponent();

                // continue recursively with the right side analysis
                return clzRight.findIntersectionContribution(typeLeft, typeRight.getParamTypes());
                }


            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                PseudoConstant idRight = (PseudoConstant) constIdRight;
                ClassStructure clzRight = (ClassStructure)
                        idRight.getDeclarationLevelClass().getComponent();
                return clzRight.findIntersectionContribution(typeLeft, typeRight.getParamTypes());
                }

            default:
                throw new IllegalStateException("unexpected constant: " + constIdRight);
            }
        }

    /**
     * Check for any reserved types relations.
     *
     * @return the calculated relation or null if no judgement can be made
     */
    protected static Relation checkReservedCompatibility(TypeConstant typeLeft, TypeConstant typeRight)
        {
        if (!typeLeft.isSingleDefiningConstant()    || !typeRight.isSingleDefiningConstant() ||
            !typeLeft.isExplicitClassIdentity(true) || !typeRight.isExplicitClassIdentity(true) ||
             typeLeft.getAccess() != typeRight.getAccess())
            {
            return null;
            }

        IdentityConstant idLeft  = typeLeft.getSingleUnderlyingClass(true);
        IdentityConstant idRight = typeRight.getSingleUnderlyingClass(true);

        if (idLeft.getFormat() == Format.NativeClass)
            {
            idLeft = ((NativeRebaseConstant) idLeft).getClassConstant();
            }

        if (idRight.getFormat() == Format.NativeClass)
            {
            idRight = ((NativeRebaseConstant) idRight).getClassConstant();
            }

        ConstantPool pool = idLeft.getConstantPool();
        if (idLeft.equals(pool.clzTuple()))
            {
            if (!typeRight.isTuple())
                {
                // nothing is assignable to a Tuple except another Tuple
                return Relation.INCOMPATIBLE;
                }
            ClassStructure clzTuple = (ClassStructure) idRight.getComponent();
            return clzTuple.findTupleContribution(typeLeft, typeRight.getParamTypes());
            }

        if (idLeft.equals(pool.clzFunction()))
            {
            return pool.checkFunctionCompatibility(typeLeft, typeRight);
            }

        if (idLeft.equals(pool.clzMethod()))
            {
            return pool.checkMethodCompatibility(typeLeft, typeRight);
            }

        return null;
        }

    /**
     * Check if this TypeConstant (L-value), which is know to be an interface, represents a type
     * that is assignable to values of the type represented by the specified TypeConstant (R-Value).
     *
     * @param typeRight   the type to check the assignability from (R-value)
     * @param accessLeft  the access level to limit the checks to
     * @param listLeft    the list of actual generic parameters
     *
     * @return a set of method/property signatures from this type that don't have a match
     *         in the specified type
     */
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight,
                                                               Access accessLeft, List<TypeConstant> listLeft)
        {
        return getUnderlyingType().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        }

    /**
     * Check if this type contains a method or a property substitutable for the specified one.
     *
     * @param signature   the signature to check the substitutability for (resolved formal types)
     * @param access      the access level to limit the check to
     * @param fFunction   if true, the signature represents a function
     * @param listParams  the list of actual generic parameters
     *
     *  @return true iff the specified type could be assigned to this interface type
     */
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return getUnderlyingType().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

    /**
     * Determine if this type consumes a formal type with the specified name in a context
     * of the given access policy.
     *
     * @param sTypeName   the formal type name
     * @param access      the access level to limit the check to
     *
     * @return true iff this type is a consumer of the specified formal type
     */
    public boolean consumesFormalType(String sTypeName, Access access)
        {
        Map<String, Usage> mapUsage = ensureConsumesMap();

        // WARNING: thread-unsafe
        Usage usage = mapUsage.get(sTypeName);
        if (usage == null)
            {
            mapUsage.put(sTypeName, Usage.IN_PROGRESS);
            try
                {
                usage = checkConsumption(sTypeName, access, Collections.EMPTY_LIST);
                }
            catch (RuntimeException | Error e)
                {
                mapUsage.remove(sTypeName);
                throw e;
                }

            mapUsage.put(sTypeName, usage);
            }
        else if (usage == Usage.IN_PROGRESS)
            {
            // we are in recursion; the answer is "no"
            mapUsage.put(sTypeName, usage = Usage.NO);
            }

        return usage == Usage.YES;
        }

    /**
     * Calculate the consumption usage for the specified formal type in a context
     * of the given access policy and actual generic parameters.
     *
     * @param sTypeName   the formal type name
     * @param access      the access level to limit the check to
     * @param listParams  the list of actual generic parameters
     *
     * @return {@link Usage#YES} if this type consumes the formal type; {@link Usage#NO} otherwise
     */
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().checkConsumption(sTypeName, access, listParams);
        }

   /**
     * Determine if this type produces a formal type with the specified name in a context
     * of the given access policy.
     *
     * @param sTypeName   the formal type name
     * @param access      the access level to limit the check to
     *
     * @return {@link Usage#YES} if this type produces the formal type; {@link Usage#NO} otherwise
     */
    public boolean producesFormalType(String sTypeName, Access access)
        {
        Map<String, Usage> mapUsage = ensureProducesMap();

        // WARNING: thread-unsafe
        Usage usage = mapUsage.get(sTypeName);
        if (usage == null)
            {
            mapUsage.put(sTypeName, Usage.IN_PROGRESS);
            try
                {
                usage = checkProduction(sTypeName, access, Collections.EMPTY_LIST);
                }
            catch (RuntimeException | Error e)
                {
                mapUsage.remove(sTypeName);
                throw e;
                }

            mapUsage.put(sTypeName, usage);
            }
        else if (usage == Usage.IN_PROGRESS)
            {
            // we are in recursion; the answer is "no"
            mapUsage.put(sTypeName, usage = Usage.NO);
            }

        return usage == Usage.YES;
        }

    /**
     * Determine if this type produces a formal type with the specified name in a context
     * of the given access policy and actual generic parameters.
     *
     * @param sTypeName   the formal type name
     * @param access      the access level to limit the check to
     * @param listParams  the list of actual generic parameters
     *
     * @return true iff this type is a producer of the specified formal type
     */
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return getUnderlyingType().checkProduction(sTypeName, access, listParams);
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

    /**
     * Find a method on "this" type that converts from "this" type to "that" type.
     *
     * @param that  the type to convert to
     *
     * @return the MethodConstant that performs the desired conversion, or null if none exists (or
     *         multiple ambiguous answers exist)
     */
    public MethodConstant getConverterTo(TypeConstant that)
        {
        // formal types are not convertible to anything
        return containsFormalType(true)
            ? null
            : ensureTypeInfo().findConversion(that);
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
     *         "interface type" and not a "formal type"
     */
    public boolean isClassType()
        {
        return getCategory() == Category.CLASS;
        }

    /**
     * @return true iff the TypeConstant represents an "interface type", which is a type that is not
     *         a "class type" and not a "formal type"
     */
    public boolean isInterfaceType()
        {
        return getCategory() == Category.IFACE;
        }

    /**
     * @return true iff the TypeConstant represents a "formal type", which could be either
     *         generic type or type parameter
     */
    public boolean isFormalType()
        {
        return getCategory() == Category.FORMAL;
        }

    /**
     * Check if this type contains a formal type, which could be either a generic type or a
     * type parameter.
     *
     * @param fAllowParams  true if type parameters are acceptable
     *
     * @return true iff the TypeConstant contains a "formal type"
     */
    public boolean containsFormalType(boolean fAllowParams)
        {
        return getUnderlyingType().containsFormalType(fAllowParams);
        }

    /**
     * @return true iff the TypeConstant represents a generic type
     */
    public boolean isGenericType()
        {
        if (getCategory() == Category.FORMAL)
            {
            Constant constant = getDefiningConstant();
            switch (constant.getFormat())
                {
                case Property:
                    return true;

                case FormalTypeChild:
                    return ((FormalTypeChildConstant) constant).
                        getTopParent().getFormat() == Format.Property;
                }
            }
        return false;
        }

    /**
     * Check if this type contains a generic type.
     *
     * @param fAllowParams  true if type parameters are acceptable
     *
     * @return true iff the TypeConstant contains a generic type
     */
    public boolean containsGenericType(boolean fAllowParams)
        {
        return getUnderlyingType().containsGenericType(fAllowParams);
        }

    /**
     * @return true iff the TypeConstant represents a type parameter
     */
    public boolean isTypeParameter()
        {
        if (getCategory() == Category.FORMAL)
            {
            Constant constant = getDefiningConstant();
            switch (constant.getFormat())
                {
                case TypeParameter:
                    return true;

                case FormalTypeChild:
                    return ((FormalTypeChildConstant) constant).
                        getTopParent().getFormat() == Format.TypeParameter;
                }
            }
        return false;
        }

    /**
     * Check if this TypeConstant contains a formal type parameter type.
     *
     * @param fAllowParams  true if type parameters are acceptable
     *
     * @return true iff the TypeConstant contains a type parameter type
     */
    public boolean containsTypeParameter(boolean fAllowParams)
        {
        return getUnderlyingType().containsTypeParameter(fAllowParams);
        }

    /**
     * Check if this TypeConstant contains a recursive type.
     *
     * @return true iff the TypeConstant contains a recursive type
     */
    public boolean containsRecursiveType()
        {
        return getUnderlyingType().containsRecursiveType();
        }

    /**
     * @return true iff the TypeConstant represents a "formal type" that materializes into a type
     *         sequence
     */
    public boolean isFormalTypeSequence()
        {
        return false;
        }

    /**
     * @return the category of this TypeConstant
     */
    public Category getCategory()
        {
        return getUnderlyingType().getCategory();
        }

    /**
     * @param fAllowInterface if true, the returning identity constant could represent an interface
     *
     * @return true iff there is exactly one underlying class that makes this a class type
     */
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return getUnderlyingType().isSingleUnderlyingClass(fAllowInterface);
        }

    /**
     * Note: Only use this method if {@link #isSingleUnderlyingClass(boolean)} returns true.
     *
     * @param fAllowInterface if true, the returning identity constant could represent an interface
     *
     * @return the one underlying class that makes this a class type
     */
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);

        return getUnderlyingType().getSingleUnderlyingClass(fAllowInterface);
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
        return isModifyingType() && getUnderlyingType().isExplicitClassIdentity(fAllowParams);
        }

    /**
     * Determine the format of the explicit class, iff the type is an explicit class identity.
     *
     * @return a {@link Component.Format Component Format} value
     */
    public Component.Format getExplicitClassFormat()
        {
        return getUnderlyingType().getExplicitClassFormat();
        }

    /**
     * Determine the "into" type of the explicit class, iff the type is an explicit class identity
     * and the format of the class is "mixin".
     *
     * @return a TypeConstant
     */
    public TypeConstant getExplicitClassInto()
        {
        throw new IllegalStateException();
        }

    /**
     * @return true iff this type can be used in an "into" clause for a mixin for a class to signify
     *         that the mix-in applies to the meta-data of the class and is not actually mixed into
     *         the class functionality itself
     */
    public boolean isIntoClassType()
        {
        return getUnderlyingType().isIntoClassType();
        }

    /**
     * @return true iff this type can be used in an "into" clause for a mixin for a property, which
     *         means that the mix-in applies to the meta-data of the property or to the Ref/Var
     *         instance used for the property
     */
    public boolean isIntoPropertyType()
        {
        return getUnderlyingType().isIntoPropertyType();
        }

    /**
     * @return one of: Property, Ref, Var, or null
     */
    public TypeConstant getIntoPropertyType()
        {
        return getUnderlyingType().getIntoPropertyType();
        }

    /**
     * @return true iff this type can be used in an "into" clause for a mixin for a method, which
     *         means that the mix-in applies to the meta-data of the method
     */
    public boolean isIntoMethodType()
        {
        return getUnderlyingType().isIntoMethodType();
        }

    /**
     * @return true iff this type can be used in an "into" clause for a mixin for a local variable
     */
    public boolean isIntoVariableType()
        {
        return getUnderlyingType().isIntoVariableType();
        }

    /**
     * @return one of: Ref, Var, or null
     */
    public TypeConstant getIntoVariableType()
        {
        return getUnderlyingType().getIntoVariableType();
        }

    /**
     * @return true iff this type is considered to be convertible to an Int for purposes of the
     *         compiler and runtime
     */
    public boolean isIntConvertible()
        {
        if (!isExplicitClassIdentity(false))
            {
            return false;
            }

        switch (getEcstasyClassName())
            {
            case "numbers.Bit":
            case "numbers.Nibble":
            case "text.Char":
            case "numbers.Int8":
            case "numbers.Int16":
            case "numbers.Int32":
            case "numbers.Int64":
            case "numbers.Int128":
            case "numbers.VarInt":
            case "numbers.UInt8":
            case "numbers.UInt16":
            case "numbers.UInt32":
            case "numbers.UInt64":
            case "numbers.UInt128":
            case "numbers.VarUInt":
                return true;

            default:
                return getExplicitClassFormat() == Component.Format.ENUM;
            }
        }

    /**
     * @return the cardinality of the integer representation for this set of constants, or
     *         {@link Integer#MAX_VALUE} if the range is too large to express with an int
     */
    public int getIntCardinality()
        {
        assert isIntConvertible();

        switch (getEcstasyClassName())
            {
            case "numbers.Bit":
                return 2;

            case "numbers.Nibble":
                return 0x10;

            case "text.Char":
                // unicode goes from 0 to 10FFFF
                return 0x10FFFF + 1;

            case "numbers.Int8":
            case "numbers.UInt8":
                return 0x100;

            case "numbers.Int16":
            case "numbers.UInt16":
                return 0x10000;

            case "numbers.Int32":
            case "numbers.UInt32":
            case "numbers.Int64":
            case "numbers.UInt64":
            case "numbers.Int128":
            case "numbers.UInt128":
            case "numbers.VarInt":
            case "numbers.VarUInt":
                return Integer.MAX_VALUE;

            default:
                // count the enum values (each ordinal value)
                // (note: enum-to-int conversion is no longer used for compiling to JumpInt)
                ClassStructure clzEnum = (ClassStructure) getSingleUnderlyingClass(false).getComponent();
                int c = 0;
                for (Component child : clzEnum.children())
                    {
                    if (child.getFormat() == Component.Format.ENUMVALUE)
                        {
                        ++c;
                        }
                    }
                return c;
            }
        }

    /**
     * @return the constant representing the minimum value for this cardinal type; null if the
     *         range is too large to express with an int
     */
    public Constant getCardinalBase()
        {
        assert isIntConvertible();

        ConstantPool pool = getConstantPool();
        switch (getEcstasyClassName())
            {
            case "numbers.Bit":
                return pool.ensureBitConstant(0);

            case "numbers.Nibble":
                return pool.ensureIntConstant(PackedInteger.valueOf(0), Format.Nibble);

            case "text.Char":
                return pool.ensureCharConstant(0);

            case "numbers.Int8":
                return pool.ensureIntConstant(PackedInteger.valueOf(-255), Format.Int8);

            case "numbers.UInt8":
                return pool.ensureIntConstant(PackedInteger.valueOf(0), Format.UInt8);

            case "numbers.Int16":
                return pool.ensureIntConstant(PackedInteger.valueOf(Short.MIN_VALUE), Format.Int16);

            case "numbers.UInt16":
                return pool.ensureIntConstant(PackedInteger.valueOf(0), Format.UInt16);

            case "numbers.Int32":
            case "numbers.UInt32":
            case "numbers.Int64":
            case "numbers.UInt64":
            case "numbers.Int128":
            case "numbers.UInt128":
            case "numbers.VarInt":
            case "numbers.VarUInt":
                return null;

            default:
                ClassStructure clzEnum = (ClassStructure) getSingleUnderlyingClass(false).getComponent();
                for (Component child : clzEnum.children())
                    {
                    if (child.getFormat() == Component.Format.ENUMVALUE)
                        {
                        return pool.ensureSingletonConstConstant(child.getIdentityConstant());
                        }
                    }
            }
        return null;
        }

    /**
     * @return the default value for this type, or null if there is none
     */
    public Constant getDefaultValue()
        {
        if (isExplicitClassIdentity(false) && isSingleUnderlyingClass(false))
            {
            IdentityConstant id   = getSingleUnderlyingClass(false);
            ClassStructure   clz  = (ClassStructure) id.getComponent();
            Component        prop = clz.getChild("default");
            if (prop instanceof PropertyStructure)
                {
                PropertyStructure propDefault = (PropertyStructure) prop;
                return propDefault.getInitialValue();
                }
            }
        else if (isNullable())
            {
            return getConstantPool().valNull();
            }

        return null;
        }


    // ----- run-time support ----------------------------------------------------------------------

    /**
     * @return an {@link OpSupport} instance for this type in the context of the specified registry
     */
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        return getUnderlyingType().getOpSupport(registry);
        }

    /**
     * @return a handle for the Type object represented by this TypeConstant
     */
    public TypeHandle getTypeHandle()
        {
        TypeHandle hType = m_handle;
        if (hType == null)
            {
            hType = m_handle = xRTType.makeHandle(this);
            }
        return hType;
        }

    /**
     * Compare for equality (==) two object handles that both belong to this type.
     *
     * @param frame    the frame
     * @param hValue1  the first handle
     * @param hValue2  the second handle
     * @param iReturn  the return register
     *
     * @return one of Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION values
     */
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }

        ClassComposition clz = frame.ensureClass(this);
        return clz.getTemplate().callEquals(frame, clz, hValue1, hValue2, iReturn);
        }

    /**
     * Compare for order (<=>) two object handles that both belong to this type.
     *
     * @param frame    the frame
     * @param hValue1  the first handle
     * @param hValue2  the second handle
     * @param iReturn  the return register
     *
     * @return one of Op.R_NEXT, Op.R_CALL or Op.R_EXCEPTION values
     */
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xOrdered.EQUAL);
            }

        ClassComposition clz = frame.ensureClass(this);
        return clz.getTemplate().callCompare(frame, clz, hValue1, hValue2, iReturn);
        }

    /**
     * Find a callable function with the specified signature.
     *
     * @param sig  the function signature
     *
     * @return the method structure for the function or null if none was found
     */
    public MethodStructure findCallable(SignatureConstant sig)
        {
        MethodInfo infoFn = findFunctionInfo(sig);
        return infoFn == null || infoFn.isAbstract()
                ? null
                : infoFn.getTopmostMethodStructure(ensureTypeInfo());
        }

    /**
     * Find a MethodInfo for a function with the specified signature.
     *
     * @param sig  the function signature
     *
     * @return the MethodInfo for the function or null if none was found
     */
    public MethodInfo findFunctionInfo(SignatureConstant sig)
        {
        return ensureTypeInfo().getMethodBySignature(sig);
        }

    /**
     * @return true iff mutable objects of this type could be proxied across the service boundary
     */
    public boolean isProxyable()
        {
        return isConstant()
            || isInterfaceType()
            || (isSingleUnderlyingClass(false)
             && getSingleUnderlyingClass(false).getComponent().getFormat() == Component.Format.SERVICE);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public abstract Constant.Format getFormat();

    @Override
    public TypeConstant getType()
        {
        ConstantPool pool = getConstantPool();

        TypeConstant typeParent = isVirtualChild()
                ? getParentType()
                : pool.typeObject();

        return pool.ensureParameterizedTypeConstant(pool.typeType(), this, typeParent);
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return isAutoNarrowing(true);
        }

    @Override
    public boolean isValueCacheable()
        {
        return !containsFormalType(true);
        }

    @Override
    protected abstract int compareDetails(Constant that);


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void setContaining(XvmStructure pool)
        {
        super.setContaining(pool);

        // clear any cached constants
        m_cInvalidations = 0;
        m_typeinfo       = null;
        m_mapRelations   = null;
        m_typeNormalized = null;
        }

    @Override
    protected abstract void registerConstants(ConstantPool pool);

    @Override
    protected abstract void assemble(DataOutput out)
            throws IOException;

    @Override
    public boolean validate(ErrorListener errs)
        {
        boolean fHalt = false;

        if (!m_fValidated)
            {
            fHalt |= super.validate(errs);
            fHalt |= isModifyingType() && getUnderlyingType().validate(errs);
            m_fValidated = !fHalt;
            }

        return fHalt;
        }

    /**
     * @return true iff this type constant has been validated
     */
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

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof TypeConstant))
            {
            return false;
            }

        // TODO: there is a concern that this allows a Typedef to equal its referent
        TypeConstant typeThis = this.resolveTypedefs();
        TypeConstant typeThat = ((TypeConstant) obj).resolveTypedefs();

        return typeThis.getFormat() == typeThat.getFormat()
            && typeThis.compareDetails(typeThat) == 0;
        }


    // ----- helpers -------------------------------------------------------------------------------

    private Map<TypeConstant, Relation> ensureRelationMap()
        {
        Map<TypeConstant, Relation> mapRelations = m_mapRelations;
        if (mapRelations == null)
            {
            s_tloInProgress.compareAndSet(this, null, new ThreadLocal<>());
            mapRelations = m_mapRelations = new ConcurrentHashMap<>();
            }
        return mapRelations;
        }

    private Map<String, Usage> ensureConsumesMap()
        {
        Map<String, Usage> mapConsumes = m_mapConsumes;
        if (mapConsumes == null)
            {
            mapConsumes = m_mapConsumes = new HashMap<>();
            }
        return mapConsumes;
        }

    private Map<String, Usage> ensureProducesMap()
        {
        Map<String, Usage> mapProduces = m_mapProduces;
        if (mapProduces == null)
            {
            mapProduces = m_mapProduces = new HashMap<>();
            }
        return mapProduces;
        }

    /**
     * Note: both this type and the typeRight are already normalized.
     *
     * @return false iff the duck-typing is known to be impossible
     */
    protected boolean isDuckTypeAbleFrom(TypeConstant typeRight)
        {
        // interfaces are duck-type able except Tuple, Function and Orderable
        // (the later due to the fact that it's has no abstract methods and
        //  is well-known by the runtime only by its "compare" function)
        if (!isInterfaceType() || isVirtualChild() || isTuple())
            {
            return false;
            }

        TypeConstant typeLeft = this;
        if (typeLeft.isSingleDefiningConstant() && typeRight.isSingleDefiningConstant()
            && typeRight.getDefiningConstant().equals(typeLeft.getDefiningConstant()))
            {
            // we have just tested the relationship between C<T1> and C<T2> and got
            // a negative answer; there is no logical way for the duck-typing to
            // produce a different result
            return false;
            }

        ConstantPool pool = getConstantPool();

        if (typeRight.equals(pool.typeObject()) || typeRight.isFormalTypeSequence())
            {
            // Object requires special treatment here for a number of reasons;
            // let's disallow it to be assigned to anything for now
            // TODO: allow an "empty" interface to be duck-typed to Object
            // the "turtle" type also is not duck-typeable to anything
            return false;
            }

        if (typeLeft.isExplicitClassIdentity(true))
            {
            IdentityConstant idLeft = typeLeft.getSingleUnderlyingClass(true);
            if (idLeft.equals(pool.clzFunction()) ||
                idLeft.equals(pool.clzOrderable()))
                {
                return false;
                }

            ClassStructure clzLeft     = (ClassStructure) idLeft.getComponent();
            int            cParamsLeft = clzLeft.getTypeParamCount();

            if (typeRight.isExplicitClassIdentity(true))
                {
                IdentityConstant idRight  = typeRight.getSingleUnderlyingClass(true);
                ClassStructure   clzRight = (ClassStructure) idRight.getComponent();

                if (cParamsLeft > clzRight.getTypeParamCount())
                    {
                    return false;
                    }

                if (cParamsLeft > 0)
                    {
                    ListMap<StringConstant, TypeConstant> mapParamsLeft  = clzLeft.getTypeParams();
                    ListMap<StringConstant, TypeConstant> mapParamsRight = clzRight.getTypeParams();

                    for (StringConstant constName : mapParamsLeft.keySet())
                        {
                        if (!mapParamsRight.containsKey(constName))
                            {
                            return false;
                            }

                        String       sName      = constName.getValue();
                        TypeConstant typePLeft  = typeLeft.getGenericParamType(sName, Collections.EMPTY_LIST);
                        TypeConstant typePRight = typeRight.getGenericParamType(sName, Collections.EMPTY_LIST);

                        if (!typePRight.equals(typePLeft))
                            {
                            return false;
                            }
                        }
                    }
                }
            else
                {
                // the right type is relational; no duck typing for parameterized left
                if (cParamsLeft > 0)
                    {
                    return false;
                    }
                }
            }
        return true;
        }

    /**
     * Helper method for registering an array of TypeConstants.
     */
    protected static TypeConstant[] registerTypeConstants(ConstantPool pool, TypeConstant[] atype)
        {
        atype = (TypeConstant[]) registerConstants(pool, atype);

        for (int i = 0, c = atype.length; i < c; i++)
            {
            atype[i].registerConstants(pool);
            }
        return atype;
        }


    // ----- inner class: Origin -------------------------------------------------------------------

    /**
     * Used during "potential call chain" creation.
     */
    public class Origin
        {
        public Origin(boolean fAnchored)
            {
            m_fAnchored = fAnchored;
            }

        public TypeConstant getType()
            {
            return TypeConstant.this;
            }

        public boolean isAnchored()
            {
            return m_fAnchored;
            }

        @Override
        public String toString()
            {
            return "Origin{type="
                    + getType()
                    + ", anchored="
                    + isAnchored()
                    + '}';
            }

        private boolean m_fAnchored;
        }


    // ----- enums ---------------------------------------------------------------------------------

    /**
     * Relationship options.
     */
    public enum Relation
        {
        IS_A, IS_A_WEAK, INCOMPATIBLE;

        public Relation bestOf(Relation that)
            {
            return this.ordinal() < that.ordinal() ? this : that;
            }

        public Relation worseOf(Relation that)
            {
            return this.ordinal() < that.ordinal() ? that : this;
            }
        }

    /**
     * Consumption/production options.
     */
    public enum Usage
        {
        IN_PROGRESS, YES, NO;

        public static Usage valueOf(boolean f)
            {
            return f ? YES : NO;
            }
        }

    /**
     * TypeConstant categories.
     */
    public enum Category
        {
        CLASS, IFACE, FORMAL, OTHER
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An immutable, empty, zero-length array of types.
     */
    public static final TypeConstant[] NO_TYPES = new TypeConstant[0];

    /**
     * Keeps track of whether the TypeConstant has been validated.
     */
    private transient boolean m_fValidated;

    /**
     * The resolved information about the type, its properties, and its methods.
     */
    private transient volatile TypeInfo m_typeinfo;
    private static final AtomicReferenceFieldUpdater<TypeConstant, TypeInfo> s_typeinfo =
            AtomicReferenceFieldUpdater.newUpdater(TypeConstant.class, TypeInfo.class, "m_typeinfo");
    private transient volatile int m_cRecursiveDepth;

    /**
     * The last time that we checked the invalidations from the ConstantPool, we cached the number
     * of invalidations that had been done up to that point in time. This is that number. This gives
     * us a very quick way of verifying that no new invalidations have occurred.
     * This value could be higher than the value in the TypeInfo, which represents the number of
     * invalidations that had been done before we started to create the TypeInfo.
     */
    private transient volatile int m_cInvalidations;
    private static final AtomicIntegerFieldUpdater<TypeConstant> s_cInvalidations =
            AtomicIntegerFieldUpdater.newUpdater(TypeConstant.class, "m_cInvalidations");

    /**
     * A cache of "isA" responses.
     */
    private transient volatile Map<TypeConstant, Relation> m_mapRelations;

    /**
     * The set of "isA() in progress" types.
     */
    private transient volatile ThreadLocal<Set> m_tloInProgress;
    private static final AtomicReferenceFieldUpdater<TypeConstant, ThreadLocal> s_tloInProgress =
            AtomicReferenceFieldUpdater.newUpdater(TypeConstant.class, ThreadLocal.class, "m_tloInProgress");

    /**
     * A cache of "consumes" responses.
     */
    private transient Map<String, Usage> m_mapConsumes;

    /**
     * A cache of "produces" responses.
     */
    private transient Map<String, Usage> m_mapProduces;

    /**
     * Cached TypeHandle.
     */
    private transient xRTType.TypeHandle m_handle;

    /**
     * Cached normalized representation.
     */
    private transient TypeConstant m_typeNormalized;
    }