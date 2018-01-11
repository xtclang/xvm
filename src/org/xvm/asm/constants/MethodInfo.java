package org.xvm.asm.constants;


import java.util.ArrayList;

import org.xvm.asm.Constants.Access;


/**
 * Represents all of the information about a method (or function).
 *
 * TODO appendDefault / overrideDefault (?)
 * TODO appendInfo(MethodInfo)
 *
 */
public class MethodInfo
    {
    /**
     * Construct a MethodInfo.
     *
     * @param constSig  the signature for the new MethodInfo
     */
    public MethodInfo(MethodBody method)
        {
        // TODO
        m_constSig = constSig;
        }

    /**
     * Construct a MethodInfo that is a copy of another MethodInfo, but with a different
     * signature.
     *
     * @param constSig  the signature for the new MethodInfo
     * @param that      the old MethodInfo to copy state from
     */
    public MethodInfo(SignatureConstant constSig, MethodInfo that)
        {
        this(constSig);
        this.m_bodyPrimary = that.m_bodyPrimary;
        this.m_bodyDefault = that.m_bodyDefault;
        }

    public SignatureConstant getSignature()
        {
        return m_constSig;
        }

    /**
     * @return the first method body in the chain to call
     */
    public MethodBody[] ensureChain()
        {
        // check if we've already cached the chain
        MethodBody[] abody = m_abodyChain;
        if (abody == null)
            {
            ArrayList<MethodBody> list = new ArrayList<>();
            MethodBody body = m_bodyPrimary;
            while (body != null)
                {
                list.add(body);
                body = body.getSuper();
                }
            if (m_bodyDefault != null)
                {
                list.add(m_bodyDefault);
                }
            assert !list.isEmpty();
            m_abodyChain = abody = list.toArray(new MethodBody[list.size()]);
            }

        return abody;
        }

    public MethodBody getFirstMethodBody()
        {
        return ensureChain()[0];
        }

    void prependBody(MethodBody body)
        {
        body.setSuper(m_bodyPrimary);
        m_bodyPrimary = body;
        m_abodyChain  = null;
        }

    void setDefault(MethodBody body)
        {
        m_bodyDefault = body;
        m_abodyChain  = null;
        }

    /**
     * @return the constant to use to invoke the method
     */
    public MethodConstant getMethodConstant()
        {
        // TODO is this correct (GG: review)
        return getFirstMethodBody().getMethodConstant();
        }

    /**
     * @return the access of the first method in the chain
     */
    public Access getAccess()
        {
        return getFirstMethodBody().getMethodStructure().getAccess();
        }

    /**
     * @return true iff this MethodInfo represents an "@Auto" auto-conversion method
     */
    public boolean isAuto()
        {
        return getFirstMethodBody().isAuto();
        }

    /**
     * @return true iff this MethodInfo represents an "@Op" operator method
     */
    public boolean isOp()
        {
        return getFirstMethodBody().findAnnotation(m_constSig.getConstantPool().clzOp()) != null;
        }

    /**
     * @return true iff this MethodInfo represents the specified "@Op" operator method
     */
    public boolean isOp(String sName, String sOp, int cParams)
        {
        return getFirstMethodBody().isOp(sName, sOp, cParams);
        }


    // -----fields ---------------------------------------------------------------------------------

    private SignatureConstant m_constSig;
    private MethodBody        m_bodyPrimary;
    private MethodBody        m_bodyDefault;
    private MethodBody[]      m_abodyChain;
    }
