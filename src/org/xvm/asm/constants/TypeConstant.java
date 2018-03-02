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

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.ParamInfo.TypeResolver;
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
     * @return the corresponding actual type
     */
    public TypeConstant getGenericParamType(String sName)
        {
        if (isSingleDefiningConstant())
            {
            TypeInfo info = getTypeInfo();
            if (info != null && info.getProgress().compareTo(Progress.Incomplete) >= 0)
                {
                ParamInfo param = info.getTypeParams().get(sName);
                if (param == null)
                    {
                    throw new IllegalArgumentException(
                            "Invalid formal name: " + sName + " for " + getValueString());
                    }
                return param.getActualType();
                }

            // because isA() uses this method, there is a chicken-and-egg problem, so instead of
            // materializing the TypeInfo at this point, just answer the question without it
            ClassStructure clz = (ClassStructure) getSingleUnderlyingClass(true).getComponent();
            TypeConstant type = clz.getGenericParamType(sName, getParamTypes());
            if (type == null)
                {
                throw new IllegalArgumentException(
                        "Invalid formal name: " + sName + " for " + this);
                }
            return type;
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
     * Determine if this is "Void".
     *
     * @return true iff this is provably "Void"
     */
    public boolean isVoid()
        {
        return isTuple() && getParamsCount() == 0;
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
     *         formal parameters for the parameterized type
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
        return isSingleDefiningConstant() && getDefiningConstant().equals(getConstantPool().clzTuple());
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
        assert !hasDeferredTypeInfo();

        // build the TypeInfo for this type
        info = buildTypeInfo(errs);
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
                    typeDeferred.ensureTypeInfo(errs);
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
            setTypeInfo(info);
            if (!info.isComplete())
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
     * @return a new TypeInfo representing this TypeConstant
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
                return getConstantPool().ensureAccessTypeConstant(this, Access.PRIVATE)
                        .ensureTypeInfo(errs).limitAccess(Access.PUBLIC);
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

        // we're going to build a map from name to param info, including whatever parameters are
        // specified by this class/interface, but also each of the contributing classes/interfaces
        Map<String, ParamInfo> mapTypeParams = new HashMap<>();
        TypeResolver resolver = createInitialTypeResolver(constId, struct, mapTypeParams, errs);

        // walk through each of the contributions, starting from the implied contributions that are
        // represented by annotations in this type constant itself, followed by the annotations in
        // the class structure, followed by the class structure (as its own pseudo-contribution),
        // followed by the remaining contributions
        List<Contribution> listProcess    = new ArrayList<>();
        List<Annotation>   listClassAnnos = new ArrayList<>();
        TypeConstant[]     atypeSpecial   = createContributionList(
                constId, struct, listProcess, listClassAnnos, resolver, errs);
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
        Annotation[] aannoClass = listClassAnnos.toArray(new Annotation[listClassAnnos.size()]);
        boolean      fAbstract  = struct.getFormat() == Component.Format.INTERFACE
                || TypeInfo.containsAnnotation(aannoClass, "Abstract");

        // next, we need to process the list of contributions in order, asking each for its
        // properties and methods, and collecting all of them
        Map<PropertyConstant , PropertyInfo> mapProps   = new HashMap<>();
        Map<MethodConstant   , MethodInfo  > mapMethods = new HashMap<>();
        fComplete &= collectMemberInfo(constId, struct, resolver,
                listProcess, listmapClassChain, listmapDefaultChain,
                mapProps, mapMethods, errs);

        // go through the members to determine if this is abstract
        if (!fAbstract)
            {
            fAbstract = mapProps.values().stream().anyMatch(PropertyInfo::isExplicitlyAbstract)
                    || mapMethods.values().stream().anyMatch(MethodInfo::isAbstract);
            }

        // make final determinations as to what fields are required, etc.
        finalizeMemberInfo(constId, struct, fAbstract, mapProps, mapMethods, errs);

        // validate the type parameters against the properties for the same
        checkTypeParameterProperties(mapTypeParams, mapProps, errs);

        return new TypeInfo(this, struct, 0, fAbstract, mapTypeParams, aannoClass,
                typeExtends, typeRebase, typeInto,
                listProcess, listmapClassChain, listmapDefaultChain, mapProps, mapMethods,
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
        ConstantPool                        pool       = getConstantPool();
        TypeInfo                            infoPri    = pool.ensureAccessTypeConstant(
                getUnderlyingType(), Access.PRIVATE).ensureTypeInfo(errs);
        Map<PropertyConstant, PropertyInfo> mapProps   = new HashMap<>();
        Map<MethodConstant  , MethodInfo  > mapMethods = new HashMap<>();

        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoPri.getProperties().entrySet())
            {
            if (entry.getValue().hasField())
                {
                mapProps.put(entry.getKey(), entry.getValue());
                }
            }

        for (Map.Entry<MethodConstant, MethodInfo> entry : infoPri.getMethods().entrySet())
            {
            if (entry.getValue().isFunction())
                {
                mapMethods.put(entry.getKey(), entry.getValue());
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
                    assert !typeContrib.isAccessSpecified();
                    TypeInfo infoContrib = pool.ensureAccessTypeConstant(typeContrib, Access.STRUCT)
                            .ensureTypeInfoInternal(errs);
                    if (infoContrib == null)
                        {
                        fIncomplete = true;
                        }
                    else
                        {
                        assert mapProps.keySet().containsAll(infoContrib.getProperties().keySet());
                        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoContrib.getProperties().entrySet())
                            {
                            if (!mapProps.containsKey(entry.getKey()))
                                {
                                mapProps.put(entry.getKey(), entry.getValue());
                                }
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
                mapProps, mapMethods, fIncomplete ? Progress.Incomplete : Progress.Complete);
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
            Map<String, ParamInfo> mapTypeParams,
            ErrorListener          errs)
        {
        TypeResolver resolver = new TypeResolver(mapTypeParams, errs);

        // obtain the type parameters encoded in this type constant
        TypeConstant[] atypeParams = getParamTypesArray();
        int            cTypeParams = atypeParams.length;

        // obtain the type parameters declared by the class
        List<Entry<StringConstant, TypeConstant>> listClassParams = struct.getTypeParamsAsList();
        int                                       cClassParams    = listClassParams.size();
        if (isTuple())
            {
            // warning: turtles
            ParamInfo param = new ParamInfo("ElementTypes", this, this);
            mapTypeParams.put(param.getName(), param);
            }
        else
            {
            if (cTypeParams  > cClassParams)
                {
                if (cClassParams == 0)
                    {
                    log(errs, Severity.ERROR, VE_TYPE_PARAMS_UNEXPECTED, constId.getPathString());
                    }
                else
                    {
                    log(errs, Severity.ERROR, VE_TYPE_PARAMS_WRONG_NUMBER,
                            constId.getPathString(), cClassParams, cTypeParams);
                    }
                }

            if (cClassParams > 0)
                {
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
                            }
                        }

                    mapTypeParams.put(sName, new ParamInfo(sName, typeConstraint, typeActual));
                    }
                }
            }

        return resolver;
        }

    /**
     * Fill in the passed list of contributions to process, and also collect a list of all the
     * annotations.
     *
     * @param constId         the identity constant of the class that the type is based on
     * @param struct          the structure of the class that the type is based on
     * @param listProcess     a list of contributions, which will be filled by this method in the
     *                        order that they should be processed
     * @param listClassAnnos  a list of annotations, which will be filled by this method
     * @param resolver        the GenericTypeResolver for the type
     * @param errs            the error list to log to
     *
     * @return an array containing the "into", "extends" and "rebase" types
     */
    private TypeConstant[] createContributionList(
            IdentityConstant    constId,
            ClassStructure      struct,
            List<Contribution>  listProcess,
            List<Annotation>    listClassAnnos,
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
                    Annotation   annotation = ((AnnotatedTypeConstant) typeClass).getAnnotation();
                    TypeConstant typeMixin  = annotation.getAnnotationType();
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
                    TypeConstant typeInto = typeMixin.getExplicitClassInto();
                    if (typeInto.isIntoClassType())
                        {
                        // check for duplicate class annotation
                        if (listClassAnnos.stream().anyMatch(annoPrev ->
                                annoPrev.getAnnotationClass().equals(annotation.getAnnotationClass())))
                            {
                            log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                                    constId.getPathString(), annotation.getAnnotationClass().getValueString());
                            }
                        else
                            {
                            listClassAnnos.add(annotation);
                            }
                        continue;
                        }

                    // the mixin has to be able to apply to the remainder of the type constant chain
                    if (!typeClass.getUnderlyingType().isA(typeInto))
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                                typeClass.getUnderlyingType().getValueString(),
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
                if (listClassAnnos.stream().anyMatch(annoPrev ->
                        annoPrev.getAnnotationClass().equals(annotation.getAnnotationClass())))
                    {
                    log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                            constId.getPathString(), annotation.getAnnotationClass().getValueString());
                    }
                else
                    {
                    listClassAnnos.add(annotation);
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
                // an interface implies the set of methods present in Object
                // (use the "Into" composition to make the Object methods implicit-only, as opposed
                // to explicitly being present in this interface)
                typeInto = pool.typeObject();
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
            TypeConstant typeContrib = contrib.resolveGenerics(resolver);

            switch (contrib.getComposition())
                {
                case Annotation:
                    log(errs, Severity.ERROR, VE_ANNOTATION_ILLEGAL,
                            contrib.getTypeConstant().getValueString(),
                            constId.getPathString());
                    break;

                case Into:
                    // only applicable on a mixin, only one allowed, and it should have been earlier
                    // in the list of contributions
                    log(errs, Severity.ERROR, VE_INTO_UNEXPECTED,
                            contrib.getTypeConstant().getValueString(),
                            constId.getPathString());
                    break;

                case Extends:
                    // not applicable on an interface, only one allowed, and it should have been
                    // earlier in the list of contributions
                    log(errs, Severity.ERROR, VE_EXTENDS_UNEXPECTED,
                            contrib.getTypeConstant().getValueString(),
                            constId.getPathString());
                    break;

                case Incorporates:
                    {
                    if (struct.getFormat() == Component.Format.INTERFACE)
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_UNEXPECTED,
                                contrib.getTypeConstant().getValueString(),
                                constId.getPathString());
                        break;
                        }

                    if (typeContrib == null)
                        {
                        // the type contribution does not apply conditionally to "this" type
                        continue NextContrib;
                        }

                    if (!typeContrib.isExplicitClassIdentity(true))
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_NOT_CLASS,
                                contrib.getTypeConstant().getValueString(),
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
                                contrib.getTypeConstant().getValueString(),
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
                                contrib.getTypeConstant().getValueString(),
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
            Map<String, ParamInfo>            mapTypeParams,
            List<Contribution>                listProcess,
            ListMap<IdentityConstant, Origin> listmapClassChain,
            ListMap<IdentityConstant, Origin> listmapDefaultChain,
            ErrorListener                     errs)
        {
        boolean fIncomplete = false;

        ConstantPool pool = getConstantPool();
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
                    (struct.getFormat() == Component.Format.INTERFACE
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
                    {
                    // append to the call chain
                    TypeConstant typeContrib = contrib.getTypeConstant(); // already resolved generics!
                    TypeInfo     infoContrib = typeContrib.ensureTypeInfoInternal(errs);
                    if (infoContrib == null)
                        {
                        // skip this one (it has been deferred)
                        fIncomplete = true;
                        break;
                        }

                    infoContrib.contributeChains(listmapClassChain, listmapDefaultChain, compContrib);

                    // collect type parameters
                    for (ParamInfo paramNew : infoContrib.getTypeParams().values())
                        {
                        String    sParam   = paramNew.getName();
                        ParamInfo paramOld = mapTypeParams.get(sParam);
                        if (paramOld == null)
                            {
                            mapTypeParams.put(sParam, paramNew);
                            }
                        else
                            {
                            // check that everything matches between the old and new parameter
                            if (paramNew.isActualTypeSpecified() != paramOld.isActualTypeSpecified())
                                {
                                if (paramOld.isActualTypeSpecified())
                                    {
                                    log(errs, Severity.ERROR, VE_TYPE_PARAM_CONTRIB_NO_SPEC,
                                            this.getValueString(), sParam,
                                            paramOld.getActualType().getValueString(),
                                            typeContrib.getValueString());
                                    }
                                else
                                    {
                                    log(errs, Severity.ERROR, VE_TYPE_PARAM_CONTRIB_HAS_SPEC,
                                            this.getValueString(), sParam,
                                            typeContrib.getValueString(),
                                            paramNew.getActualType().getValueString());
                                    }
                                }
                            else if (!paramNew.getActualType().equals(paramOld.getActualType()))
                                {
                                log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_CONTRIB,
                                        this.getValueString(), sParam,
                                        paramOld.getActualType().getValueString(),
                                        typeContrib.getValueString(),
                                        paramNew.getActualType().getValueString());
                                }
                            }
                        }
                    }
                    break;

                case Into:
                    // "into" contains only implicit methods, so it is not part of a chain;
                    // "into" does not contribute type parameters
                    break;

                default:
                    throw new IllegalStateException("composition=" + compContrib);
                }
            }

        return !fIncomplete;
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
     * @param errs                 the error list to log any errors to
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
            ErrorListener                       errs)
        {
        boolean fIncomplete = false;

        ConstantPool pool = getConstantPool();
        for (Contribution contrib : listProcess)
            {
            Map<PropertyConstant, PropertyInfo> mapContribProps;
            Map<MethodConstant  , MethodInfo  > mapContribMethods;

            Composition composition = contrib.getComposition();
            if (composition == Composition.Equal)
                {
                mapContribProps   = new HashMap<>();
                mapContribMethods = new HashMap<>();
                createMemberInfo(constId, struct.getFormat() == Component.Format.INTERFACE, struct,
                        resolver, mapContribProps, mapContribMethods, errs);
                }
            else
                {
                TypeConstant typeContrib = contrib.getTypeConstant();
                TypeInfo     infoContrib = typeContrib.ensureTypeInfoInternal(errs);
                if (infoContrib == null)
                    {
                    fIncomplete = true;
                    continue;
                    }

                mapContribProps   = infoContrib.getProperties();
                mapContribMethods = infoContrib.getMethods();

                // collect all of the IdentityConstants in the potential call chain that map to this
                // particular contribution
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

            // process properties
            for (Entry<PropertyConstant, PropertyInfo> entry : mapContribProps.entrySet())
                {
                PropertyConstant idSuper   = entry.getKey();
                PropertyInfo     propSuper = entry.getValue();

                // constant properties and private properties are always "fully scoped", so there
                // will be no a collision there; other properties may have a property both in the
                // previously-collected properties and the to-be-contributed properties, so check
                // if there is a match
                PropertyConstant idSub;
                if (!propSuper.isConstant() && propSuper.getRefAccess() != Access.PRIVATE
                        && (idSub = findSubProperty(idSuper, mapProps)) != null)
                    {
                    PropertyInfo propSub  = mapProps.get(idSub);
                    PropertyInfo propPrev = mapProps.put(idSub, propSub.append(propSuper, errs));
                    assert propPrev != null;
                    }
                else
                    {
                    PropertyInfo propPrev = mapProps.put(idSuper, propSuper);
                    assert propPrev == null;
                    }
                }

            // first find the "super" chains of each of the existing methods
            Set<MethodConstant> setSuperMethods = new HashSet<>();
            for (Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
                {
                MethodInfo method = entry.getValue();
                if (method.isFunction())
                    {
                    // functions don't have chains
                    continue;
                    }

                SignatureConstant sigSub = method.getSubSignature();
                if (sigSub == null)
                    {
                    // the chain is "terminated"
                    continue;
                    }

                // TODO need an "exact match" or "substitutable match" based on whether the last body is marked as @Override
                boolean fOverride = method.getTail().findAnnotation(pool.clzOverride()) != null;

                MethodConstant idSuper = findSuperMethod(sigSub, mapContribMethods, errs);
                if (idSuper == null)
                    {
                    // the chain doesn't have a "super" from this contribution
                    continue;
                    }

                entry.setValue(method.apply(contrib, mapContribMethods.get(idSuper)));
                setSuperMethods.add(idSuper);
                }

            // sweep over the remaining chains
            for (Entry<MethodConstant, MethodInfo> entry : mapContribMethods.entrySet())
                {
                if (!setSuperMethods.contains(entry.getKey()))
                    {
                    mapMethods.put(entry.getKey(), entry.getValue());
                    }
                }
            }

        return !fIncomplete;
        }

    /**
     * Find a corresponding property in the passed map of properties that corresponds to the passed
     * identity.
     *
     * @param idSuper      an identity being contributed to a property chain
     * @param mapSubProps  a map of existing property chains as they are being constructed
     *
     * @return the identity of the property in the passed map that would be the assumed "sub" of the
     *         property identified by {@code idSuper}
     */
    protected PropertyConstant findSubProperty(
            PropertyConstant                    idSuper,
            Map<PropertyConstant, PropertyInfo> mapSubProps)
        {
        if (mapSubProps.containsKey(idSuper))
            {
            return idSuper;
            }

        String sName  = idSuper.getName();
        int    cDepth = idSuper.depthFromClass();
        NextProperty: for (Entry<PropertyConstant, PropertyInfo> entry : mapSubProps.entrySet())
            {
            PropertyConstant idSub = entry.getKey();
            if (idSub.getName().equals(sName) && idSub.depthFromClass() == cDepth)
                {
                if (cDepth > 1)
                    {
                    // verify sameness of path (only nesting that would be allowed to match is
                    // properties within properties, since properties within methods MUST be
                    // private)
                    IdentityConstant idSubParent   = idSub  .getParentConstant();
                    IdentityConstant idSuperParent = idSuper.getParentConstant();
                    for (int i = 1; i < cDepth; ++i)
                        {
                        if ( !(    idSubParent   instanceof PropertyConstant
                                && idSuperParent instanceof PropertyConstant
                                && idSub.getName().equals(idSuperParent.getName())))
                            {
                            continue NextProperty;
                            }

                        idSubParent   = idSubParent  .getParentConstant();
                        idSuperParent = idSuperParent.getParentConstant();
                        }
                    }
                return idSub;
                }
            }

        return null;
        }

    /**
     * Find the method that would be the "super" of the specified method signature.
     *
     * @param sigSub      the method that is searching for a super
     * @param mapMethods  the possible super methods to select from
     * @param errs        the error list to log any errors to
     *
     * @return a SignatureConstant for the super method, or null if there is no one unambiguously
     *         "best" super method signature to be found
     */
    protected MethodConstant findSuperMethod(
            SignatureConstant               sigSub,
            Map<MethodConstant, MethodInfo> mapMethods,
            ErrorListener                   errs)
        {
        // brute force search
        MethodConstant       idBest    = null;
        List<MethodConstant> listMatch = null;
        for (MethodConstant idCandidate : mapMethods.keySet())
            {
            if (idCandidate.getSignature().equals(sigSub))
                {
                return idCandidate;
                }

            if (idCandidate.getSignature().isSubstitutableFor(sigSub, this))
                {
                if (listMatch == null)
                    {
                    if (idBest == null)
                        {
                        idBest = idCandidate;
                        }
                    else
                        {
                        // we've got at least 2 matches, so we'll need to compare them all
                        listMatch = new ArrayList<>();
                        listMatch.add(idBest);
                        listMatch.add(idCandidate);
                        idBest = null;
                        }
                    }
                else
                    {
                    listMatch.add(idCandidate);
                    }
                }
            }

        // if none match, then there is no match; if only 1 matches, then use it
        if (listMatch == null)
            {
            return idBest;
            }

        // if multiple candidates exist, then one must be obviously better than the rest
        SignatureConstant sigBest = null;
        nextCandidate: for (int iCur = 0, cCandidates = listMatch.size();
                iCur < cCandidates; ++iCur)
            {
            MethodConstant    idCandidate  = listMatch.get(iCur);
            SignatureConstant sigCandidate = idCandidate.getSignature();
            if (idBest == null) // that means that "best" is ambiguous thus far
                {
                // have to back-test all the ones in front of us to make sure that
                for (int iPrev = 0; iPrev < iCur; ++iPrev)
                    {
                    SignatureConstant sigPrev = listMatch.get(iPrev).getSignature();
                    if (!sigPrev.isSubstitutableFor(sigCandidate, this))
                        {
                        // still ambiguous
                        continue nextCandidate;
                        }
                    }

                // so far, this candidate is the best
                idBest  = idCandidate;
                sigBest = sigCandidate;
                }
            else if (sigBest.isSubstitutableFor(sigCandidate, this))
                {
                // this assumes that "best" is a transitive concept, i.e. we're not going to back-
                // test all of the other candidates
                idBest  = idCandidate;
                sigBest = sigCandidate;
                }
            else if (!sigCandidate.isSubstitutableFor(sigBest, this))
                {
                idBest  = null;
                sigBest = null;
                }
            }

        if (idBest == null)
            {
            log(errs, Severity.ERROR, VE_SUPER_AMBIGUOUS, sigSub.getValueString());
            }

        return idBest;
        }

    /**
     * Generate the members of the "this" class of "this" type.
     *
     * @param constId           the identity of the class (used for logging error information)
     * @param fInterface        if the class is an interface type
     * @param struct            the class structure, property structure, or method structure REVIEW or typedef?
     * @param resolver          the GenericTypeResolver that uses the known type parameters
     * @param mapProps          the properties of the class
     * @param mapMethods        the methods of the class
     * @param errs              the error list to log any errors to
     */
    private void createMemberInfo(
            IdentityConstant                    constId,
            boolean                             fInterface,
            Component                           struct,
            ParamInfo.TypeResolver              resolver,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo  > mapMethods,
            ErrorListener                       errs)
        {
        if (struct instanceof MethodStructure)
            {
            MethodStructure   method = (MethodStructure) struct;
            MethodConstant    id     = method.getIdentityConstant();
            SignatureConstant sig    = id.getSignature().resolveGenericTypes(resolver);
            MethodBody body = new MethodBody(id,
                    method.isAbstract() ? Implementation.Declared :
                    fInterface          ? Implementation.Default  :
                    method.isNative()   ? Implementation.Native   :
                                          Implementation.Explicit  );
            mapMethods.put(id, new MethodInfo(sig, body));
            }
        else if (struct instanceof PropertyStructure)
            {
            PropertyStructure prop = (PropertyStructure) struct;
            PropertyConstant  id   = prop.getIdentityConstant();
            mapProps.put(id, prop.isTypeParameter()
                    ? new PropertyInfo(new PropertyBody(prop, resolver.parameters.get(id.getName())))
                    : createPropertyInfo(prop, constId, fInterface, resolver, errs));
            }

        // recurse through children
        for (Component child : struct.ensureChildByNameMap().values())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : child.getMethodByConstantMap().values())
                    {
                    createMemberInfo(constId, fInterface, method, resolver, mapProps, mapMethods, errs);
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                createMemberInfo(constId, fInterface, child, resolver, mapProps, mapMethods, errs);
                }
            }
        }

    /**
     * Create the PropertyInfo for the specified property.
     *
     * @param prop        the PropertyStructure
     * @param constId     the identity of the containing structure (used only for error messages)
     * @param fInterface  true if the type is an interface, not a class or mixin
     * @param resolver    the GenericTypeResolver that uses the known type parameters
     * @param errs        the error list to log any errors to
     *
     * @return a new PropertyInfo for the passed PropertyStructure
     */
    private PropertyInfo createPropertyInfo(
            PropertyStructure       prop,
            IdentityConstant        constId,
            boolean                 fInterface,
            ParamInfo.TypeResolver  resolver,
            ErrorListener           errs)
        {
        ConstantPool pool      = getConstantPool();
        String       sName     = prop.getName();

        // scan the Property annotations
        Annotation[] aPropAnno    = prop.getPropertyAnnotations();
        boolean      fHasRO       = false;
        boolean      fHasAbstract = false;
        boolean      fHasOverride = false;
        for (int i = 0, c = aPropAnno.length; i < c; ++i)
            {
            Annotation annotation = aPropAnno[i];
            Constant   constMixin = annotation.getAnnotationClass();
            if (scanForDups(aPropAnno, i, constMixin))
                {
                todoLogError("duplicate annotation " + constMixin.getValueString()
                        + " on " + constId.getValueString());
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
        boolean      fHasInject  = false;
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

            TypeConstant typeInto = typeMixin.getExplicitClassInto();
            if (!typeInto.isIntoPropertyType())
                {
                log(errs, Severity.ERROR, VE_PROPERTY_ANNOTATION_INCOMPATIBLE,
                        sName, constId.getValueString(), typeMixin.getValueString());
                continue;
                }

            // we've already processed the "into Property" annotations, so this has to be an
            // "into Ref" (or some sub-class of Ref, e.g. Var) annotation
            assert typeInto.isA(pool.typeRef());

            if (scanForDups(aRefAnno, i, constMixin))
                {
                todoLogError("duplicate annotation " + constMixin.getValueString()
                        + " on " + constId.getValueString());
                }

            fHasRefAnno   = true;
            fHasVarAnno  |= typeInto.isA(pool.typeVar());
            fHasInject   |= constMixin.equals(pool.clzInject());
            }

        // functions and constants cannot have properties; methods cannot have constants
        IdentityConstant constParent = prop.getIdentityConstant().getParentConstant();
        boolean          fConstant   = prop.isStatic();
        switch (constParent.getFormat())
            {
            case Property:
                if (prop.getParent().isStatic())
                    {
                    todoLogError("a constant property cannot contain properties or constants");
                    }
                break;

            case Method:
                // "static" properties inside a method are just an indication that the Ref/Var is
                // is a property of the containing class
                fConstant = false;
                if (prop.getParent().isStatic())
                    {
                    todoLogError("a function cannot contain properties");
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
        MethodStructure  methodInit   = null;
        MethodStructure  methodGet    = null;
        MethodStructure  methodSet    = null;
        MethodStructure  methodBadGet = null;
        MethodStructure  methodBadSet = null;
        boolean          fCustomCode  = false;
        for (Component child : prop.getChildByNameMap().values())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : child.getMethodByConstantMap().values())
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
                    fCustomCode = !method.isAbstract() && !method.isStatic();
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
                todoLogError("@Override illegal on constant " + sName);
//                log(errs, Severity.ERROR, VE_CONST_OVERRIDE_ILLEGAL,
//                        getValueString(), sName);
                }

            if (fHasRefAnno)
                {
                // it is an error for a constant to be annotated in a manner that affects the Ref
                todoLogError("Ref/Var annotation illegal on constant " + sName);
//                log(errs, Severity.ERROR, VE_CONST_ANNOTATION_ILLEGAL,
//                        getValueString(), sName);
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
            impl = Implementation.Declared;

            if (fCustomCode)
                {
                // interface is not allowed to implement a property - REVIEW: GG wants to allow @RO get()
                log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_IMPLEMENTED,
                        getValueString(), sName);
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
                todoLogError("@Abstract interface property illegal");
                // log(errs, Severity.ERROR, VE_INTERFACE_PROPERTY_ABSTRACT_ILLEGAL,
                //         getValueString(), sName);
                }

            fRO      |= fHasRO;
            fRW      |= accessVar != null;
            fField    = false;
            }
        else
            {
            impl = Implementation.Explicit;

            // determine if the get explicitly calls super, or explicitly blocks super
            boolean fGetSupers      = methodGet != null && methodGet.usesSuper();
            boolean fSetSupers      = methodSet != null && methodSet.usesSuper();
            boolean fGetBlocksSuper = methodGet != null && !methodGet.isAbstract() && !fGetSupers;

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

            if (fHasInject && (fSetSupers || fHasVarAnno))
                {
                // the @Inject conflicts with the annotations that require a Var
                log(errs, Severity.ERROR, VE_PROPERTY_INJECT_NOT_VAR,
                        getValueString(), sName);
                }

            // we assume a field if @Inject is not specified, @RO is not specified,
            // @Override is not specified, and get() doesn't block going to its super
            fField = !fHasInject && !fHasRO && !fHasAbstract && !fHasOverride && !fGetBlocksSuper;

            // we assume Ref-not-Var if @RO is specified, or if there is a get() with no
            // super and no set() (or Var-implying annotations)
            fRO |= !fHasVarAnno && (fHasRO || (fGetBlocksSuper && methodSet == null));

            fRW |= fHasVarAnno | accessVar != null | methodSet != null;
            }

        if (fRO && fRW)
            {
            log(errs, Severity.ERROR, VE_PROPERTY_READWRITE_READONLY,
                    getValueString(), sName);
            fRO = false;
            }

        return new PropertyInfo(new PropertyBody(prop, impl, null,
                prop.getType().resolveGenerics(resolver), fRO, fRW, fCustomCode, fField, fConstant,
                prop.getInitialValue(), methodInit == null ? null : methodInit.getIdentityConstant()));
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
     * This step is used to finalize the processing for all of the member information that has been
     * collected. For example, some decisions are deferred until the information is all present,
     * such as whether a field is required for a property that may or may not need one.
     *
     * @param constId           the identity of the class
     * @param struct            the class structure
     * @param fAbstract         true if the type is abstract
     * @param mapProps          the properties of the class
     * @param mapMethods        the methods of the class
     * @param errs              the error list to log any errors to
     */
    private void finalizeMemberInfo(
            IdentityConstant                    constId,
            ClassStructure                      struct,
            boolean                             fAbstract,
            Map<PropertyConstant, PropertyInfo> mapProps,
            Map<MethodConstant  , MethodInfo  > mapMethods,
            ErrorListener                       errs)
        {
        // TODO methods? check for trailing override?

        Component.Format formatInfo = struct.getFormat();
        for (Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
            {
            PropertyInfo propinfo = entry.getValue();
            if (formatInfo != Component.Format.INTERFACE && formatInfo != Component.Format.MIXIN
                    && propinfo.isOverride())
                {
                log(errs, Severity.ERROR, VE_PROPERTY_OVERRIDE_NO_SPEC,
                        getValueString(), propinfo.getName());

                // erase the "override" flag, now that we've reported it
                entry.setValue(propinfo = propinfo.suppressOverride());
                }

            // for properties on a non-abstract class that come from an interface, decide which ones
            // need a field
// TODO this is wrong ... it should be "if not an interface and does not have a field and is not explicitly abstract property ..."
            if (!fAbstract && propinfo.getFirstBody().getImplementation() == Implementation.Declared)
                {
                // determine whether or not the property needs a field
                boolean fField;
                if (propinfo.isInjected() || propinfo.isOverride()) // REVIEW @Inject into Ref, needs to be overrideable
                    {
                    // injection does not use a field, and override can defer the choice
                    fField = false;
                    }
                else if (!propinfo.isCustomLogic() && propinfo.getRefAnnotations().length == 0)
                    {
                    // no logic implies that there is an underlying field
                    fField = true;
                    }
                else
                    {
                    // determine if get() blocks the super call to the field
                    MethodInfo methodinfo = mapMethods.get(propinfo.getGetterId());   // REVIEW
                    fField = true;
                    if (methodinfo != null)
                        {
                        for (MethodBody body : methodinfo.getChain())
                            {
                            if (body.getImplementation() == Implementation.Property)
                                {
                                break;
                                }

                            if (body.blocksSuper())
                                {
                                fField = false;
                                break;
                                }
                            }
                        }
                    }

                // erase the "abstract" flag, and store the result of the field-is-required
                // calculation
                if (fField)
                    {
                    entry.setValue(propinfo = propinfo.requireField());
                    }
                }
            }
        }

    /**
     * Verify that properties exist for each of the type parameters.
     *
     * @param mapTypeParams  the map containing all of the type parameters
     * @param mapProps       the public and protected properties of the class
     * @param errs           the error list to log any errors to
     */
    private void checkTypeParameterProperties(
            Map<String, ParamInfo>              mapTypeParams,
            Map<PropertyConstant, PropertyInfo> mapProps,
            ErrorListener                       errs)
        {
        ConstantPool pool       = getConstantPool();
        for (ParamInfo param : mapTypeParams.values())
            {
            String  sParam = param.getName();
            boolean fFound = false;
            for (PropertyInfo prop : mapProps.values())
                {
                if (prop.getName().equals(sParam))
                    {
                    if (fFound)
                        {
                        // TODO
                        todoLogError("duplicate?!?! type param prop: " + sParam);
                        }
                    else if (prop.isTypeParam() && prop.getType().equals(
                            pool.ensureParameterizedTypeConstant(pool.typeType(), param.getConstraintType())))
                        {
                        fFound = true;
                        }
                    else
                        {
                        log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_INCOMPATIBLE,
                                this.getValueString(), sParam);
                        }
                    }
                }
            if (!fFound)
                {
                log(errs, Severity.ERROR, VE_TYPE_PARAM_PROPERTY_MISSING,
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
        return this.ensureTypeInfo().findConversion(that);
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
        return equals(getConstantPool().typeClass());
        }

    /**
     * @return true iff this type can be used in an "into" clause for a mixin for a property, which
     *         means that the mix-in applies to the meta-data of the property or to the Ref/Var
     *         instance used for the property
     */
    public boolean isIntoPropertyType()
        {
        ConstantPool pool = getConstantPool();
        return equals(pool.typeProperty()) || isA(pool.typeRef());
        }

    /**
     * @return true iff this type can be used in an "into" clause for a mixin for a method, which
     *         means that the mix-in applies to the meta-data of the method
     */
    public boolean isIntoMethodType()
        {
        return equals(getConstantPool().typeMethod());
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