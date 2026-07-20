package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;

import org.xvm.util.Handy;


/**
 * Represents a single method (or function) implementation body.
 */
public class MethodBody {
    /**
     * Construct a MethodBody for a lambda or a private method.
     */
    public MethodBody(MethodStructure method) {
        this(method.getIdentityConstant(), method.getIdentityConstant().getSignature(),
                method.isNative() ? Implementation.Native : Implementation.Explicit, null);

        assert method.getAccess() == Access.PRIVATE;
        m_structMethod = method;
    }

    /**
     * Construct an implicit, abstract, native, or normal byte-code method body.
     *
     * @param id    the method constant that this body represents
     * @param sig   the resolved signature of the method
     * @param impl  one of FromInto, Declared, Default, Native, or Explicit
     */
    public MethodBody(MethodConstant id, SignatureConstant sig, Implementation impl) {
        this(id, sig, impl, null);
    }

    /**
     * Internal constructor for a union type.
     */
    MethodBody(MethodConstant id, SignatureConstant sig, MethodInfo method1, MethodInfo method2) {
        this(id, sig, Implementation.Union, new MethodInfo[] {method1, method2});
    }

    /**
     * Construct a method body with an optional target.
     *
     * @param id      the method constant that this body represents
     * @param sig     the resolved signature of the method
     * @param impl    specifies the implementation of the MethodBody
     * @param target  a <i>resolved</i> nid (a SignatureConstant for non-nested) from the
     *                "narrowing" chain for a Capped Implementation;
     *                a PropertyConstant for a Delegating or Field Implementation;
     *                a MethodInfo (which may be from an incomplete TypeInfo build) for a FromInto
     *                Implementation;
     *                an array of two MethodInfo objects for a Union Implementation;
     *                otherwise null
     */
    public MethodBody(MethodConstant id, SignatureConstant sig, Implementation impl, Object target) {
        assert id != null && sig != null && impl != null;
        switch (impl) {
        case FromInto:
            assert target == null || target instanceof MethodInfo;
            break;
        case Capped:
            assert target instanceof SignatureConstant
                || target instanceof IdentityConstant.NestedIdentity;
            break;
        case Delegating:
        case Field:
            assert target instanceof PropertyConstant;
            break;
        case Union:
            assert target instanceof MethodInfo[] legs
                    && legs.length == 2 && legs[0] != null && legs[1] != null;
            break;
        case Implicit:
            break;
        default:
            assert target == null;
            break;
        }

        m_id     = id;
        m_sig    = sig;
        m_impl   = impl;
        m_target = target;
    }

    /**
     * A copy constructor that allows to change the implementation of the body.
     *
     * @param body  the method body to copy
     * @param impl  the new implementation
     */
    public MethodBody(MethodBody body, Implementation impl) {
        m_id     = body.m_id;
        m_sig    = body.m_sig;
        m_target = body.m_target;
        m_impl   = impl;
    }

    /**
     * Create a MethodBody based on this body, but with resolved method.
     *
     * @param pool      the ConstantPool to create the resolved constants at
     * @param resolver  the resolver
     *
     * @return a new MethodBody
     */
    public MethodBody resolveGenerics(ConstantPool pool, GenericTypeResolver resolver) {
        assert m_impl != Implementation.Capped;

        SignatureConstant   sig  = m_sig.resolveGenericTypes(pool, resolver);
        MethodConstant      id   = pool.ensureMethodConstant(m_id.getNamespace(), sig);
        MethodBody          body = new MethodBody(id, sig, m_impl, null);
        body.setMethodStructure(getMethodStructure());
        return body;
    }

    /**
     * @return the MethodConstant that this MethodBody represents
     */
    public MethodConstant getIdentity() {
        return m_id;
    }

    /**
     * @return the <i>resolved</i> SignatureConstant that this MethodBody represents
     */
    public SignatureConstant getSignature() {
        return m_sig;
    }

