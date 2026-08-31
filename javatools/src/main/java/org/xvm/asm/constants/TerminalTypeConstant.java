package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import static org.xvm.util.Handy.writeMagnitude;
import static org.xvm.util.Handy.copyOf;


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
public sealed class TerminalTypeConstant
        extends TypeConstant
        permits RecursiveTypeConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param constId a ModuleConstant, PackageConstant, or ClassConstant
     */
    public TerminalTypeConstant(ConstantPool pool, Constant constId) {
        super(pool);

        if (!constId.getFormat().isTypeable()) {
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
            throws IOException {
        super(pool);

        m_iDef = readIndex(in);
    }

    @Override
    protected void resolveConstants() {
        m_constId = getConstantPool().getConstant(m_iDef);
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isShared(ConstantPool poolOther) {
        DefiningConstant defining = DefiningConstant.of(m_constId);
        return switch (defining) {
            case NativeRebaseConstant ignored -> true;
            case KeywordConstant ignored      -> true;

            case ModuleConstant constant  -> constant.isShared(poolOther);
            case PackageConstant constant -> constant.isShared(poolOther);
            case ClassConstant constant   -> constant.isShared(poolOther);

            case FormalConstant constant ->
                constant.getParentConstant().isShared(poolOther);

            case ThisClassConstant constant ->
                constant.getDeclarationLevelClass().isShared(poolOther);
            case ParentClassConstant constant ->
                constant.getDeclarationLevelClass().isShared(poolOther);
            case ChildClassConstant constant ->
                constant.getDeclarationLevelClass().isShared(poolOther);

            case TypedefConstant constant ->
                constant.getParentConstant().isShared(poolOther);

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, SignatureConstant _, UnresolvedNameConstant _,
                 ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds) {
        DefiningConstant defining = DefiningConstant.of(ensureResolvedConstant());
        return switch (defining) {
            case NativeRebaseConstant ignored  -> false;
            case KeywordConstant ignored       -> false;
            case UnresolvedNameConstant ignored-> false;

            case ModuleConstant constant  -> setIds.contains(constant);
            case PackageConstant constant -> setIds.contains(constant);
            case ClassConstant constant   -> setIds.contains(constant);

            // note: dynamic formal constants were never composable and stay refused below
            case DynamicFormalConstant constant ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            case FormalConstant constant  -> setIds.contains(constant.getParentConstant());

            case ThisClassConstant constant ->
                setIds.contains(constant.getDeclarationLevelClass());
            case ParentClassConstant constant ->
                setIds.contains(constant.getDeclarationLevelClass());
            case ChildClassConstant constant ->
                setIds.contains(constant.getDeclarationLevelClass());

            case TypedefConstant constant ->
                constant.getReferredToType().isComposedOfAny(setIds);

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, SignatureConstant _, ExpressionConstant _,
                 DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    @Override
    public boolean isImmutabilitySpecified() {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isImmutabilitySpecified();
    }

    @Override
    public boolean isImmutable() {
        TypeConstant type = resolveTypedefs();
        if (type != this) {
            return type.isImmutable();
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _ -> true;

            case KeywordConstant constant -> constant.getFormat() != Format.IsClass;

            case TypeParameterConstant constant -> constant.getConstraintType().isImmutable();

            case FormalConstant constant -> {
                // a formal type for an immutable type must be an immutable or a service
                TypeConstant typeParent     = constant.getParentConstant().getType();
                TypeConstant typeConstraint = constant.getConstraintType();
                yield typeConstraint.isImmutable() ||
                        typeParent.getAccess() != Access.STRUCT && typeParent.isImmutable()
                            && !typeConstraint.isA(getConstantPool().typeService());
            }

            case NativeRebaseConstant constant -> isImmutableClass(constant.getClassConstant());
            case ClassConstant constant        -> isImmutableClass(constant);
            case ThisClassConstant constant    -> isImmutableClass(constant.getDeclarationLevelClass());
            case ParentClassConstant constant  -> isImmutableClass(constant.getDeclarationLevelClass());
            case ChildClassConstant constant   -> isImmutableClass(constant.getDeclarationLevelClass());

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    /**
     * @param idClass  the identity of the class to test
     *
     * @return true iff the specified class is known to be immutable
     */
    private boolean isImmutableClass(IdentityConstant idClass) {
        // there is a possibility of this question asked during the constant registration
        // by resolveTypedefs() method; we need to play safe here
        ClassStructure clz = (ClassStructure) idClass.getComponent();
        return clz != null && clz.isImmutable();
    }

    @Override
    public boolean isService() {
        TypeConstant type = resolveTypedefs();
        if (type != this) {
            return type.isService();
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _ -> false;
            case KeywordConstant ignored             -> false;

            case FormalConstant constant -> constant.getConstraintType().isService();

            case NativeRebaseConstant constant -> isServiceClass(constant.getClassConstant());
            case ClassConstant constant        -> isServiceClass(constant);
            case ThisClassConstant constant    -> isServiceClass(constant.getDeclarationLevelClass());
            case ParentClassConstant constant  -> isServiceClass(constant.getDeclarationLevelClass());
            case ChildClassConstant constant   -> isServiceClass(constant.getDeclarationLevelClass());

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    /**
     * @param idClass  the identity of the class to test
     *
     * @return true iff the specified class is known to be a service
     */
    private boolean isServiceClass(IdentityConstant idClass) {
        ClassStructure clz = (ClassStructure) idClass.getComponent();
        return clz != null && clz.isService();
    }

    @Override
    public boolean isAccessSpecified() {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isAccessSpecified();
    }

    @Override
    public Access getAccess() {
        TypeConstant type = resolveTypedefs();
        return type == this
                ? Access.PUBLIC
                : type.getAccess();
    }

    @Override
    public boolean isAccessModifiable() {
        return !isFormalType();
    }

    @Override
    public boolean isParamsSpecified() {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isParamsSpecified();
    }

    @Override
    public int getMaxParamsCount() {
        if (!isSingleDefiningConstant()) {
            // this can happen if this type is a Typedef referring to a relational type
            return 0;
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _ -> 0;

            // note: dynamic formal constants were never counted and stay refused below
            case DynamicFormalConstant constant ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            case FormalConstant ignored -> 0;

            // note: native rebase constants were never counted and stay refused below
            case NativeRebaseConstant constant ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            // examine the structure to determine if it represents a class or interface (TODO GG - is this comment just wrong?)
            case ClassConstant constant ->
                ((ClassStructure) constant.getComponent()).getTypeParamCount();

            case ThisClassConstant constant -> declaredTypeParamCount(constant);
            case ParentClassConstant constant -> declaredTypeParamCount(constant);
            case ChildClassConstant constant -> declaredTypeParamCount(constant);

            case KeywordConstant _, DecoratedClassConstant _, MethodConstant _,
                 MultiMethodConstant _, PureIdentityConstant _, TypedefConstant _,
                 SignatureConstant _, UnresolvedNameConstant _, ExpressionConstant _,
                 DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    /**
     * @param constant  a this/parent/child class pseudo constant
     *
     * @return the number of type parameters declared by its declaration-level class
     */
    private static int declaredTypeParamCount(PseudoConstant constant) {
        return ((ClassStructure) constant.getDeclarationLevelClass().getComponent())
                .getTypeParamCount();
    }

    @Override
    public boolean containsGenericParam(String sName) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().containsGenericParam(sName);
        }

        DefiningConstant defining = getDefiningConstant();
        if (defining instanceof FormalConstant constant) {
            return constant.getConstraintType().containsGenericParam(sName);
        }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        ClassStructure clz = (ClassStructure) definingClassId(defining).getComponent();
        return clz.containsGenericParamType(sName);
    }

    /**
     * @param defining  a defining constant of a class-backed terminal kind
     *
     * @return the class identity of the specified defining constant (module, package, class,
     *         native rebase, or this/parent/child class); any other kind is refused with the
     *         historical diagnostic
     */
    private static IdentityConstant definingClassId(DefiningConstant defining) {
        return switch (defining) {
            case NativeRebaseConstant constant -> constant.getClassConstant();
            case ModuleConstant constant       -> constant;
            case PackageConstant constant      -> constant;
            case ClassConstant constant        -> constant;
            case ThisClassConstant constant    -> constant.getDeclarationLevelClass();
            case ParentClassConstant constant  -> constant.getDeclarationLevelClass();
            case ChildClassConstant constant   -> constant.getDeclarationLevelClass();

            case FormalConstant _, KeywordConstant _, DecoratedClassConstant _,
                 MethodConstant _, MultiMethodConstant _, PureIdentityConstant _,
                 TypedefConstant _, SignatureConstant _, UnresolvedNameConstant _,
                 ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().getGenericParamType(sName, listParams);
        }

        DefiningConstant defining = getDefiningConstant();
        if (defining instanceof FormalConstant constant) {
            assert listParams.isEmpty();

            return constant.getConstraintType().getGenericParamType(sName, listParams);
        }

        // because isA() uses this method, there is a chicken-and-egg problem, so instead of
        // materializing the TypeInfo at this point, just answer the question without it
        ClassStructure clz        = (ClassStructure) definingClassId(defining).getComponent();
        ConstantPool   pool       = getConstantPool();
        TypeConstant   typeActual = listParams.isEmpty()
                ? this
                : pool.ensureParameterizedTypeConstant(this, listParams.toArray(TypeConstant.NO_TYPES));

        return clz.getGenericParamType(pool, sName, typeActual);
    }

    @Override
    public TypeConstant resolveFormalType(FormalConstant constFormal) {
        return constFormal.getFormat() == Format.Property
                ? resolveGenericType(constFormal.getName())
                : null;
    }

    @Override
    public boolean isAnnotated() {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isAnnotated();
    }

    @Override
    public boolean isVirtualChild() {
        TypeConstant type = resolveTypedefs();
        return type != this && type.isVirtualChild();
    }

    @Override
    public boolean isSingleDefiningConstant() {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() != Format.Typedef ||
                ((TypedefConstant) constId).getReferredToType().isSingleDefiningConstant();
    }

    @Override
    public DefiningConstant getDefiningConstant() {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.Typedef
                ? ((TypedefConstant) constId).getReferredToType().getDefiningConstant()
                : DefiningConstant.of(constId);
    }

    /**
     * @return the underlying constant, resolving it if it is still unresolved and can be resolved
     *         at this point
     */
    /**
     * Obtain the resolved constant as the {@link TypedefConstant} it is known to be.
     *
     * <p>Every caller of this reached it through {@code !isSingleDefiningConstant()}, which is
     * exactly the predicate that establishes the constant's type - so the cast those callers used
     * to write was each of them re-asserting what the guard had already proved. Doing it once,
     * here, is the same trade as a covariant return, reached by a different route: the predicate
     * carries the type instead of a subclass carrying it.</p>
     *
     * @return the TypedefConstant this type resolves to
     */
    protected TypedefConstant ensureResolvedTypedef() {
        assert !isSingleDefiningConstant()
                : "not a typedef referring to a relational type: " + getValueString();
        return (TypedefConstant) ensureResolvedConstant();
    }

    protected Constant ensureResolvedConstant() {
        Constant constId = m_constId;

        // resolve any previously unresolved constant at this point
        Constant resolved = constId.resolve();
        if (resolved != constId && resolved != null) {
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
           String sName, Access access, MethodConstant idMethod, ResolutionCollector collector, ErrorListener errs) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().resolveContributedName(sName, access, idMethod, collector, errs);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case FormalConstant _, KeywordConstant _ -> ResolutionResult.UNKNOWN;

            case NativeRebaseConstant constant -> resolveClassContributedName(
                    constant.getClassConstant(), sName, access, idMethod, collector, errs);
            case ModuleConstant constant ->
                resolveClassContributedName(constant, sName, access, idMethod, collector, errs);
            case PackageConstant constant ->
                resolveClassContributedName(constant, sName, access, idMethod, collector, errs);
            case ClassConstant constant ->
                resolveClassContributedName(constant, sName, access, idMethod, collector, errs);

            case ThisClassConstant constant -> constant.getDeclarationLevelClass().getType().
                    resolveContributedName(sName, access, idMethod, collector, errs);
            case ParentClassConstant constant -> constant.getDeclarationLevelClass().getType().
                    resolveContributedName(sName, access, idMethod, collector, errs);
            case ChildClassConstant constant -> constant.getDeclarationLevelClass().getType().
                    resolveContributedName(sName, access, idMethod, collector, errs);

            case TypedefConstant constant -> constant.getReferredToType().
                    resolveContributedName(sName, access, idMethod, collector, errs);

            case UnresolvedNameConstant ignored -> ResolutionResult.POSSIBLE;

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, SignatureConstant _, ExpressionConstant _,
                 DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    /**
     * Resolve a contributed name for a class-identified terminal type.
     */
    private static ResolutionResult resolveClassContributedName(
            IdentityConstant idClz, String sName, Access access, MethodConstant idMethod,
            ResolutionCollector collector, ErrorListener errs) {
        if (idMethod != null) {
            if (idClz.isNestMateOf(idMethod.getClassIdentity())) {
                access = Access.PRIVATE;
            } else {
                IdentityConstant idParent = idClz.getParentConstant();
                if (idParent instanceof MethodConstant && idMethod.isDescendant(idParent)) {
                    // the class is defined inside of the method
                    access = Access.PRIVATE;
                }
            }
        }

        return idClz.getComponent().resolveName(sName, access, collector, errs);
    }

    @Override
    public TypeConstant resolveTypedefs() {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.Typedef
                ? ((TypedefConstant) constId).getReferredToType().resolveTypedefs()
                : this;
    }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().resolveGenerics(pool, resolver);
        }

        DefiningConstant constId = getDefiningConstant();
        if (constId instanceof FormalConstant constFormal) {
            TypeConstant typeResolved = constFormal.resolve(resolver);
            if (typeResolved != null) {
                return typeResolved;
            }
        }

        return this;
    }

    @Override
    public TypeConstant resolveConstraints(boolean fPendingOnly) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().resolveConstraints(fPendingOnly);
        }

        if (!fPendingOnly) {
            DefiningConstant constId = getDefiningConstant();
            if (constId instanceof FormalConstant constFormal) {
                return constFormal.getConstraintType().resolveConstraints(fPendingOnly);
            }
        }
        return this;
    }

    @Override
    public TypeConstant resolveDynamicConstraints(Register register) {
        if (isSingleDefiningConstant()) {
            DefiningConstant constId = getDefiningConstant();
            if (constId instanceof DynamicFormalConstant constDynamic) {
                if (register == null || constDynamic.getRegister() == register) {
                    return constDynamic.getConstraintType();
                }
            }
        }

        return this;
    }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams) {
        DefiningConstant defining = DefiningConstant.of(ensureResolvedConstant());
        IdentityConstant idClz;
        switch (defining) {
        case ModuleConstant ignored     -> { return this; }
        case PackageConstant ignored    -> { return this; }
        case FormalConstant ignored     -> { return this; }
        case KeywordConstant ignored    -> { return this; }

        case ClassConstant constant     -> idClz = constant;  // includes native rebase

        case ThisClassConstant constant -> idClz = constant.getDeclarationLevelClass();
        case ParentClassConstant constant -> idClz = constant.getDeclarationLevelClass();
        case ChildClassConstant constant -> idClz = constant.getDeclarationLevelClass();

        case TypedefConstant constant   -> {
            return constant.getReferredToType().adoptParameters(pool, atypeParams);
        }

        case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
             PureIdentityConstant _, SignatureConstant _, UnresolvedNameConstant _,
             ExpressionConstant _, DeferredValueConstant _ ->
            throw new IllegalStateException("unexpected defining constant: " + defining);
        }

        if (atypeParams == null) {
            // this is a "normalization" call
            atypeParams = ConstantPool.NO_TYPES;
        }

        if (isTuple()) {
            // copy parameters as is
            return pool.ensureParameterizedTypeConstant(this, atypeParams);
        }

        ClassStructure struct = (ClassStructure) idClz.getComponent();
        if (struct.isParameterized()) {
            return pool.ensureParameterizedTypeConstant(this,
                struct.normalizeParameters(pool, atypeParams));
        }

        // this type cannot adopt anything
        return this;
    }

    @Override
    public TypeConstant[] collectGenericParameters() {
        DefiningConstant defining = DefiningConstant.of(ensureResolvedConstant());
        IdentityConstant idClz;
        switch (defining) {
        case FormalConstant ignored     -> { return TypeConstant.NO_TYPES; }
        case KeywordConstant ignored    -> { return TypeConstant.NO_TYPES; }

        case ModuleConstant constant    -> idClz = constant;
        case PackageConstant constant   -> idClz = constant;
        case ClassConstant constant     -> idClz = constant;  // includes native rebase

        case ThisClassConstant constant -> idClz = constant.getDeclarationLevelClass();
        case ParentClassConstant constant -> idClz = constant.getDeclarationLevelClass();
        case ChildClassConstant constant -> idClz = constant.getDeclarationLevelClass();

        case TypedefConstant constant   -> {
            return constant.getReferredToType().collectGenericParameters();
        }

        case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
             PureIdentityConstant _, SignatureConstant _, UnresolvedNameConstant _,
             ExpressionConstant _, DeferredValueConstant _ ->
            throw new IllegalStateException("unexpected defining constant: " + defining);
        }

        if (isTuple()) {
            return TypeConstant.NO_TYPES;
        }

        ClassStructure struct = (ClassStructure) idClz.getComponent();
        if (struct.isParameterized()) {
            return struct.getFormalType().getParamTypesArray().unsafeArray();
        }
        return TypeConstant.NO_TYPES;
    }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild) {
        return ensureResolvedConstant().isAutoNarrowing();
    }

    @Override
    public boolean isAutoNarrowing() {
        return ensureResolvedConstant().isAutoNarrowing();
    }

    public TypeConstant ensureAutoNarrowing() {
        return isAutoNarrowing()
                ? this
                : getConstantPool().ensureThisTypeConstant(getDefiningConstant(), null);
    }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().
                    resolveAutoNarrowing(pool, fRetainParams, typeTarget, idCtx);
        }

        switch (getDefiningConstant()) {
        case ThisClassConstant constant: {
            IdentityConstant idClass  = constant.getDeclarationLevelClass();
            TypeConstant     typeDecl = idClass.getType();
            if (typeTarget == null || !typeTarget.isA(typeDecl)) {
                return typeDecl;
            }

            if (idCtx != null && idCtx.getType().isA(typeDecl)) {
                TypeConstant typeCtx = pool.ensureThisTypeConstant(idCtx, null);

                // apply the target's type parameters and annotations (if any)
                if (typeTarget.isParamsSpecified()) {
                    // sharing the frozen wrapper replaces the old defensive clone
                    typeCtx = pool.ensureParameterizedTypeConstant(typeCtx,
                                    typeTarget.getParamTypesArray());
                }
                if (typeTarget.isAnnotated()) {
                    typeCtx = pool.ensureAnnotatedTypeConstant(typeCtx,
                                    copyOf(typeTarget.getAnnotations()));
                }
                return typeCtx;
            }

            // strip an access modifier
            return typeTarget.removeAccess();
        }

        case ParentClassConstant constParent: {
            if (typeTarget != null) {
                if (typeTarget.isFormalType()) {
                    typeTarget = typeTarget.resolveConstraints();
                }
                if (typeTarget.isVirtualChild()) {
                    // if possible, retain the parent's type parameters
                    int           nDepth     = constParent.getDepth();
                    TypeConstant  typeParent = typeTarget.getParentType();
                    while (--nDepth > 0) {
                        if (typeParent instanceof VirtualChildTypeConstant) {
                            typeParent = typeParent.getParentType();
                        } else {
                            return constParent.getDeclarationLevelClass().getType();
                        }
                    }
                    return typeParent;
                }
            }
            return constParent.getDeclarationLevelClass().getType();
        }
        case ChildClassConstant constant:
            // currently, not used
            return constant.getDeclarationLevelClass().getType();

        case UnresolvedNameConstant constant:
            throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

        case ModuleConstant _, PackageConstant _, ClassConstant _, FormalConstant _,
             KeywordConstant _, DecoratedClassConstant _, MethodConstant _,
             MultiMethodConstant _, PureIdentityConstant _, TypedefConstant _,
             SignatureConstant _, ExpressionConstant _, DeferredValueConstant _:
            return this;
        }
    }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName) {
        switch (getDefiningConstant()) {
        case TypeParameterConstant idTypeParam: {
            MethodConstant idMethod = idTypeParam.getMethod();
            MethodStructure       method      = idMethod.getComponent();
            if (method != null) {
                Parameter param = method.getParam(idTypeParam.getRegister());
                if (param.getName().equals(sFormalName)) {
                    if (typeActual.isFormalType()) {
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
                            constFormal -> sFormalName.equals(constFormal.getName()) ? typeActual : null);
                    return typeActual.isA(typeConstraint)
                            ? typeActual
                            : null;
                }
            }
            break;
        }

        case FormalTypeChildConstant ignored:
            // this shouldn't happen; previously the silent tail of the format switch
            break;

        case PropertyConstant idProp: {
            if (idProp.getName().equals(sFormalName)) {
                ConstantPool pool = getConstantPool();
                TypeConstant typeConstraint = idProp.getConstraintType().
                    resolveConstraints().
                    resolveGenerics(pool,
                        constFormal -> sFormalName.equals(constFormal.getName()) ? typeActual : null);
                return typeActual.isA(typeConstraint)
                        ? typeActual
                        : null;
            }
            break;
        }

        case DynamicFormalConstant ignored:
            // this shouldn't happen
            break;

        case ModuleConstant _, PackageConstant _, ClassConstant _, KeywordConstant _,
             ThisClassConstant _, ParentClassConstant _, ChildClassConstant _,
             DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
             PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
             UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _:
            // previously the silent tail of the format switch
            break;
        }
        return null;
    }

    @Override
    public boolean isTuple() {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isTuple();
        }

        // exhaustive over the sealed defining-constant union: the old shape was a 16-case
        // format switch with a cast in every arm and a default that silently answered "not a
        // tuple" for every format it never listed; here the formal-constant category is one
        // arm instead of four formats, and an unlisted defining-constant kind is a compile
        // error instead of a silently wrong answer
        return switch (getDefiningConstant()) {
            case FormalConstant constant      -> constant.getConstraintType().isTuple();
            case NativeRebaseConstant constant-> isTupleClass(constant.getClassConstant());
            case ClassConstant constant       -> isTupleClass(constant);
            case IdentityConstant ignored     -> false; // modules, packages, methods, ...
            case ThisClassConstant constant   -> isTupleClass(constant.getDeclarationLevelClass());
            case ParentClassConstant constant -> isTupleClass(constant.getDeclarationLevelClass());
            case ChildClassConstant constant  -> isTupleClass(constant.getDeclarationLevelClass());
            case PseudoConstant ignored       -> false; // keywords, unresolved names, ...
        };
    }

    /**
     * @param idClz  the identity of the class to test
     *
     * @return true iff the specified class identity is or extends the Tuple class
     */
    private boolean isTupleClass(IdentityConstant idClz) {
        if (idClz.equals(getConstantPool().clzTuple())) {
            return true;
        }

        ClassStructure clz = (ClassStructure) idClz.getComponent();
        if (clz == null) {
            throw new IllegalStateException("no ClassStructure for " + idClz);
        }
        return clz.isTuple();
    }

    @Override
    public boolean isNullable() {
        if (!isSingleDefiningConstant()) {
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isNullable();
        }

        return getDefiningConstant() instanceof FormalConstant constant
                && constant.getConstraintType().isNullable();
    }

    @Override
    public boolean isOnlyNullable() {
        TypeConstant typeResolved = resolveTypedefs();
        return this == typeResolved
                ? this.equals(getConstantPool().typeNullable()) ||
                  this.equals(getConstantPool().typeNull())
                : typeResolved.isOnlyNullable();
    }

    @Override
    public TypeConstant removeNullable() {
        if (!isSingleDefiningConstant()) {
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().removeNullable();
        }

        if (getDefiningConstant() instanceof FormalConstant constant
                && constant.getConstraintType().isNullable()) {
            // Note: we use the DifferenceType here to say that "this" formal type
            //       *is not* Nullable, which is not quite the same as other usages
            //       of DifferenceType; consider adding a new TypeConstant for that case,
            //       for example "FormalDifference"...
            ConstantPool pool = getConstantPool();
            return pool.ensureDifferenceTypeConstant(this, pool.typeNullable());
        }

        return super.removeNullable();
    }

    @Override
    public TypeConstant andNot(ConstantPool pool, TypeConstant that) {
        if (!isSingleDefiningConstant()) {
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().andNot(pool, that);
        }

        if (getDefiningConstant() instanceof FormalConstant constFormal) {
            TypeConstant typeConstraint = constFormal.getConstraintType();
            /*
             * In a number of places in Ecstasy code we have a check that look like:
             *
             *   Element extends (immutable Object | Freezable)
             *   Element e = ...;
             *   if (!e.is(immutable Object)) {
             *      // the type inference implication gets resolved to: e.is(Freezable)
             *   }
             *   if (!e.is(immutable Element)) {
             *      // logically, the type inference implication here should be the same
             *      // as above, but the logic in UnionTypeConstant.andNot()
             *      // doesn't have enough knowledge to figure that out.
             *      // The logic below answers this very narrow scenario..
             *  }
             */

            if (that.isImmutabilitySpecified()) {
                TypeConstant thatBase = that.removeImmutable();
                if (thatBase.equals(this) ||
                    thatBase.isFormalType() && thatBase.resolveConstraints().equals(typeConstraint)) {
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

        return super.andNot(pool, that);
    }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type) {
        return this;
    }

    @Override
    public boolean extendsClass(IdentityConstant constClass) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().extendsClass(constClass);
        }

        DefiningConstant defining = getDefiningConstant();
        if (defining instanceof KeywordConstant constKeyword) {
            defining = constKeyword.getBaseType().getDefiningConstant();
        }
        DefiningConstant resolved = defining;
        return switch (resolved) {
            // note: native rebase constants kept the historical refusal below
            case NativeRebaseConstant constant ->
                throw new IllegalStateException("unexpected defining constant: " + constant);
            case ModuleConstant constant ->
                (constant.getComponent()).extendsClass(constClass);
            case PackageConstant constant ->
                (constant.getComponent()).extendsClass(constClass);
            case ClassConstant constant ->
                ((ClassStructure) constant.getComponent()).extendsClass(constClass);

            case FormalConstant constant ->
                constant.getConstraintType().extendsClass(constClass);

            case ThisClassConstant constant -> ((ClassStructure) constant
                    .getDeclarationLevelClass().getComponent()).extendsClass(constClass);
            case ParentClassConstant constant -> ((ClassStructure) constant
                    .getDeclarationLevelClass().getComponent()).extendsClass(constClass);
            case ChildClassConstant constant -> ((ClassStructure) constant
                    .getDeclarationLevelClass().getComponent()).extendsClass(constClass);

            case KeywordConstant _, DecoratedClassConstant _, MethodConstant _,
                 MultiMethodConstant _, PureIdentityConstant _, TypedefConstant _,
                 SignatureConstant _, UnresolvedNameConstant _, ExpressionConstant _,
                 DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + resolved);
        };
    }

    @Override
    public boolean containsFormalType(boolean fAllowParams) {
        return isFormalType();
    }

    @Override
    public void collectFormalTypes(boolean fAllowParams, Set<TypeConstant> setFormal) {
        if (isFormalType()) {
            setFormal.add(this);
        }
    }

    @Override
    public boolean containsDynamicType(Register register) {
        if (isDynamicType()) {
            if (register == null) {
                return true;
            }

            return getDefiningConstant() instanceof DynamicFormalConstant constDynamic
                    && constDynamic.getRegister() == register;
        }
        return false;
    }

    @Override
    public boolean containsGenericType(boolean fAllowParams) {
        return isGenericType();
    }

    @Override
    public boolean containsTypeParameter(boolean fAllowParams) {
        return isTypeParameter();
    }

    @Override
    public boolean containsRecursiveType() {
        if (!isSingleDefiningConstant()) {
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().containsRecursiveType();
        }
        return false;
    }

    @Override
    public boolean containsFunctionType() {
        if (isSingleDefiningConstant()) {
            return getDefiningConstant().equals(getConstantPool().clzFunction());
        }

        TypedefConstant constId = ensureResolvedTypedef();
        return constId.getReferredToType().containsFunctionType();
    }

    @Override
    public boolean isFormalTypeSequence() {
        return isGenericType() &&
            ((FormalConstant) getDefiningConstant()).getConstraintType().isFormalTypeSequence();
    }

    @Override
    public boolean isDynamicType() {
        return isSingleDefiningConstant()
                && getDefiningConstant() instanceof DynamicFormalConstant;
    }

    @Override
    public Category getCategory() {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().getCategory();
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            // modules and packages are always class types (not interface types)
            case ModuleConstant _, PackageConstant _ -> Category.CLASS;

            // native rebase is only for an interface
            case NativeRebaseConstant ignored -> Category.IFACE;

            case ClassConstant constant -> {
                // examine the structure to determine if it represents a class or interface
                ClassStructure clz = (ClassStructure) constant.getComponent();
                if (clz == null) {
                    throw new IllegalStateException("missing class for constant: " + constant);
                }
                yield clz.getFormat() == Component.Format.INTERFACE
                        ? Category.IFACE : Category.CLASS;
            }

            case FormalConstant ignored -> Category.FORMAL;

            case ThisClassConstant constant   -> categoryOfDeclaredClass(constant);
            case ParentClassConstant constant -> categoryOfDeclaredClass(constant);
            case ChildClassConstant constant  -> categoryOfDeclaredClass(constant);

            case KeywordConstant ignored -> Category.OTHER;

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    /**
     * @param constant  a this/parent/child class pseudo constant
     *
     * @return the category of its declaration-level class
     */
    private static Category categoryOfDeclaredClass(PseudoConstant constant) {
        ClassStructure clz = (ClassStructure) constant
                .getDeclarationLevelClass().getComponent();
        return clz.getFormat() == Component.Format.INTERFACE
                ? Category.IFACE : Category.CLASS;
    }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isSingleUnderlyingClass(fAllowInterface);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            // modules, packages, and native rebases are always class types (not interfaces)
            case NativeRebaseConstant ignored        -> true;
            case ModuleConstant _, PackageConstant _ -> true;

            case KeywordConstant ignored -> false;

            case ClassConstant constant ->
                isUnderlyingClass(constant, fAllowInterface);

            case FormalConstant constant ->
                constant.getConstraintType().isSingleUnderlyingClass(fAllowInterface);

            case ThisClassConstant constant ->
                isUnderlyingClass(constant.getDeclarationLevelClass(), fAllowInterface);
            case ParentClassConstant constant ->
                isUnderlyingClass(constant.getDeclarationLevelClass(), fAllowInterface);
            case ChildClassConstant constant ->
                isUnderlyingClass(constant.getDeclarationLevelClass(), fAllowInterface);

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    /**
     * @param idClz            the class identity to test
     * @param fAllowInterface  true iff an interface is acceptable
     *
     * @return true iff the specified identity is an acceptable underlying class
     */
    private static boolean isUnderlyingClass(IdentityConstant idClz, boolean fAllowInterface) {
        ClassStructure clz = (ClassStructure) idClz.getComponent();
        return fAllowInterface || clz.getFormat() != Component.Format.INTERFACE;
    }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().getSingleUnderlyingClass(fAllowInterface);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            // modules, packages, and native rebases are always class types (not interfaces)
            case NativeRebaseConstant constant -> constant;
            case ModuleConstant constant       -> constant;
            case PackageConstant constant      -> constant;

            case ClassConstant constant -> {
                assert fAllowInterface ||
                       constant.getComponent().getFormat() != Component.Format.INTERFACE;
                yield constant;
            }

            case FormalConstant constant ->
                constant.getConstraintType().getSingleUnderlyingClass(fAllowInterface);

            case ThisClassConstant constant   -> constant.getDeclarationLevelClass();
            case ParentClassConstant constant -> constant.getDeclarationLevelClass();
            case ChildClassConstant constant  -> constant.getDeclarationLevelClass();

            case KeywordConstant _, DecoratedClassConstant _, MethodConstant _,
                 MultiMethodConstant _, PureIdentityConstant _, TypedefConstant _,
                 SignatureConstant _, UnresolvedNameConstant _, ExpressionConstant _,
                 DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isExplicitClassIdentity(fAllowParams);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _, ClassConstant _, ThisClassConstant _,
                 ParentClassConstant _, ChildClassConstant _ -> true;

            case FormalConstant _, KeywordConstant _ -> false;

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }

    @Override
    public Component.Format getExplicitClassFormat() {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().getExplicitClassFormat();
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant ignored  -> Component.Format.MODULE;
            case PackageConstant ignored -> Component.Format.PACKAGE;

            // note: native rebase constants kept the historical refusal below
            case NativeRebaseConstant constant ->
                throw new IllegalStateException("no class format for: " + constant);
            // get the class referred to and return its format
            case ClassConstant constant -> constant.getComponent().getFormat();

            // follow the indirection to the class structure
            case ThisClassConstant constant ->
                constant.getDeclarationLevelClass().getComponent().getFormat();
            case ParentClassConstant constant ->
                constant.getDeclarationLevelClass().getComponent().getFormat();
            case ChildClassConstant constant ->
                constant.getDeclarationLevelClass().getComponent().getFormat();

            case FormalConstant _, KeywordConstant _, DecoratedClassConstant _,
                 MethodConstant _, MultiMethodConstant _, PureIdentityConstant _,
                 TypedefConstant _, SignatureConstant _, UnresolvedNameConstant _,
                 ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("no class format for: " + defining);
        };
    }

    @Override
    public TypeConstant getExplicitClassInto(boolean fResolve) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().getExplicitClassInto(fResolve);
        }

        DefiningConstant defining = getDefiningConstant();
        ClassStructure structMixin = switch (defining) {
            // note: native rebase constants kept the historical refusal below
            case NativeRebaseConstant constant ->
                throw new IllegalStateException("no class format for: " + constant);
            // get the class referred to
            case ClassConstant constant -> (ClassStructure) constant.getComponent();

            case ThisClassConstant constant ->
                (ClassStructure) constant.getDeclarationLevelClass().getComponent();
            case ParentClassConstant constant ->
                (ClassStructure) constant.getDeclarationLevelClass().getComponent();
            case ChildClassConstant constant ->
                (ClassStructure) constant.getDeclarationLevelClass().getComponent();

            case ModuleConstant _, PackageConstant _, FormalConstant _, KeywordConstant _,
                 DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("no class format for: " + defining);
        };

        if (structMixin == null ||
                (structMixin.getFormat() != Component.Format.ANNOTATION &&
                 structMixin.getFormat() != Component.Format.MIXIN)) {
            throw new IllegalStateException("Invalid format for " + structMixin);
        }

        return structMixin.getTypeInto();
    }

    @Override
    public boolean isIntoPropertyType() {
        return this.equals(getConstantPool().typeProperty()) || isIntoVariableType();
    }

    @Override
    public TypeConstant getIntoPropertyType() {
        TypeConstant typeProp = getConstantPool().typeProperty();

        return this.equals(typeProp)
                ? typeProp
                : getIntoVariableType();
    }

    @Override
    public boolean isIntoMetaData(TypeConstant typeTarget, boolean fStrict) {
        return fStrict
                ? typeTarget.isSingleUnderlyingClass(true) &&
                    this.equals(typeTarget.getSingleUnderlyingClass(true).getType())
                : this.isA(typeTarget);
    }

    @Override
    public boolean isIntoVariableType() {
        return this.isA(getConstantPool().typeRef());
    }

    @Override
    public TypeConstant getIntoVariableType() {
        ConstantPool pool = getConstantPool();

        if (this.isA(pool.typeVar())) {
            return pool.typeVar();
        }
        if (this.isA(pool.typeRef())) {
            return pool.typeRef();
        }
        return null;
    }

    @Override
    public boolean isConst() {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isConst();
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _ -> true;

            case KeywordConstant constant -> constant.getFormat() != Format.IsClass;

            case FormalConstant _, ThisClassConstant _, ParentClassConstant _,
                 ChildClassConstant _ -> false;

            case NativeRebaseConstant ignored -> false;
            case ClassConstant constant ->
                ((ClassStructure) constant.getComponent()).isConst();

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected constant: " + defining);
        };
    }

    @Override
    public boolean isTypeOfType() {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.Typedef
                ? ((TypedefConstant) constId).getReferredToType().isTypeOfType()
                : this.isExplicitClassIdentity(true) &&
                  this.getDefiningConstant().equals(getConstantPool().clzType());
    }

    @Override
    public TypeConstant widenEnumValueTypes() {
        return isEnumValue() && !isOnlyNullable()
                ? getSingleUnderlyingClass(false).getNamespace().getType()
                : this;
    }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().ensureTypeInfo(idClass, errs);
        }

        if (isFormalType()) {
            ConstantPool pool    = getConstantPool();
            int          cInvals = pool.getInvalidationCount();
            if (isGenericType()) {
                // check if the formal type could be resolved in the context of the specified class
                TypeConstant typeR = this.resolveGenerics(pool, idClass.getFormalType());
                if (typeR != this) {
                    TypeInfo infoR = typeR.ensureTypeInfo(idClass, errs);
                    assert isComplete(infoR);
                    return new TypeInfoReal(this, infoR, cInvals);
                }
            }

            TypeConstant typeConstraint = ((FormalConstant) getDefiningConstant()).getConstraintType();
            if (typeConstraint.containsAutoNarrowing(false)) {
                typeConstraint = typeConstraint.resolveAutoNarrowingBase();
            }
            TypeInfo infoConstraint = typeConstraint.ensureTypeInfo(idClass, errs);
            assert isComplete(infoConstraint);
            return new TypeInfoReal(this, infoConstraint, cInvals);
        }

        return super.ensureTypeInfo(idClass, errs);
    }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().ensureTypeInfoInternal(errs);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _, ClassConstant _ ->
                super.buildTypeInfo(errs);

            case KeywordConstant constant ->
                constant.getBaseType().ensureTypeInfoInternal(errs);

            case FormalConstant constant -> {
                TypeConstant typeConstraint = constant.getConstraintType();
                int          cInvalidations = getConstantPool().getInvalidationCount();

                if (typeConstraint.containsAutoNarrowing(false)) {
                    typeConstraint = typeConstraint.resolveAutoNarrowingBase();
                }
                TypeInfo infoConstraint = typeConstraint.ensureTypeInfoInternal(errs);
                yield isComplete(infoConstraint)
                        ? new TypeInfoReal(this, infoConstraint, cInvalidations)
                        : null;
            }

            case ThisClassConstant _, ParentClassConstant _, ChildClassConstant _,
                 DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft) {
        if (isFormalType()) {
            if (!isSingleDefiningConstant()) {
                // a typedef for a formal type
                TypedefConstant constId = ensureResolvedTypedef();
                return constId.getReferredToType().calculateRelationToLeft(typeLeft);
            }

            FormalConstant constRight     = (FormalConstant) getDefiningConstant();
            TypeConstant   typeConstraint = constRight.getConstraintType();
            if (isDynamicType()) {
                DynamicFormalConstant constDynamic = (DynamicFormalConstant) constRight;
                FormalConstant        constFormal  = constDynamic.getFormalConstant();

                // check the formal type constraint first
                Relation relation = constFormal.getConstraintType().calculateRelation(typeLeft);
                if (relation != Relation.INCOMPATIBLE) {
                    return relation;
                }

                Register regRight = constDynamic.getRegister();
                if (regRight != null) {
                    if (typeLeft.containsDynamicType(regRight)) {
                        // the dynamic type is allowed to be assigned from its constraint;
                        // the run-time will be responsible for the actual cast check
                        typeLeft = typeLeft.resolveDynamicConstraints(regRight);
                    }
                }
                return typeConstraint.calculateRelation(typeLeft);
            }

            Relation relation = typeConstraint.calculateRelation(typeLeft);
            if (relation != Relation.INCOMPATIBLE) {
                return relation;
            }
        }
        return super.calculateRelationToLeft(typeLeft);
    }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight) {
        if (isDynamicType()) {
            // the dynamic type is allowed to be assigned from its constraint;
            // the run-time will be responsible for the actual cast check
            TypeConstant typeConstraint =
                    ((DynamicFormalConstant) getDefiningConstant()).getConstraintType();
            return typeRight.calculateRelation(typeConstraint);
        }

        if (isSingleDefiningConstant()) {
            DefiningConstant constLeft = getDefiningConstant();
            if (constLeft instanceof KeywordConstant) {
                if (constLeft.getFormat() == Format.IsClass) {
                    return typeRight.getCategory() == Category.CLASS
                        ? Relation.IS_A
                        : Relation.INCOMPATIBLE;
                }

                if (typeRight.isSingleUnderlyingClass(true)) {
                    ClassStructure clzRight = (ClassStructure)
                        typeRight.getSingleUnderlyingClass(true).getComponent();
                    Component.Format formatRight = clzRight.getFormat();

                    if (formatRight == Component.Format.ANNOTATION ||
                        formatRight == Component.Format.MIXIN) {
                        return typeRight.getExplicitClassInto().calculateRelation(this);
                    }

                    return switch (constLeft.getFormat()) {
                        case IsConst -> switch (formatRight) {
                                case CONST, ENUMVALUE, PACKAGE, MODULE -> Relation.IS_A;
                                default -> Relation.INCOMPATIBLE;
                        };

                        case IsEnum -> formatRight == Component.Format.ENUMVALUE
                                ? Relation.IS_A
                                : Relation.INCOMPATIBLE;

                        case IsModule -> formatRight == Component.Format.MODULE
                                ? Relation.IS_A
                                : Relation.INCOMPATIBLE;

                        case IsPackage -> formatRight == Component.Format.MODULE
                                       || formatRight == Component.Format.PACKAGE
                                ? Relation.IS_A
                                : Relation.INCOMPATIBLE;

                        default -> throw new IllegalStateException();
                    };
                }
            }
        }

        return super.calculateRelationToRight(typeRight);
    }

    @Override
    public boolean isContravariantParameter(ConstantPool pool, TypeConstant typeBase, TypeConstant typeCtx) {
        if (super.isContravariantParameter(pool, typeBase, typeCtx)) {
            return true;
        }

        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isContravariantParameter(pool, typeBase, typeCtx);
        }

        if (!typeBase.isSingleDefiningConstant() || typeBase.isParamsSpecified()) {
            return false;
        }

        DefiningConstant constIdThis = this.getDefiningConstant();
        DefiningConstant constIdBase = typeBase.getDefiningConstant();

        if (constIdThis.getFormat() != constIdBase.getFormat()) {
            return false;
        }

        // the formats were just checked equal, so the base's kind decides both sides
        return switch (constIdBase) {
            case ModuleConstant _, PackageConstant _ -> false;

            // native rebase and class compare by type on both sides (formats match)
            case ClassConstant ignored -> constIdThis.getType().equals(constIdBase.getType());

            case FormalTypeChildConstant idBase ->
                ((FormalTypeChildConstant) constIdThis).getName().equals(idBase.getName());

            case PropertyConstant idBase ->
                ((PropertyConstant) constIdThis).getName().equals(idBase.getName());

            case TypeParameterConstant idBase -> {
                TypeParameterConstant idThis = (TypeParameterConstant) constIdThis;
                yield idThis.getRegister() == idBase.getRegister() ||
                      idThis.getName().equals(idBase.getName());
            }

            case ThisClassConstant constBase   -> congruentDeclarations(constBase, constIdThis);
            case ParentClassConstant constBase -> congruentDeclarations(constBase, constIdThis);
            case ChildClassConstant constBase  -> congruentDeclarations(constBase, constIdThis);

            case DynamicFormalConstant _, KeywordConstant _, DecoratedClassConstant _,
                 MethodConstant _, MultiMethodConstant _, PureIdentityConstant _,
                 TypedefConstant _, SignatureConstant _, UnresolvedNameConstant _,
                 ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected constant: " + constIdBase);
        };
    }

    /**
     * @return true iff the two pseudo constants are congruent and their declaration-level
     *         classes' types are compatible in either direction
     */
    private static boolean congruentDeclarations(PseudoConstant constBase, DefiningConstant constIdThis) {
        PseudoConstant constThis = (PseudoConstant) constIdThis;
        if (constBase.isCongruentWith(constThis)) {
            // the declaration types must be compatible
            TypeConstant typeDeclBase = constBase.getDeclarationLevelClass().getType();
            TypeConstant typeDeclThis = constThis.getDeclarationLevelClass().getType();
            return typeDeclBase.isA(typeDeclThis) || typeDeclThis.isA(typeDeclBase);
        }
        return false;
    }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(
            TypeConstant typeRight, Access accessLeft, List<TypeConstant> listLeft) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case NativeRebaseConstant constant ->
                interfaceAssignableFrom(constant.getClassConstant(), typeRight, accessLeft, listLeft);
            case ClassConstant constant ->
                interfaceAssignableFrom(constant, typeRight, accessLeft, listLeft);

            // note: dynamic formal constants kept the historical refusal below
            case DynamicFormalConstant constant ->
                throw new IllegalStateException("unexpected constant: " + constant);
            case FormalConstant constant -> constant.getConstraintType().
                isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);

            case ThisClassConstant constant -> constant.getDeclarationLevelClass().getType().
                isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
            case ParentClassConstant constant -> constant.getDeclarationLevelClass().getType().
                isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
            case ChildClassConstant constant -> constant.getDeclarationLevelClass().getType().
                isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);

            case ModuleConstant _, PackageConstant _, KeywordConstant _,
                 DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected constant: " + defining);
        };
    }

    /**
     * Delegate interface assignability to the specified interface class.
     */
    private static Set<SignatureConstant> interfaceAssignableFrom(
            IdentityConstant idLeft, TypeConstant typeRight, Access accessLeft,
            List<TypeConstant> listLeft) {
        ClassStructure clzLeft = (ClassStructure) idLeft.getComponent();

        assert clzLeft.getFormat() == Component.Format.INTERFACE;

        return clzLeft.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
    }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case NativeRebaseConstant constant -> classContainsSubstitutableMethod(
                    constant.getClassConstant(), signature, access, fFunction, listParams);
            case ModuleConstant constant -> classContainsSubstitutableMethod(
                    constant, signature, access, fFunction, listParams);
            case PackageConstant constant -> classContainsSubstitutableMethod(
                    constant, signature, access, fFunction, listParams);
            case ClassConstant constant -> classContainsSubstitutableMethod(
                    constant, signature, access, fFunction, listParams);

            case FormalConstant constant -> constant.getConstraintType().
                containsSubstitutableMethod(signature, access, fFunction, listParams);

            case ThisClassConstant constant -> constant.getDeclarationLevelClass().getType().
                containsSubstitutableMethod(signature, access, fFunction, listParams);
            case ParentClassConstant constant -> constant.getDeclarationLevelClass().getType().
                containsSubstitutableMethod(signature, access, fFunction, listParams);
            case ChildClassConstant constant -> constant.getDeclarationLevelClass().getType().
                containsSubstitutableMethod(signature, access, fFunction, listParams);

            case KeywordConstant constant -> constant.getBaseType().
                containsSubstitutableMethod(signature, access, fFunction, listParams);

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected constant: " + defining);
        };
    }

    /**
     * Delegate the substitutable-method check to the specified class.
     */
    private boolean classContainsSubstitutableMethod(
            IdentityConstant idThis, SignatureConstant signature, Access access,
            boolean fFunction, List<TypeConstant> listParams) {
        ClassStructure clzThis = (ClassStructure) idThis.getComponent();

        return clzThis.containsSubstitutableMethod(
                getConstantPool(), signature, access, fFunction, listParams);
    }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().checkConsumption(sTypeName, access, listParams);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            // formal types and keywords do not consume
            case ModuleConstant _, PackageConstant _, FormalConstant _, KeywordConstant _ ->
                Usage.NO;

            case NativeRebaseConstant constant ->
                checkClassUsage(constant.getClassConstant(), sTypeName, access, listParams, true);
            case ClassConstant constant ->
                checkClassUsage(constant, sTypeName, access, listParams, true);

            case ThisClassConstant constant -> constant.getDeclarationLevelClass().getType().
                checkConsumption(sTypeName, access, listParams);
            case ParentClassConstant constant -> constant.getDeclarationLevelClass().getType().
                checkConsumption(sTypeName, access, listParams);
            case ChildClassConstant constant -> constant.getDeclarationLevelClass().getType().
                checkConsumption(sTypeName, access, listParams);

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected constant: " + defining);
        };
    }

    /**
     * The shared class-parameter usage walk behind {@link #checkConsumption} and
     * {@link #checkProduction}: for a tuple, every element type counts both ways; otherwise
     * each type parameter is checked against the class's formal-type variance.
     *
     * @param fConsumption  true for the consumption check, false for the production check
     */
    private Usage checkClassUsage(IdentityConstant idClz, String sTypeName, Access access,
                                  List<TypeConstant> listParams, boolean fConsumption) {
        if (isTuple()) {
            // Tuple consumes and produces every element type
            for (TypeConstant constParam : listParams) {
                if (constParam.consumesFormalType(sTypeName, access)
                    ||
                    constParam.producesFormalType(sTypeName, access)) {
                    return Usage.YES;
                }
            }
        } else if (!listParams.isEmpty()) {
            ConstantPool   pool = getConstantPool();
            ClassStructure clz  = (ClassStructure) idClz.getComponent();

            Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

            listParams = clz.normalizeParameters(pool, listParams);

            Iterator<TypeConstant>   iterParams = listParams.iterator();
            Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

            while (iterParams.hasNext()) {
                TypeConstant constParam = iterParams.next();
                String       sFormal    = iterNames.next().getValue();

                boolean fMatch = fConsumption
                        ? constParam.consumesFormalType(sTypeName, access)
                                && clz.producesFormalType(pool, sFormal, access, listParams)
                            ||
                          constParam.producesFormalType(sTypeName, access)
                                && clz.consumesFormalType(pool, sFormal, access, listParams)
                        : constParam.producesFormalType(sTypeName, access)
                                && clz.producesFormalType(pool, sFormal, access, listParams)
                            ||
                          constParam.consumesFormalType(sTypeName, access)
                                && clz.consumesFormalType(pool, sFormal, access, listParams);
                if (fMatch) {
                    return Usage.YES;
                }
            }
        }
        return Usage.NO;
    }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().checkProduction(sTypeName, access, listParams);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case ModuleConstant _, PackageConstant _, KeywordConstant _ -> Usage.NO;

            case TypeParameterConstant ignored  -> Usage.NO;
            case FormalTypeChildConstant ignored-> Usage.NO;

            case DynamicFormalConstant constant -> {
                FormalConstant constFormal = constant.getFormalConstant();
                yield Usage.valueOf(constFormal instanceof PropertyConstant &&
                        constFormal.getName().equals(sTypeName));
            }
            case PropertyConstant constant ->
                Usage.valueOf(constant.getName().equals(sTypeName));

            case NativeRebaseConstant constant ->
                checkClassUsage(constant.getClassConstant(), sTypeName, access, listParams, false);
            case ClassConstant constant ->
                checkClassUsage(constant, sTypeName, access, listParams, false);

            case ThisClassConstant constant -> constant.getDeclarationLevelClass().getType().
                checkProduction(sTypeName, access, listParams);
            case ParentClassConstant constant -> constant.getDeclarationLevelClass().getType().
                checkProduction(sTypeName, access, listParams);
            case ChildClassConstant constant -> constant.getDeclarationLevelClass().getType().
                checkProduction(sTypeName, access, listParams);

            case DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 PureIdentityConstant _, TypedefConstant _, SignatureConstant _,
                 UnresolvedNameConstant _, ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected constant: " + defining);
        };
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public boolean isJavaPrimitive() {
        if (isAutoNarrowing()) {
            return removeAutoNarrowing().isJavaPrimitive();
        }
        if (isFormalType()) {
            return resolveConstraints().isJavaPrimitive();
        }
        if (isSingleDefiningConstant() && getDefiningConstant() instanceof ClassConstant id
                && id.getModuleConstant().isEcstasyModule()) {
            return switch (id.getName()) {
                case "Bit", "Nibble", "Byte",
                     "Int8",  "Int16",  "Int32",  "Int64",
                     "UInt8", "UInt16", "UInt32", "UInt64",
                     "Float16", "Float32", "Float64",
                     "Boolean", "Char" -> true;

                default -> false;
            };
        }
        return false;
    }

    @Override
    public boolean isXvmPrimitive() {
        if (isAutoNarrowing()) {
            return removeAutoNarrowing().isXvmPrimitive();
        }
        if (isFormalType()) {
            return resolveConstraints().isXvmPrimitive();
        }
        if (isSingleDefiningConstant() && getDefiningConstant() instanceof ClassConstant id
                && id.getModuleConstant().isEcstasyModule()) {
            return switch (id.getName()) {
                case "Dec32", "Dec64", "Dec128", "Int128", "UInt128" -> true;
                default -> false;
            };
        }
        return false;
    }

    @Override
    public boolean isJitInterface() {
        return isFormalType()
                ? resolveConstraints().isJitInterface()
                : super.isJitInterface();
    }

    @Override
    public TypeConstant getCallableJitType() {
        return isFormalType()
                ? resolveConstraints().getCallableJitType()
                : super.getCallableJitType();
    }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(Container container) {
        if (!isSingleDefiningConstant()) {
            // this can only happen if this type is a Typedef referring to a relational type
            TypedefConstant constId = ensureResolvedTypedef();
            return constId.getReferredToType().getTemplate(container);
        }

        DefiningConstant defining = getDefiningConstant();
        return switch (defining) {
            case NativeRebaseConstant constant ->
                container.getTemplate(constant.getClassConstant());
            case ModuleConstant constant  -> container.getTemplate(constant);
            case PackageConstant constant -> container.getTemplate(constant);
            case ClassConstant constant   -> container.getTemplate(constant);

            case ThisClassConstant constant ->
                container.getTemplate(constant.getDeclarationLevelClass());
            case ParentClassConstant constant ->
                container.getTemplate(constant.getDeclarationLevelClass());
            case ChildClassConstant constant ->
                container.getTemplate(constant.getDeclarationLevelClass());

            case FormalConstant _, KeywordConstant _, DecoratedClassConstant _,
                 MethodConstant _, MultiMethodConstant _, PureIdentityConstant _,
                 TypedefConstant _, SignatureConstant _, UnresolvedNameConstant _,
                 ExpressionConstant _, DeferredValueConstant _ ->
                throw new IllegalStateException("unexpected defining constant: " + defining);
        };
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.TerminalType;
    }

    @Override
    protected TerminalTypeConstant copyForAdoption(AdoptionContext context) {
        var pool = requireSharedAdoptionPool(context, "terminal type with foreign identity");

        // Rebuild the type shell instead of shallow-cloning it. TypeConstant owner caches are not
        // logical type value; the defining identity is registered by the destination pool below.
        var constId = Objects.requireNonNull(ensureResolvedConstant(), "defining constant");
        return new TerminalTypeConstant(pool, constId);
    }

    @Override
    public boolean containsUnresolved() {
        if (isHashCached()) {
            return false;
        }

        Constant constId = ensureResolvedConstant();
        if (constId.containsUnresolved()) {
            return true;
        }

        if (getFormat() == Format.Typedef) {
            return ((TypedefConstant) constId).getReferredToType().containsUnresolved();
        }

        return false;
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(ensureResolvedConstant());
    }

    @Override
    protected Object getLocator() {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.UnresolvedName
                ? null
                : constId;
    }

    @Override
    protected int compareDetails(Constant obj) {
        if (!(obj instanceof TerminalTypeConstant that)) {
            return -1;
        }

        Constant constThis = this.m_constId.resolve();
        Constant constThat = that.m_constId.resolve();
        return constThis.compareTo(constThat);
    }

    @Override
    public String getValueString() {
        // PURE: read the resolution, never STORE it. ensureResolvedConstant() writes the resolved
        // constant back into m_constId, so rendering a type mutated it; resolve()/unwrap() itself is
        // a pure chain-walk, so render from a local instead. See
        // docs/reentrancy/plans/side-effect-free-tostring.md.
        Constant constId  = m_constId;
        Constant resolved = constId.resolve();
        return (resolved == null ? constId : resolved).getValueString();
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constId = pool.register(ensureResolvedConstant());
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writeMagnitude(out, ensureResolvedConstant().getPosition());
    }

    @Override
    public boolean validate(ErrorListener errs) {
        if (!isValidated()) {
            if (!isSingleDefiningConstant()) {
                // this can only happen if this type is a Typedef referring to a relational type
                TypedefConstant constId = ensureResolvedTypedef();
                return constId.getReferredToType().validate(errs) && super.validate(errs);
            }

            DefiningConstant defining = getDefiningConstant();
            switch (defining) {
            case ModuleConstant _, PackageConstant _, ClassConstant _, FormalConstant _,
                 ThisClassConstant _, ParentClassConstant _, ChildClassConstant _ -> {
                return super.validate(errs);
            }

            case KeywordConstant ignored -> { }

            case UnresolvedNameConstant _, DecoratedClassConstant _, MethodConstant _,
                 MultiMethodConstant _, PureIdentityConstant _, TypedefConstant _,
                 SignatureConstant _, ExpressionConstant _, DeferredValueConstant _ -> {
                // this is basically an illegal state exception
                log(errs, Severity.ERROR, VE_UNKNOWN, defining.getValueString()
                        + " (" + defining.getFormat() + ')');
                return true;
            }
            }
        }

        return false;
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    protected int computeHashCode() {
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
