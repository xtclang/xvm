package org.xvm.asm.constants;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Component;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.asm.constants.ParamInfo.TypeResolver;


/**
 * Represents the "flattened" information about the type.
 */
public class TypeInfo
    {
    public TypeInfo(TypeConstant type, Map<String, ParamInfo> mapTypeParams)
        {
        assert type != null;
        assert mapTypeParams != null;

        this.type       = type;
        this.parameters = mapTypeParams;
        }

    /**
     * @return the format of the topmost structure that the TypeConstant refers to
     */
    public Component.Format getFormat()
        {
        return m_formatActual;
        }

    void setFormat(Component.Format format)
        {
        assert format != null;
        assert m_formatActual == null;
        this.m_formatActual = format;
        }

    public GenericTypeResolver ensureTypeResolver(ErrorListener errs)
        {
        assert errs != null;

        TypeResolver resolver = m_resolver;
        if (resolver == null || resolver.errs != errs)
            {
            m_resolver = resolver = new TypeResolver(parameters, errs);
            }
        return resolver;
        }

    /**
     * @return the TypeConstant representing the "mixin into" type for a mixin, or null if it is
     *         not a mixin
     */
    public TypeConstant getInto()
        {
        return m_typeInto;
        }

    void setInto(TypeConstant type)
        {
        assert type != null;
        assert m_typeInto == null;
        this.m_typeInto = type;
        }

    public boolean isSingleton()
        {
        // TODO
        return false;
        }

    public boolean isAbstract()
        {
        // TODO
        return false;
        }

    public boolean isImmutable()
        {
        // TODO
        return false;
        }

    public boolean isService()
        {
        // TODO
        return false;
        }

    /**
     * Obtain all of the methods that are annotated with "@Op".
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getOpMethodInfos()
        {
        Set<MethodInfo> setOps = m_setOps;
        if (setOps == null)
            {
            for (MethodInfo info : methods.values())
                {
                if (info.isOp())
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info);
                    }
                }

            // cache the result
            m_setOps = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Given the specified method signature, find the most appropriate method that matches that
     * signature, and return that method. If there is no matching method, then return null. If
     * there are multiple methods that match, but it is ambiguous which method is "the best"
     * match, then log an error to the error list, and return null.
     *
     * @param constSig  the method signature to search for
     * @param errs      the error list to log errors to
     *
     * @return the MethodInfo for the method that is the "best match" for the signature, or null
     *         if no method is a best match (including the case in which more than one method
     *         matches, but no one of those methods is a provable unambiguous "best match")
     */
    public MethodInfo findMethod(SignatureConstant constSig, ErrorListener errs)
        {
        // TODO
        return null;
        }

    /**
     * Obtain all of the matching op methods for the specified name and/or the operator string, that
     * take the specified number of params.
     *
     * @param sName    the default op name, such as "add"
     * @param sOp      the operator string, such as "+"
     * @param cParams  the number of parameters for the operator method, such as 1
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodConstant> findOpMethods(String sName, String sOp, int cParams)
        {
        Set<MethodConstant> setOps = null;

        String sKey = sName + sOp + cParams;
        if (m_sOp != null && sKey.equals(m_sOp))
            {
            setOps = m_setOp;
            }
        else
            {
            for (MethodInfo info : getOpMethodInfos())
                {
                if (info.isOp(sName, sOp, cParams))
                    {
                    if (setOps == null)
                        {
                        setOps = new HashSet<>(7);
                        }
                    setOps.add(info.getMethodConstant());
                    }
                }

            // cache the result
            m_sOp   = sKey;
            m_setOp = setOps = (setOps == null ? Collections.EMPTY_SET : setOps);
            }

        return setOps;
        }

    /**
     * Obtain all of the auto conversion methods found on this type.
     *
     * @return a set of zero or more method constants
     */
    public Set<MethodInfo> getAutoMethodInfos()
        {
        Set<MethodInfo> setAuto = m_setAuto;
        if (setAuto == null)
            {
            for (MethodInfo info : methods.values())
                {
                if (info.isAuto())
                    {
                    if (setAuto == null)
                        {
                        setAuto = new HashSet<>(7);
                        }
                    setAuto.add(info);
                    }
                }

            // cache the result
            m_setAuto = setAuto = (setAuto == null ? Collections.EMPTY_SET : setAuto);
            }

        return setAuto;
        }

    /**
     * Find a method on this type that converts an object of this type to a desired type.
     *
     * @param typeDesired  the type desired to convert to, or that the conversion result would be
     *                     assignable to ("isA" would be true)
     *
     * @return a MethodConstant representing an {@code @Auto} conversion method resulting in an
     *         object whose type is compatible with the specified (desired) type, or null if either
     *         no method matches, or more than one method matches (ambiguous)
     */
    public MethodConstant findConversion(TypeConstant typeDesired)
        {
        MethodConstant methodMatch = null;

        // check the cached result
        if (m_typeAuto != null && typeDesired.equals(m_typeAuto))
            {
            methodMatch = m_methodAuto;
            }
        else
            {
            for (MethodInfo info : getAutoMethodInfos())
                {
                MethodConstant method = info.getMethodConstant();
                TypeConstant typeResult = method.getRawReturns()[0];
                if (typeResult.equals(typeDesired))
                    {
                    // exact match -- it's not going to get any better than this
                    return method;
                    }

                if (typeResult.isA(typeDesired))
                    {
                    if (methodMatch == null)
                        {
                        methodMatch = method;
                        }
                    else
                        {
                        TypeConstant typeResultMatch = methodMatch.getRawReturns()[0];
                        boolean fSub = typeResult.isA(typeResultMatch);
                        boolean fSup = typeResultMatch.isA(typeResult);
                        if (fSub ^ fSup)
                            {
                            // use the obviously-more-specific type conversion
                            methodMatch = fSub ? method : methodMatch;
                            }
                        else
                            {
                            // ambiguous - there are at least two methods that match
                            methodMatch = null;
                            break;
                            }
                        }
                    }
                }

            // cache the result
            m_typeAuto   = typeDesired;
            m_methodAuto = methodMatch;
            }

        return methodMatch;
        }


    // -----fields ---------------------------------------------------------------------------------

    public final TypeConstant           type;
    public final Map<String, ParamInfo> parameters;
    public final Map<String, PropertyInfo>          properties = new HashMap<>();
    public final Map<SignatureConstant, MethodInfo> methods    = new HashMap<>();

    /**
     * The Format of the topmost class structure.
     * TODO what about relational types?
     */
    private Component.Format m_formatActual;

    /**
     * This is one of {@link Component.Format#CLASS}, {@link Component.Format#INTERFACE}, and
     * {@link Component.Format#MIXIN}. It identifies how this type is actually used:
     * <ul>
     * <li>Class - this is a type that requires a specific class identity, either by being an
     * instance of that class, or by being a sub-class of that class;</li>
     * <li>Interface - this is an interface type, and ;</li>
     * <li>Mixin - this is an instantiable (or abstract or singleton) class type;</li>
     * </ul>
     */
    private Component.Format m_formatUsage;

    public final Set<TypeConstant> extended     = new HashSet<>();
    public final Set<TypeConstant> implemented  = new HashSet<>();
    public final Set<TypeConstant> incorporated = new HashSet<>();
    public final Set<TypeConstant> implicit     = new HashSet<>();

    private TypeConstant m_typeInto;

    // cached results
    private transient Set<MethodInfo>     m_setAuto;
    private transient Set<MethodInfo>     m_setOps;
    private transient String              m_sOp;
    private transient Set<MethodConstant> m_setOp;
    private transient TypeConstant        m_typeAuto;
    private transient MethodConstant      m_methodAuto;

    // cached resolver
    private transient TypeResolver m_resolver;
    }