    /**
     * @return the Access required for the method, or null if unknown
     */
    public Access getAccess() {
        if (m_structMethod != null) {
            return m_structMethod.getAccess();
        }

        switch (m_impl) {
        case FromInto:
            MethodInfo infoInto = getIntoMethodInfo();
            return infoInto == null ? null : infoInto.getAccess();

        case Delegating:
        case Field:
        case Capped:
            // TODO CP - need more context info!!!
        }

        return null;
    }

    /**
     * @return the MethodStructure that this MethodBody represents, or null if the method
     *         implementation does not have a MethodStructure, such as when the implementation is
     *         FromInto, Delegating, Field, or Capped
     */
    public MethodStructure getMethodStructure() {
        MethodStructure structMethod = m_structMethod;
        if (structMethod == null) {
            switch (m_impl) {
            case FromInto:
            case Implicit:
            case Delegating:
            case Field:
            case Capped:
                return null;

            default:
                if (m_id.getComponent() instanceof MethodStructure method) {
                    return m_structMethod = method;
                }
            }
        }
        return structMethod;
    }

    /**
     * Set the method structure for this body.
     */
    public void setMethodStructure(MethodStructure method) {
        assert m_structMethod == null;
        m_structMethod = method;
    }

    /**
     * @return true iff this is an abstract method, which means that the method is declared or
     *         implied, but not implemented
     */
    public boolean isAbstract() {
        return switch (m_impl) {
            case FromInto,
                 Implicit,          // this body is abstract, but what it represents may not be
                 Union,             // this body is abstract, but the 2x union "legs" may not be
                 Declared,
                 Abstract,
                 SansCode -> true;  // special case -> it could be used to make a chain non-abstract,
                                    // so even though this body is abstract, the chain may not be

            case Default,           // default methods are neither abstract nor concrete
                 Capped,            // capped are not abstract, because they represent a redirect
                 Delegating,        // delegating methods also represent a redirect
                 Field,             // field access is a terminal (non-abstract) implementation
                 Native,            // native code is a terminal (non-abstract) implementation
                 Explicit -> false; // this is actual "user" code in a class, annotation or mixin
        };
    }

    /**
     * @return true iff the body is neither an abstract nor a default method body
     */
    public boolean isConcrete() {
        return switch (m_impl) {
            case FromInto,
                 Implicit,          // this body is abstract, but what it represents may not be
                 Union,             // this body is abstract, but the 2x union "legs" may not be
                 Declared,
                 Default,           // default methods are neither abstract nor concrete
                 Abstract,
                 SansCode -> false;

            case Capped,
                 Delegating,
                 Field,
                 Native,
                 Explicit -> true;
        };
    }

    /**
     * @return true iff this is a function, not a method or constructor
     */
    public boolean isFunction() {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isFunction();
    }

    /**
     * @return assuming that this is the last body in a chain in a method in a TypeInfo, determine
     *         if this body represents a method present in an "into" type
     */
    public boolean isInto() {
        return m_impl == Implementation.FromInto;
    }

    /**
     * @return the (potentially incomplete) MethodInfo from which the "into" MethodBody was created
     */
    MethodInfo getIntoMethodInfo() {
        assert isInto();
        return (MethodInfo) m_target;
    }

    /**
     * @return true iff the method body is on a (i.e. from a) mixin
     */
    public boolean isMixin() {
        if (getImplementation().EXISTS == Existence.Class) {
            MethodStructure structMethod = getMethodStructure();
            if (structMethod != null && structMethod.getContaining() instanceof ClassStructure clz) {
                    Format fmt = clz.getFormat();
                    return fmt == Format.ANNOTATION || fmt == Format.MIXIN;
            }
        }
        return false;
    }

    /**
     * @return true iff this is a constructor
     */
    public boolean isConstructor() {
        MethodStructure structMethod = getClassifyingMethodStructure();
        return structMethod != null && structMethod.isConstructor();
    }

    /**
     * @return true iff this is a virtual constructor
     */
    public boolean isVirtualConstructor() {
        MethodStructure structMethod = getClassifyingMethodStructure();
        return structMethod != null && structMethod.isVirtualConstructor();
    }

