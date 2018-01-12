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

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.ParamInfo.TypeResolver;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

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
        return getActualParamType(constProperty.getName());
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
     * Find the type of the specified formal parameter for this actual type.
     *
     * @param sName  the formal parameter name
     *
     * @return the corresponding actual type
     */
    public TypeConstant getActualParamType(String sName)
        {
        // TODO: use the type info when done
        if (isSingleDefiningConstant())
            {
            ClassStructure clz = (ClassStructure)
                ((ClassConstant) getDefiningConstant()).getComponent();
            TypeConstant type = clz.getActualParamType(sName, getParamTypes());
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
// TODO remove
System.out.println(m_typeinfo);
            }

        return m_typeinfo;
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
        if (!isSingleDefiningConstant())
            {
            return ((TypeConstant) simplify()).buildTypeInfo(errs);
            }

        // load the class structure for the type
        IdentityConstant constId;
        ClassStructure   struct;
        try
            {
            // TODO Property, Register, and auto-narrowing types?
            constId = (IdentityConstant) ((TypeConstant) simplify()).getDefiningConstant();
            struct  = (ClassStructure) constId.getComponent();
            }
        catch (Exception e)
            {
            System.err.println("** Unable to get underlying class for " + getValueString());
            return new TypeInfo(this, Component.Format.INTERFACE, Collections.EMPTY_MAP,
                    getConstantPool().typeObject(), null, getConstantPool().typeObject(),
                    new ListMap<>(), new ListMap<>(),
                    Collections.EMPTY_MAP, Collections.EMPTY_MAP);
            // TODO CP throw new IllegalStateException("Unable to get underlying class for " + getValueString(), e);
            }

        // we're going to build a map from name to param info, including whatever parameters are
        // specified by this class/interface, but also each of the contributing classes/interfaces
        Map<String, ParamInfo> mapTypeParams = new HashMap<>();
        TypeResolver           resolver      = new TypeResolver(mapTypeParams, errs);

        // obtain the type parameters encoded in this type constant
        ConstantPool   pool        = getConstantPool();
        TypeConstant[] atypeParams = getParamTypesArray();
        int            cTypeParams = atypeParams.length;
        boolean        fTuple      = isTuple();

        // obtain the type parameters declared by the class
        List<Entry<StringConstant, TypeConstant>> listClassParams = struct.getTypeParamsAsList();
        int                                       cClassParams    = listClassParams.size();
        if (fTuple)
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

        // walk through each of the contributions, starting from the implied contributions that are
        // represented by annotations in this type constant itself, followed by the annotations in
        // the class structure, followed by the class structure (as its own pseudo-contribution),
        // followed by the remaining contributions
        List<Contribution> listProcess = new ArrayList<>();
        Component.Format   formatInfo  = struct.getFormat();

        // glue any annotations from the type constant onto the front of the contribution list
        // (and remember the type of the annotated class)
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
                                constId.getPathString(), typeMixin.getValueString());
                        continue;
                        }

                    // the mixin has to be able to apply to the remainder of the type constant chain
                    TypeInfo infoMixin = typeMixin.ensureTypeInfo(errs);
                    if (!typeClass.getUnderlyingType().isA(infoMixin.getInto()))
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                                typeClass.getUnderlyingType().getValueString(),
                                typeMixin.getValueString(),
                                infoMixin.getInto().getValueString());
                        continue;
                        }

                    // apply annotation
                    listProcess.add(new Contribution(annotation));
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
        NextContrib: for ( ; iContrib < cContribs; ++iContrib)
            {
            // only process annotations
            Contribution contrib = listContribs.get(iContrib);
            if (contrib.getComposition() != Composition.Annotation)
                {
                // ... all done processing annotations; move to the next stage
                break NextContrib;
                }

            // has to be an explicit class identity
            TypeConstant typeMixin = contrib.getTypeConstant();
            if (!typeMixin.isExplicitClassIdentity(false))
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_NOT_CLASS,
                        constId.getPathString(), typeMixin.getValueString());
                continue NextContrib;
                }

            // has to be a mixin
            if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_NOT_MIXIN,
                        constId.getPathString(), typeMixin.getValueString());
                continue NextContrib;
                }

            // the mixin has to apply to this type
            TypeInfo infoMixin = typeMixin.ensureTypeInfo(errs);
            if (!typeClass.isA(infoMixin.getInto())) // note: not 100% correct because the presence of this mixin may affect the answer
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                        typeClass.getValueString(),
                        typeMixin.getValueString(),
                        infoMixin.getInto().getValueString());
                continue NextContrib;
                }

            listProcess.add(contrib);
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
                IdentityConstant constExtends = typeExtends.getSingleUnderlyingClass();
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
                    // clause
                    TypeInfo     infoMixin   = typeContrib.ensureTypeInfo(errs);
                    TypeConstant typeRequire = infoMixin.getInto();
                    if (typeRequire != null && !this.isA(typeRequire)) // note: not 100% correct because the presence of this mixin may affect the answer
                        {
                        log(errs, Severity.ERROR, VE_INCORPORATES_INCOMPATIBLE,
                                constId.getPathString(),
                                contrib.getTypeConstant().getValueString(),
                                this.getValueString(),
                                typeRequire.getValueString());
                        break;
                        }

                    listProcess.add(new Contribution(Composition.Incorporates, typeContrib));
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

                    listProcess.add(new Contribution(typeContrib, contrib.getDelegatePropertyConstant()));
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

                    listProcess.add(new Contribution(Composition.Implements, typeContrib));
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
            listProcess.add(new Contribution(Composition.RebasesOnto, typeRebase));
            }
        if (typeExtends != null)
            {
            listProcess.add(new Contribution(Composition.Extends, typeExtends));
            }
        if (typeInto != null)
            {
            listProcess.add(new Contribution(Composition.Into, typeInto));
            }

        // 1) build the "potential call chains" (basically, the order in which we would search for
        //    methods to call in a virtual manner)
        // 2) collect all of the type parameter data from the various contributions
        ListMap<IdentityConstant, Boolean> listmapClassChain   = new ListMap<>();
        ListMap<IdentityConstant, Boolean> listmapDefaultChain = new ListMap<>();
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
                    if (formatInfo == Component.Format.INTERFACE)
                        {
                        listmapDefaultChain.put(constId, true);
                        }
                    else
                        {
                        listmapClassChain.put(constId, true);
                        }

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
                    TypeInfo     infoContrib = typeContrib.ensureTypeInfo(errs);
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

        // next, we need to process the list of contributions in order, asking each for its
        // properties and methods, and collecting all of them
        Map<String           , PropertyInfo> mapProperties = new HashMap<>();
        Map<SignatureConstant, MethodInfo  > mapMethods    = new HashMap<>();
        for (Contribution contrib : listProcess)
            {
            Map<String           , PropertyInfo> mapContribProperties;
            Map<SignatureConstant, MethodInfo  > mapContribMethods;
            Composition composition = contrib.getComposition();
            if (composition == Composition.Equal)
                {
                // add the properties and methods from "struct"
                // if this is an interface, then these are "abstract" or "default" methods
                // otherwise these are added to the primary chain
                mapContribProperties = new HashMap<>();
                mapContribMethods    = new HashMap<>();
                for (Entry<String, Component> entryChild : struct.ensureChildByNameMap().entrySet())
                    {
                    String    sName = entryChild.getKey();
                    Component child = entryChild.getValue();
                    if (child instanceof MultiMethodStructure)
                        {
                        for (MethodStructure structMethod : child.getMethodByConstantMap().values())
                            {
                            SignatureConstant constSig = structMethod.getIdentityConstant()
                                    .getSignature().resolveGenericTypes(resolver);
                            MethodBody body = new MethodBody(structMethod.getIdentityConstant(),
                                    structMethod.isAbstract()                ? Implementation.Declared :
                                    formatInfo == Component.Format.INTERFACE ? Implementation.Default  :
                                    structMethod.isNative()                  ? Implementation.Native   :
                                                                               Implementation.Explicit  );
                            mapContribMethods.put(constSig, new MethodInfo(constSig, body));
                            }
                        }
                    else if (child instanceof PropertyStructure)
                        {
                        mapContribProperties.put(sName, new PropertyInfo(constId, (PropertyStructure) child));
                        }
                    }
                }
            else
                {
                TypeInfo infoContrib = contrib.getTypeConstant().getTypeInfo();
                mapContribProperties = infoContrib.getProperties();
                mapContribMethods    = infoContrib.getMethods();
                }

            contributeMembers(mapProperties, mapMethods,
                    composition, mapContribProperties, mapContribMethods,
                    contrib.getComposition() == Composition.Delegates ? contrib.getDelegatePropertyConstant() : null,
                    errs);
            }

        return new TypeInfo(this, formatInfo, mapTypeParams, typeExtends, typeRebase, typeInto, listmapClassChain, listmapDefaultChain, mapProperties, mapMethods);
        }

    protected void contributeMembers(
            Map<String, PropertyInfo>          mapProperties,
            Map<SignatureConstant, MethodInfo> mapMethods,
            Composition                        compAdd,
            Map<String, PropertyInfo>          mapAddProperties,
            Map<SignatureConstant, MethodInfo> mapAddMethods,
            PropertyConstant                   propDelegate,
            ErrorListener                      errs)
        {
        // first find the "super" chains of each of the existing methods
        Set<SignatureConstant> setSuperMethods = new HashSet<>();
        for (Entry<SignatureConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            // TODO for this method, find the super, add it to the method info, add the sig to the setSuperMethods
            // mapMethods.put(entry.getKey(), )
            }

        // sweep over the remaining chains
        for (Entry<SignatureConstant, MethodInfo> entry : mapAddMethods.entrySet())
            {
            if (!setSuperMethods.contains(entry.getKey()))
                {
                // TODO for this method, find the super, add it to the method info, add the sig to the setSuperMethods
                mapMethods.put(entry.getKey(), entry.getValue());
                }
            }

//        switch (compAdd)
//            {
//            case Equal:
//                break;
//
//            case Implements:
//                // each property/method in the contrib type need to be declared abstract/default
//                // TODO need a way to say "use just the abstract/default part"
//                break;
//
//            case Delegates:
//                // each property/method in the contrib type need to be delegated (in the primary call chains)
//                // TODO
//                break;
//
//            case Into:
//                // each property/method in the contrib type need to be declared "implicit"
//                // TODO
//                break;
//
//            }
//            TypeConstant typeContrib = contrib.getTypeConstant();
//            TypeInfo     infoContrib = typeContrib.ensureTypeInfo(errs);
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
     * @param clz
     */
    public <T extends TypeConstant> T findFirst(Class<T> clz)
        {
        return clz == getClass() ? (T) this : getUnderlyingType().findFirst(clz);
        }


    // ----- run-time support ----------------------------------------------------------------------

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