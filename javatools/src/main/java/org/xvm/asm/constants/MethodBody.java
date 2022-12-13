package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;

import org.xvm.util.Handy;


/**
 * Represents a single method (or function) implementation body.
 */
public class MethodBody
    {
    /**
     * Construct a MethodBody for a lambda or a private method.
     */
    public MethodBody(MethodStructure method)
        {
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
     * @param impl  one of Implicit, Declared, Default, Native, or Explicit
     */
    public MethodBody(MethodConstant id, SignatureConstant sig, Implementation impl)
        {
        this(id, sig, impl, null);
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
     *                otherwise null
     */
    public MethodBody(MethodConstant id, SignatureConstant sig, Implementation impl, Object target)
        {
        assert id != null && sig != null && impl != null;
        switch (impl)
            {
            case Capped:
                assert target instanceof SignatureConstant
                    || target instanceof IdentityConstant.NestedIdentity;
                break;
            case Delegating:
            case Field:
                assert target instanceof PropertyConstant;
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
    public MethodBody(MethodBody body, Implementation impl)
        {
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
    public MethodBody resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
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
    public MethodConstant getIdentity()
        {
        return m_id;
        }

    /**
     * @return the <i>resolved</i> SignatureConstant that this MethodBody represents
     */
    public SignatureConstant getSignature()
        {
        return m_sig;
        }

    /**
     * @return the MethodStructure that this MethodBody represents, or null if the method
     *         implementation does not have a MethodStructure, such as when the implementation is
     *         Delegating, Field, or Capped
     */
    public MethodStructure getMethodStructure()
        {
        MethodStructure structMethod = m_structMethod;
        if (structMethod == null)
            {
            switch (m_impl)
                {
                case Capped:
                case Delegating:
                case Field:
                    return null;

                default:
                    {
                    Component component = m_id.getComponent();
                    if (component instanceof MethodStructure method)
                        {
                        return m_structMethod = method;
                        }
                    }
                }
            }
        return structMethod;
        }

    /**
     * Set the method structure for this body.
     */
    public void setMethodStructure(MethodStructure method)
        {
        assert m_structMethod == null;
        m_structMethod = method;
        }


    /**
     * @return true iff this is an abstract method, which means that the method is declared or
     *         implied, but not implemented
     */
    public boolean isAbstract()
        {
        switch (m_impl)
            {
            case Implicit:
            case Declared:
            case Abstract:
            case SansCode:          // special case: it could be used to make a chain non-abstract,
                                    // so even though this body is abstract, the chain may not be
                return true;

            case Default:           // default methods are neither abstract nor concrete
            case Capped:            // capped are not abstract, because they represent a redirect
            case Delegating:        // delegating methods also represent a redirect
            case Field:             // field access is a terminal (non-abstract) implementation
            case Native:            // native code is a terminal (non-abstract) implementation
            case Explicit:          // this is actual "user" code in a class or mixin
                return false;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return true iff the body is neither an abstract nor a default method body
     */
    public boolean isConcrete()
        {
        switch (m_impl)
            {
            case Implicit:
            case Declared:
            case Default:           // default methods are neither abstract nor concrete
            case Abstract:
            case SansCode:
                return false;

            case Capped:
            case Delegating:
            case Field:
            case Native:
            case Explicit:
                return true;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return true iff this is a function, not a method or constructor
     */
    public boolean isFunction()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isFunction();
        }

    /**
     * @return true iff this is a constructor or validator, and not a method or function
     */
    public boolean isConstructor()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null &&
            (structMethod.isConstructor() || structMethod.isValidator());
        }

    /**
     * @return true iff this is a virtual constructor
     */
    public boolean isVirtualConstructor()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isVirtualConstructor();
        }

    /**
     * @return true iff this is a validator
     */
    public boolean isValidator()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isValidator();
        }

    /**
     * @return true iff this is a synthetic method
     */
    public boolean isSynthetic()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isSynthetic();
        }

    /**
     * @return true iff this is a non-virtual method that can be covered (subclassed) by private
     *         methods with compatible signatures
     */
    public boolean isVisibilityReductionAllowed()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null &&
                (structMethod.isConstructor()          ||
                 structMethod.isConstructorFinalizer() ||
                 structMethod.isValidator()            );
        }

    /**
     * @return true iff this specifies the @Override annotation
     */
    public boolean isOverride()
        {
        return m_impl != Implementation.Implicit && findAnnotation(pool().clzOverride()) != null;
        }

    /**
     * @return true iff this method is native
     */
    public boolean isNative()
        {
        return m_impl == Implementation.Native;
        }

    /**
     * @return true iff the body represents functionality that would show up in an optimized chain
     */
    public boolean isOptimized()
        {
        switch (m_impl)
            {
            case Implicit:
            case Declared:
            case Abstract:
            case SansCode:
            case Capped:
                return false;

            case Default:           // at most one default method body in an optimized chain
            case Delegating:
            case Field:
            case Native:
            case Explicit:
                return true;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return the Implementation form of this MethodBody
     */
    public Implementation getImplementation()
        {
        return m_impl;
        }

    /**
     * @return true if this method is known to call "super",the next body in the chain
     */
    public boolean usesSuper()
        {
        return m_impl == Implementation.Explicit && getMethodStructure().usesSuper();
        }

    /**
     * @return true if this method blocks a super call from getting to the next body in the chain
     */
    public boolean blocksSuper()
        {
        switch (m_impl)
            {
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

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * @return the PropertyConstant of the property that provides the reference to delegate this
     *         method to
     */
    public PropertyConstant getPropertyConstant()
        {
        return m_impl == Implementation.Delegating || m_impl == Implementation.Field
                ? (PropertyConstant) m_target
                : null;
        }

    /**
     * @return the <i>resolved</i> nid of the method that narrowed this method, iff this MethodBody
     *         is a cap
     */
    public Object getNarrowingNestedIdentity()
        {
        return m_impl == Implementation.Capped ? m_target : null;
        }

    /**
     * Determine if this method is annotated with the specified annotation.
     *
     * @param clzAnno  the annotation class to look for
     *
     * @return the annotation, or null
     */
    public Annotation findAnnotation(ClassConstant clzAnno)
        {
        MethodStructure structMethod = getMethodStructure();
        if (structMethod != null && structMethod.getAnnotationCount() > 0)
            {
            for (Annotation annotation : structMethod.getAnnotations())
                {
                if (((ClassConstant) annotation.getAnnotationClass()).extendsClass(clzAnno))
                    {
                    return annotation;
                    }
                }
            }

        return null;
        }

    /**
     * @return true iff this is an auto converting method
     */
    public boolean isAuto()
        {
        // all @Auto methods must have no params and a single return value
        SignatureConstant sig       = m_id.getSignature();
        MethodStructure   struct    = getMethodStructure();
        int               cRequired = struct == null
                ? sig.getParamCount()
                : struct.getParamCount() - struct.getDefaultParamCount();
        return cRequired == 0 && sig.getReturnCount() > 0 &&
               findAnnotation(pool().clzAuto()) != null;
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
    public boolean isOp(String sName, String sOp, int cParams)
        {
        // must be a method (not a function)
        if (isFunction() || isConstructor())
            {
            return false;
            }

        // there has to be an @Op annotation
        Annotation annotation = findAnnotation(pool().clzOp());
        if (annotation == null)
            {
            return false;
            }

        // the number of non-default parameters must match
        SignatureConstant sig = getSignature();
        if (cParams >= 0)
            {
            MethodStructure struct    = getMethodStructure();
            int             cRequired = struct == null
                    ? sig.getParamCount()
                    : struct.getParamCount() - struct.getDefaultParamCount();
            if (cRequired != cParams)
                {
                return false;
                }
            }

        // if the method name matches the default method name for the op, then we're ok;
        if (sName != null && sig.getName().equals(sName))
            {
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
    private ConstantPool pool()
        {
        return ConstantPool.getCurrentPool();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_id.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof MethodBody that))
            {
            return false;
            }

        return this.m_impl == that.m_impl
            && Handy.equals(this.m_id, that.m_id)
            && Handy.equals(this.m_sig, that.m_sig)
            && Handy.equals(this.m_target, that.m_target);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_id.getPathString())
          .append(" {sig=")
          .append(m_sig.getValueString())
          .append(", impl=")
          .append(m_impl);

        if (m_target != null)
            {
            sb.append(", target=")
              .append(m_target instanceof Constant constant ? constant.getValueString() : m_target);
            }

        return sb.append('}').toString();
        }


    // ----- enumeration: Implementation -----------------------------------------------------------

    /**
     * An enumeration of various forms of method body implementations.
     * <p/>
     * <ul>
     * <li><b>Implicit</b> - the method body represents a method known to exist for compilation
     * purposes, but is otherwise not present; this is the result of the {@code into} clause, or the
     * methods of {@code Object} in the context of an interface, for example;</li>
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
    public enum Implementation
        {
        Implicit,
        Declared,
        Default,
        Abstract,
        SansCode,
        Capped,
        Delegating,
        Field,
        Native,
        Explicit;

        public Existence getExistence()
            {
            switch (this)
                {
                case Implicit:
                    return Existence.Implied;

                case Declared:
                case Default:
                    return Existence.Interface;

                default:
                    return Existence.Class;
                }
            }
        }

    /**
     * An enumeration of various forms of method existence:
     * <p/>
     * <ul>
     * <li><b>Implied</b> - the method exists implicitly; this is the result of the {@code into}
     * clause, or the methods of {@code Object} in the context of an interface, for example;</li>
     * <li><b>Interface</b> - the method is defined as part of an interface;</li>
     * <li><b>Class</b> - the method is defined as part of a class.</li>
     * </ul>
     * <p/>
     * Only the highest level of existence is used; for example, a method that exists due to an
     * "into type" clause, an "implements interface" clause, and is also implemented on a class, is
     * considered to have an Existence of "Class".
     */
    public enum Existence
        {
        Implied,
        Interface,
        Class
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
    private final Implementation m_impl;

    /**
     * The constant denoting additional information (if required) for the MethodBody implementation:
     * <ul>
     * <li>For Implementation Capped, this specifies a <i>resolved</i> nid for the narrowing method
     * that the cap redirects execution to via a virtual method call;</li>
     * <li>For Implementation Delegating, this specifies the property which contains the reference
     * to delegate to.</li>
     * <li>For Implementation Field, this specifies the property that the method body corresponds
     * to. For example, this is used to represent the field access for a {@code get()} method on a
     * property.</li>
     * </ul>
     */
    private final Object m_target;

    /**
     * The cached method structure.
     */
    private transient MethodStructure m_structMethod;
    }