    /**
     * @return true iff this is a validator
     */
    public boolean isValidator() {
        MethodStructure structMethod = getClassifyingMethodStructure();
        return structMethod != null && structMethod.isValidator();
    }

    /**
     * @return true iff this is a constructor or validator, and not a method or function
     */
    public boolean isCtorOrValidator() {
        MethodStructure structMethod = getClassifyingMethodStructure();
        return structMethod != null &&
            (structMethod.isConstructor() || structMethod.isValidator());
    }

    /**
     * @return the MethodStructure used for classification, even when this body is an implicit
     *         FromInto placeholder that deliberately exposes no executable structure
     */
    private MethodStructure getClassifyingMethodStructure() {
        MethodStructure structMethod = getMethodStructure();
        if (structMethod != null || !isInto()) {
            return structMethod;
        }

        MethodInfo infoInto = getIntoMethodInfo();
        if (infoInto != null) {
            return infoInto.getHead().getClassifyingMethodStructure();
        }

        return m_id.getComponent() instanceof MethodStructure method
                ? method
                : null;
    }

    /**
     * @return true iff this is a funky interface function
     */
    public boolean isAbstractFunction() {
        return isFunction() && getImplementation() == Implementation.Declared;
    }

    /**
     * @return true iff this is a synthetic method
     */
    public boolean isSynthetic() {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isSynthetic();
    }

    /**
     * @return true iff this is a non-virtual method that can be covered (subclassed) by private
     *         methods with compatible signatures
     */
    public boolean isVisibilityReductionAllowed() {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null &&
                (structMethod.isConstructor()          ||
                 structMethod.isConstructorFinalizer() ||
                 structMethod.isValidator()            );
    }

    /**
     * @return true iff this specifies the @Override annotation
     */
    public boolean isOverride() {
        if (isUnion()) {
            return getUnionLeft().getHead().isOverride() && getUnionRight().getHead().isOverride();
        }

        return findAnnotation(pool().clzOverride()) != null;
    }

    /**
     * @return true iff this method is native
     */
    public boolean isNative() {
        if (isUnion()) {
            return getUnionLeft().getHead().isNative() && getUnionRight().getHead().isNative();
        }

        return m_impl == Implementation.Native;
    }

    /**
     * Mark this body as native
     */
    public void markNative() {
        if (isUnion()) {
            getUnionLeft().getHead().markNative();
            getUnionRight().getHead().markNative();
        } else {
            m_impl = Implementation.Native;
        }

    }

    /**
     * @return true iff the body represents functionality that would show up in an optimized chain
     */
    public boolean isOptimized() {
        return switch (m_impl) {
            case FromInto,
                 Implicit,
                 Union,
                 Declared,
                 Abstract,
                 SansCode,
                 Capped -> false;

            case Default,           // at most one default method body in an optimized chain
                 Delegating,
                 Field,
                 Native,
                 Explicit -> true;
        };
    }

    /**
     * @return true iff this MethodBody represents the Union of two MethodInfos
     */
    public boolean isUnion() {
        return m_impl == Implementation.Union;
    }

    /**
     * @return the left "leg" of the union MethodInfo
     */
    public MethodInfo getUnionLeft() {
        if (m_target instanceof MethodInfo[] legs) {
            return legs[0];
        }
        throw new IllegalStateException("not a union: " + this);
    }

    /**
     * @return the left "leg" of the union MethodInfo
     */
    public MethodInfo getUnionRight() {
        if (m_target instanceof MethodInfo[] legs) {
            return legs[1];
        }
        throw new IllegalStateException("not a union: " + this);
    }

    /**
     * @return the Implementation form of this MethodBody
     */
    public Implementation getImplementation() {
        return m_impl;
    }

    /**
     * @return true if this method is known to call "super",the next body in the chain
     */
    public boolean usesSuper() {
        if (isUnion()) {
            return getUnionLeft().getHead().usesSuper() && getUnionRight().getHead().usesSuper();
        }

        return m_impl == Implementation.Explicit && getMethodStructure().usesSuper();
    }

