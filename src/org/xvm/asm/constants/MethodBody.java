package org.xvm.asm.constants;


import org.xvm.asm.Annotation;
import org.xvm.asm.MethodStructure;


/**
 * Represents a single method (or function) implementation body.
 */
public class MethodBody
    {
    public MethodBody(MethodConstant constMethod)
        {
        m_constMethod = constMethod;
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
        return (MethodStructure) m_constMethod.getComponent();
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

    /**
     * @return the signature of the MethodConstant associated with this MethodBody
     */
    public SignatureConstant getSignature()
        {
        return getMethodConstant().getSignature();
        }

    /**
     * @return the Implementation form of this MethodBody
     */
    public Implementation getImplementationForm()
        {
        return m_impl;
        }

    void setImplementationForm(Implementation impl)
        {
        assert impl != null;
        m_impl = impl;
        }

    /**
     * @return the PropertyConstant of the property that provides the reference to delegate this
     *         method to
     */
    public PropertyConstant getDelegationProperty()
        {
        return m_constDelegProp;
        }

    void setDelegationProperty(PropertyConstant constProp)
        {
        m_impl           = Implementation.Delegating;
        m_constDelegProp = constProp;
        }

    /**
     * @return the MethodBody that represents the "super" of this MethodBody
     */
    public MethodBody getSuper()
        {
        return m_super;
        }

    void setSuper(MethodBody bodySuper)
        {
        m_super = bodySuper;
        }

    /**
     * An enumeration of various forms of method body implementations.
     */
    public enum Implementation {Implicit, Abstract, Delegating, Native, ActualCode}


    // -----fields ---------------------------------------------------------------------------------

    private MethodConstant m_constMethod;
    private Implementation m_impl = Implementation.ActualCode;
    private PropertyConstant m_constDelegProp;
    private MethodBody       m_super;
    }
