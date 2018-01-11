package org.xvm.asm.constants;


import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.MethodBody.Implementation;


/**
 * Represents all of the information about a method (or function). For methods, this includes a
 * method chain, which is a sequence of method bodies representing implementations of the method.
 */
public class MethodInfo
    {
    /**
     * Construct a MethodInfo for a method body.
     *
     * @param body  the initial method body
     */
    public MethodInfo(MethodBody body)
        {
        this(body.getSignature(), new MethodBody[] {body});
        }

    /**
     * Internal: Construct a MethodInfo.
     *
     * @param constSig  the method signature
     * @param abody     the array of method bodies that make up the method chain
     */
    protected MethodInfo(SignatureConstant constSig, MethodBody[] abody)
        {
        assert m_constSig != null;
        assert m_abody != null && m_abody.length >= 1;

        m_constSig = constSig;
        m_abody    = abody;
        }

    /**
     * Use the passed method body as the default implementation for the method chain, if the method
     * chain does not already have a default implementation.
     *
     * @param bodyDefault  the body to use as the default implementation
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo appendDefault(MethodBody bodyDefault)
        {
        if (hasDefault())
            {
            // defaults do not chain
            return this;
            }

        assert !isFunction();

        assert !bodyDefault.isAbstract();
        if (isAbstract())
            {
            // the default replaces the implicit or abstract method body
            return new MethodInfo(m_constSig, new MethodBody[] {bodyDefault});
            }

        // add the default to the end of the chain
        MethodBody[] abodyOld = m_abody;
        int          cbodyOld = abodyOld.length;
        int          cbodyNew = cbodyOld + 1;
        MethodBody[] abodyNew = new MethodBody[cbodyNew];
        System.arraycopy(abodyOld, 0, abodyNew, 0, cbodyOld);
        abodyNew[cbodyOld] = bodyDefault;
        return new MethodInfo(m_constSig, abodyNew);
        }

    /**
     * Use the passed method info as information that needs to be appended to this method info.
     *
     * @param that  the MethodInfo to append to this
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo appendChain(MethodInfo that)
        {
        assert !isFunction();

        // if the thing to append is abstract, then we probably don't have anything to do, i.e. we
        // can use this MethodInfo as is (unless this is also abstract and the other MethodInfo has
        // more information than we do)
        if (that.isAbstract())
            {
            return this.isAbstract()
                && this.m_abody[0].getImplementation() == Implementation.Implicit
                && that.m_abody[0].getImplementation() == Implementation.Declared
                    ? new MethodInfo(m_constSig, that.m_abody)
                    : this;
            }

        // if this is abstract and that isn't, then just use the information from that method info
        if (this.isAbstract())
            {
            return new MethodInfo(m_constSig, that.m_abody);
            }

        // neither this nor that are abstract; combine the two
// TODO remove dups
        MethodBody[] abodyThis    = this.m_abody;
        MethodBody[] abodyThat    = that.m_abody;
        boolean      fThisDefault = this.hasDefault();
        boolean      fThatDefault = that.hasDefault();
        boolean      fHasDefault  = fThisDefault | fThatDefault;
        int          cThis        = this.m_abody.length - (fThisDefault ? 1 : 0);
        int          cThat        = that.m_abody.length - (fThatDefault ? 1 : 0);
        int          cTotal       = cThis + cThat + (fHasDefault ? 1 : 0);
        MethodBody[] abody        = new MethodBody[cTotal];

        System.arraycopy(abodyThis, 0, abody, 0, cThis);
        System.arraycopy(abodyThat, 0, abody, cThis, cThat);
        if (fHasDefault)
            {
            abody[cTotal-1] = fThisDefault ? this.getDefault() : that.getDefault();
            }

        return new MethodInfo(m_constSig, abody);
        }

    /**
     * @return the method signature
     */
    public SignatureConstant getSignature()
        {
        return m_constSig;
        }

    /**
     * @return true iff this is an abstract method
     */
    public boolean isAbstract()
        {
        // the only way to be abstract is to have a chain that contains only a declared or implicit
        // method body
        return m_abody.length == 1 && m_abody[0].isAbstract();
        }

    /**
     * @return true iff this is a function (not a method)
     */
    public boolean isFunction()
        {
        return m_abody.length == 1 && m_abody[0].isFunction();
        }

    /**
     * @return the method chain
     */
    public MethodBody[] getChain()
        {
        return m_abody;
        }

    /**
     * @return true iff the method chain has a default method implementation at the end of it
     */
    public boolean hasDefault()
        {
        // the only way to have a default method body is to have it at the very end of the chain
        return m_abody[m_abody.length-1].getImplementation() == Implementation.Default;
        }

    /**
     * @return the default implementation from the end of this method chain, or null
     */
    public MethodBody getDefault()
        {
        MethodBody body = m_abody[m_abody.length-1];
        return body.getImplementation() == Implementation.Default
                ? body
                : null;
        }

    /**
     * @return the constant to use to invoke the method
     */
    public MethodConstant getMethodConstant()
        {
        return m_abody[0].getMethodConstant();
        }

    /**
     * @return the access of the first method in the chain
     */
    public Access getAccess()
        {
        return m_abody[0].getMethodStructure().getAccess();
        }

    /**
     * @return true iff this MethodInfo represents an "@Auto" auto-conversion method
     */
    public boolean isAuto()
        {
        return m_abody[0].isAuto();
        }

    /**
     * @return true iff this MethodInfo represents an "@Op" operator method
     */
    public boolean isOp()
        {
        return m_abody[0].findAnnotation(m_constSig.getConstantPool().clzOp()) != null;
        }

    /**
     * @return true iff this MethodInfo represents the specified "@Op" operator method
     */
    public boolean isOp(String sName, String sOp, int cParams)
        {
        return m_abody[0].isOp(sName, sOp, cParams);
        }


    // -----fields ---------------------------------------------------------------------------------

    /**
     * The method signature that identifies the MethodInfo.
     */
    private SignatureConstant m_constSig;

    /**
     * The method chain.
     */
    private MethodBody[] m_abody;
    }