    /**
     * @return true if this method blocks a super call from getting to the next body in the chain
     */
    public boolean blocksSuper() {
        switch (m_impl) {
        case FromInto:
        case Implicit:
        case Declared:
        case Capped:    // this does redirect, but eventually the chain comes back to the super
        case Abstract:
        case SansCode:
            return false;

        case Default:
        case Delegating:
        case Field:
        case Native:
            return true;

        case Explicit:
            MethodStructure structMethod = getMethodStructure();
            assert !structMethod.isAbstract();
            return !structMethod.usesSuper();

        case Union:
            // TODO GG TODO CP "||" or "&&"
            return getUnionLeft().getHead().blocksSuper() || getUnionRight().getHead().blocksSuper();

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * @return the PropertyConstant of the property that provides the reference to delegate this
     *         method to
     */
    public PropertyConstant getPropertyConstant() {
        if (isUnion()) {
            PropertyConstant propLeft  = getUnionLeft().getHead().getPropertyConstant();
            PropertyConstant propRight = getUnionRight().getHead().getPropertyConstant();
            return propLeft != null && propRight != null && propLeft.equals(propRight) ? propLeft : null;
        }

        return m_impl == Implementation.Delegating || m_impl == Implementation.Field
                ? (PropertyConstant) m_target
                : null;
    }

    /**
     * @return the <i>resolved</i> nid of the method that narrowed this method, iff this MethodBody
     *         is a cap
     */
    public Object getNarrowingNestedIdentity() {
        if (m_impl == Implementation.Capped) {
            return m_target;
        }

        if (m_impl == Implementation.FromInto) {
            MethodInfo intoInfo = getIntoMethodInfo();
            if (intoInfo != null) {
                return intoInfo.getHead().getNarrowingNestedIdentity();
            }
        }

        if (m_impl == Implementation.Union) {
            Object nidLeft  = getUnionLeft().getHead().getNarrowingNestedIdentity();
            Object nidRight = getUnionRight().getHead().getNarrowingNestedIdentity();
            if (nidLeft == null) {
                return nidRight;
            }
            if (nidRight == null) {
                return nidLeft;
            }
            return nidLeft; // TODO GG or CP ???
        }

        return null;
    }

    /**
     * Determine if this method is annotated with the specified annotation.
     *
     * @param clzAnno  the annotation class to look for
     *
     * @return the annotation, or null
     */
    public Annotation findAnnotation(ClassConstant clzAnno) {
        MethodStructure structMethod = getMethodStructure();
        if (structMethod != null && structMethod.getAnnotationCount() > 0) {
            for (Annotation annotation : structMethod.getAnnotations()) {
                if (((ClassConstant) annotation.getAnnotationClass()).extendsClass(clzAnno)) {
                    return annotation;
                }
            }
        }

        return null;
    }

    /**
     * @return true iff this is an auto converting method
     */
    public boolean isAuto() {
        if (isUnion()) {
            return getUnionLeft().isAuto() && getUnionRight().isAuto();
        }

        // all @Auto methods must have no required params and a single return value
        SignatureConstant sig       = m_id.getSignature();
        MethodStructure   struct    = getMethodStructure();
        int               cRequired = struct == null
                ? sig.getParamCount()
                : struct.getParamCount() - struct.getDefaultParamCount();
        return cRequired == 0 && sig.getReturnCount() > 0 &&
               findAnnotation(pool().clzAuto()) != null;
    }

    /**
     * @return true iff this MethodInfo represents an "@Op" operator method
     */
    public boolean isOp() {
        if (isInto()) {
            MethodInfo info = getIntoMethodInfo();
            return info != null && info.isOp();
        }

        if (isUnion()) {
            return getUnionLeft().isOp() && getUnionRight().isOp();
        }

        return findAnnotation(pool().clzOp()) != null;
    }

    /**
     * Determine if this is a matching "@Op" method.
     *
     * @param sName    the default name of the method (optional)
     * @param sOp      the operator text (optional)
     * @param cParams  the number of method parameters, or -1 to match any
     *
     * @return true iff this is an "@Op" method that matches the specified attributes
     */
    public boolean isOp(String sName, String sOp, int cParams) {
        // must be a method (not a function)
        if (isFunction() || isCtorOrValidator()) {
            return false;
        }

        if (isInto()) {
            return getIntoMethodInfo().isOp(sName, sOp, cParams);
        }

        if (isUnion()) {
            return getUnionLeft().isOp(sName, sOp, cParams) && getUnionRight().isOp(sName, sOp, cParams);
        }

        // there has to be an @Op annotation
        Annotation annotation = findAnnotation(pool().clzOp());
        if (annotation == null) {
            return false;
        }

        // the number of non-default parameters must match
        SignatureConstant sig = getSignature();
        if (cParams >= 0) {
            MethodStructure struct    = getMethodStructure();
            int             cRequired = struct == null
                    ? sig.getParamCount()
                    : struct.getParamCount() - struct.getDefaultParamCount();
            if (cRequired != cParams) {
                return false;
            }
        }

        // if the method name matches the default method name for the op, then we're ok;
        if (sName != null && sig.getName().equals(sName)) {
            return true;
        }

        // otherwise we need to get the operator text from the operator annotation
        // (it's the first of the @Op annotation parameters)
        Constant[] aconstParams = annotation.getParams();
        return aconstParams.length >= 1
                && aconstParams[0] instanceof StringConstant s
                && s.getValue().equals(sOp);
    }

    /**
     * @return the ConstantPool
     */
    private ConstantPool pool() {
        return ConstantPool.getCurrentPool();
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return m_id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof MethodBody that)) {
            return false;
        }

        return this.m_impl == that.m_impl
            && Handy.equals(this.m_id, that.m_id)
            && Handy.equals(this.m_sig, that.m_sig)
            && Handy.equals(this.m_target, that.m_target);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(m_id.getPathString())
          .append(" {sig=")
          .append(m_sig.getValueString())
          .append(", impl=")
          .append(m_impl);

        if (m_target != null) {
            sb.append(", target=");
            if (isInto()) {
                sb.append(getIntoMethodInfo().getHead().getIdentity());
                if (getIntoMethodInfo().isCapped()) {
                    sb.append(" (Capped -> ")
                      .append(getIntoMethodInfo().getHead().getNarrowingNestedIdentity())
                      .append(")");
                }
            } else {
                sb.append(m_target instanceof Constant constant ? constant.getValueString() : m_target);
            }
        }

        return sb.append('}').toString();
    }

