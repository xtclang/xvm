package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;


/**
 * Represents a single method (or function) implementation body.
 */
public class MethodBody
    {
    /**
     * Construct an implicit, abstract, native, or normal byte-code method body.
     *
     * @param constMethod  the method constant that this body represents
     * @param impl         one of Implicit, Declared, Native, or Explicit
     */
    public MethodBody(MethodConstant constMethod, Implementation impl)
        {
        assert constMethod != null;
        assert impl != null && impl != Implementation.Delegating && impl != Implementation.Property;

        m_constMethod = constMethod;
        m_impl        = impl;
        }

    /**
     * Construct a delegating or property field access method body.
     *
     * @param constMethod  the method constant that this body represents
     * @param impl         one of Delegating or Property
     * @param constProp    the property constant that provides the reference to delegate to
     */
    public MethodBody(MethodConstant constMethod, Implementation impl, PropertyConstant constProp)
        {
        assert constMethod != null;
        assert impl == Implementation.Delegating || impl == Implementation.Property;
        assert constProp != null;

        m_constMethod = constMethod;
        m_impl        = impl;
        m_constProp   = constProp;
        }

    /**
     * @return the MethodConstant that this MethodBody represents
     */
    public MethodConstant getMethodConstant()
        {
        return m_constMethod;
        }

    /**
     * @return the MethodStructure that this MethodBody represents, or null if the method
     *         implementation is Delegating or Property
     */
    public MethodStructure getMethodStructure()
        {
        MethodStructure structMethod = m_structMethod;
        if (structMethod == null)
            {
            // delegating methods and property methods don't exist in the structural sense
            if (m_impl == Implementation.Delegating || m_impl == Implementation.Property)
                {
                return null;
                }

            m_structMethod = structMethod = (MethodStructure) m_constMethod.getComponent();
            }
        return structMethod;
        }

    /**
     * @return true iff this is an abstract method, which means that the method is declared or
     *         implied, but not implemented
     */
    public boolean isAbstract()
        {
        return m_impl == Implementation.Implicit || m_impl == Implementation.Declared;
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
                return false;

            case Default:
            case Delegating:
            case Property:
            case Native:
                return true;

            case Explicit:
                MethodStructure structMethod = getMethodStructure();
                return !structMethod.isAbstract() && !structMethod.usesSuper();

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
        return m_constProp;
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
        return  m_constMethod.getRawParams().length == 0 &&
                m_constMethod.getRawReturns().length == 1 &&
                findAnnotation(m_constMethod.getConstantPool().clzAuto()) != null;
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
        if (m_constMethod.getRawParams().length != cParams)
            {
            return false;
            }

        // there has to be an @Op annotation
        // if the method name matches the default method name for the op, then we're ok;
        // otherwise we need to get the operator text from the operator annotation
        // (it's the first of the @Op annotation parameters)
        Annotation annotation = findAnnotation(m_constMethod.getConstantPool().clzOp());
        if (annotation == null)
            {
            return false;
            }

        if (m_constMethod.getName().equals(sName))
            {
            return true;
            }

        Constant[] aconstParams = annotation.getParams();
        return aconstParams.length >= 1
                && aconstParams[0] instanceof StringConstant
                && ((StringConstant) aconstParams[0]).getValue().equals(sOp);
        }


    // ----- enumeration: Implementation -----------------------------------------------------------

    /**
     * An enumeration of various forms of method body implementations.
     * <p/>
     * <ul>
     * <li><b>Implicit</b> - the method body represents a method known to exist for compilation
     * purposes, but is otherwise not present; this is the result of the {@code into} clause, or the
     * methods of {@code Object} in the context of an interface, for example</li>
     * <li><b>Declared</b> - the method body represents a declared but non-implemented method</li>
     * <li><b>Default</b> - the method body is a default implementation from an interface</li>
     * <li><b>Delegating</b> - the method body is implemented by delegating the method call</li>
     * <li><b>Property</b> - the method body represents access to a property's underlying field,
     * which occurs when a property's method is overridden and calls {@code super()}</li>
     * <li><b>Native</b> - the method body is implemented natively by the runtime</li>
     * <li><b>Explicit</b> - the method body is represented by byte code that gets executed</li>
     * </ul>
     */
    public enum Implementation {Implicit, Declared, Default, Delegating, Property, Native, Explicit}


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The MethodConstant that this method body corresponds to.
     */
    private MethodConstant m_constMethod;

    /**
     * The implementation type for the method body.
     */
    private Implementation m_impl;

    /**
     * The property related to the method body:
     * <ul>
     * <li>For Implementation Property, this specifies the property that the method body corresponds
     * to. For example, this is used to represent the field access for a {@code get()} method on a
     * property.</li>
     * <li>For Implementation Delegating, this specifies the property which contains the reference
     * to delegate to.</li>
     * </ul>
     */
    private PropertyConstant m_constProp;

    /**
     * The cached method structure.
     */
    private transient MethodStructure m_structMethod;
    }
