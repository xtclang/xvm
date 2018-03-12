package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.util.Handy;


/**
 * Represents a single method (or function) implementation body.
 */
public class MethodBody
    {
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
     * @param id           the method constant that this body represents
     * @param sig          the resolved signature of the method
     * @param impl         specifies the implementation of the MethodBody
     * @param constTarget  a <i>resolved</i> SignatureConstant from the "narrowing" chain for a
     *                     Capped Implementation; a PropertyConstant for a Delegating or Field
     *                     Implementation; otherwise null
     */
    public MethodBody(MethodConstant id, SignatureConstant sig, Implementation impl, Constant constTarget)
        {
        assert id != null && sig != null && impl != null;
        switch (impl)
            {
            case Capped:
                assert constTarget instanceof SignatureConstant;
                break;
            case Delegating:
            case Field:
                assert constTarget instanceof PropertyConstant;
                break;
            default:
                assert constTarget == null;
                break;
            }

        m_id          = id;
        m_sig         = sig;
        m_impl        = impl;
        m_constTarget = constTarget;
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
                    m_structMethod = structMethod = (MethodStructure) m_id.getComponent();
                }
            }
        return structMethod;
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
     * @return true iff this is a function, not a method
     */
    public boolean isFunction()
        {
        MethodStructure structMethod = getMethodStructure();
        return structMethod != null && structMethod.isFunction();
        }

    /**
     * @return true iff this specifies the @Override annotation
     */
    public boolean isOverride()
        {
        return findAnnotation(pool().clzOverride()) != null;
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
                ? (PropertyConstant) m_constTarget
                : null;
        }

    /**
     * @return the <i>resolved</i> SignatureConstant of the method that narrowed this method, iff
     *         this MethodBody is a cap
     */
    public SignatureConstant getNarrowingNestedIdentity()
        {
        return m_impl == Implementation.Capped
                ? (SignatureConstant) m_constTarget
                : null;
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
        return  m_id.getRawParams().length == 0 &&
                m_id.getRawReturns().length == 1 &&
                findAnnotation(pool().clzAuto()) != null;
        }

    /**
     * Determine if this is a matching "@Op" method.
     *
     * @param sName    the default name of the method
     * @param sOp      the operator text
     * @param cParams  the number of required method parameters
     *
     * @return true iff this is an "@Op" method that matches the specified attributes
     */
    public boolean isOp(String sName, String sOp, int cParams)
        {
        // the number of parameters must match
        if (m_id.getRawParams().length != cParams)
            {
            return false;
            }

        // there has to be an @Op annotation
        // if the method name matches the default method name for the op, then we're ok;
        // otherwise we need to get the operator text from the operator annotation
        // (it's the first of the @Op annotation parameters)
        Annotation annotation = findAnnotation(pool().clzOp());
        if (annotation == null)
            {
            return false;
            }

        if (m_id.getName().equals(sName))
            {
            return true;
            }

        Constant[] aconstParams = annotation.getParams();
        return aconstParams.length >= 1
                && aconstParams[0] instanceof StringConstant
                && ((StringConstant) aconstParams[0]).getValue().equals(sOp);
        }

    /**
     * @return the ConstantPool
     */
    ConstantPool pool()
        {
        return m_id.getConstantPool();
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

        if (!(obj instanceof MethodBody))
            {
            return false;
            }

        MethodBody that = (MethodBody) obj;
        return this.m_impl == that.m_impl
            && Handy.equals(this.m_id, that.m_id)
            && Handy.equals(this.m_sig, that.m_sig)
            && Handy.equals(this.m_constTarget, that.m_constTarget);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_id.getValueString())
          .append(" {sig=")
          .append(m_sig.getValueString())
          .append(", impl=")
          .append(m_impl);

        if (m_constTarget != null)
            {
            sb.append(", target=")
              .append(m_constTarget.getValueString());
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
        Explicit
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
     * <li>For Implementation Capped, this specifies a <i>resolved</i> SignatureConstant from the
     * narrowing method that the cap redirects execution to via a virtual method call;</li>
     * <li>For Implementation Delegating, this specifies the property which contains the reference
     * to delegate to.</li>
     * <li>For Implementation Field, this specifies the property that the method body corresponds
     * to. For example, this is used to represent the field access for a {@code get()} method on a
     * property.</li>
     * </ul>
     */
    private final Constant m_constTarget;

    /**
     * The cached method structure.
     */
    private transient MethodStructure m_structMethod;
    }