    // ----- enumeration: Implementation -----------------------------------------------------------

    /**
     * An enumeration of various forms of method body implementations.
     * <p/>
     * <ul>
     * <li><b>FromInto</b> - the method body represents a method known to exist for compilation
     * purposes, but is otherwise not present; this is the result of the {@code into} clause, or the
     * methods of {@code Object} in the context of an interface, for example;</li>
     * <li><b>Union</b> - the method body represents the union of two MethodInfos from a union type;
     * <li><b>Declared</b> - the method body represents a declared but non-implemented method;</li>
     * <li><b>Default</b> - the method body is a default implementation from an interface;</li>
     * <li><b>Abstract</b> - the method body is on a class, but is explicitly abstract;</li>
     * <li><b>SansCode</b> - the method body has no code, but isn't explicitly abstract;</li>
     * <li><b>Capped</b> - the method body represents the "cap" on a method chain;</li>
     * <li><b>Delegating</b> - the method body is implemented by delegating to the same signature on
     * a different reference;</li>
     * <li><b>Field</b> - the method body represents access to a property's underlying field,
     * which occurs when a property's method is overridden and calls {@code super()};</li>
     * <li><b>Native</b> - the method body is implemented natively by the runtime;</li>
     * <li><b>Explicit</b> - the method body is represented by byte code that gets executed.</li>
     * </ul>
     */
    public enum Implementation {
        FromInto(Existence.Implicit),           // these must only exist within a mixin's TypeInfo
        Implicit(Existence.Implicit),           // assumed to exist with an Explicit Implementation
        Union(Existence.Implicit),              // a union of two methods on a union type
        Declared(Existence.Interface),
        Default(Existence.Interface),
        Abstract(Existence.Class),
        SansCode(Existence.Class),
        Capped(Existence.Class),
        Delegating(Existence.Class),
        Field(Existence.Class),
        Native(Existence.Class),
        Explicit(Existence.Class),
        ;

