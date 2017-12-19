package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ClassStructure.SimpleTypeResolver;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Component.Format;
import org.xvm.asm.CompositeComponent;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.runtime.TypeSet;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents a type that is defined by some other structure within the module.
 * Specifically, the definition pointed to by this TypeConstant can be any one of:
 * <p/>
 * <ul>
 * <li>{@link ModuleConstant} for a module</li>
 * <li>{@link PackageConstant} for a package</li>
 * <li>{@link ClassConstant} for a class</li>
 * <li>{@link TypedefConstant} for a typedef</li>
 * <li>{@link PropertyConstant} for a class' type parameter</li>
 * <li>{@link RegisterConstant} for a method's type parameter</li>
 * <li>{@link ThisClassConstant} to indicate the auto-narrowing "this" class</li>
 * <li>{@link ParentClassConstant} for an auto-narrowing parent of an auto-narrowing class</li>
 * <li>{@link ChildClassConstant} for a named auto-narrowing child of an auto-narrowing class</li>
 * <li>{@link UnresolvedNameConstant} for a definition that has not been resolved at this point</li>
 * </ul>
 */
public class TerminalTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param format the format of the Constant in the stream
     * @param in     the DataInput stream to read the Constant value from
     *
     * @throws IOException if an issue occurs reading the Constant value
     */
    public TerminalTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iDef = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param constId a ModuleConstant, PackageConstant, or ClassConstant
     */
    public TerminalTypeConstant(ConstantPool pool, Constant constId)
        {
        super(pool);

        assert !(constId instanceof TypeConstant);

        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Typedef:
            case Property:
            case Register:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case UnresolvedName:
                break;

            default:
                throw new IllegalArgumentException("constant " + constId.getFormat()
                        + " is not a Module, Package, Class, Typedef, or formal type parameter");
            }

        m_constId = constId;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isImmutabilitySpecified()
        {
        return false;
        }

    @Override
    public boolean isAccessSpecified()
        {
        return false;
        }

    @Override
    public Access getAccess()
        {
        return Access.PUBLIC;
        }

    @Override
    public boolean isParamsSpecified()
        {
        return false;
        }

    @Override
    public boolean isAnnotated()
        {
        return false;
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        return true;
        }

    @Override
    public Constant getDefiningConstant()
        {
        Constant constId = m_constId;
        if (constId instanceof ResolvableConstant)
            {
            Constant constResolved = ((ResolvableConstant) constId).getResolvedConstant();
            if (constResolved != null)
                {
                m_constId = constId = constResolved;
                }
            }
        return constId;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return m_constId.isAutoNarrowing();
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return (TypeConstant) simplify();
        }

    @Override
    public TypeConstant resolveGenerics(GenericTypeResolver resolver)
        {
        Constant constId = getDefiningConstant();
        return constId instanceof PropertyConstant
            ? resolver.resolveGenericType((PropertyConstant) constId)
            : this;
        }

    @Override
    public TypeConstant modifyAccess(Access access)
        {
        return this;
        }

    @Override
    public boolean isOnlyNullable()
        {
        TypeConstant typeResolved = resolveTypedefs();
        return this == typeResolved
                ? m_constId.equals(getConstantPool().clzNullable())
                : typeResolved.isOnlyNullable();
        }

    @Override
    protected TypeConstant cloneSingle(TypeConstant type)
        {
        return this;
        }

    @Override
    protected TypeConstant unwrapForCongruence()
        {
        TypeConstant typeResolved = resolveTypedefs();
        return typeResolved == this
                ? this
                : typeResolved.unwrapForCongruence();
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).extendsClass(constClass);

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).extendsClass(constClass);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).extendsClass(constClass);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).extendsClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).extendsClass(constClass);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).impersonatesClass(constClass);

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).impersonatesClass(constClass);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).impersonatesClass(constClass);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).impersonatesClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).impersonatesClass(constClass);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean extendsOrImpersonatesClass(IdentityConstant constClass)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).extendsOrImpersonatesClass(constClass);

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).extendsOrImpersonatesClass(
                    constClass);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).extendsOrImpersonatesClass(constClass);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).extendsOrImpersonatesClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).extendsOrImpersonatesClass(constClass);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isClassType()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these are always class types (not interface types)
                return true;

            case Class:
                {
                // examine the structure to determine if it represents a class or interface
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return clz.getFormat() != Component.Format.INTERFACE;
                }

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).isClassType();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).isClassType();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).isClassType();

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clz = (ClassStructure) ((PseudoConstant) constant)
                        .getDeclarationLevelClass().getComponent();
                return clz.getFormat() != Component.Format.INTERFACE;
                }

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isSingleUnderlyingClass()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).isSingleUnderlyingClass();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).isSingleUnderlyingClass();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).isSingleUnderlyingClass();

            default:
                return isClassType();
            }
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these are always class types (not interface types)
                return (IdentityConstant) constant;

            case Class:
                // must not be an interface
                assert (((ClassConstant) constant).getComponent()).getFormat() != Component.Format.INTERFACE;
                return (IdentityConstant) constant;

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).getSingleUnderlyingClass();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).getSingleUnderlyingClass();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).getSingleUnderlyingClass();

            case ParentClass:
            case ChildClass:
            case ThisClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass();

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public Set<IdentityConstant> underlyingClasses()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).underlyingClasses();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).underlyingClasses();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).underlyingClasses();

            default:
                return isSingleUnderlyingClass()
                        ? Collections.singleton(getSingleUnderlyingClass())
                        : Collections.EMPTY_SET;
            }
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these always specify a class identity
                return true;

            case Class:
                {
                // examine the structure to determine if it represents a class identity
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return clz.getFormat() != Component.Format.INTERFACE;
                }

            case Typedef:
            case Property:
            case Register:
                return false;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                // follow the indirection to the class structure to determine if it represents a
                // class identity
                ClassStructure clz = (ClassStructure) ((PseudoConstant) constant)
                        .getDeclarationLevelClass().getComponent();
                return clz.getFormat() != Component.Format.INTERFACE;
                }

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }


    @Override
    public boolean isConstant()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).isConstant();

            case Property:
            case Register:
                return false;

            default:
                return true;
            }
        }

    @Override
    protected boolean resolveStructure(TypeInfo typeinfo, Access access, TypeConstant[] atypeParams, ErrorListener errs)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant)
                        .resolveStructure(typeinfo, access, atypeParams, errs);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant)
                        .resolveStructure(typeinfo, access, atypeParams, errs);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant)
                        .resolveStructure(typeinfo, access, atypeParams, errs);

            case Module:
            case Package:
            case Class:
                // load the structure
                Component component = ((IdentityConstant) constant).getComponent();
                return resolveClassStructure((ClassStructure) component, typeinfo, access,
                        atypeParams, errs);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                // TODO - find an example where this happens. particularly in a composition.
                // currently this is a mindf**k just thinking about what it means, but theoretically
                // it could be some type represented by a child of a parent of this or whatever ...
                // for example, we could implement an inner interface .. would that use a "child of this" type?
                throw new IllegalStateException("TODO resolveStructures() for " + this + " (" + typeinfo.type + ")");

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    /**
     * Accumulate any information for the type represented by the specified structure into the
     * passed {@link TypeInfo}, checking the validity of the resulting type and logging any errors.
     *
     * @param struct       the class structure
     * @param typeinfo     the type info to contribute to
     * @param access       the desired accessibility into the current type
     * @param atypeParams  the types for the type parameters of this class, if any (may be null)
     * @param errs         the error list to log any errors to
     *
     * @return true if the resolution process was halted before it completed, for example if the
     *         error list reached its size limit
     */
    protected boolean resolveClassStructure(ClassStructure struct, TypeInfo typeinfo, Access access, TypeConstant[] atypeParams, ErrorListener errs)
        {
        assert struct != null;
        assert typeinfo != null;
        assert access != null;

        boolean fHalt = false;

        // at this point, the typeinfo represents everything that has already been "built up"; our
        // job is to contribute everything from the class struct to it
        boolean fTopmost = typeinfo.getFormat() == null;
        if (fTopmost)
            {
            // this is the "top most" class structure, so remember its format
            typeinfo.setFormat(struct.getFormat());
            }

        // evaluate type parameters
        int cTypeParams = atypeParams == null ? 0 : atypeParams.length;
        Map<StringConstant, TypeConstant> mapClassParams = struct.getTypeParams();
        if (mapClassParams.isEmpty())
            {
            if (cTypeParams  > 0)
                {
                fHalt |= log(errs, Severity.ERROR, VE_TYPE_PARAMS_UNEXPECTED,
                        struct.getIdentityConstant().getPathString());
                }
            }
        else
            {
            List<Entry<StringConstant, TypeConstant>> listClassParams = struct.getTypeParamsAsList();
            int cClassParams = listClassParams.size();
            if (atypeParams != null && cTypeParams != cClassParams)
                {
                if (struct.getIdentityConstant().equals(getConstantPool().clzTuple()))
                    {
                    // TODO lots of work to validate the tuple type here
                    }
                else
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_TYPE_PARAMS_WRONG_NUMBER,
                            struct.getIdentityConstant().getPathString(), cClassParams, cTypeParams);
                    }
                }

            for (int i = 0; i < cClassParams; ++i)
                {
                Entry<StringConstant, TypeConstant> entry          = listClassParams.get(i);
                String                              sName          = entry.getKey().getValue();
                TypeConstant                        typeConstraint = entry.getValue();

                ParamInfo paraminfo = typeinfo.parameters.get(sName);
                if (paraminfo == null)
                    {
                    paraminfo = new ParamInfo(sName);
                    paraminfo.setConstraintType(typeConstraint);
                    typeinfo.parameters.put(sName, paraminfo);
                    }
                else if (!paraminfo.getConstraintType().isA(typeConstraint)) // TODO what if constraint is a (or refers to a) formal type (see note below etc.etc.etc.)
                    {
                    // since we're accumulating type parameter information "on the way down" the
                    // tree of structures that form the type, it is possible that our constraint is
                    // "looser" than the constraint that came before us on the way down, e.g. our
                    // sub-class narrowed our constraint, but any conflict is an error
                    fHalt |= log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_CONSTRAINT,
                            struct.getIdentityConstant().getPathString(), sName,
                            typeConstraint.getValueString(),
                            paraminfo.getConstraintType().getValueString(),
                            typeinfo.type.getValueString());
                    }

                if (i >= cTypeParams)
                    {
                    // this just means that we already detected a mismatch between the number of
                    // class parameters and the number of actual type parameters passed in, and we
                    // already logged that error, so ignore it here
                    continue;
                    }

                TypeConstant typeActual = atypeParams[i];
                assert typeActual != null;

                // quite often, the type parameter type is a reference to a type parameter
                typeActual = typeActual.resolveGenerics(typeinfo.ensureTypeResolver(errs));

                if (!typeActual.isA(typeConstraint))
                    {
                    if (typeConstraint.getDefiningConstant() != null && typeConstraint.getDefiningConstant().equals(getConstantPool().clzTuple()))
                        {
                        // TODO lots of work to validate the tuple type here
                        }
                    else
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_TYPE_PARAM_INCOMPATIBLE_TYPE,
                                struct.getIdentityConstant().getPathString(), sName,
                                typeConstraint.getValueString(),
                                typeActual.getValueString(), typeinfo.type.getValueString());
                        }
                    }

                TypeConstant typeOld = paraminfo.getActualType();
                if (typeOld == null)
                    {
                    paraminfo.setActualType(typeActual);
                    }
                else if (!typeOld.equals(typeActual))
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_TYPE_PARAM_CONFLICTING_TYPES,
                            struct.getIdentityConstant().getPathString(), sName,
                            typeOld.getValueString(), typeActual.getValueString(),
                            typeinfo.type.getValueString());
                    }
                }
            }
        if (fHalt)
            {
            return fHalt;
            }

        // first, determine which compositions need to be subsequently processed; any compositions
        // that have not already (directly or indirectly) been processed by a "higher" (i.e. toward
        // the topmost) class structure will be the responsibility of this class structure to
        // compose. for example, consider mixin M2 extends mixin M1, and class B incorporates M1,
        // and class D extends B incorporates M2; when processing D, it is responsible for M2
        // which is responsible for M1, so B does not process the M1 composition. to achieve this
        // behavior, we will ignore any compositions here that have already appeared in the typeinfo
        List<Contribution> listRawContribs = struct.getContributionsAsList();
        int                cContribs       = listRawContribs.size();
        int                iContrib        = 0;

        // peel off all of the annotations at the front of the contribution list
        List<Contribution> listAnnotations = Collections.EMPTY_LIST;
        NextContrib: for ( ; iContrib < cContribs; ++iContrib)
            {
            // only process annotations
            Contribution contrib = listRawContribs.get(iContrib);
            if (contrib.getComposition() != Composition.Annotation)
                {
                break;
                }

            TypeConstant typeContrib = contrib.getTypeConstant();
            if (!typeContrib.isExplicitClassIdentity(false))
                {
                fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_NOT_CLASS,
                        struct.getIdentityConstant().getPathString(), typeContrib.getValueString());
                continue NextContrib;
                }

            // if any mix-ins already registered match or extend this mixin, then this
            // mix-in gets ignored
            if (typeinfo.incorporated.contains(typeContrib))
                {
                continue NextContrib;
                }
            IdentityConstant constClass = typeContrib.getSingleUnderlyingClass();
            for (TypeConstant typeMixin : typeinfo.incorporated)
                {
                if (typeMixin.extendsClass(constClass))
                    {
                    continue NextContrib;
                    }
                }

            // even though we haven't processed it yet, stake our claim to the
            // responsibility of processing it by registering it in the typeinfo
            typeinfo.incorporated.add(typeContrib);

            // ... and add it to our list of things that we need to process
            if (listAnnotations.isEmpty())
                {
                listAnnotations = new ArrayList<>();    // lazy list instantiation
                }
            listAnnotations.add(contrib);
            }
        if (fHalt)
            {
            return fHalt;
            }

        // next up, for any class type (other than Object itself), there MUST be an "extends"
        // contribution that specifies another class
        List<Contribution> listContributions = new ArrayList<>();
        boolean            fInto             = false;
        boolean            fExtends          = false;
        switch (struct.getFormat())
            {
            case MODULE:
            case PACKAGE:
            case CLASS:
            case CONST:
            case ENUM:
            case ENUMVALUE:
            case SERVICE:
                {
                Contribution contrib = iContrib < cContribs ? listRawContribs.get(iContrib) : null;
                fExtends = contrib != null && contrib.getComposition() == Composition.Extends;

                // Object does not (and must not) extend anything
                if (struct.getIdentityConstant().equals(getConstantPool().clzObject()))
                    {
                    if (fExtends)
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_UNEXPECTED,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        }
                    break;
                    }

                // all other classes must extends something
                if (!fExtends)
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_EXPECTED,
                            struct.getIdentityConstant().getPathString());
                    break;
                    }

                // the "extends" clause must specify a class identity
                TypeConstant typeExtends = contrib.getTypeConstant();
                if (!typeExtends.isExplicitClassIdentity(true))
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_NOT_CLASS,
                            struct.getIdentityConstant().getPathString(),
                            typeExtends.getValueString());
                    break;
                    }

                if (typeinfo.extended.contains(typeExtends))
                    {
                    // some sor of circular loop
                    fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_CYCLICAL,
                            struct.getIdentityConstant().getPathString());
                    break;
                    }

                // add the "extends" to the list of contributions to process, and register it so
                // that no one else will do it
                typeinfo.extended.add(typeExtends);
                listContributions.add(contrib);
                ++iContrib;
                }
                break;

            case MIXIN:
            case TRAIT:
                {
                // a mixin can extend another mixin, and it can specify an "into" that defines a
                // base type that defines the environment that it will be working within. if neither
                // is present, then there is an implicit "into Object"
                Contribution contrib = iContrib < cContribs ? listRawContribs.get(iContrib) : null;

                // check "into"
                fInto = contrib != null && contrib.getComposition() == Composition.Into;
                if (fInto)
                    {
                    ++iContrib;

                    if (typeinfo.getFormat() == Component.Format.MIXIN && typeinfo.getInto() == null)
                        {
                        // the first "into" in the inheritance chain is the one that gets used
                        typeinfo.setInto(contrib.getTypeConstant());
                        listContributions.add(contrib);
                        }
                    else if (typeinfo.getInto() != null && !typeinfo.getInto().isA(contrib.getTypeConstant()))
                        {
                        // subsequent "into" clauses in the inheritance chain must be compatible
                        // with any previous one
                        // TODO note that this test will is wrong for a mixin into a mixin
                        fHalt |= log(errs, Severity.ERROR, VE_INTO_INCOMPATIBLE,
                                struct.getIdentityConstant().getPathString(),
                                contrib.getTypeConstant().getValueString(),
                                typeinfo.type.getValueString(), typeinfo.getInto().getValueString());
                        }

                    // load the next contribution
                    contrib = iContrib < cContribs ? listRawContribs.get(iContrib) : null;
                    }

                // check "extends"
                fExtends = contrib != null && contrib.getComposition() == Composition.Extends;
                if (fExtends)
                    {
                    ++iContrib;

                    TypeConstant typeExtends = contrib.getTypeConstant();
                    if (!typeExtends.isExplicitClassIdentity(true))
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_NOT_CLASS,
                                struct.getIdentityConstant().getPathString(),
                                typeExtends.getValueString());
                        break;
                        }

                    if (typeinfo.incorporated.contains(typeExtends))
                        {
                        // some sort of circular loop or badly directed graph
                        fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_CYCLICAL,
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    typeinfo.incorporated.add(typeExtends);
                    listContributions.add(contrib);
                    }
                else if (typeinfo.getInto() == null)
                    {
                    // add fake "into Object"
                    TypeConstant typeInto = getConstantPool().typeObject();
                    typeinfo.setInto(typeInto);
                    listContributions.add(new Contribution(Composition.Into, typeInto));
                    }
                }
                break;

            case INTERFACE:
                // first, lay down the set of methods present in Object
                if (fTopmost)
                    {
                    listContributions.add(new Contribution(Composition.Implements, getConstantPool().typeObject()));
                    }
                break;
            }
        if (fHalt)
            {
            return fHalt;
            }

        // like "extends" and "into", only one "impersonates" clause is allowed
        boolean fImpersonates = false;

        // go through the rest of the contributions, and add the ones that need to be processed to
        // the list to do
        NextContrib: for ( ; iContrib < cContribs; ++iContrib)
            {
            // only process annotations
            Contribution contrib     = listRawContribs.get(iContrib);
            TypeConstant typeContrib = contrib.getTypeConstant();

            switch (contrib.getComposition())
                {
                case Annotation:
                    fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_UNEXPECTED,
                            contrib.getTypeConstant().getValueString(),
                            struct.getIdentityConstant().getPathString());
                    break;

                case Into:
                    // only applicable on a mixin, only one allowed, and it should have been earlier
                    // in the list of contributions
                    fHalt |= log(errs, Severity.ERROR, VE_INTO_UNEXPECTED,
                            contrib.getTypeConstant().getValueString(),
                            struct.getIdentityConstant().getPathString());
                    fInto |= struct.getFormat() == Component.Format.MIXIN;
                    break;

                case Extends:
                    // not applicable on an interface, only one allowed, and it should have been
                    // earlier in the list of contributions
                    fHalt |= log(errs, Severity.ERROR, VE_EXTENDS_UNEXPECTED,
                            contrib.getTypeConstant().getValueString(),
                            struct.getIdentityConstant().getPathString());
                    fExtends |= struct.getFormat() != Component.Format.INTERFACE;
                    break;

                case Impersonates:
                    if (fImpersonates
                            || struct.getFormat() == Component.Format.MIXIN
                            || struct.getFormat() == Component.Format.INTERFACE)
                        {
                        // only one allowed, and it must be a class type (not mixin or interface)
                        fHalt |= log(errs, Severity.ERROR, VE_IMPERSONATES_UNEXPECTED,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    fImpersonates = true;

                    if (contrib.getTypeConstant().isExplicitClassIdentity(true))
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_IMPERSONATES_NOT_CLASS,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    if (typeinfo.getImpersonates() == null)
                        {
                        typeinfo.setImpersonates(contrib.getTypeConstant());
                        }
                    else if (!typeinfo.getImpersonates().isA(contrib.getTypeConstant()))
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_IMPERSONATES_INCOMPATIBLE,
                                struct.getIdentityConstant().getPathString(),
                                contrib.getTypeConstant().getValueString(),
                                typeinfo.type.getValueString(),
                                typeinfo.getImpersonates().getValueString());
                        }
                    break;

                case Incorporates:
                    if (struct.getFormat() == Component.Format.INTERFACE)
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_INCORPORATES_UNEXPECTED,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    if (!typeContrib.isExplicitClassIdentity(true))
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_INCORPORATES_NOT_CLASS,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    // TODO handle the conditional case (GG wrote a helper)

                    // if any mix-ins already registered match or extend this mixin, then this
                    // mix-in gets ignored
                    if (typeinfo.incorporated.contains(typeContrib))
                        {
                        continue NextContrib;
                        }
                    IdentityConstant constClass = typeContrib.getSingleUnderlyingClass();
                    for (TypeConstant typeMixin : typeinfo.incorporated)
                        {
                        if (typeMixin.extendsClass(constClass))
                            {
                            continue NextContrib;
                            }
                        }

                    // the mixin must be compatible with this type, as specified by its "into"
                    // clause; note: have to validate the type before asking it for a typeinfo
                    fHalt |= typeContrib.validate(errs);
                    TypeConstant typeInto = typeContrib.getTypeInfo().getInto();
                    if (typeInto == null)
                        {
                        assert typeContrib.getTypeInfo().getFormat() != Component.Format.MIXIN;
                        fHalt |= log(errs, Severity.ERROR, VE_INCORPORATES_NOT_MIXIN,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        }
                    else if (!typeinfo.type.isA(typeInto))
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_INCORPORATES_INCOMPATIBLE,
                                struct.getIdentityConstant().getPathString(),
                                contrib.getTypeConstant().getValueString(),
                                typeinfo.type.getValueString(),
                                typeInto.getValueString());
                        }
                    else
                        {
                        // even though we haven't processed it yet, stake our claim to the
                        // responsibility of processing it by registering it in the typeinfo
                        typeinfo.incorporated.add(typeContrib);

                        // ... and add it to our list of things that we need to process
                        listContributions.add(contrib);
                        }
                    break;


                case Delegates:
                    // not applicable on an interface
                    if (struct.getFormat() == Component.Format.INTERFACE)
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_DELEGATES_UNEXPECTED,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    // must be an "interface type"
                    if (typeContrib.isExplicitClassIdentity(true)) // TODO TypeConstant.isInterfaceType()
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_DELEGATES_NOT_INTERFACE,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    // even though we haven't processed it yet, stake our claim to the
                    // responsibility of processing it by registering it in the typeinfo
                    typeinfo.implemented.add(typeContrib);

                    // ... and add it to our list of things that we need to process
                    listContributions.add(contrib);
                    break;

                case Implements:
                    // must be an "interface type"
                    if (typeContrib.isExplicitClassIdentity(true)) // TODO TypeConstant.isInterfaceType()
                        {
                        fHalt |= log(errs, Severity.ERROR, VE_IMPLEMENTS_NOT_INTERFACE,
                                contrib.getTypeConstant().getValueString(),
                                struct.getIdentityConstant().getPathString());
                        break;
                        }

                    // check if it is already implemented
                    if (typeinfo.implemented.contains(typeContrib))
                        {
                        continue NextContrib;
                        }
                    for (TypeConstant typeImplemented : typeinfo.implemented)
                        {
                        List<ContributionChain> chains = typeImplemented.collectContributions(
                                typeContrib, new ArrayList<>());
                        if (!chains.isEmpty())
                            {
                            for (ContributionChain chain : chains)
                                {
                                if (chain.getOrigin().getComposition() != Component.Composition.MaybeDuckType)
                                    {
                                    continue NextContrib;
                                    }
                                }
                            }
                        }

                    // even though we haven't processed it yet, stake our claim to the
                    // responsibility of processing it by registering it in the typeinfo
                    typeinfo.implemented.add(typeContrib);

                    // ... and add it to our list of things that we need to process
                    listContributions.add(contrib);
                    break;

                default:
                    throw new IllegalStateException(struct.getIdentityConstant().getPathString()
                            + ", contribution=" + contrib);
                }
            }
        if (fHalt)
            {
            return fHalt;
            }

        // recurse through compositions
        for (Contribution contrib : listContributions)
            {
            // TODO use Contribution "transform" helper
            TypeConstant typeContrib = contrib.getTypeConstant();
            // TODO what should be passed for "access" here? e.g. should be PROTECTED if the orig was PRIVATE, for example
            fHalt |= typeContrib.resolveStructure(typeinfo, Access.PUBLIC, null, errs);
            }
        if (fHalt)
            {
            return fHalt;
            }

        // properties & methods
        for (Component child : struct.children())
            {
            switch (child.getFormat())
                {
                case PROPERTY:
                    fHalt |= resolvePropertyStructure((PropertyStructure) child, typeinfo, access, errs);
                    break;

                case MULTIMETHOD:
                    for (Component method : child.children())
                        {
                        if (method instanceof MethodStructure)
                            {
                            fHalt |= resolveMethodStructure((MethodStructure) method, typeinfo, null, access, errs);
                            }
                        else
                            {
                            throw new IllegalStateException("multi-method " + child.getName()
                                    + " contains non-method: " + method);
                            }
                        }
                    break;

                case METHOD:
                case FILE:
                case RSVD_D:
                    throw new IllegalStateException("class " + struct.getName()
                            + " contains illegal child: " + child);
                }
            }
        if (fHalt)
            {
            return fHalt;
            }

        // process annotations
        for (Contribution contrib : listAnnotations)
            {
            TypeConstant typeContrib = contrib.getTypeConstant();
            // TODO what should be passed for "access" here? e.g. should be PROTECTED if the orig was PRIVATE, for example
            fHalt |= typeContrib.resolveStructure(typeinfo, Access.PUBLIC, null, errs);
            }
        if (fHalt)
            {
            return fHalt;
            }

        // TODO trim out everything that doesn't meet our accessibility requirements

        return fHalt;
        }

    /**
     * Accumulate any information from the passed property structure into the passed
     * {@link TypeInfo}, checking the validity of the property and the resulting type, and logging
     * any errors.
     *
     * @param struct    the property structure
     * @param typeinfo  the type info to contribute to
     * @param access    the desired accessibility into the current type
     * @param errs      the error list to log any errors to
     *
     * @return true if the resolution process was halted before it completed, for example if the
     *         error list reached its size limit
     */
    protected boolean resolvePropertyStructure(PropertyStructure struct, TypeInfo typeinfo, Access access, ErrorListener errs)
        {
        assert struct != null;
        assert typeinfo != null;
        assert access != null;

        boolean fHalt = false;

        String       sName    = struct.getName();
        PropertyInfo propinfo = typeinfo.properties.get(sName);
        if (propinfo == null)
            {
            propinfo = new PropertyInfo(this, struct.getType(), sName);
            if (struct.isSynthetic())
                {
                propinfo.markReadOnly();
                }
            }
        else
            {
            // make sure there are no conflicts
            // TODO

            // update property remove annotations (some things get over-written; others merged)
            // TODO
            }

        // go through children (methods)
        // TODO

        return fHalt;
        }

    /**
     * Accumulate any information from the passed method structure into the passed
     * {@link TypeInfo}, checking the validity of the property and the resulting type, and logging
     * any errors.
     *
     * @param struct      the method structure
     * @param typeinfo    the type info that this method is somehow a part of
     * @param mapMethods  the map of methods to contribute to
     * @param access      the desired accessibility into the current type
     * @param errs        the error list to log any errors to
     *
     * @return true if the resolution process was halted before it completed, for example if the
     *         error list reached its size limit
     */
    protected boolean resolveMethodStructure(MethodStructure struct, TypeInfo typeinfo,
            Map<SignatureConstant, MethodInfo> mapMethods, Access access, ErrorListener errs)
        {
        assert struct != null;
        assert typeinfo != null;
        assert access != null;

        boolean fHalt = false;

        // first determine if this method should be registered
        // TODO
        // System.out.println("method: " + struct.getIdentityConstant().getValueString());

        return fHalt;
        }

    @Override
    public <T extends TypeConstant> T findFirst(Class<? extends TypeConstant> clz)
        {
        return clz == getClass() ? (T) this : null;
        }

    /**
     * Dereference a typedef constant to find the type to which it refers.
     *
     * @param constTypedef  a typedef constant
     *
     * @return the type that the typedef refers to
     */
    public static TypeConstant getTypedefTypeConstant(TypedefConstant constTypedef)
        {
        return ((TypedefStructure) constTypedef.getComponent()).getType();
        }

    /**
     * Dereference a property constant that is used for a type parameter, to obtain the constraint
     * type of that type parameter.
     *
     * @param constProp the property constant for the property that holds the type parameter type
     *
     * @return the constraint type of the type parameter
     */
    public static TypeConstant getPropertyTypeConstant(PropertyConstant constProp)
        {
        // the type points to a property, which means that the type is a parameterized type;
        // the type of the property will be "Type<X>", so return X
        TypeConstant typeProp = ((PropertyStructure) constProp.getComponent()).getType();
        assert typeProp.isEcstasy("Type") && typeProp.isParamsSpecified();
        return typeProp.getParamTypesArray()[0];
        }

    /**
     * Dereference a register constant that is used for a type parameter, to obtain the constraint
     * type of that type parameter.
     *
     * @param constReg  the register constant for the register that holds the type parameter type
     *
     * @return the constraint type of the type parameter
     */
    public static TypeConstant getRegisterTypeConstant(RegisterConstant constReg)
        {
        // the type points to a register, which means that the type is a parameterized type;
        // the type of the register will be "Type<X>", so return X
        MethodConstant   constMethod = constReg.getMethod();
        int              nReg        = constReg.getRegister();
        TypeConstant[]   atypeParams = constMethod.getRawParams();
        assert atypeParams.length > nReg;
        TypeConstant     typeParam   = atypeParams[nReg];
        assert typeParam.isEcstasy("Type") && typeParam.isParamsSpecified();
        return typeParam.getParamTypesArray()[0];
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected List<ContributionChain> collectContributions(TypeConstant that, List<ContributionChain> chains)
        {
        Constant constIdThis = getDefiningConstant();

        if (that.isSingleDefiningConstant()
                && constIdThis.equals(that.getDefiningConstant()))
            {
            chains.add(new ContributionChain(new Contribution(Composition.Equal, null)));
            return chains;
            }

        switch (constIdThis.getFormat())
            {
            case Module:
            case Package:
                break;

            case Class:
                {
                ClassStructure clzThis = (ClassStructure)
                    ((IdentityConstant) constIdThis).getComponent();
                chains.addAll(that.collectClassContributions(clzThis, chains));
                break;
                }

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constIdThis).
                    collectContributions(that, chains);

            case Property:
                {
                // scenarios we can handle here are:
                // 1. r-value (this) = T (formal parameter type), constrained by U (other formal type)
                //    l-value (that) = U (formal parameter type)
                //
                // 2. r-value (this) = T (formal parameter type), constrained by U (real type)
                //    l-value (that) = V (real type), where U "is a" V

                PropertyConstant constProp = (PropertyConstant) constIdThis;
                TypeConstant typeConstraint = constProp.getRefType();

                return typeConstraint.collectContributions(that, chains);
                }

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constIdThis).
                    collectContributions(that, chains);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clzThis = (ClassStructure)
                    ((PseudoConstant) constIdThis).getDeclarationLevelClass().getComponent();
                chains.addAll(that.collectClassContributions(clzThis, chains));
                break;
                }

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constIdThis);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constIdThis);
            }
        return chains;
        }

    @Override
    protected List<ContributionChain> collectClassContributions(ClassStructure clzThat, List<ContributionChain> chains)
        {
        Constant constIdThis = getDefiningConstant();

        switch (constIdThis.getFormat())
            {
            case Module:
            case Package:
                break;

            case Class:
                {
                IdentityConstant idThis = (IdentityConstant) constIdThis;
                if (idThis.equals(getConstantPool().clzObject()))
                    {
                    // everything is considered to extend Object (even interfaces)
                    chains.add(new ContributionChain(
                        new Contribution(Composition.Extends, getConstantPool().typeObject())));
                    break;
                    }

                List<ContributionChain> chainsClz =
                    clzThat.collectContributions(idThis, new LinkedList<>());
                if (chainsClz.isEmpty())
                    {
                    ClassStructure clzThis = (ClassStructure) idThis.getComponent();
                    if (clzThis.getFormat() == Component.Format.INTERFACE)
                        {
                        chains.add(new ContributionChain(
                            new Contribution(Composition.MaybeDuckType, null)));
                        }
                    }
                else
                    {
                    chains.addAll(chainsClz);
                    }
                break;
                }

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constIdThis).
                    collectClassContributions(clzThat, chains);

            case Property:
            case Register:
                // r-value (that) is a real type; it cannot have a formal type contribution
                // (assigned to a formal type)
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clzThis = (ClassStructure)
                    ((PseudoConstant) constIdThis).getDeclarationLevelClass().getComponent();
                return clzThis.getIdentityConstant().asTypeConstant().collectClassContributions(clzThat, chains);
                }

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constIdThis);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constIdThis);
            }

        return chains;
        }

    @Override
    protected boolean validateContributionFrom(TypeConstant that, Access access, ContributionChain chain)
        {
        // there is nothing that could change the result of "checkAssignableTo"
        return true;
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant that, Access access,
                                                               List<TypeConstant> listParams)
        {
        Constant constIdThis = getDefiningConstant();

        assert (constIdThis.getFormat() == Format.Class);

        IdentityConstant idThis  = (IdentityConstant) constIdThis;
        ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

        assert (clzThis.getFormat() == Component.Format.INTERFACE);

        return clzThis.isInterfaceAssignableFrom(that, access, listParams);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        Constant constIdThis = getDefiningConstant();

        assert (constIdThis.getFormat() == Format.Class);

        IdentityConstant idThis  = (IdentityConstant) constIdThis;
        ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

        return clzThis.containsSubstitutableMethod(signature, access, listParams);
        }

    @Override
    public boolean containsSubstitutableProperty(SignatureConstant signature, Access access, List<TypeConstant> listParams)
        {
        Constant constIdThis = getDefiningConstant();

        assert (constIdThis.getFormat() == Format.Class);

        IdentityConstant idThis  = (IdentityConstant) constIdThis;
        ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

        return clzThis.containsSubstitutableProperty(signature, access, listParams);
        }

    @Override
    public boolean consumesFormalType(String sTypeName, Access access)
        {
        return false;
        }

    @Override
    public boolean producesFormalType(String sTypeName, Access access)
        {
        Constant constId = getDefiningConstant();

        return constId.getFormat() == Format.Property &&
            ((PropertyConstant) constId).getName().equals(sTypeName);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.TerminalType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constId.containsUnresolved();
        }

    @Override
    public Constant simplify()
        {
        m_constId = m_constId.simplify();

        // compile down all of the types that refer to typedefs so that they refer to the underlying
        // types instead
        if (m_constId instanceof TypedefConstant)
            {
            Component    typedef   = ((TypedefConstant) m_constId).getComponent();
            TypeConstant constType;
            if (typedef instanceof CompositeComponent)
                {
                List<Component> typdefs = ((CompositeComponent) typedef).components();
                constType = (TypeConstant) ((TypedefStructure) typdefs.get(0)).getType().simplify();
                for (int i = 1, c = typdefs.size(); i < c; ++i)
                    {
                    TypeConstant constTypeN = (TypeConstant) ((TypedefStructure) typdefs.get(i)).getType().simplify();
                    if (!constType.equals(constTypeN))
                        {
                        // typedef points to more than one type, conditionally, so just leave the
                        // typedef in place
                        return this;
                        }
                    }
                }
            else
                {
                constType = (TypeConstant) ((TypedefStructure) typedef).getType().simplify();
                }
            assert constType != null;
            return constType;
            }

        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constId);
        }

    @Override
    protected Object getLocator()
        {
        return m_constId.getFormat() == Format.UnresolvedName
                ? null
                : m_constId;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        TerminalTypeConstant that = (TerminalTypeConstant) obj;
        Constant constThis = this.m_constId;
        if (constThis instanceof ResolvableConstant)
            {
            constThis = ((ResolvableConstant) constThis).unwrap();
            }
        Constant constThat = that.m_constId;
        if (constThat instanceof ResolvableConstant)
            {
            constThat = ((ResolvableConstant) constThat).unwrap();
            }
        return constThis.compareTo(constThat);
        }

    @Override
    public String getValueString()
        {
        return m_constId.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constId = getConstantPool().getConstant(m_iDef);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constId = pool.register(m_constId);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constId.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constId.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that defines this type.
     */
    private transient int m_iDef;

    /**
     * The class referred to. May be an IdentityConstant (ModuleConstant, PackageConstant,
     * ClassConstant, TypedefConstant, PropertyConstant), or a PseudoConstant (ThisClassConstant,
     * ParentClassConstant, ChildClassConstant, RegisterConstant, or UnresolvedNameConstant).
     */
    private Constant m_constId;
    }
