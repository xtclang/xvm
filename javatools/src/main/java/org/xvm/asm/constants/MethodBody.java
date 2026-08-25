package org.xvm.asm.constants;


import java.util.Objects;

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
import org.xvm.util.Hash;


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
        this(id, sig, Implementation.Union, new Target.Union(method1, method2));
    }

    /**
     * Construct a method body with an optional target.
     *
     * @param id      the method constant that this body represents
     * @param sig     the resolved signature of the method
     * @param impl    specifies the implementation of the MethodBody
     * @param target  the typed {@link Target} payload for the implementation kind, or null for
     *                implementations that carry none
     */
    public MethodBody(MethodConstant id, SignatureConstant sig, Implementation impl, Target target) {
        assert id != null && sig != null && impl != null;

        // the Target union already carries the payload shape; this pairing check is the only part
        // the type system cannot express (the old Object shape validated both shape and pairing
        // with asserts, so -da accepted any Object here)
        boolean fValid = switch (impl) {
            case FromInto          -> target == null || target instanceof Target.Origin;
            case Implicit          -> true;
            case Capped            -> target instanceof Target.Narrowing;
            case Delegating, Field -> target instanceof Target.Prop;
            case Union             -> target instanceof Target.Union;
            default                -> target == null;
        };
        if (!fValid) {
            throw new IllegalArgumentException(impl + " body cannot carry target " + target);
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
     * Internal: Copy a MethodBody for a new containing MethodInfo without virtual dispatch. This
     * is the constructor-safe ownership path used by MethodInfo; post-construction adoption uses
     * forMethod(...).
     *
     * @param method  the containing MethodInfo
     * @param body    the MethodBody to copy
     */
    MethodBody(MethodInfo method, MethodBody body) {
        m_infoMethod   = method;
        m_id           = body.m_id;
        m_sig          = body.m_sig;
        // rewrite the target only when the source body genuinely targets its own owner; a fresh
        // unowned body has no target and a null owner, and treating null == null as
        // "self-targeting" would fabricate a target the source never had, changing the body's
        // equality and corrupting union/difference TypeInfo merges built from independently owned
        // copies
        m_target       = body.m_target instanceof Target.Origin(var info) && info == body.m_infoMethod
                ? new Target.Origin(method)
                : body.m_target;
        m_impl         = body.m_impl;
        m_structMethod = body.m_structMethod;
    }

    /**
     * Internal: Associate this MethodBody with the specified MethodInfo, copying it if it already
     * belongs to a different MethodInfo.
     */
    synchronized MethodBody forMethod(MethodInfo method) {
        assert method != null;

        if (m_infoMethod == null) {
            m_infoMethod = method;
            return this;
        }

        return m_infoMethod == method
                ? this
                : new MethodBody(method, this);
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
     * @return the containing MethodInfo, or null if this is a standalone MethodBody
     */
    public MethodInfo getMethodInfo() {
        return m_infoMethod;
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
        return m_target instanceof Target.Origin(var info) ? info : null;
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
     * @return true iff this is a constructor or validator, and not a method, function or
     *         property initializer
     */
    public boolean isCtorOrValidator() {
        MethodStructure structMethod = getClassifyingMethodStructure();
        return structMethod != null &&
                (structMethod.isConstructor() || structMethod.isValidator()) &&
                !structMethod.isPropertyInitializer();
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
        if (m_target instanceof Target.Union(var left, _)) {
            return left;
        }
        throw new IllegalStateException("not a union: " + this);
    }

    /**
     * @return the right "leg" of the union MethodInfo
     */
    public MethodInfo getUnionRight() {
        if (m_target instanceof Target.Union(_, var right)) {
            return right;
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

        // only Delegating and Field bodies can carry a Prop target, by construction
        return m_target instanceof Target.Prop(var idProp) ? idProp : null;
    }

    /**
     * @return the <i>resolved</i> nid of the method that narrowed this method, iff this MethodBody
     *         is a cap; the return type stays the legacy untyped nid union
     *         (SignatureConstant | NestedIdentity) because the TypeInfo member maps still key on it
     */
    public Object getNarrowingNestedIdentity() {
        if (m_impl == Implementation.Capped) {
            return switch (m_target) {
                case Target.BySignature(var sig) -> sig;
                case Target.ByNestedId(var nid)  -> nid;
                case null, default -> throw new IllegalStateException("capped without narrowing: " + this);
            };
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
        // A MethodBody is identified by m_id; annotation lookup must use that identity's owner
        // pool, not whichever pool a caller happened to bind around metadata traversal.
        return m_id.getConstantPool();
    }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return Hash.of(m_id, Hash.of(m_sig, Hash.of(m_impl, targetHash())));
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
            && targetEquals(that);
    }

    /**
     * Compare implementation targets without recursively expanding MethodInfo graphs.
     *
     * <p>FromInto, Implicit, and Union targets are method metadata links, and those links can
     * legitimately point back into the MethodInfo graph that owns this body. Calling
     * MethodInfo.equals() through those links can recurse forever. Compare the stable method
     * identity shape instead; that is the same distinction MethodInfo maps and caches use to find
     * these bodies.</p>
     */
    private boolean targetEquals(MethodBody that) {
        // equals() has already established that both bodies share one Implementation, and the
        // constructor pairing check ties the payload kind to that Implementation
        return switch (this.m_target) {
        case null                       -> that.m_target == null;
        case Target.Origin(var info)    -> that.m_target instanceof Target.Origin(var thatInfo)
                                            && methodTargetEquals(info, thatInfo);
        case Target.Union(var l, var r) -> that.m_target instanceof Target.Union(var thatL, var thatR)
                                            && methodTargetEquals(l, thatL)
                                            && methodTargetEquals(r, thatR);
        case Target.BySignature(var sig) -> that.m_target instanceof Target.BySignature(var thatSig)
                                            && sig.equals(thatSig);
        case Target.ByNestedId(var nid) -> that.m_target instanceof Target.ByNestedId(var thatNid)
                                            && nid.equals(thatNid);
        case Target.Prop(var idProp)    -> that.m_target instanceof Target.Prop(var thatId)
                                            && idProp.equals(thatId);
        };
    }

    private int targetHash() {
        return switch (m_target) {
        case null                        -> 0;
        case Target.Origin(var info)     -> methodTargetHash(info);
        case Target.Union(var l, var r)  -> Hash.of(methodTargetHash(r), methodTargetHash(l));
        case Target.BySignature(var sig) -> Hash.of(sig);
        case Target.ByNestedId(var nid)  -> Hash.of(nid);
        case Target.Prop(var idProp)     -> Hash.of(idProp);
        };
    }

    private static int methodTargetHash(MethodInfo info) {
        return info == null ? 0 : Hash.of(info.getRank(),
                Hash.of(info.getIdentity(), Hash.of(info.getSignature())));
    }

    private static boolean methodTargetEquals(MethodInfo info1, MethodInfo info2) {
        if (info1 == info2) {
            return true;
        }

        return info1 != null && info2 != null
            && info1.getRank() == info2.getRank()
            && Handy.equals(info1.getIdentity(), info2.getIdentity())
            && Handy.equals(info1.getSignature(), info2.getSignature());
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
                sb.append(switch (m_target) {
                    case Target.Prop(var idProp)     -> idProp.getValueString();
                    case Target.BySignature(var sig) -> sig.getValueString();
                    case Target.ByNestedId(var nid)  -> nid;
                    case Target.Origin(var info)     -> info;
                    case Target.Union(var l, var r)  -> l + " + " + r;
                });
            }
        }

        return sb.append('}').toString();
    }

    // ----- typed target payload ------------------------------------------------------------------

    /**
     * The typed payload of a method body, by implementation kind. Master modeled this as a bare
     * {@code Object} whose legal shapes lived in a constructor javadoc and an assert switch that
     * {@code -da} compiled away; every reader re-proved the shape with a cast. The sealed union
     * makes each payload compile-checked and a two-leg union with a missing leg unconstructible.
     */
    public sealed interface Target {
        /**
         * The Capped narrowing nid: a {@link SignatureConstant} for non-nested members, a
         * {@link IdentityConstant.NestedIdentity} for nested ones. This is the typed form of the
         * legacy untyped "nid" convention that the TypeInfo member maps still key on.
         */
        sealed interface Narrowing extends Target permits BySignature, ByNestedId {}

        /** FromInto/Implicit: the (potentially incomplete) origin MethodInfo. */
        record Origin(MethodInfo info) implements Target {
            public Origin {
                Objects.requireNonNull(info);
            }
        }

        /** Capped: narrowed by a non-nested member signature. */
        record BySignature(SignatureConstant sig) implements Narrowing {
            public BySignature {
                Objects.requireNonNull(sig);
            }
        }

        /** Capped: narrowed by a nested member identity. */
        record ByNestedId(IdentityConstant.NestedIdentity nid) implements Narrowing {
            public ByNestedId {
                Objects.requireNonNull(nid);
            }
        }

        /** Delegating/Field: the property that provides the delegate reference or the field. */
        record Prop(PropertyConstant id) implements Target {
            public Prop {
                Objects.requireNonNull(id);
            }
        }

        /** Union: exactly two non-null legs. */
        record Union(MethodInfo left, MethodInfo right) implements Target {
            public Union {
                Objects.requireNonNull(left);
                Objects.requireNonNull(right);
            }
        }

        /**
         * The checked conversion boundary from the legacy untyped nid union; retire this once the
         * TypeInfo member maps key on {@link Narrowing} directly.
         */
        static Narrowing narrowing(Object nid) {
            return switch (nid) {
                case SignatureConstant sig                  -> new BySignature(sig);
                case IdentityConstant.NestedIdentity nested -> new ByNestedId(nested);
                case null, default -> throw new IllegalArgumentException(
                        "not a narrowing nid: " + nid);
            };
        }
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
        if (typeTarget == null) {
            typeTarget = getIdentity().getNamespace().getType();
        }

        MethodStructure   method = getClassifyingMethodStructure();
        SignatureConstant sig    = method.resolveSignature(
                builder.pool(), typeTarget.getCallableJitType());

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
     * The typed payload (if required) for the MethodBody implementation; see {@link Target}:
     * <ul>
     * <li>Capped carries {@link Target.Narrowing} - the <i>resolved</i> nid for the narrowing
     * method that the cap redirects execution to via a virtual method call;</li>
     * <li>Delegating/Field carry {@link Target.Prop} - the property which contains the reference
     * to delegate to, or that the field-access body corresponds to;</li>
     * <li>FromInto carries {@link Target.Origin} (or null) - the MethodInfo that the MethodBody
     * came from. First, this makes it possible to avoid incorporates/into infinite recursion.
     * Second, capped chains are visible from the mixin side, allowing for meaningful compiler
     * errors to be raised when methods on the mixin side are overriding known-capped chains. (A
     * mixin may be incorporated at runtime in a manner that collides with a cap, but this would be
     * a validation error at link- or run-time, not at compile-time; the goal is to catch errors at
     * compile time if possible.)</li>
     * <li>Union carries {@link Target.Union} - the two "legs" of the union.</li>
     * </ul>
     */
    private final Target m_target;

    /**
     * The MethodInfo that contains this MethodBody, or null for a standalone MethodBody.
     */
    private MethodInfo m_infoMethod;

    /**
     * Cached method structure.
     */
    private transient MethodStructure m_structMethod;
}