        private Implementation(Existence existence) {
            EXISTS = existence;
        }

        public final Existence EXISTS;
    }

    /**
     * An enumeration of various forms of method existence:
     * <p/>
     * <ul>
     * <li><b>Implicit</b> - the method exists implicitly; this is the result of the {@code into}
     * clause, or the methods of {@code Object} in the context of an interface, for example;</li>
     * <li><b>Interface</b> - the method is defined as part of an interface;</li>
     * <li><b>Class</b> - the method is defined as part of a class.</li>
     * </ul>
     * <p/>
     * Only the highest level of existence is used; for example, a method that exists due to an
     * "into type" clause, an "implements interface" clause, and is also implemented on a class, is
     * considered to have an Existence of "Class".
     */
    public enum Existence {
        Implicit,
        Interface,
        Class
    }

    // ----- JIT support ---------------------------------------------------------------------------

    /**
     * @return the JitMethodDesc for the method associated with this body
     */
    public synchronized JitMethodDesc getJitDesc(Builder builder, TypeConstant typeTarget) {
        MethodStructure   method = getClassifyingMethodStructure();
        SignatureConstant sig    = method.resolveSignature(
                builder.pool(), typeTarget.getCanonicalJitType());

        // TODO consider caching this
        return JitMethodDesc.of(builder, typeTarget, isFunction() || isCtorOrValidator(),
                isCtorOrValidator(), sig.getRawParams(), sig.getRawReturns(),
                method.getTypeParamCount() + method.getRequiredParamCount());
    }

    /**
     * @return the function or method type for the function or method represented by this body
     */
    public TypeConstant asFunctionType(ConstantPool pool, TypeConstant typeContainer) {
        SignatureConstant sig = getMethodStructure().resolveSignature(pool(), typeContainer);
        return isFunction()
                ? sig.asFunctionType()
                : sig.asMethodType(pool, typeContainer);
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of method bodies.
     */
    public static final MethodBody[] NO_BODIES = new MethodBody[0];

    /**
     * The MethodConstant that this method body corresponds to.
     */
    private final MethodConstant m_id;

    /**
     * The <b>resolved</b> method signature. The MethodBody cannot resolve a signature, because the
     * necessary information is external to the MethodInfo and MethodBody, yet it is required to
     * have the resolved signature so that collisions can be detected and the method chains will be
     * correctly assembled.
     */
    private final SignatureConstant m_sig;

    /**
     * The implementation type for the method body.
     */
    private Implementation m_impl;

    /**
     * The constant denoting additional information (if required) for the MethodBody implementation:
     * <ul>
     * <li>For Implementation "Capped", this specifies a <i>resolved</i> nid for the narrowing
     * method that the cap redirects execution to via a virtual method call;</li>
     * <li>For Implementation "Delegating", this specifies the property which contains the reference
     * to delegate to.</li>
     * <li>For Implementation "Field", this specifies the property that the method body corresponds
     * to. For example, this is used to represent the field access for a {@code get()} method on a
     * property.</li>
     * <li>For Implementation "FromInto", this specifies a MethodInfo that the MethodBody came from.
     * (The value may be null.) First, this makes it possible to avoid incorporates/into infinite
     * recursion. Second, capped chains are visible from the mixin side, allowing for meaningful
     * compiler errors to be raised when methods on the mixin side are overriding known-capped
     * chains. (A mixin may be incorporated at runtime in a manner that collides with a cap, but
     * this would be a validation error at link- or run-time, not at compile-time; the goal is to
     * catch errors at compile time if possible.)</li>
     * <li>For Implementation "Union", this is an array of two MethodInfo, representing the two
     * "legs" of the union.</li>
     * </ul>
     */
    private final Object m_target;

    /**
     * Cached method structure.
     */
    private transient MethodStructure m_structMethod;
}
