package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.Register;

import org.xvm.runtime.ClassTemplate;

import org.xvm.runtime.Container;
import org.xvm.util.Hash;
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

        if (!constId.getFormat().isTypeable())
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
    public boolean isShared(ConstantPool poolOther)
        {
        Constant constant = m_constId;
        return switch (constant.getFormat())
            {
            case NativeClass, IsConst, IsEnum, IsModule, IsPackage, IsClass ->
                true;

            case Module, Package, Class ->
                ((IdentityConstant) constant).isShared(poolOther);

            case Property, TypeParameter, FormalTypeChild, DynamicFormal ->
                ((FormalConstant) constant).getParentConstant().isShared(poolOther);

            case ThisClass, ParentClass, ChildClass ->
                ((PseudoConstant) constant).getDeclarationLevelClass().isShared(poolOther);

            case Typedef ->
                ((TypedefConstant) constant).getParentConstant().isShared(poolOther);

            default ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            };
        }

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        Constant constant = ensureResolvedConstant();
        return switch (constant.getFormat())
            {
            case Module, Package, Class ->
                setIds.contains((IdentityConstant) constant);

            case Property, TypeParameter, FormalTypeChild ->
                setIds.contains(((FormalConstant) constant).getParentConstant());

            case ThisClass, ParentClass, ChildClass ->
                setIds.contains(((PseudoConstant) constant).getDeclarationLevelClass());

            case NativeClass, UnresolvedName, IsConst, IsEnum, IsModule, IsPackage, IsClass
                -> false;

            case Typedef ->
                ((TypedefConstant) constant).getReferredToType().isComposedOfAny(setIds);

            default ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            };
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
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
                // always immutable
                return true;

            case IsClass:
                return false;

            case Property:
            case DynamicFormal:
            case FormalTypeChild:
                {
                // a formal type for an immutable type must be an immutable or a service
                FormalConstant constFormal    = (FormalConstant) constant;
                TypeConstant   typeParent     = constFormal.getParentConstant().getType();
                TypeConstant   typeConstraint = constFormal.getConstraintType();
                return typeConstraint.isImmutable() ||
                        typeParent.getAccess() != Access.STRUCT && typeParent.isImmutable()
                            && !typeConstraint.isA(getConstantPool().typeService());
                }

            case TypeParameter:
                return ((FormalConstant) constant).getConstraintType().isImmutable();

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

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }

        // there is a possibility of this question asked during the constant registration
        // by resolveTypedefs() method; we need to play safe here
        ClassStructure clz = (ClassStructure) idClass.getComponent();
        return clz != null && clz.isImmutable();
        }

    @Override
    public boolean isService()
        {
        TypeConstant type = resolveTypedefs();
        if (type != this)
            {
            return type.isService();
            }

        Constant         constant = getDefiningConstant();
        IdentityConstant idClass;
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return false;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                return (((FormalConstant) constant)).getConstraintType().isService();

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

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }

        ClassStructure clz = (ClassStructure) idClass.getComponent();
        return clz != null && clz.isService();
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

        Constant         constant = getDefiningConstant();
        IdentityConstant idClz;
        switch (constant.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                return ((FormalConstant) constant).getConstraintType().containsGenericParam(sName);

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Module:
            case Package:
            case Class:
                idClz = (IdentityConstant) constant;
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                idClz = ((PseudoConstant) constant).getDeclarationLevelClass();
                break;

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        ClassStructure clz = (ClassStructure) idClz.getComponent();
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

        Constant         constant = getDefiningConstant();
        IdentityConstant idClz;
        switch (constant.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                assert listParams.isEmpty();

                return ((FormalConstant) constant).getConstraintType().
                    getGenericParamType(sName, listParams);

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Module:
            case Package:
            case Class:
                idClz = (IdentityConstant) constant;
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                idClz = ((PseudoConstant) constant).getDeclarationLevelClass();
                break;

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        ClassStructure clz        = (ClassStructure) idClz.getComponent();
        ConstantPool   pool       = getConstantPool();
        TypeConstant   typeActual = listParams.isEmpty()
                ? this
                : pool.ensureParameterizedTypeConstant(this, listParams.toArray(TypeConstant.NO_TYPES));

        return clz.getGenericParamType(pool, sName, typeActual);
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
        Constant resolved = constId.resolve();
        if (resolved != constId && resolved != null)
            {
            // note that this TerminalTypeConstant could not have previously been registered
            // with the pool because it was not resolved, so changing the reference to the
            // underlying constant is still safe at this point
            m_constId = constId = resolved;

            assert !constId.containsUnresolved();
            }

        return constId;
        }

    @Override
    public ResolutionResult resolveContributedName(
           String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveContributedName(sName, access, idMethod, collector);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return ResolutionResult.UNKNOWN;

            case NativeClass:
                constant = ((NativeRebaseConstant) constant).getClassConstant();
                // fall through
            case Module:
            case Package:
            case Class:
                {
                IdentityConstant idClz = (IdentityConstant) constant;
                if (idMethod != null)
                    {
                    if (idClz.isNestMateOf(idMethod.getClassIdentity()))
                        {
                        access = Access.PRIVATE;
                        }
                    else
                        {
                        IdentityConstant idParent = idClz.getParentConstant();
                        if (idParent instanceof MethodConstant && idMethod.isDescendant(idParent))
                            {
                            // the class is defined inside of the method
                            access = Access.PRIVATE;
                            }
                        }
                    }

                return idClz.getComponent().resolveName(sName, access, collector);
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass().getType().
                        resolveContributedName(sName, access, idMethod, collector);

            case Typedef:
                return ((TypedefConstant) constant).getReferredToType().
                    resolveContributedName(sName, access, idMethod, collector);

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
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveGenerics(pool, resolver);
            }

        Constant constId = getDefiningConstant();
        if (constId instanceof FormalConstant constFormal)
            {
            TypeConstant typeResolved = constFormal.resolve(resolver);
            if (typeResolved != null)
                {
                return typeResolved;
                }
            }

        return this;
        }

    @Override
    public TypeConstant resolveConstraints(boolean fPendingOnly)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveConstraints(fPendingOnly);
            }

        if (!fPendingOnly)
            {
            Constant constId = getDefiningConstant();
            if (constId instanceof FormalConstant constFormal)
                {
                return constFormal.getConstraintType().resolveConstraints(fPendingOnly);
                }
            }
        return this;
        }

    @Override
    public TypeConstant resolveDynamicConstraints(Register register)
        {
        if (isSingleDefiningConstant())
            {
            Constant constId = getDefiningConstant();
            if (constId instanceof DynamicFormalConstant constDynamic)
                {
                if (register == null || constDynamic.getRegister() == register)
                    {
                    return constDynamic.getConstraintType();
                    }
                }
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
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return this;

            case Class:
            case NativeClass:
                idClz = (IdentityConstant) constId;
                break;

            case ThisClass:
            case ParentClass:
            case ChildClass:
                idClz = ((PseudoConstant) constId).getDeclarationLevelClass();
                break;

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
    public TypeConstant[] collectGenericParameters()
        {
        Constant constId = ensureResolvedConstant();

        IdentityConstant idClz;
        switch (constId.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return TypeConstant.NO_TYPES;

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

            case Typedef:
                return ((TypedefConstant) constId).getReferredToType().
                    collectGenericParameters();

            default:
                throw new IllegalStateException("unexpected defining constant: " + constId);
            }

        if (isTuple())
            {
            return TypeConstant.NO_TYPES;
            }

        ClassStructure struct = (ClassStructure) idClz.getComponent();
        if (struct.isParameterized())
            {
            return struct.getFormalType().getParamTypesArray();
            }
        return TypeConstant.NO_TYPES;
        }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild)
        {
        return ensureResolvedConstant().isAutoNarrowing();
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return ensureResolvedConstant().isAutoNarrowing();
        }

    public TypeConstant ensureAutoNarrowing()
        {
        return isAutoNarrowing()
                ? this
                : getConstantPool().ensureThisTypeConstant(getDefiningConstant(), null);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().
                    resolveAutoNarrowing(pool, fRetainParams, typeTarget, idCtx);
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

                if (idCtx != null && idCtx.getType().isA(typeDecl))
                    {
                    TypeConstant typeCtx = pool.ensureThisTypeConstant(idCtx, null);

                    // apply the target's type parameters and annotations (if any)
                    if (typeTarget.isParamsSpecified())
                        {
                        typeCtx = pool.ensureParameterizedTypeConstant(typeCtx,
                                        typeTarget.getParamTypesArray().clone());
                        }
                    if (typeTarget.isAnnotated())
                        {
                        typeCtx = pool.ensureAnnotatedTypeConstant(typeCtx,
                                        typeTarget.getAnnotations().clone());
                        }
                    return typeCtx;
                    }

                // strip an access modifier
                return typeTarget.removeAccess();
                }

            case ParentClass:
                {
                ParentClassConstant constParent = (ParentClassConstant) constant;
                if (typeTarget != null)
                    {
                    if (typeTarget.isFormalType())
                        {
                        typeTarget = typeTarget.resolveConstraints();
                        }
                    if (typeTarget.isVirtualChild())
                        {
                        // if possible, retain the parent's type parameters
                        int           nDepth     = constParent.getDepth();
                        TypeConstant  typeParent = typeTarget.getParentType();
                        while (--nDepth > 0)
                            {
                            if (typeParent instanceof VirtualChildTypeConstant)
                                {
                                typeParent = typeParent.getParentType();
                                }
                            else
                                {
                                return constParent.getDeclarationLevelClass().getType();
                                }
                            }
                        return typeParent;
                        }
                    }
                return constParent.getDeclarationLevelClass().getType();
                }
            case ChildClass:
                // currently, not used
                return ((ChildClassConstant) constant).getDeclarationLevelClass().getType();

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                return this;
            }
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
                        if (typeActual.isFormalType())
                            {
                            // the only thing we could validate is that the "source" constraint (this)
                            // is know to fit the "destination" constraint (actual). However, there
                            // could be some context-specific knowledge that narrows the actual
                            // formal type, making this check too restrictive;
                            // let's leave the final assignability determination to the caller
                            return typeActual;
                            }

                        // The constraint type itself could be formal, for example (Array.x)
                        //   static <CompileType extends Hasher> Int hashCode(CompileType array)
                        // so trying to resolve a call, such as
                        //   Int[] array = ...
                        //   Int   hash = Array<Int>.hashCode(array);
                        // requires having the actual type of "Array<Int>" to resolve the type
                        // parameter "CompileType" with the constraint type Hasher<Element>
                        // to the resolved type of "Hasher<Int>"
                        //
                        // To do that, first let's pretend that the types match and resolve
                        // the constraint type using that knowledge and only then validate
                        // the actual type against the resolved constraint.

                        ConstantPool pool           = getConstantPool();
                        TypeConstant typeConstraint = idTypeParam.getConstraintType().
                            resolveConstraints().
                            resolveGenerics(pool,
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
                    ConstantPool pool = getConstantPool();
                    TypeConstant typeConstraint = idProp.getConstraintType().
                        resolveConstraints().
                        resolveGenerics(pool,
                            sName -> sFormalName.equals(sName) ? typeActual : null);
                    return typeActual.isA(typeConstraint)
                            ? typeActual
                            : null;
                    }
                break;
                }

            case FormalTypeChild:
            case DynamicFormal:
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
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return false;

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Class:
                idClz = (ClassConstant) constant;
                break;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
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

        if (idClz.equals(getConstantPool().clzTuple()))
            {
            return true;
            }

        ClassStructure clz = (ClassStructure) idClz.getComponent();
        if (clz == null)
            {
            throw new IllegalStateException("no ClassStructure for " + idClz);
            }
        return clz.isTuple();
        }

    @Override
    public boolean isNullable()
        {
        if (!isSingleDefiningConstant())
            {
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isNullable();
            }

        Constant constant = getDefiningConstant();
        return switch (constant.getFormat())
            {
            case Property, TypeParameter, FormalTypeChild, DynamicFormal ->
                ((FormalConstant) constant).getConstraintType().isNullable();

            default ->
                false;
            };
        }

    @Override
    public boolean isOnlyNullable()
        {
        TypeConstant typeResolved = resolveTypedefs();
        return this == typeResolved
                ? this.equals(getConstantPool().typeNullable()) ||
                  this.equals(getConstantPool().typeNull())
                : typeResolved.isOnlyNullable();
        }

    @Override
    public TypeConstant removeNullable()
        {
        if (!isSingleDefiningConstant())
            {
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().removeNullable();
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                if (((FormalConstant) constant).getConstraintType().isNullable())
                    {
                    // Note: we use the DifferenceType here to say that "this" formal type
                    //       *is not* Nullable, which is not quite the same as other usages
                    //       of DifferenceType; consider adding a new TypeConstant for that case,
                    //       for example "FormalDifference"...
                    ConstantPool pool = getConstantPool();
                    return pool.ensureDifferenceTypeConstant(this, pool.typeNullable());
                    }
                break;
            }

        return super.removeNullable();
        }

    @Override
    public TypeConstant andNot(ConstantPool pool, TypeConstant that)
        {
        if (!isSingleDefiningConstant())
            {
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().andNot(pool, that);
            }

        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                {
                FormalConstant constFormal    = (FormalConstant) constant;
                TypeConstant   typeConstraint = constFormal.getConstraintType();
                /*
                 * In a number of places in Ecstasy code we have a check that look like:
                 *
                 *   Element extends (immutable Object | Freezable)
                 *   Element e = ...;
                 *   if (!e.is(immutable Object))
                 *      {
                 *      // the type inference implication gets resolved to: e.is(Freezable)
                 *      }
                 *   if (!e.is(immutable Element))
                 *      {
                 *      // logically, the type inference implication here should be the same
                 *      // as above, but the logic in UnionTypeConstant.andNot()
                 *      // doesn't have enough knowledge to figure that out.
                 *      // The logic below answers this very narrow scenario..
                 *      }
                 */

                if (that.isImmutabilitySpecified())
                    {
                    TypeConstant thatBase = that.removeImmutable();
                    if (thatBase.equals(this) ||
                        thatBase.isFormalType() && thatBase.resolveConstraints().equals(typeConstraint))
                        {
                        that = pool.ensureImmutableTypeConstant(pool.typeObject());
                        }
                    }

                TypeConstant typeR = typeConstraint.andNot(pool, that);
                return typeR == null
                        ? null
                        : typeR.equals(typeConstraint)
                            ? this
                            : this.combine(pool, typeR);
                }
            }

        return super.andNot(pool, that);
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
        return switch (constant.getFormat())
            {
            case Module, Package, Class ->
                ((ClassStructure) ((IdentityConstant) constant).getComponent()).extendsClass(constClass);

            case Property, TypeParameter, FormalTypeChild, DynamicFormal ->
                ((FormalConstant) constant).getConstraintType().extendsClass(constClass);

            case ThisClass, ParentClass, ChildClass ->
                ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass().
                    getComponent()).extendsClass(constClass);

            default ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            };
        }

    @Override
    public boolean containsFormalType(boolean fAllowParams)
        {
        return isFormalType();
        }

    @Override
    public void collectFormalTypes(boolean fAllowParams, Set<TypeConstant> setFormal)
        {
        if (isFormalType())
            {
            setFormal.add(this);
            }
        }

    @Override
    public boolean containsDynamicType(Register register)
        {
        if (isDynamicType())
            {
            if (register == null)
                {
                return true;
                }

            DynamicFormalConstant constDynamic = (DynamicFormalConstant) getDefiningConstant();
            return constDynamic.getRegister() == register;
            }
        return false;
        }

    @Override
    public boolean containsGenericType(boolean fAllowParams)
        {
        return isGenericType();
        }

    @Override
    public boolean containsTypeParameter(boolean fAllowParams)
        {
        return isTypeParameter();
        }

    @Override
    public boolean containsRecursiveType()
        {
        if (!isSingleDefiningConstant())
            {
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().containsRecursiveType();
            }
        return false;
        }

    @Override
    public boolean containsFunctionType()
        {
        if (isSingleDefiningConstant())
            {
            return getDefiningConstant().equals(getConstantPool().clzFunction());
            }

        TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
        return constId.getReferredToType().containsFunctionType();
        }

    @Override
    public boolean isFormalTypeSequence()
        {
        return isGenericType() &&
            ((FormalConstant) getDefiningConstant()).getConstraintType().isFormalTypeSequence();
        }

    @Override
    public boolean isDynamicType()
        {
        if (isSingleDefiningConstant())
            {
            Constant constant = getDefiningConstant();
            return constant.getFormat() == Format.DynamicFormal;
            }
        return false;
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
                if (clz == null)
                    {
                    throw new IllegalStateException("missing class for constant: " + constant);
                    }
                return clz.getFormat() == Component.Format.INTERFACE
                        ? Category.IFACE : Category.CLASS;
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
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

            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return Category.OTHER;

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

            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return false;

            case Class:
                {
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return fAllowInterface || clz.getFormat() != Component.Format.INTERFACE;
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
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
            case NativeClass:
                // these are always class types (not interface types)
                return (IdentityConstant) constant;

            case Class:
                assert fAllowInterface ||
                       (((ClassConstant) constant).getComponent()).getFormat() != Component.Format.INTERFACE;
                return (IdentityConstant) constant;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
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
        return switch (constant.getFormat())
            {
            case Module, Package, Class, ThisClass, ParentClass, ChildClass, NativeClass ->
                true;

            case Property, TypeParameter, FormalTypeChild, DynamicFormal, IsConst,
                 IsEnum, IsModule, IsPackage, IsClass ->
                false;

            default ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            };
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
        return switch (constant.getFormat())
            {
            case Module ->
                Component.Format.MODULE;

            case Package ->
                Component.Format.PACKAGE;

            case Class ->
                // get the class referred to and return its format
                ((ClassConstant) constant).getComponent().getFormat();

            case ThisClass, ParentClass, ChildClass ->
                // follow the indirection to the class structure
                ((PseudoConstant) constant).getDeclarationLevelClass().getComponent().getFormat();

            default ->
                throw new IllegalStateException("no class format for: " + constant);
            };
        }

    @Override
    public TypeConstant getExplicitClassInto(boolean fResolve)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getExplicitClassInto(fResolve);
            }

        Constant       constId = getDefiningConstant();
        ClassStructure structMixin = switch (constId.getFormat())
            {
            case Class ->
                // get the class referred to and return its format
                (ClassStructure) ((ClassConstant) constId).getComponent();

            case ThisClass, ParentClass, ChildClass ->
                (ClassStructure) ((PseudoConstant) constId).getDeclarationLevelClass().getComponent();

            default ->
                throw new IllegalStateException("no class format for: " + constId);
            };

        if (structMixin == null ||
                (structMixin.getFormat() != Component.Format.ANNOTATION &&
                 structMixin.getFormat() != Component.Format.MIXIN))
            {
            throw new IllegalStateException("Invalid format for " + structMixin);
            }

        return structMixin.getTypeInto();
        }

    @Override
    public boolean isIntoPropertyType()
        {
        return this.equals(getConstantPool().typeProperty()) || isIntoVariableType();
        }

    @Override
    public TypeConstant getIntoPropertyType()
        {
        TypeConstant typeProp = getConstantPool().typeProperty();

        return this.equals(typeProp)
                ? typeProp
                : getIntoVariableType();
        }

    @Override
    public boolean isIntoMetaData(TypeConstant typeTarget, boolean fStrict)
        {
        return fStrict
                ? typeTarget.isSingleUnderlyingClass(true) &&
                    this.equals(typeTarget.getSingleUnderlyingClass(true).getType())
                : this.isA(typeTarget);
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
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
                return true;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case NativeClass:
            case IsClass:
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
                : this.isExplicitClassIdentity(true) &&
                  this.getDefiningConstant().equals(getConstantPool().clzType());
        }

    @Override
    public TypeConstant widenEnumValueTypes()
        {
        return isEnumValue() && !isOnlyNullable()
                ? getSingleUnderlyingClass(false).getNamespace().getType()
                : this;
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().ensureTypeInfo(idClass, errs);
            }

        if (isFormalType())
            {
            ConstantPool pool    = getConstantPool();
            int          cInvals = pool.getInvalidationCount();
            if (isGenericType())
                {
                // check if the formal type could be resolved in the context of the specified class
                TypeConstant typeR = this.resolveGenerics(pool, idClass.getFormalType());
                if (typeR != this)
                    {
                    TypeInfo infoR = typeR.ensureTypeInfo(idClass, errs);
                    assert isComplete(infoR);
                    return new TypeInfo(this, infoR, cInvals);
                    }
                }

            TypeConstant typeConstraint = ((FormalConstant) getDefiningConstant()).getConstraintType();
            if (typeConstraint.containsAutoNarrowing(false))
                {
                typeConstraint = typeConstraint.resolveAutoNarrowingBase();
                }
            TypeInfo infoConstraint = typeConstraint.ensureTypeInfo(idClass, errs);
            assert isComplete(infoConstraint);
            return new TypeInfo(this, infoConstraint, cInvals);
            }

        return super.ensureTypeInfo(idClass, errs);
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

            case IsClass:
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
                return ((KeywordConstant) constant).getBaseType().ensureTypeInfoInternal(errs);

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                {
                TypeConstant typeConstraint = ((FormalConstant) constant).getConstraintType();
                int          cInvalidations = getConstantPool().getInvalidationCount();

                if (typeConstraint.containsAutoNarrowing(false))
                    {
                    typeConstraint = typeConstraint.resolveAutoNarrowingBase();
                    }
                TypeInfo infoConstraint = typeConstraint.ensureTypeInfoInternal(errs);
                return isComplete(infoConstraint)
                        ? new TypeInfo(this, infoConstraint, cInvalidations)
                        : null;
                }

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        if (isFormalType())
            {
            if (!isSingleDefiningConstant())
                {
                // a typedef for a formal type
                TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
                return constId.getReferredToType().calculateRelationToLeft(typeLeft);
                }

            FormalConstant constRight     = (FormalConstant) getDefiningConstant();
            TypeConstant   typeConstraint = constRight.getConstraintType();
            if (isDynamicType())
                {
                DynamicFormalConstant constDynamic = (DynamicFormalConstant) constRight;
                FormalConstant        constFormal  = constDynamic.getFormalConstant();

                // check the formal type constraint first
                Relation relation = constFormal.getConstraintType().calculateRelation(typeLeft);
                if (relation != Relation.INCOMPATIBLE)
                    {
                    return relation;
                    }

                Register regRight = constDynamic.getRegister();
                if (regRight != null)
                    {
                    if (typeLeft.containsDynamicType(regRight))
                        {
                        // the dynamic type is allowed to be assigned from its constraint;
                        // the run-time will be responsible for the actual cast check
                        typeLeft = typeLeft.resolveDynamicConstraints(regRight);
                        }
                    }
                return typeConstraint.calculateRelation(typeLeft);
                }

            Relation relation = typeConstraint.calculateRelation(typeLeft);
            if (relation != Relation.INCOMPATIBLE)
                {
                return relation;
                }
            }
        return super.calculateRelationToLeft(typeLeft);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        if (isDynamicType())
            {
            // the dynamic type is allowed to be assigned from its constraint;
            // the run-time will be responsible for the actual cast check
            TypeConstant typeConstraint =
                    ((DynamicFormalConstant) getDefiningConstant()).getConstraintType();
            return typeRight.calculateRelation(typeConstraint);
            }

        if (isSingleDefiningConstant())
            {
            Constant constLeft = getDefiningConstant();
            if (constLeft instanceof KeywordConstant)
                {
                if (constLeft.getFormat() == Format.IsClass)
                    {
                    return typeRight.getCategory() == Category.CLASS
                        ? Relation.IS_A
                        : Relation.INCOMPATIBLE;
                    }

                if (typeRight.isSingleUnderlyingClass(true))
                    {
                    ClassStructure clzRight = (ClassStructure)
                        typeRight.getSingleUnderlyingClass(true).getComponent();
                    Component.Format formatRight = clzRight.getFormat();

                    if (formatRight == Component.Format.ANNOTATION ||
                        formatRight == Component.Format.MIXIN)
                        {
                        return typeRight.getExplicitClassInto().calculateRelation(this);
                        }

                    return switch (constLeft.getFormat())
                        {
                        case IsConst ->
                            switch (formatRight)
                                {
                                case CONST, ENUMVALUE, PACKAGE, MODULE ->
                                    Relation.IS_A;
                                default ->
                                    Relation.INCOMPATIBLE;
                                };

                        case IsEnum ->
                            formatRight == Component.Format.ENUMVALUE
                                    ? Relation.IS_A
                                    : Relation.INCOMPATIBLE;

                        case IsModule ->
                            formatRight == Component.Format.MODULE
                                    ? Relation.IS_A
                                    : Relation.INCOMPATIBLE;

                        case IsPackage ->
                            formatRight == Component.Format.MODULE ||
                            formatRight == Component.Format.PACKAGE
                                    ? Relation.IS_A
                                    : Relation.INCOMPATIBLE;

                        default ->
                            throw new IllegalStateException();
                        };
                    }
                }
            }

        return super.calculateRelationToRight(typeRight);
        }

    @Override
    public boolean isContravariantParameter(TypeConstant typeBase, TypeConstant typeCtx)
        {
        if (super.isContravariantParameter(typeBase, typeCtx))
            {
            return true;
            }

        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().isContravariantParameter(typeBase, typeCtx);
            }

        if (!typeBase.isSingleDefiningConstant() || typeBase.isParamsSpecified())
            {
            return false;
            }

        Constant constIdThis = this.getDefiningConstant();
        Constant constIdBase = typeBase.getDefiningConstant();

        if (constIdThis.getFormat() != constIdBase.getFormat())
            {
            return false;
            }

        switch (constIdBase.getFormat())
            {
            case Module:
            case Package:
                return false;

            case Class:
            case NativeClass:
                return constIdThis.getType().equals(constIdBase.getType());

            case Property:
                {
                PropertyConstant idBase = (PropertyConstant) constIdBase;
                return ((PropertyConstant) constIdThis).getName().equals(idBase.getName());
                }

            case TypeParameter:
                {
                TypeParameterConstant idBase = (TypeParameterConstant) constIdBase;
                TypeParameterConstant idThis = (TypeParameterConstant) constIdThis;

                return idThis.getRegister() == idBase.getRegister() ||
                       idThis.getName().equals(idBase.getName());
                }

            case FormalTypeChild:
                {
                FormalTypeChildConstant idBase = (FormalTypeChildConstant) constIdBase;
                return ((FormalTypeChildConstant) constIdThis).getName().equals(idBase.getName());
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                PseudoConstant constBase = (PseudoConstant) constIdBase;
                PseudoConstant constThis = (PseudoConstant) constIdThis;
                if (constBase.isCongruentWith(constThis))
                    {
                    // the declaration types must be compatible
                    TypeConstant typeDeclBase = constBase.getDeclarationLevelClass().getType();
                    TypeConstant typeDeclThis = constThis.getDeclarationLevelClass().getType();
                    return typeDeclBase.isA(typeDeclThis) || typeDeclThis.isA(typeDeclBase);
                    }
                return false;
                }

            default:
                throw new IllegalStateException("unexpected constant: " + constIdBase);
            }
        }

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
                IdentityConstant idLeft  = (IdentityConstant) constIdLeft;
                ClassStructure   clzLeft = (ClassStructure) idLeft.getComponent();

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
            case NativeClass:
                constIdThis = ((NativeRebaseConstant) constIdThis).getClassConstant();
                // fall through
            case Module:
            case Package:
            case Class:
                {
                IdentityConstant idThis  = (IdentityConstant) constIdThis;
                ClassStructure   clzThis = (ClassStructure) idThis.getComponent();

                return clzThis.containsSubstitutableMethod(
                        getConstantPool(), signature, access, fFunction, listParams);
                }

            case Property:
            case TypeParameter:
            case FormalTypeChild:
            case DynamicFormal:
                return ((FormalConstant) constIdThis).getConstraintType().
                    containsSubstitutableMethod(signature, access, fFunction, listParams);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constIdThis).getDeclarationLevelClass().getType().
                    containsSubstitutableMethod(signature, access, fFunction, listParams);

            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return ((KeywordConstant) constIdThis).getBaseType().
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
            case TypeParameter:
            case FormalTypeChild:
            case Property: // formal types do not consume
            case DynamicFormal:
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return Usage.NO;

            case NativeClass:
                constId = ((NativeRebaseConstant) constId).getClassConstant();
                // fall through
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
                    ConstantPool   pool = getConstantPool();
                    ClassStructure clz  = (ClassStructure) ((IdentityConstant) constId).getComponent();

                    Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

                    listParams = clz.normalizeParameters(pool, listParams);

                    Iterator<TypeConstant>   iterParams = listParams.iterator();
                    Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

                    while (iterParams.hasNext())
                        {
                        TypeConstant constParam = iterParams.next();
                        String       sFormal    = iterNames.next().getValue();

                        if (constParam.consumesFormalType(sTypeName, access)
                                && clz.producesFormalType(pool, sFormal, access, listParams)
                            ||
                            constParam.producesFormalType(sTypeName, access)
                                && clz.consumesFormalType(pool, sFormal, access, listParams))
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
            case IsConst:
            case IsEnum:
            case IsModule:
            case IsPackage:
            case IsClass:
                return Usage.NO;

            case Property:
                return Usage.valueOf(((PropertyConstant) constId).getName().equals(sTypeName));

            case DynamicFormal:
                {
                FormalConstant constFormal = ((DynamicFormalConstant) constId).getFormalConstant();
                return Usage.valueOf(constFormal instanceof PropertyConstant &&
                        constFormal.getName().equals(sTypeName));
                }

            case NativeClass:
                constId = ((NativeRebaseConstant) constId).getClassConstant();
                // fall through
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
                    ConstantPool   pool = getConstantPool();
                    ClassStructure clz  = (ClassStructure) ((IdentityConstant) constId).getComponent();

                    Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

                    listParams = clz.normalizeParameters(pool, listParams);

                    Iterator<TypeConstant>   iterParams = listParams.iterator();
                    Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

                    while (iterParams.hasNext())
                        {
                        TypeConstant constParam = iterParams.next();
                        String       sFormal    = iterNames.next().getValue();

                        if (constParam.producesFormalType(sTypeName, access)
                                && clz.producesFormalType(pool, sFormal, access, listParams)
                            ||
                            constParam.consumesFormalType(sTypeName, access)
                                && clz.consumesFormalType(pool, sFormal, access, listParams))
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
    public ClassTemplate getTemplate(Container container)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().getTemplate(container);
            }

        Constant constIdThis = getDefiningConstant();
        return switch (constIdThis.getFormat())
            {
            case Module, Package, Class ->
                container.getTemplate((IdentityConstant) constIdThis);

            case NativeClass ->
                container.getTemplate(((NativeRebaseConstant) constIdThis).getClassConstant());

            case ThisClass, ParentClass, ChildClass ->
                container.getTemplate(((PseudoConstant) constIdThis).getDeclarationLevelClass());

            default ->
                throw new IllegalStateException("unexpected defining constant: " + constIdThis);
            };
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
        if (isHashCached())
            {
            return false;
            }

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
        if (!(obj instanceof TerminalTypeConstant that))
            {
            return -1;
            }

        Constant constThis = this.m_constId.resolve();
        Constant constThat = that.m_constId.resolve();
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
                case DynamicFormal:
                case ThisClass:
                case ParentClass:
                case ChildClass:
                case NativeClass:
                    return super.validate(errs);

                case IsConst:
                case IsEnum:
                case IsModule:
                case IsPackage:
                case IsClass:
                    break;

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
    protected int computeHashCode()
        {
        return Hash.of(ensureResolvedConstant());
        }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that defines this type.
     */
    private transient int m_iDef;

    /**
     * The class referred to. It may be an IdentityConstant (ModuleConstant, PackageConstant,
     * ClassConstant, TypedefConstant, PropertyConstant), or a PseudoConstant (ThisClassConstant,
     * ParentClassConstant, ChildClassConstant, TypeParameterConstant, or UnresolvedNameConstant).
     */
    private Constant m_constId;
    }