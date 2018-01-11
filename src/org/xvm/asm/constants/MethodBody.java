package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
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
     * @param impl         one of Implicit, Abstract, Native, or ByteCode
     */
    public MethodBody(MethodConstant constMethod, Implementation impl)
        {
        assert constMethod != null;
        assert impl != null && impl != Implementation.Delegating;

        m_constMethod = constMethod;
        m_impl        = impl;
        }

    /**
     * Construct a delegating method body.
     *
     * @param constMethod  the method constant that this body represents
     * @param constProp    the property constant that provides the reference to delegate to
     */
    public MethodBody(MethodConstant constMethod, PropertyConstant constProp)
        {
        m_constMethod    = constMethod;
        m_impl           = Implementation.Delegating;
        m_constDelegProp = constProp;
        }

    /**
     * @return the signature of the MethodConstant associated with this MethodBody
     */
    public SignatureConstant getSignature()
        {
        return m_constMethod.getSignature();
        }

    /**
     * @return the MethodConstant that this MethodBody represents
     */
    public MethodConstant getMethodConstant()
        {
        return m_constMethod;
        }

    /**
     * @return the MethodStructure that this MethodBody represents
     */
    public MethodStructure getMethodStructure()
        {
        MethodStructure structMethod = m_structMethod;
        if (structMethod == null)
            {
            m_structMethod = structMethod = (MethodStructure) m_constMethod.getComponent();
            }
        return structMethod;
        }

    /**
     * @return true iff this is a function, not a method
     */
    public boolean isFunction()
        {
        return getMethodStructure().isFunction();
        }

    /**
     * @return the Implementation form of this MethodBody
     */
    public Implementation getImplementation()
        {
        return m_impl;
        }

    /**
     * @return the PropertyConstant of the property that provides the reference to delegate this
     *         method to
     */
    public PropertyConstant getDelegationProperty()
        {
        return m_constDelegProp;
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
        MethodStructure struct = getMethodStructure();
        if (struct.getAnnotationCount() > 0)
            {
            for (Annotation annotation : struct.getAnnotations())
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
        return annotation != null &&
                (m_constMethod.getName().equals(sName) ||
                 annotation.getParams().length >= 1 && sOp.equals(annotation.getParams()[0]));
        }


    // ----- enumeration: Implementation -----------------------------------------------------------

    /**
     * An enumeration of various forms of method body implementations.
     * <p/>
     * <ul>
     * <li><b>Implicit</b> - the method body represents a method known to exist for compilation
     * purposes, but is otherwise not present; this is the result of the {@code into} clause</li>
     * <li><b>Abstract</b> - the method body represents a declared but non-implemented method</li>
     * <li><b>Delegating</b> - the method body is implemented by delegating the method call</li>
     * <li><b>Native</b> - the method body is implemented natively by the runtime</li>
     * <li><b>ByteCode</b> - the method body is represented by byte code that gets executed</li>
     * </ul>
     */
    public enum Implementation {Implicit, Abstract, Delegating, Native, ByteCode}


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
     * In the case of delegation, this specifies the property which contains the reference to
     * delegate to.
     */
    private PropertyConstant m_constDelegProp;

    /**
     * The cached method structure.
     */
    private transient MethodStructure m_structMethod;
    }
