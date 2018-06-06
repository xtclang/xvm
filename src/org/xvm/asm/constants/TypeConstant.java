package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import java.util.function.Function;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.ParamInfo.TypeResolver;
import org.xvm.asm.constants.PropertyBody.Effect;
import org.xvm.asm.constants.TypeInfo.Progress;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xType;
import org.xvm.runtime.template.xType.TypeHandle;

import org.xvm.util.ListMap;
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


    // ----- GenericTypeResolver -------------------------------------------------------------------

    @Override
    public TypeConstant resolveGenericType(PropertyConstant constProperty)
        {
        return getGenericParamType(constProperty.getName());
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
     * @return true iff this TypeConstant refers to an immutable type
     */
    public boolean isImmutable()
        {
        return getUnderlyingType().isImmutable();
        }

    /**
     * @return a type constant that represents an immutable type of this type constant
     */
    public TypeConstant ensureImmutable()
        {
        return isImmutable()
                ? this
                : getConstantPool().ensureImmutableTypeConstant(this);
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
        return isModifyingType()
                ? getUnderlyingType().getParamTypesArray()
                : ConstantPool.NO_TYPES;
        }

    /**
     * @return true iff this type has a formal type parameter with the specified name
     */
     public boolean isGenericType(String sName)
        {
        TypeInfo info = getTypeInfo();
        if (info != null)
            {
            return info.getTypeParams().containsKey(sName);
            }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        if (isSingleDefiningConstant())
            {
            ClassStructure clz = (ClassStructure) getSingleUnderlyingClass(true).getComponent();
            TypeConstant type = clz.getGenericParamType(sName, getParamTypes());
            return type != null;
            }

        return false;
        }

    /**
     * Find the type of the specified formal parameter for this actual type.
     *
     * @param sName  the formal parameter name
     *
     * @return the corresponding actual type or null if there is no matching formal type
     */
    public TypeConstant getGenericParamType(String sName)
        {
        if (isSingleDefiningConstant())
            {
            TypeInfo info = getTypeInfo();
            if (info != null && info.getProgress() == Progress.Complete)
                {
                ParamInfo param = info.getTypeParams().get(sName);
                return param == null
                    ? null
                    : param.getActualType();
                }

            // because isA() uses this method, there is a chicken-and-egg problem, so instead of
            // materializing the TypeInfo at this point, just answer the question without it
            ClassStructure clz = (ClassStructure) getSingleUnderlyingClass(true).getComponent();

            return clz.getGenericParamType(sName, getParamTypes());
            }

        throw new UnsupportedOperationException();
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
     * Determine if the specified name is referring to a name introduced by any of the contributions
     * for this type.
     *
     * @param sName      the name to resolve
     * @param collector  the collector to which the potential name matches will be reported
     *
     * @return the resolution result
     */
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        return getUnderlyingType().resolveContributedName(sName, collector);
        }

    @Override
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
     *         formal parameters for the the underlying terminal type
     */
    public TypeConstant normalizeParameters()
        {
        return adoptParameters(null);
        }

    /**
     * Create a semantically equivalent type that is parameterized by the specified type parameters,
     * and normalized (the total number of parameters equal to the number of formal parameters
     * for the underlying terminal type)
     *
     * @param atypeParams the parameters to adopt or null if the parameters of this type are
     *                    simply to be normalized
     *
     * @return potentially new normalized type that is parameterized by the specified types
     */
    public TypeConstant adoptParameters(TypeConstant[] atypeParams)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.adoptParameters(atypeParams);

        return constResolved == constOriginal
                ? this
                : cloneSingle(constResolved);
        }

    /**
     * If this type is auto-narrowing (or has any references to auto-narrowing types), replace the
     * any auto-narrowing portion with an explicit class identity.
     *
     * @return the TypeConstant with explicit identities swapped in for any auto-narrowing
     *         identities
     */
    public TypeConstant resolveAutoNarrowing()
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = constOriginal.resolveAutoNarrowing();

        return constResolved == constOriginal
            ? this
            : cloneSingle(constResolved);
        }

    /**
     * Assuming the "ThisClass" of A, the inference rules are:
     * <ul>
     *   <li>A&lt;T&gt; => TC(A)&lt;T&gt;
     *   <li>B&lt;A&gt; => B&lt;TC(A)&gt;
     *
     *   //TODO: children and parents
     * </ul>
     * where TC is ThisClassConstant
     *
     * @param constThisClass  the class "context" in which the inference is calculated
     *
     * @return this same type, but with the underlying class of "this" replaced with the
     *         {@link ThisClassConstant}
     * @param constThisClass
     */
    public TypeConstant inferAutoNarrowing(IdentityConstant constThisClass)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constInferred = constOriginal.inferAutoNarrowing(constThisClass);

        return constInferred == constOriginal
                ? this
                : cloneSingle(constInferred);
        }

    /**
     * Create a new type by replacing the underlying type for this one according to the specified
     * function.
     *
     * Note, that a TerminalTypeConstant doesn't have an underlying type and is not "transformable".
     *
     * @param transformer  the transformation function
     *
     * @return potentially transformed type
     */
    public TypeConstant replaceUnderlying(Function<TypeConstant, TypeConstant> transformer)
        {
        TypeConstant constOriginal = getUnderlyingType();
        TypeConstant constResolved = transformer.apply(constOriginal);

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
     * @return true iff the type is a Tuple type
     */
    public boolean isTuple()
        {
        return isSingleDefiningConstant() && getUnderlyingType().isTuple();
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
     * @return true iff the type is a Sequence type
     */
    public boolean isSequence()
        {
        TypeConstant constThis = resolveTypedefs();
        assert !constThis.containsUnresolved();

        constThis = constThis.resolveAutoNarrowing();
        return     constThis.isEcstasy("String")
                || constThis.isEcstasy("Array")
                || constThis.isEcstasy("List")
                || constThis.isEcstasy("Sequence")
                || constThis.isA(getConstantPool().typeSequence());
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
        assert isTuple();
        TypeConstant[] atypeParam = getParamTypesArray();
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
    public TypeInfo ensureTypeInfo()
        {
        return ensureTypeInfo(getErrorListener());
        }

    /**
     * Obtain all of the information about this type, resolved from its recursive composition.
     *
     * @param errs  the error list to log errors to
     *
     * @return the flattened TypeInfo that represents the resolved type of this TypeConstant
     */
    public TypeInfo ensureTypeInfo(ErrorListener errs)
        {
        TypeInfo info = getTypeInfo();
        if (isComplete(info))
            {
            return info;
            }

        // validate this TypeConstant (necessary before we build the TypeInfo)
        if (info == null)
            {
            validate(errs);
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
        setTypeInfo(getConstantPool().TYPEINFO_PLACEHOLDER);

        // since this can only be used "from the outside", there should be no deferred TypeInfo
        // objects at this point
        if (hasDeferredTypeInfo())
            {
            throw new IllegalStateException("Infinite loop while producing a TypeInfo for "
                    + this + "; deferred types=" + takeDeferredTypeInfo());
            }

        try
            {
            // build the TypeInfo for this type
            info = buildTypeInfo(errs);
            }
        catch (Exception | Error e)
            {
            // clean up the deferred types
            takeDeferredTypeInfo();
            throw e;
            }

        // info here can't be null, because we should be at the "zero level"; in other words, anyone
        // who calls ensureTypeInfo() should get a usable result, because nothing is already on the
        // stack blocking it from finishing correctly (which is why we can't use ensureTypeInfo()
        // ourselves within this process of creating type infos)
        if (info == null)
            {
            throw new IllegalStateException("Failure to produce a TypeInfo for "
                    + this + "; deferred types=" + takeDeferredTypeInfo());
            }

        setTypeInfo(info);

        if (hasDeferredTypeInfo())
            {
            // any downstream TypeInfo that could not be completed during the building of
            // this TypeInfo is considered to be "deferred", but now that we've built
            // something (even if it isn't complete), we should be able to complete the
            // deferred TypeInfo building
            for (TypeConstant typeDeferred : takeDeferredTypeInfo())
                {
                if (typeDeferred != this)
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
                    TypeInfo infoDeferred = typeDeferred.ensureTypeInfo(errs);
                    --m_cRecursiveDepth;
                    assert infoDeferred.getProgress() == Progress.Complete;
                    }
                }
            }

        // now that all those other deferred types are done building, rebuild this if necessary
        if (!isComplete(info))
            {
            info = buildTypeInfo(errs);
            assert isComplete(info);
            setTypeInfo(info);
            }

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
        if (info == null)
            {
            setTypeInfo(getConstantPool().TYPEINFO_PLACEHOLDER);
            info = buildTypeInfo(errs);
            if (info != null)
                {
                setTypeInfo(info);
                }
            if (!isComplete(info))
                {
                addDeferredTypeInfo(this);
                }
            return info;
            }

        if (info.isPlaceHolder())
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
        while (rankTypeInfo(info) > rankTypeInfo(infoOld = s_typeinfo.get(this)))
            {
            if (s_typeinfo.compareAndSet(this, infoOld, info))
                {
                return;
                }
            }
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
        if (info == null)
            {
            return 0;
            }

        if (info.isPlaceHolder())
            {
            return 1;
            }

        return info.isIncomplete()
                ? 2
                : 3;
        }

    /**
     * @param info  the TypeInfo to evaluate
     *
     * @return true iff the passed TypeInfo is non-null, not the place-holder, and not incomplete
     */
    private static boolean isComplete(TypeInfo info)
        {
        return rankTypeInfo(info) == 3;
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
        // resolve the type to make sure that typedefs etc. are removed from the equation
        TypeConstant typeResolved = resolveTypedefs().resolveAutoNarrowing();
        if (typeResolved != this)
            {
            return typeResolved.buildTypeInfo(errs);
            }

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

        // it's possible that not all of the parameters have been specified, and while this type
        // is not equal to the normalized type, the TypeInfo for the two will be identical
        if (struct.getTypeParams().size() > getParamsCount() && !isTuple())
            {
            return normalizeParameters().ensureTypeInfoInternal(errs);
            }

        // we're going to build a map from name to param info, including whatever parameters are
        // specified by this class/interface, but also each of the contributing classes/interfaces
        Map<Object, ParamInfo> mapTypeParams = new HashMap<>();
        TypeResolver resolver = createInitialTypeResolver(constId, struct, mapTypeParams, errs);

        // walk through each of the contributions, starting from the implied contributions that are
        // represented by annotations in this type constant itself, followed by the annotations in
        // the class structure, followed by the class structure (as its own pseudo-contribution),
        // followed by the remaining contributions
        List<Contribution> listProcess  = new ArrayList<>();
        List<Annotation>   listAnnos    = new ArrayList<>();
        TypeConstant[]     atypeSpecial = createContributionList(
                constId, struct, listProcess, listAnnos, resolver, errs);
        TypeConstant typeInto    = atypeSpecial[0];
        TypeConstant typeExtends = atypeSpecial[1];
        TypeConstant typeRebase  = atypeSpecial[2];

        // 1) build the "potential call chains" (basically, the order in which we would search for
        //    methods to call in a virtual manner)
        // 2) collect all of the type parameter data from the various contributions
        ListMap<IdentityConstant, Origin> listmapClassChain   = new ListMap<>();
        ListMap<IdentityConstant, Origin> listmapDefaultChain = new ListMap<>();

        boolean fComplete = createCallChains(constId, struct, mapTypeParams,
                listProcess, listmapClassChain, listmapDefaultChain, errs);

        // determine if the type is explicitly abstract
        Annotation[] aannoClass = listAnnos.toArray(new Annotation[listAnnos.size()]);
        boolean      fAbstract  = isInterface(constId, struct)
                || TypeInfo.containsAnnotation(aannoClass, "Abstract");

        // next, we need to process the list of contributions in order, asking each for its
        // properties and methods, and collecting all of them
        Map<PropertyConstant , PropertyInfo> mapProps       = new HashMap<>();
        Map<MethodConstant   , MethodInfo  > mapMethods     = new HashMap<>();
        Map<Object           , PropertyInfo> mapVirtProps   = new HashMap<>(); // keyed by nested id
        Map<Object           , MethodInfo  > mapVirtMethods = new HashMap<>(); // keyed by nested id

        fComplete &= collectMemberInfo(constId, struct, resolver,
                listProcess, listmapClassChain, listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods, errs);

        // go through the members to determine if this is abstract
        if (!fAbstract)
            {
            fAbstract = mapProps.values().stream().anyMatch(PropertyInfo::isExplicitlyAbstract)
                    || mapMethods.values().stream().anyMatch(MethodInfo::isAbstract);
            }

        // validate the type parameters against the properties
        checkTypeParameterProperties(mapTypeParams, mapVirtProps, errs);

        return new TypeInfo(this, struct, 0, fAbstract, mapTypeParams, aannoClass,
                typeExtends, typeRebase, typeInto,
                listProcess, listmapClassChain, listmapDefaultChain,
                mapProps, mapMethods, mapVirtProps, mapVirtMethods,
                fComplete ? Progress.Complete : Progress.Incomplete);
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
        assert getAccess() == Access.STRUCT;
        assert this instanceof AccessTypeConstant;

        // start by copying all the fields and functions from the private type of this
        Map<PropertyConstant, PropertyInfo> mapProps     = new HashMap<>();
        Map<MethodConstant  , MethodInfo  > mapMethods   = new HashMap<>();
        Map<Object          , PropertyInfo> mapVirtProps = new HashMap<>();

        ConstantPool pool    = getConstantPool();
        TypeInfo     infoPri = pool.ensureAccessTypeConstant(getUnderlyingType(), Access.PRIVATE).
                               ensureTypeInfoInternal(errs);
        if (infoPri == null)
            {
            return null;
            }

        ParamInfo.TypeResolver resolver = infoPri.ensureTypeResolver(errs);
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoPri.getProperties().entrySet())
            {
            // the properties that show up in structure types are those that have a field; however,
            // we also need to retain both type params and constants, even though they technically
            // are not "in" the structure itself
            PropertyInfo prop = entry.getValue();
            if (prop.isTypeParam() || prop.isConstant() || prop.hasField())
                {
                PropertyConstant id = entry.getKey();
                // REVIEW do we need to transform "prop" into some sort of "struct" form?
                // REVIEW if we do, then we need to explicitly retain the PropertyInfo.getFieldIdentity()
                if (prop.isVirtual())
                    {
                    mapVirtProps.put(id.resolveNestedIdentity(resolver), prop);
                    }
                mapProps.put(id, prop);
                }
            }

        for (Map.Entry<MethodConstant, MethodInfo> entry : infoPri.getMethods().entrySet())
            {
            MethodInfo method = entry.getValue();
            if (method.isFunction())
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
                    if (typeContrib instanceof AccessTypeConstant)
                        {
                        // unwrap the access type constant
                        typeContrib = typeContrib.getUnderlyingType();
                        }
                    if (typeContrib.getAccess() != Access.STRUCT)
                        {
                        // wrap the type as an access type constant
                        assert !typeContrib.isAccessSpecified();
                        typeContrib = pool.ensureAccessTypeConstant(typeContrib, Access.STRUCT);
                        }

                    TypeInfo infoContrib = typeContrib.ensureTypeInfoInternal(errs);
                    if (infoContrib == null)
                        {
                        fIncomplete = true;
                        }
                    else
                        {
                        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoContrib.getProperties().entrySet())
                            {
                            PropertyInfo prop = entry.getValue();
                            if (prop.isTypeParam()
                                    || (prop.isConstant() && prop.getRefAccess().isAsAccessibleAs(Access.PROTECTED))
                                    || prop.hasField())
                                {
                                PropertyConstant id = entry.getKey();
                                if (prop.isVirtual())
                                    {
                                    Object nid = id.resolveNestedIdentity(resolver);
                                    if (mapVirtProps.containsKey(nid))
                                        {
                                        continue;
                                        }

                                    mapVirtProps.put(nid, prop);
                                    }
                                mapProps.putIfAbsent(id, prop);
                                }
                            }

                        for (Map.Entry<MethodConstant, MethodInfo> entry : infoContrib.getMethods().entrySet())
                            {
                            MethodInfo method = entry.getValue();
                            assert method.isFunction();
                            mapMethods.putIfAbsent(entry.getKey(), method);
                            }
                        }
                    }
                    break;
                }
            }

        return new TypeInfo(this, infoPri.getClassStructure(), 0,
                infoPri.isAbstract(), infoPri.getTypeParams(), infoPri.getClassAnnotations(),
                infoPri.getExtends(), infoPri.getRebases(), infoPri.getInto(),
                infoPri.getContributionList(), infoPri.getClassChain(), infoPri.getDefaultChain(),
                mapProps, mapMethods, mapVirtProps, Collections.EMPTY_MAP,
                fIncomplete ? Progress.Incomplete : Progress.Complete);
        }

    /**
     * Populate the type parameter map with the type parameters of this type (not counting any
     * further contributions), and create a GenericTypeResolver based on that type parameter map.
     *
     * @param constId        the identity constant of the class that the type is based on
     * @param struct         the structure of the class that the type is based on
     * @param mapTypeParams  the map of type parameters
     * @param errs           the error list to log to
     *
     * @return a generic type resolver based on the (mutable) contents of the passed map
     */
    private TypeResolver createInitialTypeResolver(
            IdentityConstant       constId,
            ClassStructure         struct,
            Map<Object, ParamInfo> mapTypeParams,
            ErrorListener          errs)
        {
        TypeResolver resolver = new TypeResolver(mapTypeParams, errs);

        // obtain the type parameters encoded in this type constant
        TypeConstant[] atypeParams = getParamTypesArray();
        int            cTypeParams = atypeParams.length;

        if (isTuple())
            {
            // warning: turtles
            assert this instanceof AccessTypeConstant;

            TypeConstant typeElements = new TupleElementsTypeConstant(getConstantPool(), atypeParams);
            TypeConstant typePublic   = getUnderlyingType();

            ParamInfo param = new ParamInfo("ElementTypes", typePublic, typeElements);
            mapTypeParams.put(param.getName(), param);
            }
        else
            {
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

            for (int i = 0; i < cClassParams; ++i)
                {
                Entry<StringConstant, TypeConstant> entryClassParam = listClassParams.get(i);
                String                              sName           = entryClassParam.getKey().getValue();
                TypeConstant                        typeConstraint  = entryClassParam.getValue();
                TypeConstant                        typeActual      = null;

                // resolve any generics in the type constraint
                typeConstraint = typeConstraint.resolveGenerics(resolver);

                // validate the actual type, if there is one
                if (i < cTypeParams)
                    {
                    typeActual = atypeParams[i];
                    assert typeActual != null;

                    // the actual type of the type parameter may refer to other type parameters
                    typeActual = typeActual.resolveGenerics(resolver);

                    if (!typeActual.isA(typeConstraint))
                        {
                        log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_TYPE,
                                constId.getPathString(), sName,
                                typeConstraint.getValueString(),
                                typeActual.getValueString(), this.getValueString());

                        // to avoid a repetitive errors; proceed with the constraint type
                        typeActual = typeConstraint;
                        }
                    }

                mapTypeParams.put(sName, new ParamInfo(sName, typeConstraint, typeActual));
                }
            }

        return resolver;
        }

    /**
     * Fill in the passed list of contributions to process, and also collect a list of all the
     * annotations.
     *
     * @param constId      the identity constant of the class that the type is based on
     * @param struct       the structure of the class that the type is based on
     * @param listProcess  a list of contributions, which will be filled by this method in the
     *                     order that they should be processed
     * @param listAnnos    a list of annotations, which will be filled by this method
     * @param resolver     the GenericTypeResolver for the type
     * @param errs         the error list to log to
     *
     * @return an array containing the "into", "extends" and "rebase" types
     */
    private TypeConstant[] createContributionList(
            IdentityConstant    constId,
            ClassStructure      struct,
            List<Contribution>  listProcess,
            List<Annotation>    listAnnos,
            GenericTypeResolver resolver,
            ErrorListener       errs)
        {
        // glue any annotations from the type constant onto the front of the contribution list
        // (and remember the type of the annotated class)
        ConstantPool pool      = getConstantPool();
        TypeConstant typeClass = this;
        NextTypeInChain: while (true)
            {
            switch (typeClass.getFormat())
                {
                case ParameterizedType:
                case TerminalType:
                    // we found the class specification (with optional parameters) at the end of the
                    // type constant chain
                    break NextTypeInChain;

                case AnnotatedType:
                    // has to be an explicit class identity
                    AnnotatedTypeConstant typeAnno   = (AnnotatedTypeConstant) typeClass;
                    Annotation            annotation = typeAnno.getAnnotation();
                    TypeConstant          typeMixin  = typeAnno.getAnnotationType();

                    if (!typeMixin.isExplicitClassIdentity(true))
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_NOT_CLASS,
                                constId.getPathString(), typeMixin.getValueString());
                        break;
                        }

                    // has to be a mixin
                    if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_NOT_MIXIN,
                                typeMixin.getValueString());
                        break;
                        }

                    // the annotation could be a mixin "into Class", which means that it's a
                    // non-virtual, compile-time mixin (like @Abstract)
                    TypeConstant typeInto = typeMixin.getExplicitClassInto();
                    if (typeInto.isIntoClassType())
                        {
                        // check for duplicate class annotation
                        if (listAnnos.stream().anyMatch(annoPrev ->
                                annoPrev.getAnnotationClass().equals(annotation.getAnnotationClass())))
                            {
                            log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                                    constId.getPathString(), annotation.getAnnotationClass().getValueString());
                            }
                        else
                            {
                            listAnnos.add(annotation);
                            }
                        break;
                        }

                    // the mixin has to be able to apply to the remainder of the type constant chain
                    if (!typeClass.getUnderlyingType().isA(typeInto))
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                                typeClass.getUnderlyingType().getValueString(),
                                typeMixin.getValueString(),
                                typeInto.getValueString());
                        break;
                        }

                    // check for duplicate type annotation
                    if (listProcess.stream().anyMatch(contribPrev ->
                            contribPrev.getAnnotation().getAnnotationClass().equals(annotation.getAnnotationClass())))
                        {
                        log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                                constId.getPathString(), annotation.getAnnotationClass().getValueString());
                        }
                    else
                        {
                        // apply annotation
                        listProcess.add(new Contribution(annotation, pool.ensureAccessTypeConstant(
                                typeMixin, Access.PROTECTED)));
                        }
                    break;

                default:
                    break;
                }

            // advance to the next type in the chain
            typeClass = typeClass.getUnderlyingType();
            }
        assert typeClass != null;

        // process the annotations at the front of the contribution list
        List<Contribution> listContribs = struct.getContributionsAsList();
        int                cContribs    = listContribs.size();
        int                iContrib     = 0;
        for ( ; iContrib < cContribs; ++iContrib)
            {
            // only process annotations
            Contribution contrib = listContribs.get(iContrib);
            if (contrib.getComposition() != Composition.Annotation)
                {
                // ... all done processing annotations; move to the next stage
                break;
                }

            // has to be an explicit class identity
            TypeConstant typeMixin = contrib.getTypeConstant();
            if (!typeMixin.isExplicitClassIdentity(false))
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

            // the annotation could be a mixin "into Class", which means that it's a
            // non-virtual, compile-time mixin (like @Abstract)
            Annotation   annotation = contrib.getAnnotation();
            TypeConstant typeInto   = typeMixin.getExplicitClassInto();
            if (typeInto.isIntoClassType())
                {
                // check for duplicate class annotation
                if (listAnnos.stream().anyMatch(annoPrev ->
                        annoPrev.getAnnotationClass().equals(annotation.getAnnotationClass())))
                    {
                    log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                            constId.getPathString(), annotation.getAnnotationClass().getValueString());
                    }
                else
                    {
                    listAnnos.add(annotation);
                    }
                continue;
                }

            // the mixin has to apply to this type
            if (!typeClass.isA(typeInto)) // note: not 100% correct because the presence of this mixin may affect the answer
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                        typeClass.getValueString(),
                        typeMixin.getValueString(),
                        typeInto.getValueString());
                continue;
                }

            // check for duplicate type annotation
            if (listProcess.stream().anyMatch(contribPrev ->
                    contribPrev.getAnnotation().getAnnotationClass().equals(annotation.getAnnotationClass())))
                {
                log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                        constId.getPathString(), annotation.getAnnotationClass().getValueString());
                }
            else
                {
                // apply annotation
                listProcess.add(new Contribution(annotation, pool.ensureAccessTypeConstant(
                        annotation.getAnnotationType(), Access.PROTECTED)));
                }
            }

        // add a marker into the list of contributions at this point to indicate that this class
        // structure's contents need to be processed next
        listProcess.add(new Contribution(Composition.Equal, typeClass));  // place-holder for "this"

        // error check the "into" and "extends" clauses, plus rebasing (they'll get processed later)
        TypeConstant typeInto    = null;
        TypeConstant typeExtends = null;
        TypeConstant typeRebase  = null;
        switch (struct.getFormat())
            {
            case MODULE:
            case PACKAGE:
            case ENUMVALUE:
            case ENUM:
            case CLASS:
            case CONST:
            case SERVICE:
                {
                // next up, for any class type (other than Object itself), there MUST be an "extends"
                // contribution that specifies another class
                Contribution contrib = iContrib < cContribs ? listContribs.get(iContrib) : null;
                boolean fExtends = contrib != null && contrib.getComposition() == Composition.Extends;
                if (fExtends)
                    {
                    ++iContrib;
                    }

                // Object does not (and must not) extend anything
                if (constId.equals(pool.clzObject()))
                    {
                    if (fExtends)
                        {
                        log(errs, Severity.ERROR, VE_EXTENDS_UNEXPECTED,
                                contrib.getTypeConstant().getValueString(),
                                constId.getPathString());
                        }
                    break;
                    }

                // all other classes must extends something
                if (!fExtends)
                    {
                    log(errs, Severity.ERROR, VE_EXTENDS_EXPECTED, constId.getPathString());
                    typeExtends = pool.typeObject();
                    break;
                    }

                // the "extends" clause must specify a class identity
                typeExtends = contrib.resolveGenerics(resolver);
                if (!typeExtends.isExplicitClassIdentity(true))
                    {
                    log(errs, Severity.ERROR, VE_EXTENDS_NOT_CLASS,
                            constId.getPathString(),
                            typeExtends.getValueString());
                    typeExtends = pool.typeObject();
                    break;
                    }

                if (typeExtends.extendsClass(constId))
                    {
                    // some sort of circular loop
                    log(errs, Severity.ERROR, VE_EXTENDS_CYCLICAL, constId.getPathString());
                    typeExtends = pool.typeObject();
                    break;
                    }

                // the class structure will have to verify its "extends" clause in more detail, but
                // for now perform a quick sanity check
                IdentityConstant constExtends = typeExtends.getSingleUnderlyingClass(false);
                ClassStructure   structExtends = (ClassStructure) constExtends.getComponent();
                if (!ClassStructure.isExtendsLegal(struct.getFormat(), structExtends.getFormat()))
                    {
                    log(errs, Severity.ERROR, VE_EXTENDS_INCOMPATIBLE,
                            constId.getPathString(), struct.getFormat(),
                            constExtends.getPathString(), structExtends.getFormat());
                    typeExtends = pool.typeObject();
                    break;
                    }

                // check for re-basing; this occurs when a class format changes and the system has
                // to insert a layer of code between this class and the class being extended, such
                // as when a service (which is a Service format) extends Object (which is a Class
                // format)
                typeRebase = struct.getRebaseType();
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
                    ++iContrib;
                    typeInto = contrib.resolveGenerics(resolver);

                    // load the next contribution
                    contrib = iContrib < cContribs ? listContribs.get(iContrib) : null;
                    }

                // check "extends"
                boolean fExtends = contrib != null && contrib.getComposition() == Composition.Extends;
                if (fExtends)
                    {
                    ++iContrib;

                    typeExtends = contrib.resolveGenerics(resolver);
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
                        log(errs, Severity.ERROR, VE_EXTENDS_NOT_MIXIN,
                                typeExtends.getValueString(),
                                constId.getPathString());
                        break;
                        }

                    if (typeExtends.extendsClass(constId))
                        {
                        // some sort of circular loop
                        log(errs, Severity.ERROR, VE_EXTENDS_CYCLICAL, constId.getPathString());
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
                    // the original interface
                    TypeConstant typeNatural = ((NativeRebaseConstant) constId).getClassConstant().getType();
                    if (isParamsSpecified())
                        {
                        typeNatural = pool.ensureParameterizedTypeConstant(typeNatural, getParamTypesArray());
                        }
                    listProcess.add(new Contribution(Composition.Implements, typeNatural));

                    // since we're a class (not an interface), we need to extend Object somehow
                    typeExtends = pool.typeObject();
                    }
                else
                    {
                    // an interface implies the set of methods present in Object
                    // (use the "Into" composition to make the Object methods implicit-only, as
                    // opposed to explicitly being present in this interface)
                    typeInto = pool.typeObject();
                    }
                break;

            default:
                throw new IllegalStateException(getValueString() + "=" + struct.getFormat());
            }

        // go through the rest of the contributions, and add the ones that need to be processed to
        // the list to do
        NextContrib: for ( ; iContrib < cContribs; ++iContrib)
            {
            // only process annotations
            Contribution contrib     = listContribs.get(iContrib);
            TypeConstant typeContrib = contrib.resolveGenerics(this);

            switch (contrib.getComposition())
                {
                case Annotation:
                    log(errs, Severity.ERROR, VE_ANNOTATION_ILLEGAL,
                            typeContrib.getValueString(),
                            constId.getPathString());
                    break;

                case Into:
                    // only applicable on a mixin, only one allowed, and it should have been earlier
                    // in the list of contributions
                    log(errs, Severity.ERROR, VE_INTO_UNEXPECTED,
                            typeContrib.getValueString(),
                            constId.getPathString());
                    break;

                case Extends:
                    // not applicable on an interface, only one allowed, and it should have been
                    // earlier in the list of contributions
                    log(errs, Severity.ERROR, VE_EXTENDS_UNEXPECTED,
                            typeContrib.getValueString(),
                            constId.getPathString());
                    break;

                case Incorporates:
                    {
                    if (typeContrib == null)
                        {
                        // the type contribution does not apply conditionally to "this" type
                        continue NextContrib;
                        }

                    if (struct.getFormat() == Component.Format.INTERFACE)
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_UNEXPECTED,
                                typeContrib.getValueString(),
                                constId.getPathString());
                        break;
                        }

                    if (!typeContrib.isExplicitClassIdentity(true))
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_NOT_CLASS,
                                typeContrib.getValueString(),
                                constId.getPathString());
                        break;
                        }

                    // validate that the class is a mixin
                    if (typeContrib.getExplicitClassFormat() != Component.Format.MIXIN)
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_NOT_MIXIN,
                                typeContrib.getValueString(),
                                constId.getPathString());
                        break;
                        }

                    // the mixin must be compatible with this type, as specified by its "into"
                    // clause; note: not 100% correct because the presence of this mixin may affect
                    // the answer, so this requires an eventual fix
                    TypeConstant typeRequire = typeContrib.getExplicitClassInto();
                    if (typeRequire != null && !this.isA(typeRequire))
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_INCOMPATIBLE,
                                constId.getPathString(),
                                typeContrib.getValueString(),
                                this.getValueString(),
                                typeRequire.getValueString());
                        break;
                        }

                    // check for duplicate mixin (not exact match!!!)
                    if (listProcess.stream().anyMatch(contribPrev ->
                            contribPrev.getComposition() == Composition.Incorporates &&
                            contribPrev.getTypeConstant().equals(typeContrib)))
                        {
                        log(errs, Severity.ERROR, VE_DUP_INCORPORATES,
                                constId.getPathString(), typeContrib.getValueString());
                        }
                    else
                        {
                        listProcess.add(new Contribution(Composition.Incorporates,
                                pool.ensureAccessTypeConstant(typeContrib, Access.PROTECTED)));
                        }
                    }
                    break;

                case Delegates:
                    {
                    // not applicable on an interface
                    if (struct.getFormat() == Component.Format.INTERFACE)
                        {
                        log(errs, Severity.ERROR, VE_DELEGATES_UNEXPECTED,
                                typeContrib.getValueString(),
                                constId.getPathString());
                        break;
                        }

                    // must be an "interface type" (not a class type)
                    if (typeContrib.isExplicitClassIdentity(true)
                            && typeContrib.getExplicitClassFormat() != Component.Format.INTERFACE)
                        {
                        log(errs, Severity.ERROR, VE_DELEGATES_NOT_INTERFACE,
                                typeContrib.getValueString(),
                                constId.getPathString());
                        break;
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
                    break;

                case Implements:
                    {
                    // must be an "interface type" (not a class type)
                    if (typeContrib.isExplicitClassIdentity(true)
                            && typeContrib.getExplicitClassFormat() != Component.Format.INTERFACE)
                        {
                        log(errs, Severity.ERROR, VE_IMPLEMENTS_NOT_INTERFACE,
                                typeContrib.getValueString(),
                                constId.getPathString());
                        break;
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
                    break;

                default:
                    throw new IllegalStateException(constId.getPathString()
                            + ", contribution=" + contrib);
                }
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

                case Annotation:
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
                    TypeConstant typeContrib = contrib.getTypeConstant(); // already resolved generics!
                    TypeInfo     infoContrib = typeContrib.ensureTypeInfoInternal(errs);

                    if (infoContrib == null)
                        {
                        // skip this one (it has been deferred); an "into" represents a "right to
                        // left" resolution (from mixin to class), which presents a potential
                        // infinite cycle if we consider it to be incomplete; only consider it
                        // deferred (requiring a retry) iff the resolution is moving left to right
                        fIncomplete = compContrib != Composition.Into;
                        break;
                        }

                    if (compContrib != Composition.Into)
                        {
                        infoContrib.contributeChains(listmapClassChain, listmapDefaultChain, compContrib);
                        }

                    // collect type parameters
                    for (ParamInfo paramNew : infoContrib.getTypeParams().values())
                        {
                        Object    nid      = paramNew.getNestedIdentity();
                        ParamInfo paramOld = mapTypeParams.get(nid);
                        if (paramOld == null)
                            {
                            mapTypeParams.put(nid, paramNew);
                            }
                        else
                            {
                            // check that everything matches between the old and new parameter
                            if (paramNew.isActualTypeSpecified() != paramOld.isActualTypeSpecified())
                                {
                                if (paramOld.isActualTypeSpecified())
                                    {
                                    log(errs, Severity.ERROR, VE_TYPE_PARAM_CONTRIB_NO_SPEC,
                                            this.getValueString(), nid,
                                            paramOld.getActualType().getValueString(),
                                            typeContrib.getValueString());
                                    }
                                else
                                    {
                                    log(errs, Severity.ERROR, VE_TYPE_PARAM_CONTRIB_HAS_SPEC,
                                            this.getValueString(), nid,
                                            typeContrib.getValueString(),
                                            paramNew.getActualType().getValueString());
                                    }
                                }
                            else if (!paramNew.getActualType().equals(paramOld.getActualType()))
                                {
                                log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_CONTRIB,
                                        this.getValueString(), nid,
                                        paramOld.getActualType().getValueString(),
                                        typeContrib.getValueString(),
                                        paramNew.getActualType().getValueString());
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
     * @param resolver             the GenericTypeResolver that uses the known type parameters
     * @param listProcess          list of contributions in the order that they should be processed
     * @param listmapClassChain    potential call chain
     * @param listmapDefaultChain  potential default call chain
     * @param mapProps             properties of the class
     * @param mapMethods           methods of the class
     * @param mapVirtProps         the virtual properties of the type, keyed by nested id
     * @param mapVirtMethods       the virtual methods of the type, keyed by nested id
     * @param errs                 the error list to log any errors to
     *
     * @return true iff the processing was able to obtain all of its dependencies
     */
    private boolean collectMemberInfo(
            IdentityConstant                    constId,
            ClassStructure                      struct,
            TypeResolver                        resolver,
            List<Contribution>                  listProcess,
            ListMap<IdentityConstant, Origin>   listmapClassChain,
            ListMap<IdentityConstant, Origin>   listmapDefaultChain,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo  > mapMethods,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<Object, MethodInfo  >           mapVirtMethods,
            ErrorListener                       errs)
        {
        boolean fIncomplete = false;
        boolean fNative     = constId instanceof NativeRebaseConstant;

        for (int i = listProcess.size()-1; i >= 0; --i)
            {
            Contribution contrib = listProcess.get(i);

            Map<PropertyConstant, PropertyInfo> mapContribProps;
            Map<MethodConstant  , MethodInfo  > mapContribMethods;

            TypeConstant typeContrib = contrib.getTypeConstant();
            Composition  composition = contrib.getComposition();
            boolean      fSelf       = composition == Composition.Equal;
            if (fSelf)
                {
                mapContribProps   = new HashMap<>();
                mapContribMethods = new HashMap<>();
                ArrayList<PropertyConstant> listExplode = new ArrayList<>();
                if (!createMemberInfo(constId, isInterface(constId, struct),
                        struct, resolver, mapContribProps, mapContribMethods, listExplode, errs))
                    {
                    fIncomplete = true;
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
                    layerOnProp(constId, fSelf, resolver, mapProps, mapVirtProps,
                            typeContrib, idProp, prop, errs);

                    if (!fNative)
                        {
                        // now that the necessary data is in place, explode the property
                        if (!explodeProperty(constId, struct, idProp, prop, resolver,
                                mapProps, mapVirtProps, mapMethods, mapVirtMethods, errs))
                            {
                            fIncomplete = true;
                            }
                        }
                    }
                }
            else
                {
                TypeInfo infoContrib = typeContrib.ensureTypeInfoInternal(errs);
                if (infoContrib == null)
                    {
                    fIncomplete = true;
                    continue;
                    }

                if (composition == Composition.Into)
                    {
                    infoContrib = infoContrib.asInto();
                    }

                mapContribProps   = infoContrib.getProperties();
                mapContribMethods = infoContrib.getMethods();

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
                            PropertyInfo infoReduced = entry.getValue().retainOnly(setClass, setDefault);
                            if (infoReduced != null)
                                {
                                mapReducedProps.put(entry.getKey(), infoReduced);
                                }
                            }
                        mapContribProps = mapReducedProps;

                        Map<MethodConstant, MethodInfo> mapReducedMethods = new HashMap<>();
                        for (Entry<MethodConstant, MethodInfo> entry : mapContribMethods.entrySet())
                            {
                            MethodInfo infoReduced = entry.getValue().retainOnly(setClass, setDefault);
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
            layerOnProps(constId, fSelf, resolver, mapProps, mapVirtProps,
                    typeContrib, mapContribProps, errs);

            // if there are any remaining declared-but-not-overridden properties originating from
            // an interface on a class once the "self" layer is applied, then those need to be
            // analyzed to determine if they require fields, etc.
            if (fSelf && !isInterface(constId, struct))
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
                            Object       nid       = entry.getKey().resolveNestedIdentity(resolver);
                            PropertyInfo infoCheck = mapVirtProps.put(nid, infoNew);
                            assert infoOld == infoCheck;
                            }
                        }
                    }
                }

            // process methods
            if (!mapContribMethods.isEmpty())
                {
                layerOnMethods(constId, fSelf, resolver, mapMethods, mapVirtMethods,
                        typeContrib, mapContribMethods, errs);
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
                            Object     nid       = entry.getKey().resolveNestedIdentity(resolver);
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
     * @param resolver        the GenericTypeResolver that uses the known type parameters
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
            TypeResolver                        resolver,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<MethodConstant, MethodInfo>     mapMethods,
            Map<Object, MethodInfo>             mapVirtMethods,
            ErrorListener                       errs)
        {
        boolean fComplete = true;

        // layer on an "into" of either "into Ref" or "into Var"
        {
        ConstantPool pool     = getConstantPool();
        TypeConstant typeTerm = info.isVar() ? pool.typeVarRB() : pool.typeRefRB();
        TypeConstant typeInto = pool.ensureAccessTypeConstant(
            pool.ensureParameterizedTypeConstant(typeTerm, info.getType()), Access.PROTECTED);
        TypeInfo     infoInto = typeInto.ensureTypeInfoInternal(errs);
        if (infoInto == null)
            {
            fComplete = false;
            }
        else
            {
            nestAndLayerOn(constId, idProp, resolver, mapProps, mapVirtProps, mapMethods,
                mapVirtMethods, typeInto, infoInto, errs);
            }
        }

        // layer on any annotations, if any
        Annotation[] aAnnos = info.getRefAnnotations();
        int          cAnnos = aAnnos.length;
        for (int i = cAnnos - 1; i >= 0; --i)
            {
            Annotation     anno     = aAnnos[i];
            ConstantPool   pool     = anno.getConstantPool();
            TypeConstant   typeAnno = anno.getAnnotationType();
            ClassStructure clzAnno  = (ClassStructure) ((IdentityConstant) anno.getAnnotationClass()).getComponent();
            if (clzAnno.indexOfGenericParameter("RefType") == 0)
                {
                typeAnno = pool.ensureParameterizedTypeConstant(typeAnno, info.getType());
                }
            typeAnno = pool.ensureAccessTypeConstant(typeAnno, Access.PROTECTED);

            TypeInfo infoAnno = typeAnno.ensureTypeInfoInternal(errs);
            if (infoAnno == null)
                {
                fComplete = false;
                }
            else
                {
                nestAndLayerOn(constId, idProp, resolver, mapProps, mapVirtProps, mapMethods,
                    mapVirtMethods, typeAnno, infoAnno, errs);
                }
            }

        // the custom logic will get overlaid later by layerOnMethods(); in the case of a native
        // getter for otherwise natural classes, it needs to be added (ensured) at this point so
        // that it will get picked up in that layer-on processing
        if (struct.getFormat() != Component.Format.INTERFACE)
            {
            PropertyStructure prop = (PropertyStructure) idProp.getComponent();
            if (prop != null && prop.isNativeGetter())
                {
                MethodConstant idGet   = info.getGetterId();
                MethodBody     bodyGet = new MethodBody(idGet, idGet.getSignature(), Implementation.Native);
                MethodInfo     infoGet = new MethodInfo(bodyGet);

                mapMethods.put(idGet, infoGet);
                mapVirtMethods.put(idGet.resolveNestedIdentity(resolver), infoGet);
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
     * @param resolver        the TypeResolver that uses the known type parameters
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
            TypeResolver                        resolver,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            Map<MethodConstant, MethodInfo>     mapMethods,
            Map<Object, MethodInfo>             mapVirtMethods,
            TypeConstant                        typeContrib,
            TypeInfo                            infoContrib,
            ErrorListener                       errs)
        {
        // basically, everything in infoContrib needs to be "indented" (nested) within the nested
        // identity of the property
        Map<PropertyConstant, PropertyInfo> mapContribProps = new HashMap<>();
        for (Entry<PropertyConstant, PropertyInfo> entry : infoContrib.getProperties().entrySet())
            {
            Object           nidContrib = entry.getKey().resolveNestedIdentity(resolver);
            PropertyConstant idContrib  = (PropertyConstant) idProp.appendNestedIdentity(nidContrib);
            mapContribProps.put(idContrib, entry.getValue());
            }
        layerOnProps(constId, false, resolver, mapProps, mapVirtProps, typeContrib, mapContribProps, errs);

        Map<MethodConstant, MethodInfo> mapContribMethods = new HashMap<>();
        for (Entry<MethodConstant, MethodInfo> entry : infoContrib.getMethods().entrySet())
            {
            Object         nidContrib = entry.getKey().resolveNestedIdentity(resolver);
            MethodConstant idContrib  = (MethodConstant) idProp.appendNestedIdentity(nidContrib);
            mapContribMethods.put(idContrib, entry.getValue());
            }
        layerOnMethods(constId, false, resolver, mapMethods, mapVirtMethods,
            typeContrib, mapContribMethods, errs);
        }

    /**
     * Layer on the passed property contributions onto the property information already collected.
     *
     * @param constId          identity of the class
     * @param resolver         the TypeResolver that uses the known type parameters
     * @param mapProps         properties of the class
     * @param mapVirtProps     the virtual properties of the type, keyed by nested id
     * @param typeContrib      the type whose members are being contributed
     * @param mapContribProps  the property information to add to the existing properties
     * @param fSelf            true if the layer being added represents the "Equals" contribution of
     *                         the type
     * @param errs             the error list to log any errors to
     */
    protected void layerOnProps(
            IdentityConstant                    constId,
            boolean                             fSelf,
            TypeResolver                        resolver,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            TypeConstant                        typeContrib,
            Map<PropertyConstant, PropertyInfo> mapContribProps,
            ErrorListener                       errs)
        {
        for (Entry<PropertyConstant, PropertyInfo> entry : mapContribProps.entrySet())
            {
            layerOnProp(constId, fSelf, resolver, mapProps, mapVirtProps,
                typeContrib, entry.getKey(), entry.getValue(), errs);
            }
        }

    /**
     * Layer on the passed property contribution onto the property information already collected.
     *
     * @param constId       identity of the class
     * @param fSelf         true if the layer being added represents the "Equals" contribution of
     *                      the type
     * @param resolver      the TypeResolver that uses the known type parameters
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
            TypeResolver                        resolver,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<Object, PropertyInfo>           mapVirtProps,
            TypeConstant                        typeContrib,
            PropertyConstant                    idContrib,
            PropertyInfo                        propContrib,
            ErrorListener                       errs)
        {
        Object           nidContrib = idContrib.resolveNestedIdentity(resolver);
        PropertyConstant idResult   = (PropertyConstant) constId.appendNestedIdentity(nidContrib);

        // the property is not virtual if it is a constant, if it is private/private, or if
        // it is inside a method (which coincidentally must be private/private). in this
        // case, the properties are always "fully scoped" (they have only one identity), so
        // there is no chance of a collision
        boolean fVirtual = propContrib.isVirtual();

        // look for a property of the same name (using its nested identity); only virtually
        // composable properties are registered using their nested identities
        PropertyInfo propBase   = fVirtual
                ? mapVirtProps.get(nidContrib)
                : null;
        PropertyInfo propResult = propBase == null
                ? propContrib
                : propBase.layerOn(propContrib, fSelf, errs);

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
     * @param resolver           the TypeResolver that uses the known type parameters
     * @param mapMethods         methods of the class
     * @param mapVirtMethods     the virtual methods of the type, keyed by nested id
     * @param typeContrib        the type whose members are being contributed
     * @param mapContribMethods  the method information to add to the existing methods
     * @param errs               the error list to log any errors to
     */
    protected void layerOnMethods(
            IdentityConstant                constId,
            boolean                         fSelf,
            TypeResolver                    resolver,
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
        Map<Object, MethodInfo>  mapVirtMods     = new HashMap<>();
        Map<Object, Set<Object>> mapNarrowedNids = null;
        for (Entry<MethodConstant, MethodInfo> entry : mapContribMethods.entrySet())
            {
            MethodConstant idContrib     = entry.getKey();
            MethodInfo     methodContrib = entry.getValue();
            Object         nidContrib    = idContrib.resolveNestedIdentity(resolver);

            // the method is not virtual if it is a function, if it is private, or if it is
            // contained inside a method or some other structure (such as a property) that is
            // non-virtual
            if (!methodContrib.isVirtual())
                {
                // TODO check for collision, because a function could theoretically replace a virtual method
                // TODO (e.g. 2 modules, 1 introduces a virtual method in a new version that collides with a function in the other)
                // TODO we'll also have to check similar conditions below
                mapMethods.put((MethodConstant) constId.appendNestedIdentity(nidContrib), methodContrib);
                continue;
                }

            // look for a method of the same signature (using its nested identity); only
            // virtual methods are registered using their nested identities
            MethodInfo methodBase   = mapVirtMethods.get(nidContrib);
            MethodInfo methodResult = methodContrib;
            if (methodBase == null)
                {
                if (methodContrib.isOverride())
                    {
                    // the @Override tag gives us permission to look for a method with a
                    // different signature that can be narrowed to the signature of the
                    // contribution (because @Override means there MUST be a super method)
                    Object nidBase = findRequiredSuper(
                            nidContrib, methodContrib.getSignature(), mapVirtMethods, errs);
                    if (nidBase != null)
                        {
                        methodBase = mapVirtMethods.get(nidBase);
                        assert methodBase != null;

                        // there exists a method that this method will narrow, so add this
                        // method to the set of methods that are narrowing the super method
                        if (mapNarrowedNids == null)
                            {
                            mapNarrowedNids = new HashMap<>();
                            }
                        Set<Object> setNarrowing = mapNarrowedNids.get(nidBase);
                        if (setNarrowing == null)
                            {
                            setNarrowing = new HashSet<>();
                            mapNarrowedNids.put(nidBase, setNarrowing);
                            }
                        setNarrowing.add(nidContrib);
                        }
                    }
                }

            if (methodBase != null)
                {
                methodResult = methodBase.layerOn(methodContrib, fSelf, errs);
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
                    mapVirtMods.put(nidNarrowed, infoNarrowed.cappedBy(infoNarrowing));
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
            MethodConstant id   = (MethodConstant) constId.appendNestedIdentity(nid);

            mapMethods.put(id, info);
            mapVirtMethods.put(nid, info);
            }
        }

    /**
     * Find the method that would be the "super" of the specified method signature. A super is
     * required to exist, and one super must be the unambiguously best choice, otherwise an error
     * will be logged.
     *
     * @param nidSub     the nested identity of the method that is searching for a super
     * @param mapSupers  the possible super methods to select from
     * @param errs       the error list to log any errors to
     *
     * @return the nested identity for the super method, or null if there is no one unambiguously
     *         "best" super method signature to be found
     */
    protected Object findRequiredSuper(
            Object                  nidSub,
            SignatureConstant       sigSub,
            Map<Object, MethodInfo> mapSupers,
            ErrorListener           errs)
        {
        // check for exact match
        if (mapSupers.containsKey(nidSub))
            {
            return nidSub;
            }

        // brute force search
        Object            nidBest   = null;
        List<Object>      listMatch = null;
        for (Entry<Object, MethodInfo> entry : mapSupers.entrySet())
            {
            Object nidCandidate = entry.getKey();
            if (IdentityConstant.isNestedSibling(nidSub, nidCandidate))
                {
                SignatureConstant sigCandidate = entry.getValue().getSignature();
                if (sigSub.isSubstitutableFor(sigCandidate, this))
                    {
                    if (listMatch == null)
                        {
                        if (nidBest == null)
                            {
                            nidBest = nidCandidate;
                            }
                        else
                            {
                            // we've got at least 2 matches, so we'll need to compare them all
                            listMatch = new ArrayList<>();
                            listMatch.add(nidBest);
                            listMatch.add(nidCandidate);
                            nidBest = null;
                            }
                        }
                    else
                        {
                        listMatch.add(nidCandidate);
                        }
                    }
                }
            }

        // if none match, then there is no match; if only 1 matches, then use it
        if (listMatch == null)
            {
            if (nidBest == null)
                {
                log(errs, Severity.ERROR, VE_SUPER_MISSING,
                        sigSub.getValueString(), getValueString());
                }

            return nidBest;
            }

        // REVIEW could this be updated to use selectBest() ?
        // if multiple candidates exist, then one must be obviously better than the rest
        SignatureConstant sigBest = null;
        nextCandidate: for (int iCur = 0, cCandidates = listMatch.size();
                iCur < cCandidates; ++iCur)
            {
            Object            nidCandidate = listMatch.get(iCur);
            SignatureConstant sigCandidate = mapSupers.get(nidCandidate).getSignature();
            if (nidBest == null) // that means that "best" is ambiguous thus far
                {
                // have to back-test all the ones in front of us to make sure that
                for (int iPrev = 0; iPrev < iCur; ++iPrev)
                    {
                    SignatureConstant sigPrev = mapSupers.get(listMatch.get(iPrev)).getSignature();
                    if (!sigPrev.isSubstitutableFor(sigCandidate, this))
                        {
                        // still ambiguous
                        continue nextCandidate;
                        }
                    }

                // so far, this candidate is the best
                nidBest = nidCandidate;
                sigBest = sigCandidate;
                }
            else if (sigBest.isSubstitutableFor(sigCandidate, this))
                {
                // this assumes that "best" is a transitive concept, i.e. we're not going to back-
                // test all of the other candidates
                nidBest = nidCandidate;
                sigBest = sigCandidate;
                }
            else if (!sigCandidate.isSubstitutableFor(sigBest, this))
                {
                nidBest = null;
                sigBest = null;
                }
            }

        if (nidBest == null)
            {
            log(errs, Severity.ERROR, VE_SUPER_AMBIGUOUS, sigSub.getValueString());
            }

        return nidBest;
        }

    /**
     * Helper to select the "best" signature from an array of signatures.
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
     * Generate the members of the "this" class of "this" type.
     *
     * @param constId           the identity of the class (used for logging error information)
     * @param fInterface        if the class is an interface type
     * @param structContrib     the class structure, property structure, or method structure REVIEW or typedef?
     * @param resolver          the GenericTypeResolver that uses the known type parameters
     * @param mapProps          the properties of the class
     * @param mapMethods        the methods of the class
     * @param errs              the error list to log any errors to
     *
     * @return true iff the processing was able to obtain all of its dependencies
     */
    private boolean createMemberInfo(
            IdentityConstant                    constId,
            boolean                             fInterface,
            Component                           structContrib,
            TypeInfo.TypeResolver               resolver,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo  > mapMethods,
            List<PropertyConstant>              listExplode,
            ErrorListener                       errs)
        {
        boolean fComplete = true;
        boolean fNative   = constId instanceof NativeRebaseConstant;

        if (structContrib instanceof MethodStructure)
            {
            MethodStructure   method       = (MethodStructure) structContrib;
            boolean           fHasNoCode   = !method.hasCode();
            boolean           fHasAbstract = method.findAnnotation(getConstantPool().clzAbstract()) != null;
            MethodConstant    id           = method.getIdentityConstant();
            SignatureConstant sig          = id.getSignature().resolveGenericTypes(resolver);
            MethodBody        body         = new MethodBody(id, sig,
                    fInterface && fHasNoCode    ? Implementation.Declared :
                    fInterface                  ? Implementation.Default  :
                    fNative | method.isNative() ? Implementation.Native   :
                    fHasAbstract                ? Implementation.Abstract :
                    fHasNoCode                  ? Implementation.SansCode :
                                                  Implementation.Explicit  );
            MethodInfo infoNew = new MethodInfo(body);
            mapMethods.put(id, infoNew);
            }
        else if (structContrib instanceof PropertyStructure)
            {
            PropertyStructure prop = (PropertyStructure) structContrib;
            PropertyConstant  id   = prop.getIdentityConstant();
            PropertyInfo      info;
            if (prop.isTypeParameter())
                {
                // this only knows how to create a type-param PropertyInfo for the type parameters
                // of the class
                assert id.getNestedDepth() == 1;
                info = new PropertyInfo(new PropertyBody(prop, resolver.findParamInfo(id.getName())));
                }
            else
                {
                assert !(fNative && fInterface); // cannot be native and interface at the same time
                info = createPropertyInfo(prop, constId, fNative, fInterface, resolver, errs);
                }
            mapProps.put(id, info);

            if (info.isCustomLogic() || info.isRefAnnotated())
                {
                // this property needs to be "exploded"
                listExplode.add(id);

                // create a ParamInfo and a type-param PropertyInfo for the RefType type parameter
                // note: while this is very hard-coded and dense and inelegant, it basically is
                //       compensating for the fact that we're about to treat the property (id/info)
                //       as it's own ***class***, just like the type for which we are currently
                //       producing a TypeInfo. however, unlike the top level class & TypeInfo, the
                //       property doesn't have a chance to go through the createInitialTypeResolver
                //       method, so lacking that, this "jams in" the additional type parameters that
                //       the property relies on (as if they had been correctly populated by going
                //       through createInitialTypeResolver)
                ConstantPool     pool      = id.getConstantPool();
                PropertyConstant idParam   = pool.ensurePropertyConstant(id, "RefType");
                Object           nidParam  = idParam.resolveNestedIdentity(resolver);
                ParamInfo        param     = new ParamInfo(nidParam, "RefType", pool.typeObject(), info.getType());
                PropertyInfo     propParam = new PropertyInfo(new PropertyBody(null, param));
                resolver.registerParamInfo(nidParam, param);
                mapProps.put(idParam, propParam);
                }

            // we're about to go down inside of the property, so create a type resolver that knows
            // how to resolve the property's type params (specifically, "RefType")
            resolver = info.new TypeResolver(resolver);
            }

        // recurse through children
        for (Component child : structContrib.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    fComplete &= createMemberInfo(constId, fInterface, method, resolver,
                            mapProps, mapMethods, listExplode, errs);
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                fComplete &= createMemberInfo(constId, fInterface, child, resolver,
                        mapProps, mapMethods, listExplode, errs);
                }
            }

        return fComplete;
        }

    /**
     * Create the PropertyInfo for the specified property.
     *
     * @param prop        the PropertyStructure
     * @param constId     the identity of the containing structure (used only for error messages)
     * @param fNative     true if the type is a native rebase
     * @param fInterface  true if the type is an interface, not a class or mixin (only if not native)
     * @param resolver    the GenericTypeResolver that uses the known type parameters
     * @param errs        the error list to log any errors to
     *
     * @return a new PropertyInfo for the passed PropertyStructure
     */
    private PropertyInfo createPropertyInfo(
            PropertyStructure     prop,
            IdentityConstant      constId,
            boolean               fNative,
            boolean               fInterface,
            TypeInfo.TypeResolver resolver,
            ErrorListener         errs)
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
            fHasInject   |= constMixin.equals(pool.clzInject());
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
                        sName, constId.getValueString(), typeMixin.getValueString());
                continue;
                }

            // we've already processed the "into Property" annotations, so this has to be an
            // "into Ref" (or some sub-class of Ref, e.g. Var) annotation
            assert typeInto.isA(pool.typeRef());

// TODO verify that the mixin has one and only one type parameter, and it is named RefType, i.e. "mixin M<RefType> into Var<RefType>"
// TODO does the annotation class provide a hard-coded value for RefType? because if it does, we need to "isA()" test it against the type of the property

            if (scanForDups(aRefAnno, i, constMixin))
                {
                log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                        prop.getIdentityConstant().getValueString(),
                        constMixin.getValueString());
                }

            fHasRefAnno   = true;
            fHasVarAnno  |= typeInto.isA(pool.typeVar());
            }

        // functions and constants cannot have properties; methods cannot have constants
        IdentityConstant constParent = prop.getIdentityConstant().getParentConstant();
        boolean          fConstant   = prop.isStatic();
        switch (constParent.getFormat())
            {
            case Property:
                if (prop.getParent().isStatic())
                    {
                    log(errs, Severity.ERROR, VE_CONST_CODE_ILLEGAL,
                            constParent.getValueString(), sName);
                    }
                break;

            case Method:
                // "static" properties inside a method are just an indication that the Ref/Var is
                // is a property of the containing class
                fConstant = false;
                if (prop.getParent().isStatic())
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
                        if (methodInit == null && method.isInitializer(prop.getType(), resolver))
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
                        if (method.isGetter(prop.getType(), resolver))
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
                        if (method.isSetter(prop.getType(), resolver))
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
        else  if (accessVar != null && accessRef.compareTo(accessVar) > 0)
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
                    if (!fHasRO)
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
            fNative |= prop.isNativeGetter();
            impl     = fNative ? Implementation.Native : Implementation.Explicit;

            // determine if the get explicitly calls super, or explicitly blocks super
            boolean fGetSupers      = methodGet != null && methodGet.usesSuper();
            boolean fSetSupers      = methodSet != null && methodSet.usesSuper();
            boolean fGetBlocksSuper = methodGet != null && !methodGet.isAbstract() && !fGetSupers;
            boolean fSetBlocksSuper = methodSet != null && !methodGet.isAbstract() && !fSetSupers;

            if (fNative)
                {
                fGetSupers      = false;
                fGetBlocksSuper = true;
                }

            if (fHasRO && (fSetSupers || fHasVarAnno))
                {
                // the @RO conflicts with the annotations that require a Var
                log(errs, Severity.ERROR, VE_PROPERTY_READONLY_NOT_VAR,
                        getValueString(), sName);
                }

            if (fHasRO && !(fHasAbstract || fHasOverride || fHasInject || methodGet != null || fNative))
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
            fField = !fHasInject & !fHasRO & !fHasAbstract & !fHasOverride & !fGetBlocksSuper & !fNative;

            // we assume Ref-not-Var if @RO is specified, or if there is a get() with no
            // super and no set() (or Var-implying annotations)
            fRO |= !fHasVarAnno && (fHasRO || (fGetBlocksSuper && methodSet == null));

            fRW |= fHasVarAnno | accessVar != null | methodSet != null;

            effectGet = effectOf(fGetSupers, fGetBlocksSuper);
            effectSet = effectOf(fSetSupers, fSetBlocksSuper);
            }

        if (fRO && fRW)
            {
            log(errs, Severity.ERROR, VE_PROPERTY_READWRITE_READONLY,
                    getValueString(), sName);
            fRO = false;
            }

        return new PropertyInfo(new PropertyBody(prop, impl, null,
                prop.getType().resolveGenerics(resolver), fRO, fRW, cCustomMethods > 0,
                effectGet, effectSet,  fField, fConstant, prop.getInitialValue(),
                methodInit == null ? null : methodInit.getIdentityConstant()));
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
        ConstantPool pool = getConstantPool();
        for (ParamInfo param : mapTypeParams.values())
            {
            if (param.getNestedIdentity() instanceof NestedIdentity)
                {
                continue;
                }

            String       sParam = param.getName();
            PropertyInfo prop   = mapProps.get(sParam);
            if (prop == null)
                {
                log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_MISSING,
                        this.getValueString(), sParam);
                }
            else if (!prop.isTypeParam() || !prop.getType().getParamTypesArray()[0].isA(param.getConstraintType()))
                {
                log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_INCOMPATIBLE,
                        this.getValueString(), sParam);
                }
            }
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
        if (this.equals(typeLeft) || typeLeft.equals(getConstantPool().typeObject()))
            {
            return Relation.IS_A;
            }

        // WARNING: thread-unsafe
        Map<TypeConstant, Relation> mapRelations = ensureRelationMap();
        Relation relation = mapRelations.get(typeLeft);

        if (relation == null)
            {
            // there's a special case of the conditional return, where
            // "False" _isA_ "ConditionalTuple"
            // TODO: replace with the ConditionalTuple when added
            ConstantPool pool = getConstantPool();
            if (this.equals(pool.typeFalse())
                    && typeLeft.isTuple()
                    && typeLeft.isParamsSpecified()
                    && typeLeft.getParamsCount() > 0
                    && typeLeft.getParamTypesArray()[0].equals(pool.typeBoolean()))
                {
                mapRelations.put(typeLeft, Relation.IS_A);
                return Relation.IS_A;
                }

            mapRelations.put(typeLeft, Relation.IN_PROGRESS);
            try
                {
                List<ContributionChain> chains = this.collectContributions(typeLeft,
                    new ArrayList<>(), new ArrayList<>());

                relation = chains.isEmpty()
                    ? Relation.INCOMPATIBLE
                    : validateChains(chains, this, typeLeft);

                mapRelations.put(typeLeft, relation);
                }
            catch (RuntimeException | Error e)
                {
                mapRelations.remove(typeLeft);
                throw e;
                }
            }
        else if (relation == Relation.IN_PROGRESS)
            {
            // we are in recursion; this can only happen for duck-typing, for example:
            //
            //    interface I { I! foo(); }
            //    class C { C! foo(); }
            //
            // the check on whether C is assignable to I depends on whether the return value of
            // C.foo() is assignable to the return value of I.foo(), which causes a recursion
            //
            assert !typeLeft.isClassType();
            mapRelations.put(typeLeft, relation = Relation.IS_A);
            }
        return relation;
        }

    /**
     * Validate the list of chains that were collected by the collectContribution() method, but
     * now from the L-value's perspective. The chains that deemed to be non-fitting will be
     * deleted from the chain list.
     */
    protected static Relation validateChains(List<ContributionChain> chains,
                                             TypeConstant typeRight, TypeConstant typeLeft)
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
     * @param typeLeft   the type to match (L-value)
     * @param listRight  the list of actual generic parameters for this type
     * @param chains     the list of chains to modify
     *
     * @return a list of ContributionChain objects that describe how "that" type could be found in
     *         the contribution tree of "this" type; empty if the types are incompatible
     */
    public List<ContributionChain> collectContributions(
            TypeConstant typeLeft, List<TypeConstant> listRight, List<ContributionChain> chains)
        {
        return getUnderlyingType().collectContributions(typeLeft, listRight, chains);
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
    protected boolean validateContributionFrom(TypeConstant typeRight, Access accessLeft,
                                               ContributionChain chain)
        {
        return getUnderlyingType().validateContributionFrom(typeRight, accessLeft, chain);
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
     * Determine if this type consumes a formal type with the specified name in a context
     * of the given TypeComposition and access policy.
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
     * of the given TypeComposition and access policy.
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
     * of the given TypeComposition and access policy.
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
     * of the given TypeComposition and access policy.
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
        // formal type is not convertible to anything
        return isFormalType()
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
     *         "interface type"
     */
    public boolean isClassType()
        {
        // generally, a type is a class type if any of the underlying types is a class type
        return getUnderlyingType().isClassType();
        }

    /**
     * @return true iff the TypeConstant represents a "formal type"
     */
    public boolean isFormalType()
        {
        return false;
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
        assert (fAllowInterface || isClassType()) && isSingleUnderlyingClass(fAllowInterface);

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
     * Find an underlying TypeConstant of the specified class.
     *
     * @return the matching TypeConstant or null
     * @param clz
     */
    public <T extends TypeConstant> T findFirst(Class<T> clz)
        {
        return clz == getClass() ? (T) this : getUnderlyingType().findFirst(clz);
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
            hType = m_handle = xType.makeHandle(this);
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
        return getUnderlyingType().callEquals(frame, hValue1, hValue2, iReturn);
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
        return getUnderlyingType().callCompare(frame, hValue1, hValue2, iReturn);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public abstract Constant.Format getFormat();

    @Override
    public TypeConstant getType()
        {
        return this;
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
            fHalt |= super.validate(errlist);
            fHalt |= isModifyingType() && getUnderlyingType().validate(errlist);
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

        TypeConstant that = (TypeConstant) obj;

        return this.getFormat() == that.getFormat() && this.compareDetails(that) == 0;
        }


    // ----- helpers -------------------------------------------------------------------------------

    private Map<TypeConstant, Relation> ensureRelationMap()
        {
        Map<TypeConstant, Relation> mapRelations = m_mapRelations;
        if (mapRelations == null)
            {
            mapRelations = m_mapRelations = new HashMap<>();
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


    // ----- inner class: Origin -------------------------------------------------------------------

    /**
     * Used during "potential call chain" creation.
     */
    class Origin
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


    // -----fields ---------------------------------------------------------------------------------

    /**
     * An immutable, empty, zero-length array of types.
     */
    public static final TypeConstant[] NO_TYPES = new TypeConstant[0];

    /**
     * Relationship options.
     */
    public enum Relation {IN_PROGRESS, IS_A, IS_A_WEAK, INCOMPATIBLE}

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
     * Keeps track of whether the TypeConstant has been validated.
     */
    private boolean m_fValidated;

    /**
     * The resolved information about the type, its properties, and its methods.
     */
    private transient volatile int m_cRecursiveDepth;
    private transient volatile TypeInfo m_typeinfo;
    private static AtomicReferenceFieldUpdater<TypeConstant, TypeInfo> s_typeinfo =
            AtomicReferenceFieldUpdater.newUpdater(TypeConstant.class, TypeInfo.class, "m_typeinfo");

    /**
     * A cache of "isA" responses.
     */
    private Map<TypeConstant, Relation> m_mapRelations;

    /**
     * A cache of "consumes" responses.
     */
    private Map<String, Usage> m_mapConsumes;

    /**
     * A cache of "produces" responses.
     */
    private Map<String, Usage> m_mapProduces;

    /**
     * Cached TypeHandle.
     */
    private xType.TypeHandle m_handle;
    }