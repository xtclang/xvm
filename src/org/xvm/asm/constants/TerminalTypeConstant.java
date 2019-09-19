package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.Register;

import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

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
 * <li>{@link TypeParameterConstant} for a method's type parameter</li>
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
     * Construct a constant whose value is a data type.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param constId a ModuleConstant, PackageConstant, or ClassConstant
     */
    public TerminalTypeConstant(ConstantPool pool, Constant constId)
        {
        super(pool);

        if (!constId.getFormat().isTypable())
            {
            throw new IllegalArgumentException("constant " + constId.getFormat()
                + " is not a Module, Package, Class, Typedef, or formal type parameter");
            }

        m_constId = constId;
        }

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

    @Override
    protected void resolveConstants()
        {
        m_constId = getConstantPool().getConstant(m_iDef);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isComposedOfAny(setIds);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return setIds.contains(constant);

            case NativeClass:
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case UnresolvedName:
                return false;

            case Typedef:
                return ((TypedefConstant) constant).getReferredToType().isComposedOfAny(setIds);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isImmutabilitySpecified()
        {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isImmutabilitySpecified();
        }

    @Override
    public boolean isImmutable()
        {
        TypeConstant type = resolveTypedefs();
        if (type != this)
            {
            return type.isImmutable();
            }

        Constant         constant = getDefiningConstant();
        IdentityConstant idClass;
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // always immutable
                return true;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return (((FormalConstant) constant)).getConstraintType().isImmutable();

            case NativeClass:
                constant = ((NativeRebaseConstant) constant).getClassConstant();
                // fall through
            case Class:
                idClass = (IdentityConstant) constant;
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                idClass = ((PseudoConstant) constant).getDeclarationLevelClass();
                break;

            case Typedef:
            case UnresolvedName:
            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }

        switch (idClass.getComponent().getFormat())
            {
            case MODULE:
            case PACKAGE:
            case CONST:
            case ENUM:
            case ENUMVALUE:
                return true;

            case INTERFACE:     // interfaces cannot specify mutability
            case CLASS:         // classes default to mutable
            case SERVICE:       // service is always assumed to be NOT immutable
                return false;

            case MIXIN:
                // a mixin is immutable iff its "into" is immutable
                return idClass.getType().getExplicitClassInto().isImmutable();

            default:
                throw new IllegalStateException("unexpected class constant: " + idClass);
            }
        }

    @Override
    public boolean isAccessSpecified()
        {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isAccessSpecified();
        }

    @Override
    public Access getAccess()
        {
        TypeConstant type = resolveTypedefs();
        return type == this
                ? Access.PUBLIC
                : type.getAccess();
        }

    @Override
    public boolean isParamsSpecified()
        {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isParamsSpecified();
        }

    @Override
    public int getMaxParamsCount()
        {
        if (!isSingleDefiningConstant())
            {
            // this can happen if this type is a Typedef referring to a relational type
            return 0;
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return 0;

            case Class:
                {
                // examine the structure to determine if it represents a class or interface
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return clz.getTypeParamCount();
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clz = (ClassStructure) ((PseudoConstant) constant)
                        .getDeclarationLevelClass().getComponent();
                return clz.getTypeParamCount();
                }

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().containsGenericParam(sName);
            }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        ClassStructure clz = (ClassStructure) getSingleUnderlyingClass(true).getComponent();

        return clz.containsGenericParamType(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getGenericParamType(sName, listParams);
            }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        ClassStructure clz = (ClassStructure) getSingleUnderlyingClass(true).getComponent();
        if (isFormalType())
            {
            assert listParams.isEmpty();

            IdentityConstant idFormal = (IdentityConstant) getDefiningConstant();
            return clz.containsGenericParamType(sName)
                    ? getConstantPool().ensureFormalTypeChildConstant(idFormal, sName).getType()
                    : null;
            }
        return clz.getGenericParamType(getConstantPool(), sName, listParams);
        }

    @Override
    public boolean isAnnotated()
        {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isAnnotated();
        }

    @Override
    public boolean isVirtualChild()
        {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isVirtualChild();
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() != Format.Typedef ||
                ((TypedefConstant) constId).getReferredToType().isSingleDefiningConstant();
        }

    @Override
    public Constant getDefiningConstant()
        {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.Typedef
                ? ((TypedefConstant) constId).getReferredToType().getDefiningConstant()
                : constId;
        }

    /**
     * @return the underlying constant, resolving it if it is still unresolved and can be resolved
     *         at this point
     */
    protected Constant ensureResolvedConstant()
        {
        Constant constId = m_constId;

        // resolve any previously unresolved constant at this point
        if (constId instanceof ResolvableConstant)
            {
            Constant constResolved = ((ResolvableConstant) constId).getResolvedConstant();
            if (constResolved != null)
                {
                // note that this TerminalTypeConstant could not have previously been registered
                // with the pool because it was not resolved, so changing the reference to the
                // underlying constant is still safe at this point
                m_constId = constId = constResolved;

                assert !constId.containsUnresolved();
                }
            }

        return constId;
        }

    @Override
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return ensureResolvedConstant().isAutoNarrowing();
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveContributedName(sName, collector);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ResolutionResult.UNKNOWN;

            case NativeClass:
                constant = ((NativeRebaseConstant) constant).getClassConstant();
                // fall through
            case Class:
                {
                ClassConstant constClz = (ClassConstant) constant;

                return constClz.getComponent().resolveName(sName, Access.PUBLIC, collector);
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass().getType()
                        .resolveContributedName(sName, collector);

            case Typedef:
                return ((TypedefConstant) constant).getReferredToType().
                    resolveContributedName(sName, collector);

            case UnresolvedName:
                return ResolutionResult.POSSIBLE;

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.Typedef
                ? ((TypedefConstant) constId).getReferredToType().resolveTypedefs()
                : this;
        }

    @Override
    public void bindTypeParameters(MethodConstant idMethod)
        {
        Constant constId = ensureResolvedConstant();
        if (constId instanceof TypeParameterConstant)
            {
            ((TypeParameterConstant) constId).bindMethod(idMethod);
            }
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveGenerics(pool, resolver);
            }

        Constant constId = getDefiningConstant();
        if (constId instanceof FormalConstant)
            {
            FormalConstant constFormal  = (FormalConstant) constId;
            TypeConstant   typeResolved = constFormal.resolve(resolver);
            if (typeResolved != null && !typeResolved.equals(this))
                {
                return typeResolved;
                }
            }

        return this;
        }

    @Override
    public TypeConstant resolveConstraints(ConstantPool pool)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveConstraints(pool);
            }

        Constant constId = getDefiningConstant();
        if (constId instanceof FormalConstant)
            {
            FormalConstant constFormal  = (FormalConstant) constId;
            return constFormal.getConstraintType();
            }

        return this;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        Constant constId = ensureResolvedConstant();

        IdentityConstant idClz;
        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case NativeClass:
                idClz = (IdentityConstant) constId;
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                idClz = ((PseudoConstant) constId).getDeclarationLevelClass();
                break;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return this;

            case Typedef:
                return ((TypedefConstant) constId).getReferredToType().
                    adoptParameters(pool, atypeParams);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constId);
            }

        if (atypeParams == null)
            {
            // this is a "normalization" call
            atypeParams = ConstantPool.NO_TYPES;
            }

        if (isTuple())
            {
            // copy parameters as is
            return pool.ensureParameterizedTypeConstant(this, atypeParams);
            }

        ClassStructure struct = (ClassStructure) idClz.getComponent();
        if (struct.isParameterized())
            {
            return pool.ensureParameterizedTypeConstant(this,
                struct.normalizeParameters(pool, atypeParams));
            }

        // this type cannot adopt anything
        return this;
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveAutoNarrowing(pool, fRetainParams, typeTarget);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case ThisClass:
                {
                IdentityConstant idClass  = ((ThisClassConstant) constant).getDeclarationLevelClass();
                TypeConstant     typeDecl = idClass.getType();
                if (typeTarget == null || !typeTarget.isA(typeDecl))
                    {
                    return typeDecl;
                    }

                // strip the immutability and access modifiers
                while (typeTarget instanceof ImmutableTypeConstant ||
                       typeTarget instanceof AccessTypeConstant)
                    {
                    typeTarget = typeTarget.getUnderlyingType();
                    }
                return typeTarget;
                }

            case ParentClass:
            case ChildClass:
                {
                IdentityConstant idClass = null;
                if (typeTarget != null && typeTarget.isSingleUnderlyingClass(true))
                    {
                    idClass = typeTarget.getSingleUnderlyingClass(true);
                    }

                return ((PseudoConstant) constant).resolveClass(idClass).getType();
                }

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                return this;
            }
        }

    @Override
    public boolean isNarrowedFrom(TypeConstant typeSuper, TypeConstant typeCtx)
        {
        assert typeSuper.isAutoNarrowing();

        if (!(typeSuper instanceof TerminalTypeConstant))
            {
            return false;
            }

        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            return false;
            }

        Constant constSuper = typeSuper.getDefiningConstant();
        if (constSuper instanceof PseudoConstant)
            {
            // this type represents a type in the D rows below, the super type represents the B rows
            // and the context type has the identity of D;
            // E extends D extends C extends B; X may extend B, but is not "related" to D
            //
            // valid scenarios
            // -----------  -------  --------  --------  ---------  -------  --------
            // Derived (D)  D        this:D     D        this:D     C        E
            // Base (B)     B        this:B     this:B   B          this:B   this:B

            // invalid scenarios
            // ---------    --------  --------
            // Derived (D)  X          X
            // Base (B)     this:B     B

            TypeConstant typeSuperR = ((PseudoConstant) constSuper).resolveClass(null).getType();
            return this.isA(typeSuperR) && (this.isA(typeCtx) || typeCtx.isA(this));
            }
        return false;
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case TypeParameter:
                {
                TypeParameterConstant idTypeParam = (TypeParameterConstant) constant;
                MethodConstant        idMethod    = idTypeParam.getMethod();
                MethodStructure       method      = (MethodStructure) idMethod.getComponent();
                if (method != null)
                    {
                    Parameter param = method.getParam(idTypeParam.getRegister());
                    if (param.getName().equals(sFormalName))
                        {
                        // The constraint type itself could be formal, for example (Array.x)
                        //   static <CompileType extends Hasher> Int hashCode(CompileType array)
                        // so trying to resolve a call, such as
                        //   Int[] array = ...
                        //   Int   hash = Array<Int>.hashCode(array);
                        // requires having the the actual type of "Array<Int>" to resolve the type
                        // parameter "CompileType" with the constraint type (Hasher<Element>)
                        // to the resolved type of "Hasher<Int>"
                        //
                        // To do that, first let's pretend that the types match and resolve
                        // the constraint type using that knowledge and only then validate
                        // the actual type against the resolved constraint.

                        TypeConstant typeConstraint = idTypeParam.getConstraintType().
                            resolveGenerics(getConstantPool(),
                                sName -> sFormalName.equals(sName) ? typeActual : null);
                        return typeActual.isA(typeConstraint)
                                ? typeActual
                                : null;
                        }
                    }
                break;
                }

            case Property:
                {
                PropertyConstant idProp = (PropertyConstant) constant;
                if (idProp.getName().equals(sFormalName))
                    {
                    TypeConstant typeConstraint = idProp.getConstraintType().
                        resolveGenerics(getConstantPool(),
                            sName -> sFormalName.equals(sName) ? typeActual : null);
                    return typeActual.isA(typeConstraint)
                            ? typeActual
                            : null;
                    }
                break;
                }

            case FormalTypeChild:
                // this shouldn't happen
                break;
            }
        return null;
        }

    @Override
    public boolean isTuple()
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isTuple();
            }

        Constant         constant = getDefiningConstant();
        IdentityConstant idClz;
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                return false;

            case Class:
                idClz = (ClassConstant) constant;
                break;

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().isTuple();

            case ThisClass:
            case ParentClass:
            case ChildClass:
                idClz = ((PseudoConstant) constant).getDeclarationLevelClass();
                break;

            default:
                // let's be tolerant to unresolved constants
                return false;
            }

        if (!idClz.equals(getConstantPool().clzTuple()))
            {
            Component component = idClz.getComponent();
            assert component instanceof ClassStructure;
            }

        return idClz.equals(getConstantPool().clzTuple()) ||
                ((ClassStructure) idClz.getComponent()).isTuple();
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().ensureTypeInfoInternal(errs);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case NativeClass:
                return super.buildTypeInfo(errs);

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                {
                ConstantPool pool           = getConstantPool();
                TypeConstant typeConstraint = ((FormalConstant) constant).getConstraintType();
                int          cInvalidations = pool.getInvalidationCount();

                if (typeConstraint.isAutoNarrowing())
                    {
                    typeConstraint = typeConstraint.resolveAutoNarrowingBase(pool);
                    }
                return new TypeInfo(this, typeConstraint.ensureTypeInfoInternal(errs), cInvalidations);
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass().getType()
                        .ensureTypeInfoInternal(errs);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isOnlyNullable()
        {
        TypeConstant typeResolved = resolveTypedefs();
        return this == typeResolved
                ? ensureResolvedConstant().equals(getConstantPool().clzNullable())
                : typeResolved.isOnlyNullable();
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return this;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().extendsClass(constClass);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).extendsClass(constClass);

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().extendsClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).extendsClass(constClass);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean containsFormalType()
        {
        return isFormalType();
        }

    @Override
    public boolean containsGenericType()
        {
        return isGenericType();
        }

    @Override
    public boolean containsTypeParameter()
        {
        return isTypeParameter();
        }

    @Override
    public Category getCategory()
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getCategory();
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these are always class types (not interface types)
                return Category.CLASS;

            case NativeClass:
                // native rebase is only for an interface
                return Category.IFACE;

            case Class:
                {
                // examine the structure to determine if it represents a class or interface
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return clz.getFormat() == Component.Format.INTERFACE
                        ? Category.IFACE : Category.CLASS;
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return Category.FORMAL;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clz = (ClassStructure) ((PseudoConstant) constant)
                        .getDeclarationLevelClass().getComponent();
                return clz.getFormat() == Component.Format.INTERFACE
                        ? Category.IFACE : Category.CLASS;
                }

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isSingleUnderlyingClass(fAllowInterface);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case NativeClass:
                // these are always class types (not interface types)
                return true;

            case Class:
                {
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return fAllowInterface || clz.getFormat() != Component.Format.INTERFACE;
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().
                        isSingleUnderlyingClass(fAllowInterface);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clz = (ClassStructure) ((PseudoConstant) constant)
                        .getDeclarationLevelClass().getComponent();
                return fAllowInterface || clz.getFormat() != Component.Format.INTERFACE;
                }

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getSingleUnderlyingClass(fAllowInterface);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these are always class types (not interface types)
                return (IdentityConstant) constant;

            case Class:
            case NativeClass:
                if (!fAllowInterface)
                    {
                    // must not be an interface
                    assert (((ClassConstant) constant).getComponent()).getFormat() != Component.Format.INTERFACE;
                    }
                return (IdentityConstant) constant;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().
                    getSingleUnderlyingClass(fAllowInterface);

            case ParentClass:
            case ChildClass:
            case ThisClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass();

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isExplicitClassIdentity(fAllowParams);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case NativeClass:
                return true;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return false;

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public Component.Format getExplicitClassFormat()
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getExplicitClassFormat();
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
                return Component.Format.MODULE;

            case Package:
                return Component.Format.PACKAGE;

            case Class:
                // get the class referred to and return its format
                return ((ClassConstant) constant).getComponent().getFormat();

            case ThisClass:
            case ParentClass:
            case ChildClass:
                // follow the indirection to the class structure
                return ((PseudoConstant) constant).getDeclarationLevelClass().getComponent().getFormat();

            default:
                throw new IllegalStateException("no class format for: " + constant);
            }
        }

    @Override
    public TypeConstant getExplicitClassInto()
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getExplicitClassInto();
            }

        Constant       constId = getDefiningConstant();
        ClassStructure structMixin;
        switch (constId.getFormat())
            {
            case Class:
                // get the class referred to and return its format
                structMixin = (ClassStructure) ((ClassConstant) constId).getComponent();
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                structMixin = (ClassStructure) ((PseudoConstant) constId).getDeclarationLevelClass().getComponent();
                break;

            default:
                throw new IllegalStateException("no class format for: " + constId);
            }

        if (structMixin == null || structMixin.getFormat() != Component.Format.MIXIN)
            {
            throw new IllegalStateException("mixin=" + structMixin);
            }

        return structMixin.getTypeInto();
        }

    @Override
    public boolean isIntoClassType()
        {
        return this.equals(getConstantPool().typeClass());
        }

    @Override
    public boolean isIntoPropertyType()
        {
        ConstantPool pool = getConstantPool();
        return this.equals(pool.typeProperty()) || this.isA(pool.typeRef());
        }

    @Override
    public TypeConstant getIntoPropertyType()
        {
        ConstantPool pool = getConstantPool();

        if (this.equals(pool.typeProperty()))
            {
            return pool.typeProperty();
            }
        if (this.isA(pool.typeVar()))
            {
            return pool.typeVar();
            }
        if (this.isA(pool.typeRef()))
            {
            return pool.typeRef();
            }
        return null;
        }

    @Override
    public boolean isIntoMethodType()
        {
        return this.equals(getConstantPool().typeMethod());
        }

    @Override
    public boolean isIntoVariableType()
        {
        return this.isA(getConstantPool().typeRef());
        }

    @Override
    public TypeConstant getIntoVariableType()
        {
        ConstantPool pool = getConstantPool();

        if (this.isA(pool.typeVar()))
            {
            return pool.typeVar();
            }
        if (this.isA(pool.typeRef()))
            {
            return pool.typeRef();
            }
        return null;
        }

    @Override
    public boolean isConstant()
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isConstant();
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                return true;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case NativeClass:
                return false;

            case Class:
                {
                ClassConstant idClass = (ClassConstant) constant;
                return ((ClassStructure) idClass.getComponent()).isConst();
                }

            default:
                throw new IllegalStateException("unexpected constant: " + constant);
            }
        }

    @Override
    public boolean isTypeOfType()
        {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.Typedef
                ? ((TypedefConstant) constId).getReferredToType().isTypeOfType()
                : this.equals(getConstantPool().typeType());
        }

    @Override
    public Argument getTypeArgument()
        {
        Constant constant = getDefiningConstant();
        if (constant.getFormat() == Format.TypeParameter)
            {
            TypeParameterConstant constTypeParam = (TypeParameterConstant) constant;

            return new Register(getType(), constTypeParam.getRegister());
            }

        return super.getTypeArgument();
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(
            TypeConstant typeRight, Access accessLeft, List<TypeConstant> listLeft)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
            }

        Constant constIdLeft = getDefiningConstant();
        switch (constIdLeft.getFormat())
            {
            case NativeClass:
                constIdLeft = ((NativeRebaseConstant) constIdLeft).getClassConstant();
                // fall through
            case Class:
                {
                IdentityConstant idLeft = (IdentityConstant) constIdLeft;
                ClassStructure clzLeft = (ClassStructure) idLeft.getComponent();

                assert clzLeft.getFormat() == Component.Format.INTERFACE;

                return clzLeft.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constIdLeft).getConstraintType().
                    isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constIdLeft).getDeclarationLevelClass().getType().
                    isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);

            default:
                throw new IllegalStateException("unexpected constant: " + constIdLeft);
            }
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().containsSubstitutableMethod(signature, access, fFunction, listParams);
            }

        Constant constIdThis = getDefiningConstant();
        switch (constIdThis.getFormat())
            {
            case Module:
            case Package:
            case Class:
                {
                IdentityConstant idThis  = (IdentityConstant) constIdThis;
                ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

                return clzThis.containsSubstitutableMethod(signature, access, fFunction, listParams);
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constIdThis).getConstraintType().
                    containsSubstitutableMethod(signature, access, fFunction, listParams);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constIdThis).getDeclarationLevelClass().getType().
                    containsSubstitutableMethod(signature, access, fFunction, listParams);

            default:
                throw new IllegalStateException("unexpected constant: " + constIdThis);
            }
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().checkConsumption(sTypeName, access, listParams);
            }

        Constant constId = getDefiningConstant();
        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Property: // formal types do not consume
            case TypeParameter:
            case FormalTypeChild:
                return Usage.NO;

            case Class:
                if (isTuple())
                    {
                    // Tuple consumes and produces every element type
                    for (TypeConstant constParam : listParams)
                        {
                        if (constParam.consumesFormalType(sTypeName, access)
                            ||
                            constParam.producesFormalType(sTypeName, access))
                            {
                            return Usage.YES;
                            }
                        }
                    }
                else if (!listParams.isEmpty())
                    {
                    ClassStructure clz = (ClassStructure) ((IdentityConstant) constId).getComponent();

                    Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

                    listParams = clz.normalizeParameters(ConstantPool.getCurrentPool(), listParams);

                    Iterator<TypeConstant>   iterParams = listParams.iterator();
                    Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

                    while (iterParams.hasNext())
                        {
                        TypeConstant constParam = iterParams.next();
                        String       sFormal    = iterNames.next().getValue();

                        if (constParam.consumesFormalType(sTypeName, access)
                                && clz.producesFormalType(sFormal, access, listParams)
                            ||
                            constParam.producesFormalType(sTypeName, access)
                                && clz.consumesFormalType(sFormal, access, listParams))
                            {
                            return Usage.YES;
                            }
                        }
                    }
                return Usage.NO;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constId).getDeclarationLevelClass().getType().
                    checkConsumption(sTypeName, access, listParams);

            default:
                throw new IllegalStateException("unexpected constant: " + constId);
            }
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().checkProduction(sTypeName, access, listParams);
            }

        Constant constId = getDefiningConstant();
        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case TypeParameter:
            case FormalTypeChild:
                return Usage.NO;

            case Property:
                return Usage.valueOf(((PropertyConstant) constId).getName().equals(sTypeName));

            case Class:
                if (isTuple())
                    {
                    // Tuple consumes and produces every element type
                    for (TypeConstant constParam : listParams)
                        {
                        if (constParam.producesFormalType(sTypeName, access)
                            ||
                            constParam.consumesFormalType(sTypeName, access))
                            {
                            return Usage.YES;
                            }
                        }
                    }
                else if (!listParams.isEmpty())
                    {
                    ClassStructure clz = (ClassStructure) ((IdentityConstant) constId).getComponent();

                    Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

                    listParams = clz.normalizeParameters(ConstantPool.getCurrentPool(), listParams);

                    Iterator<TypeConstant>   iterParams = listParams.iterator();
                    Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

                    while (iterParams.hasNext())
                        {
                        TypeConstant constParam = iterParams.next();
                        String       sFormal    = iterNames.next().getValue();

                        if (constParam.producesFormalType(sTypeName, access)
                                && clz.producesFormalType(sFormal, access, listParams)
                            ||
                            constParam.consumesFormalType(sTypeName, access)
                                && clz.consumesFormalType(sFormal, access, listParams))
                            {
                            return Usage.YES;
                            }
                        }
                    }
                return Usage.NO;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constId).getDeclarationLevelClass().getType().
                    checkProduction(sTypeName, access, listParams);

            default:
                throw new IllegalStateException("unexpected constant: " + constId);
            }
        }

    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getOpSupport(registry);
            }

        Constant constIdThis = getDefiningConstant();
        switch (constIdThis.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return registry.getTemplate((IdentityConstant) constIdThis);

            case NativeClass:
                return registry.getTemplate(((NativeRebaseConstant) constIdThis).getClassConstant());

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return registry.getTemplate(((PseudoConstant) constIdThis).getDeclarationLevelClass());

            default:
                throw new IllegalStateException("unexpected defining constant: " + constIdThis);
            }
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
        Constant constId = ensureResolvedConstant();
        if (constId.containsUnresolved())
            {
            return true;
            }
        if (getFormat() == Format.Typedef)
            {
            return ((TypedefConstant) constId).getReferredToType().containsUnresolved();
            }
        return false;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(ensureResolvedConstant());
        }

    @Override
    protected Object getLocator()
        {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.UnresolvedName
                ? null
                : constId;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof TerminalTypeConstant))
            {
            return -1;
            }

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
        return ensureResolvedConstant().getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constId = pool.register(ensureResolvedConstant());
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, ensureResolvedConstant().getPosition());
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        if (!isValidated())
            {
            if (!isSingleDefiningConstant())
                {
                // this can only happen if this type is a Typedef referring to a relational type
                TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
                return constId.getReferredToType().validate(errs) && super.validate(errs);
                }

            Constant constant = getDefiningConstant();
            switch (constant.getFormat())
                {
                case Module:
                case Package:
                case Class:
                case Property:
                case TypeParameter:
                case FormalTypeChild:
                case ThisClass:
                case ParentClass:
                case ChildClass:
                case NativeClass:
                    return super.validate(errs);

                case UnresolvedName:
                default:
                    // this is basically an illegal state exception
                    log(errs, Severity.ERROR, VE_UNKNOWN, constant.getValueString()
                            + " (" + constant.getFormat() + ')');
                    return true;
                }
            }

        return false;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return ensureResolvedConstant().hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that defines this type.
     */
    private transient int m_iDef;

    /**
     * The class referred to. May be an IdentityConstant (ModuleConstant, PackageConstant,
     * ClassConstant, TypedefConstant, PropertyConstant), or a PseudoConstant (ThisClassConstant,
     * ParentClassConstant, ChildClassConstant, TypeParameterConstant, or UnresolvedNameConstant).
     */
    private Constant m_constId;
    }
