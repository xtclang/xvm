package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.CompositeComponent;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
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
            ? resolver.resolveGenericType(((PropertyConstant) constId).getName())
            : this;
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

        // evaluate type parameters
        int cTypeParams = atypeParams == null ? 0 : atypeParams.length;
        Map<StringConstant, TypeConstant> mapClassParams = struct.getTypeParams();
        if (mapClassParams.isEmpty())
            {
            if (cTypeParams  > 0)
                {
                log(errs, Severity.ERROR, VE_UNEXPECTED_TYPE_PARAMS, struct.getIdentityConstant().getPathString());
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
                    // TODO log error: either too few or too many type params
                    throw new IllegalStateException("TODO error on " + struct.getName()
                            + " - type param type count (" + cTypeParams
                            + ") does not match class (" + cClassParams + ")");
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
                    // TODO log error: actual type is not legal because it violates the constraint
                    throw new IllegalStateException("TODO error on " + struct.getName()
                            + " - sub's type param constraint (" + paraminfo.getConstraintType()
                            + ") not isA(" + typeConstraint + ")");
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
                // TODO need a helper to translate in case e.g. "KeyType | ValueType" etc. etc. etc. etc.
                if (typeActual instanceof TerminalTypeConstant)
                    {
                    Constant constant = typeActual.getDefiningConstant();
                    if (constant instanceof PropertyConstant)
                        {
                        String sPropName = ((PropertyConstant) constant).getName();
                        if (sPropName.equals(sName))
                            {
                            // no change; nothing to do for this parameter
                            // e.g. LinkedList<ElementType> implements List<ElementType>
                            continue;
                            }

                        ParamInfo paraminfoProp = typeinfo.parameters.get(sPropName);
                        if (paraminfoProp == null || paraminfoProp.getActualType() == null)
                            {
                            // TODO log error: missing type for type parameter
                            throw new IllegalStateException("TODO error on " + struct.getName()
                                    + " - type param " + sPropName + " has no type");
                            }

                        typeActual = paraminfoProp.getActualType();
                        }
                    }

                if (!typeActual.isA(typeConstraint))
                    {
                    if (typeConstraint.getDefiningConstant() != null && typeConstraint.getDefiningConstant().equals(getConstantPool().clzTuple()))
                        {
                        // TODO lots of work to validate the tuple type here
                        }
                    else
                        {
                        // TODO log error: actual type is not legal because it violates the constraint
                        throw new IllegalStateException("TODO error on " + struct.getName()
                                + " - type param type (" + typeActual
                                + ") not isA(" + typeConstraint + ")");
                        }
                    }

                TypeConstant typeOld = paraminfo.getActualType();
                if (typeOld == null)
                    {
                    paraminfo.setActualType(typeActual);
                    }
                else if (!typeOld.equals(typeActual))
                    {
                    // TODO log error: actual type conflicts with the actual type already spec'd
                    throw new IllegalStateException("TODO error on " + struct.getName()
                            + " - type param type (" + typeActual
                            + ") for " + sName
                            + " is different from the prev spec'd type  (" + typeOld + ")");
                    }
                }
            }

        // recurse through compositions
        boolean fAnyAnnotations = false;
        for (Contribution contrib : struct.getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Annotation:
                    // skip annotations (we'll handle them at the end, since they wrap around this
                    // class, i.e. they're the last to evaluate now because they're first in the
                    // call chain
                    fAnyAnnotations = true;
                    break;

                case Delegates:
                case Impersonates:
                case Implements:
                case Incorporates:  // TODO conditional
                case Into:
                case Extends:
                    {
                    // TODO use Contribution "transform" helper
                    TypeConstant typeContrib = contrib.getTypeConstant();
                    // TODO what should be passed for "access" here? e.g. should be PROTECTED if the orig was PRIVATE, for example
                    typeContrib.resolveStructure(typeinfo, Access.PUBLIC, null, errs);
                    break;
                    }

                default:
                    throw new IllegalStateException("struct=" + struct.getName() + ", contribution=" + contrib);
                }
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

        // process annotations
        if (fAnyAnnotations)
            {
            // TODO
            }

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

        // TODO
        // System.out.println("property: " + struct.getIdentityConstant().getValueString());

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
        return typeProp.getParamTypes().get(0);
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
        return typeParam.getParamTypes().get(0);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected ContributionChain checkAssignableTo(TypeConstant that)
        {
        Constant constIdThis = this.getDefiningConstant();
        Constant constIdThat = that.getDefiningConstant(); // TODO: what if relational

        if (constIdThis.equals(constIdThat))
            {
            return new ContributionChain(
                    new Contribution(Composition.Equal, null));
            }

        switch (constIdThis.getFormat())
            {
            case Module:
            case Package:
                return null;

            case Class:
                switch (constIdThat.getFormat())
                    {
                    case Module:
                    case Package:
                        return null;

                    case Class:
                        // scenarios we can cover here are:
                        // 1. this extends that (recursively)
                        // 2. this or any of its contributions (recursively) impersonates that
                        // 3. this or any of its contributions (recursively) incorporates that
                        // 4. this or any of its contributions (recursively) delegates to that
                        // 5. this or any of its contributions (recursively) declares to implement that
                        {
                        IdentityConstant idThis  = (IdentityConstant) constIdThis;
                        IdentityConstant idThat  = (IdentityConstant) constIdThat;
                        ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

                        ContributionChain chain = clzThis.findContribution(idThat);
                        if (chain == null)
                            {
                            ClassStructure clzThat = (ClassStructure) idThat.getComponent();
                            if (clzThat.getFormat() == Component.Format.INTERFACE)
                                {
                                chain = new ContributionChain(
                                    new Contribution(Composition.MaybeDuckType, that));
                                }
                            }
                        return chain;
                        }

                    case Typedef:
                        return this.checkAssignableTo(getTypedefTypeConstant((TypedefConstant) constIdThat));

                    case Property:
                        PropertyConstant constProp = (PropertyConstant) constIdThat;
                        TypeConstant typeConstraint = constProp.getRefType();
                        return this.checkAssignableTo(typeConstraint);

                    case Register:
                        return this.checkAssignableTo(getRegisterTypeConstant((RegisterConstant) constIdThat));

                    case ThisClass:
                    case ParentClass:
                    case ChildClass:
                        ClassStructure clz = (ClassStructure)
                            ((PseudoConstant) constIdThis).getDeclarationLevelClass().getComponent();
                        return clz.getIdentityConstant().asTypeConstant().checkAssignableTo(that);

                    case UnresolvedName:
                        throw new IllegalStateException("unexpected unresolved-name constant: " + constIdThis);

                    default:
                        throw new IllegalStateException("unexpected defining constant: " + constIdThis);
                    }

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constIdThis).checkAssignableTo(that);

            case Property:
                // scenario we can handle here is:
                // 1. this = T (formal parameter type), constrained by U (other formal type)
                //    that = U (formal parameter type)
                //
                // 2. this = T (formal parameter type), constrained by U (real type)
                //    that = V (real type), where U "is a" V

                PropertyConstant constProp = (PropertyConstant) constIdThis;
                TypeConstant typeConstraint = constProp.getRefType();

                return typeConstraint.checkAssignableTo(that);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constIdThis).checkAssignableTo(that);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                ClassStructure clz = (ClassStructure)
                    ((PseudoConstant) constIdThis).getDeclarationLevelClass().getComponent();
                return clz.getIdentityConstant().asTypeConstant().checkAssignableTo(that);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constIdThis);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constIdThis);
            }
        }

    @Override
    protected boolean checkAssignableFrom(TypeConstant that, ContributionChain chain)
        {
        // there is nothing that could change the result of "checkAssignableTo"
        return true;
        }

    @Override
    protected boolean isInterfaceAssignableFrom(TypeConstant that, Access access,
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

    @Override
    public boolean consumesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return false;
        }

    @Override
    public boolean producesFormalType(String sTypeName, TypeSet types, Access access)
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
