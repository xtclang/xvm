package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.PropertyExprAST;

import org.xvm.compiler.ast.Context;

import org.xvm.javajit.TypeSystem;


/**
 * Represent a property constant, which identifies a particular property structure.
 */
public sealed class PropertyConstant
        extends FormalConstant
        permits FormalTypeChildConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a property identifier.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this property
     * @param sName        the property name
     */
    public PropertyConstant(ConstantPool pool, IdentityConstant constParent, String sName) {
        this(pool, constParent, sName, ParentFormat.PROPERTY);
    }

    /**
     * Construct a property-like formal constant with explicit parent validation. This avoids the
     * old constructor-time virtual checkParent(...) dispatch while preserving subclass-specific
     * parent rules and the public constructor behavior.
     */
    protected PropertyConstant(
            ConstantPool     pool,
            IdentityConstant constParent,
            String           sName,
            ParentFormat     format) {
        this(pool, constParent, sName, format, true);
    }

    /**
     * Construction with a relaxed parent check for ADOPTION. Direct construction ({@code fStrict})
     * always validates. Adoption re-homes an ALREADY-VALIDATED constant into a new pool, where
     * {@link #validateParent}'s {@code isFormalType()} test resolves the parent's
     * {@code PropertyStructure} - which is not available mid-merge, before the module is linked. So
     * adoption validates whenever the structure IS resolvable and skips only the genuinely
     * unknowable case; it does not blanket-skip. See {@link #validateParentIfKnowable}.
     */
    protected PropertyConstant(
            ConstantPool     pool,
            IdentityConstant constParent,
            String           sName,
            ParentFormat     format,
            boolean          fStrict) {
        super(pool, constParent, sName);

        if (fStrict) {
            validateParent(format, constParent);
        } else {
            validateParentIfKnowable(format, constParent);
        }
    }

    /**
     * Best-effort parent validation for adoption: identical to {@link #validateParent} except that a
     * formal-child parent whose {@code PropertyStructure} is not resolvable yet is accepted rather
     * than rejected. {@code isFormalType()} cannot distinguish "not a formal type" from "not knowable
     * yet" - both read as false - so validating unconditionally here would spuriously fail a valid
     * constant being re-homed mid-merge, while skipping unconditionally would stop catching a
     * genuinely invalid parent. Checking resolvability first gives both.
     *
     * @param format     the parent format rule to apply
     * @param idParent   the parent identity to check
     */
    protected static void validateParentIfKnowable(ParentFormat format, IdentityConstant idParent) {
        if (format == ParentFormat.FORMAL_CHILD
                && idParent instanceof PropertyConstant constParent
                && constParent.getComponent() == null) {
            return;     // not linked yet: the formal-ness of this parent is unknowable, not false
        }
        validateParent(format, idParent);
    }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public PropertyConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool, format, in);
    }

    /**
     * Validate the parent's format. The constructor uses the static ParentFormat route instead of
     * the protected virtual hook below, so subclass validation remains explicit without exposing a
     * half-constructed PropertyConstant.
     */
    protected static void validateParent(ParentFormat format, IdentityConstant idParent) {
        switch (format) {
        case PROPERTY:
            // formal type children are not legal property parents even though they subtype
            // PropertyConstant, so their refusal comes first
            switch (idParent) {
            case FormalTypeChildConstant _ ->
                throw new IllegalArgumentException("invalid parent: " + idParent.getFormat());
            case ModuleConstant _, PackageConstant _, ClassConstant _,
                 PropertyConstant _, MethodConstant _ -> { }
            case DecoratedClassConstant _, MultiMethodConstant _, TypedefConstant _,
                 TypeParameterConstant _, DynamicFormalConstant _, PureIdentityConstant _ ->
                throw new IllegalArgumentException("invalid parent: " + idParent.getFormat());
            }
            break;

        case FORMAL_CHILD:
            switch (idParent) {
            case FormalTypeChildConstant _, TypeParameterConstant _ -> { }
            case PropertyConstant constParent -> {
                if (!constParent.isFormalType()) {
                    throw new IllegalArgumentException(
                            "parent does not represent a formal constant: " + idParent);
                }
            }
            case ModuleConstant _, PackageConstant _, ClassConstant _,
                 DecoratedClassConstant _, MethodConstant _, MultiMethodConstant _,
                 TypedefConstant _, DynamicFormalConstant _, PureIdentityConstant _ ->
                throw new IllegalArgumentException(
                        "parent does not represent a formal constant: " + idParent);
            }
            break;
        }
    }

    protected enum ParentFormat {
        PROPERTY,
        FORMAL_CHILD
    }

    /**
     * Validate the parent's format.
     *
     * @param idParent  the parent's id
     */
    protected void checkParent(IdentityConstant idParent) {
        validateParent(ParentFormat.PROPERTY, idParent);
    }

    // ----- FormalConstant methods ----------------------------------------------------------------

    /** @return the PropertyStructure this identity names */
    @Override
    public PropertyStructure getComponent() {
        return (PropertyStructure) super.getComponent();
    }

    @Override
    public TypeConstant getConstraintType() {
        TypeConstant typeConstraint = m_typeConstraint;
        if (typeConstraint != null) {
            return typeConstraint;
        }

        assert isFormalType();

        // the type of the property must be "Type<X>", so return X
        typeConstraint = getType();

        assert typeConstraint.isTypeOfType() && typeConstraint.isParamsSpecified();

        typeConstraint = typeConstraint.getParamType(0);

        if (!typeConstraint.isParamsSpecified() && typeConstraint.isExplicitClassIdentity(true)) {
            // create a normalized formal type
            ConstantPool   pool = getConstantPool();
            ClassStructure clz  = (ClassStructure) typeConstraint.getSingleUnderlyingClass(true).getComponent();
            if (clz == null) {
                // there is a possibility for this method be called before the pool is fully
                // assembled; return the naked type without caching it
                return typeConstraint;
            }

            if (clz.isParameterized()) {
                Set<StringConstant> setFormalNames = clz.getTypeParams().keySet();
                TypeConstant[]      atypeFormal    = new TypeConstant[setFormalNames.size()];
                int ix = 0;
                for (StringConstant constName : setFormalNames) {
                    Constant constant = pool.ensureFormalTypeChildConstant(this, constName.getValue());
                    atypeFormal[ix++] = constant.getType();
                }
                typeConstraint = pool.ensureParameterizedTypeConstant(typeConstraint, atypeFormal);
            }
        }
        return m_typeConstraint = typeConstraint;
    }

    @Override
    public TypeConstant resolve(GenericTypeResolver resolver) {
        if (isTypeSequenceTypeParameter()) {
            // the following block is for nothing else, but compilation of Tuple and
            // ConditionalTuple natural classes
            if (resolver instanceof TypeConstant typeResolver &&
                    typeResolver.isTuple() && !typeResolver.isParamsSpecified()) {
                return null;
            }
        }

        return super.resolve(resolver);
    }

    @Override
    public ExprAST toExprAst(Context ctx) {
        return ctx.isMethod() || ctx.isConstructor()
                ? new PropertyExprAST(ctx.getThisRegisterAST(), this)
                : null;
    }

    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return a signature constant representing this property
     */
    public SignatureConstant getSignature() {
        SignatureConstant sig = m_constSig;
        if (sig == null) {
            // transient synthetic constant; no need to register
            sig = m_constSig = new SignatureConstant(getConstantPool(), this);
        }
        return sig;
    }

    /**
     * @return true iff this property is a generic type parameter
     */
    public boolean isFormalType() {
        PropertyStructure struct = getComponent();
        return struct != null && struct.isGenericTypeParameter();
    }

    /**
     * @return a TypeConstant representing a formal (generic) type represented by this property,
     *         which must be a generic type parameter
     */
    public TypeConstant getFormalType() {
        assert isFormalType();
        return getConstantPool().ensureTerminalTypeConstant(this);
    }

    /**
     * @return true iff this property is a formal type parameter that materializes into a
     *         sequence of types
     */
    public boolean isTypeSequenceTypeParameter() {
        return isFormalType() && getConstraintType() instanceof TypeSequenceTypeConstant;
    }

    /**
     * @return true iff the property is a named constant value
     */
    public boolean isConstant() {
        PropertyStructure prop = getComponent();
        return prop != null && prop.isConstant();
    }

    /**
     * @return true iff the property has a Future annotation
     */
    public boolean isFuture() {
        PropertyStructure prop = getComponent();
        return prop != null && prop.isFuture();
    }

    /**
     * Obtain the TypeConstant that represents the runtime type of a Ref/Var for this property in
     * the context of the specified target.
     *
     * @param typeTarget  the target type (null if the property's {@link #getClassIdentity()
     *                    class identity} is the target)
     *
     * @return a TypeConstant
     */
    public TypeConstant getRefType(TypeConstant typeTarget) {
        PropertyInfo infoThis = getPropertyInfo(typeTarget);
        if (infoThis.isCustomLogic()) {
            if (typeTarget == null) {
                typeTarget = getConstantPool().ensureAccessTypeConstant(
                    getClassIdentity().getType(), Access.PRIVATE);
            }
            return getConstantPool().ensurePropertyClassTypeConstant(typeTarget, this);
        } else {
            return infoThis.getBaseRefType();
        }
    }

    /**
     * @return the PropertyInfo for this property using its {@link #getClassIdentity() class
     *         identity} as the target
     */
    public PropertyInfo getPropertyInfo() {
        PropertyInfo info = m_info;
        if (info == null) {
            info = m_info = getPropertyInfo(null);
        }
        return info;
    }

    /**
     * Obtain the PropertyInfo for this property on the specified target type.
     *
     * @param typeTarget  the target type (null if the property's {@link #getClassIdentity()
     *                    class identity} is the target)
     *
     * @return the PropertyInfo
     */
    public PropertyInfo getPropertyInfo(TypeConstant typeTarget) {
        if (typeTarget == null) {
            typeTarget = getConstantPool().ensureAccessTypeConstant(
                getClassIdentity().getType(), Access.PRIVATE);
        } else {
            if (typeTarget.isFormalType()) {
                typeTarget = typeTarget.resolveConstraints();
            }
            Access accessProp   = getComponent().getAccess();
            Access accessTarget = typeTarget.getAccess();
            if (accessTarget != Access.STRUCT &&
                    accessProp.isLessAccessibleThan(accessTarget)) {
                typeTarget = getConstantPool().ensureAccessTypeConstant(typeTarget, accessProp);
            }
        }

        PropertyInfo infoThis = typeTarget.ensureTypeInfo().findProperty(this, true);
        assert infoThis != null;
        return infoThis;
    }

    /**
     * Bjarne Lambda is a function that performs the following transformation:
     *      t -> t.p
     * where "p" is this property and "t" is a target argument of the {@link #getNamespace host} type.
     *
     * @return the TypeConstant that represents a Bjarne lambda for this property
     */
    public TypeConstant getBjarneLambdaType() {
        return getConstantPool().buildFunctionType(
            new TypeConstant[] {getNamespace().getType()}, getType());
    }

    /**
     * @return true iff this property is nested directly inside of a class
     */
    public boolean isTopLevel() {
        return getParentConstant().isClass();
    }

    /**
     * Invalidate any cached information for this PropertyConstant. This method should be called
     * when there are any structural changes to the property that this constant identifies.
     */
    public void invalidateCache() {
        m_type           = null;
        m_constSig       = null;
        m_typeConstraint = null;
    }

    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant replaceParentConstant(IdentityConstant idParent) {
        return new PropertyConstant(getConstantPool(), idParent, getName());
    }

    @Override
    public TypeConstant getValueType(ConstantPool pool, TypeConstant typeTarget) {
        if (typeTarget == null) {
            typeTarget = getClassIdentity().getType();
        }

        TypeConstant typePrivate  = typeTarget.ensureAccess(Access.PRIVATE);
        // BLACKHOLE, explicitly: this is a metadata lookup, not an operation that produces
        // diagnostics. Whoever asked for typeTarget to be validated owns the errors that building
        // its TypeInfo turns up; a caller asking "what type does this property hold?" does not, and
        // is routinely a speculative one (NewExpression.testFit reaches here through
        // PropertyDeclarationStatement.validateContent).
        PropertyInfo infoProp     = typePrivate.typeInfo()
                                            .findProperty(this);
        TypeConstant typeReferent = infoProp.getType();
        TypeConstant typeImpl     = pool.ensurePropertyClassTypeConstant(typePrivate, this);

        return pool.ensureParameterizedTypeConstant(pool.typeProperty(),
                typeTarget, typeReferent, typeImpl);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    protected StringBuilder buildJitClassName(TypeSystem ts) {
        return getNamespace().buildJitClassName(ts).append('$').append(getName());
    }

    /**
     * Ensure a unique name for this property at the specified TypeSystem.
     */
    public String ensureJitPropertyName(TypeSystem ts) {
        String sJitName = m_sJitName;
        if (sJitName == null) {
            synchronized (this) {
                sJitName = m_sJitName;
                if (sJitName == null) {
                    PropertyStructure prop = getComponent();
                    assert prop != null;
                    ClassStructure clzParent = prop.getContainingClass();
                    String         sNameOrig = getName();
                    sJitName = switch (clzParent.getFormat()) {
                        case ANNOTATION, MIXIN -> sNameOrig + ts.xvm.createUniqueSuffix(sNameOrig);
                        default                -> sNameOrig;
                    };
                    m_sJitName = sJitName;
                }
            }
        }
        return sJitName;
    }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.Property;
    }

    @Override
    public boolean isProperty() {
        return true;
    }

    @Override
    public TypeConstant getType() {
        TypeConstant type = m_type;
        if (type == null) {
            // it's not our responsibility to report any errors
            PropertyStructure prop = getComponent();
            m_type = type = prop == null
                    ? getConstantPool().typeObject()
                    : prop.getType();
        }
        return type;
    }

    @Override
    protected PropertyConstant copyForAdoption(AdoptionContext context) {
        // Property identity is parent + name. Type, signature, PropertyInfo, constraint, and JIT
        // name caches are owner/type-system helper state and must be recomputed by the target pool.
        return new PropertyConstant(context.pool(), getParentConstant(), getName());
    }

    @Override
    public Nid getNestedIdentity() {
        // property can be identified with only a name, assuming it is not recursively nested
        return getNamespace().isNested()
                ? getCanonicalNestedIdentity()
                : Nid.of(getName());
    }

    @Override
    public Nid resolveNestedIdentity(ConstantPool pool, GenericTypeResolver resolver) {
        // property can be identified with only a name, assuming it is not recursively nested
        return getNamespace().isNested()
                ? resolver == null
                    ? getCanonicalNestedIdentity()
                    : new NestedIdentity(pool, resolver)
                : Nid.of(getName());
    }

    @Override
    public PropertyStructure relocateNestedIdentity(ClassStructure clz) {
        Component parent = getNamespace().relocateNestedIdentity(clz);
        if (parent == null) {
            return null;
        }

        Component that = parent.getChild(this.getName());
        return that instanceof PropertyStructure
                ? (PropertyStructure) that
                : null;
    }

    @Override
    public PropertyConstant ensureNestedIdentity(ConstantPool pool, IdentityConstant that) {
        IdentityConstant idParent = getParentConstant();
        return idParent.equals(that)
            ? this
            : pool.ensurePropertyConstant(
                    idParent.ensureNestedIdentity(pool, that), getName());
    }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that) {
        return that.getConstantPool().ensurePropertyConstant(that, getName());
    }

    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        invalidateCache();

        super.registerConstants(pool);
    }

    @Override
    protected void assemble(DataOutput out) throws IOException {
        super.assemble(out);

        m_type     = null;
        m_constSig = null;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder().append(getName());
        IdentityConstant idParent = getNamespace();
        while (idParent != null) {
            // formal type children stop the walk exactly as the old default did for their
            // format, even though they subtype PropertyConstant
            switch (idParent) {
            case FormalTypeChildConstant _   -> idParent = null;
            case MethodConstant _,
                 PropertyConstant _          -> {
                sb.insert(0, idParent.getName() + '#');
                idParent = idParent.getNamespace();
            }
            case ModuleConstant _, PackageConstant _, ClassConstant _,
                 DecoratedClassConstant _, MultiMethodConstant _, TypedefConstant _,
                 TypeParameterConstant _, DynamicFormalConstant _,
                 PureIdentityConstant _      -> idParent = null;
            }
        }

        return sb.toString();
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * Cached type.
     */
    private transient TypeConstant m_type;

    /**
     * Cached constant that represents the signature of this property.
     */
    private transient SignatureConstant m_constSig;

    /**
     * Cached constraint type.
     */
    protected transient TypeConstant m_typeConstraint;

    /**
     * Cached PropertyInfo.
     */
    protected transient PropertyInfo m_info;

    /**
     * Cached JIT property name.
     */
    private transient volatile String m_sJitName;
}
