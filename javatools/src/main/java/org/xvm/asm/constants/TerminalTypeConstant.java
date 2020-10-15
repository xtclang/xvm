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
        switch (constant.getFormat())
            {
            case NativeClass:
                return true;

            case Module:
            case Package:
            case Class:
                return ((IdentityConstant) constant).isShared(poolOther);

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getParentConstant().isShared(poolOther);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass().isShared(poolOther);

            case Typedef:
                return ((TypedefConstant) constant).getParentConstant().isShared(poolOther);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        Constant constant = ensureResolvedConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return setIds.contains((IdentityConstant) constant);

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return setIds.contains(((FormalConstant) constant).getParentConstant());

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return setIds.contains(((PseudoConstant) constant).getDeclarationLevelClass());

            case NativeClass:
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

        // there is a possibility of this question asked during the constant registration
        // by resolveTypedefs() method; we need to play safe here
        ClassStructure clz = (ClassStructure) idClass.getComponent();
        return clz != null && clz.isImmutable();
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
            case Module:
            case Package:
                return false;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().containsGenericParam(sName);

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Class:
                idClz = (ClassConstant) constant;
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
            case Module:
            case Package:
                return null;

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().
                    getGenericParamType(sName, listParams);

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Class:
                idClz = (ClassConstant) constant;
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
    public ResolutionResult resolveContributedName(String sName, Access access, ResolutionCollector collector)
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveContributedName(sName, access, collector);
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

                return constClz.getComponent().resolveName(sName, access, collector);
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass().getType().
                        resolveContributedName(sName, access, collector);

            case Typedef:
                return ((TypedefConstant) constant).getReferredToType().
                    resolveContributedName(sName, access, collector);

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
    public TypeConstant resolveConstraints()
        {
        if (!isSingleDefiningConstant())
            {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = (TypedefConstant) ensureResolvedConstant();
            return constId.getReferredToType().resolveConstraints();
            }

        Constant constId = getDefiningConstant();
        if (constId instanceof FormalConstant)
            {
            FormalConstant constFormal  = (FormalConstant) constId;
            return constFormal.getConstraintType().resolveConstraints();
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
            case Module:
            case Package:
            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return TypeConstant.NO_TYPES;

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
                if (typeTarget != null && typeTarget.isExplicitClassIdentity(true))
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
                        // requires having the the actual type of "Array<Int>" to resolve the type
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

            case NativeClass:
                idClz = ((NativeRebaseConstant) constant).getClassConstant();
                break;

            case Class:
                idClz = (ClassConstant) constant;
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
        switch (constant.getFormat())
            {
            case Property:
            case TypeParameter:
            case FormalTypeChild:
                return ((FormalConstant) constant).getConstraintType().isNullable();
            }

        return false;
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
                 *      // as above, but the logic in IntersectionTypeConstant.andNot()
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
                return typeR.equals(typeConstraint)
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
    public boolean containsFormalType(boolean fAllowParams)
        {
        return isFormalType();
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
    public boolean isFormalTypeSequence()
        {
        return isGenericType() &&
            ((FormalConstant) getDefiningConstant()).getConstraintType().isFormalTypeSequence();
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
            case NativeClass:
                // these are always class types (not interface types)
                return (IdentityConstant) constant;

            case Class:
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
                : this.isExplicitClassIdentity(true) &&
                  this.getDefiningConstant().equals(getConstantPool().clzType());
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
            if (typeConstraint.isAutoNarrowing())
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

            case Property:
            case TypeParameter:
            case FormalTypeChild:
                {
                TypeConstant typeConstraint = ((FormalConstant) constant).getConstraintType();
                int          cInvalidations = getConstantPool().getInvalidationCount();

                if (typeConstraint.isAutoNarrowing())
                    {
                    typeConstraint = typeConstraint.resolveAutoNarrowingBase();
                    }
                TypeInfo infoConstraint = typeConstraint.ensureTypeInfoInternal(errs);
                return isComplete(infoConstraint)
                        ? new TypeInfo(this, infoConstraint, cInvalidations)
                        : null;
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                TypeConstant typeDeclared   = ((PseudoConstant) constant).getDeclarationLevelClass().getType();
                int          cInvalidations = getConstantPool().getInvalidationCount();

                TypeInfo infoDeclared = typeDeclared.ensureTypeInfoInternal(errs);
                return isComplete(infoDeclared)
                        ? new TypeInfo(this, infoDeclared, cInvalidations)
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

            TypeConstant typeConstraint = ((FormalConstant) getDefiningConstant()).getConstraintType();
            Relation     relation       = typeConstraint.calculateRelation(typeLeft);
            if (relation != Relation.INCOMPATIBLE)
                {
                return relation;
                }
            }
        return super.calculateRelationToLeft(typeLeft);
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
                PropertyConstant idRight = (PropertyConstant) constIdBase;
                return ((PropertyConstant) constIdThis).getName().equals(idRight.getName());
                }

            case TypeParameter:
                {
                TypeParameterConstant idRight = (TypeParameterConstant) constIdBase;
                return ((TypeParameterConstant) constIdThis).getRegister() == idRight.getRegister();
                }

            case FormalTypeChild:
                {
                FormalTypeChildConstant idRight = (FormalTypeChildConstant) constIdBase;
                return ((FormalTypeChildConstant) constIdThis).getName().equals(idRight.getName());
                }

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                PseudoConstant idRight = (PseudoConstant) constIdBase;
                return idRight.isCongruentWith((PseudoConstant) constIdThis);
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
            case TypeParameter:
            case FormalTypeChild:
            case Property: // formal types do not consume
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
                return Usage.NO;

            case Property:
                return Usage.valueOf(((PropertyConstant) constId).getName().equals(sTypeName));

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
