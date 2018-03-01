package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import org.xvm.asm.Component.Contribution;
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
     * @param constSig  the method signature
     * @param body      the initial method body
     */
    public MethodInfo(SignatureConstant constSig, MethodBody body)
        {
        this(constSig, new MethodBody[] {body});
        }

    /**
     * Internal: Construct a MethodInfo.
     *
     * @param constSig  the method signature
     * @param aBody     the array of method bodies that make up the method chain
     */
    protected MethodInfo(SignatureConstant constSig, MethodBody[] aBody)
        {
        assert constSig != null;
        assert aBody != null && aBody.length >= 1;

        m_constSig = constSig;
        m_aBody    = aBody;
        }

    /**
     * Use the passed method info as information that needs to be appended to this method info.
     *
     * @param contrib  the information about how the other MethodInfo is being contributed to this
     * @param that     the MethodInfo to append to this
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo apply(Contribution contrib, MethodInfo that)
        {
        assert !isFunction();

        switch (contrib.getComposition())
            {
            case Implements:
                // since this method info already exists, nothing to "declare" from the passed
                // method info, but it might have a "default" that we can append
                return this.hasDefault() || !that.hasDefault()
                        ? this
                        : append(that.getDefault());

            case Delegates:
                // we'll have to create a method body that represents the delegation
                return append(new MethodBody(that.getMethodConstant(), Implementation.Delegating,
                        contrib.getDelegatePropertyConstant()));

            case Into:
                // this MethodInfo already exists, so an "implicit" adds nothing to this
                // TODO nowhere is the "implicit" implementation actually used, which must be a bug
                return this;
            }

        return append(that);
        }

    /**
     * Use the passed method body as a sequence of implementations to add to the method chain.
     *
     * @param that  the MethodInfo to add
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo append(MethodInfo that)
        {
        MethodBody[] aAdd = that.m_aBody;
        if (aAdd.length == 1)
            {
            return append(aAdd[0]);
            }

        // if the thing to append is abstract, then we probably don't have anything to do, i.e. we
        // can use this MethodInfo as is (unless this is also abstract and the other MethodInfo has
        // more information than we do)
        if (that.isAbstract())
            {
            return this.isAbstract()
                    && this.m_aBody[0].getImplementation() == Implementation.Implicit
                    && that.m_aBody[0].getImplementation() == Implementation.Declared
                    ? new MethodInfo(this.m_constSig, that.m_aBody)
                    : this;
            }

        // if this is abstract and that isn't, then just use the information from that method info
        if (this.isAbstract())
            {
            return new MethodInfo(this.m_constSig, that.m_aBody);
            }

        // neither this nor that are abstract; combine the two
        MethodBody[] aBodyThis    = this.m_aBody;
        MethodBody[] aBodyThat    = that.m_aBody;
        boolean      fThisDefault = this.hasDefault();
        boolean      fThatDefault = that.hasDefault();
        boolean      fHasDefault  = fThisDefault | fThatDefault;
        int          cThis        = this.m_aBody.length - (fThisDefault ? 1 : 0);
        int          cThat        = that.m_aBody.length - (fThatDefault ? 1 : 0);
        int          cTotal       = cThis + cThat + (fHasDefault ? 1 : 0);
        MethodBody[] aBody        = new MethodBody[cTotal];

        System.arraycopy(aBodyThis, 0, aBody, 0, cThis);
        System.arraycopy(aBodyThat, 0, aBody, cThis, cThat);
        if (fHasDefault)
            {
            aBody[cTotal-1] = fThisDefault ? this.getDefault() : that.getDefault();
            }

        return new MethodInfo(m_constSig, aBody);
        }

    /**
     * Use the passed method body as an implementation to add to the method chain.
     *
     * @param body  the MethodBody to add
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo append(MethodBody body)
        {
        assert !isFunction() && !body.isFunction();
        switch (body.getImplementation())
            {
            case Implicit:
                // implicit cannot add anything (because this MethodInfo cannot be "less than"
                // implicit)
                return this;

            case Declared:
                // declared can only add something if this MethodInfo is implicit, in which case
                // this MethodInfo becomes declared
                return m_aBody[0].getImplementation() == Implementation.Implicit
                        ? new MethodInfo(m_constSig, body)
                        : this;

            case Delegating:
            case Property:
            case Native:
            case Explicit:
                if (isAbstract())
                    {
                    return new MethodInfo(m_constSig, body);
                    }

                MethodBody[] aOld = m_aBody;
                int          cOld = aOld.length;
                int          cNew = cOld + 1;
                MethodBody[] aNew = new MethodBody[cNew];
                int          cDft = hasDefault() ? 1 : 0;
                int          cPre = cOld - cDft;
                System.arraycopy(aOld, 0, aNew, 0, cPre);
                aNew[cPre] = body;
                if (cDft > 0)
                    {
                    aNew[cOld] = aOld[cPre];
                    }
                return new MethodInfo(m_constSig, aNew);

            case Default:
                if (hasDefault())
                    {
                    // defaults do not chain
                    return this;
                    }

                assert !body.isAbstract();
                if (isAbstract())
                    {
                    // the default replaces the implicit or abstract method body
                    return new MethodInfo(m_constSig, new MethodBody[] {body});
                    }

                // add the default to the end of the chain
                MethodBody[] aBodyOld = m_aBody;
                int          cBodyOld = aBodyOld.length;
                int          cBodyNew = cBodyOld + 1;
                MethodBody[] aBodyNew = new MethodBody[cBodyNew];
                System.arraycopy(aBodyOld, 0, aBodyNew, 0, cBodyOld);
                aBodyNew[cBodyOld] = body;
                return new MethodInfo(m_constSig, aBodyNew);

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Retain only method bodies that originate from the identities specified in the passed sets.
     *
     * @param setClass    the set of identities that call chain bodies can come from
     * @param setDefault  the set of identities that default bodies can come from
     *
     * @return the resulting MethodInfo, or null if nothing has been retained
     */
    public MethodInfo retainOnly(Set<IdentityConstant> setClass, Set<IdentityConstant> setDefault)
        {
        ArrayList<MethodBody> list  = null;
        MethodBody[]          aBody = m_aBody;
        for (int i = 0, c = aBody.length; i < c; ++i)
            {
            MethodBody       body     = aBody[i];
            IdentityConstant constClz = body.getMethodConstant().getClassIdentity();
            boolean fRetain;
            switch (body.getImplementation())
                {
                case Implicit:
                case Declared:
                    fRetain = setClass.contains(constClz) || setDefault.contains(constClz);
                    break;

                case Default:
                    fRetain = setDefault.contains(constClz);
                    break;

                default:
                    fRetain = setClass.contains(constClz);
                    break;
                }
            if (fRetain)
                {
                if (list != null)
                    {
                    list.add(body);
                    }
                }
            else if (list == null)
                {
                list = new ArrayList<>();
                for (int iCopy = 0; iCopy < i; ++iCopy)
                    {
                    list.add(aBody[iCopy]);
                    }
                }
            }

        if (list == null)
            {
            return this;
            }

        return list.isEmpty()
                ? null
                : new MethodInfo(m_constSig, list.toArray(new MethodBody[list.size()]));
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
        return m_aBody.length == 1 && m_aBody[0].isAbstract();
        }

    /**
     * @return true iff this is a function (not a method)
     */
    public boolean isFunction()
        {
        return m_aBody.length == 1 && m_aBody[0].isFunction();
        }

    /**
     * @return the method chain
     */
    public MethodBody[] getChain()
        {
        return m_aBody;
        }

    /**
     * @return the last concrete MethodBody in the method chain, or null if there is none
     */
    public MethodBody getTail()
        {
        MethodBody[] aBody    = m_aBody;
        int          cBody    = aBody.length;
        MethodBody   bodyLast = aBody[cBody-1];
        if (bodyLast.isConcrete())
            {
            return bodyLast;
            }

        return cBody > 1
                ? aBody[cBody - 2]
                : null;
        }

    /**
     * @return the signature to use to find a "super" call chain
     */
    public SignatureConstant getSubSignature()
        {
        MethodBody bodyTail = getTail();
        return bodyTail == null
                ? null
                : bodyTail.getMethodConstant().getSignature();
        }

    /**
     * @return true iff the method chain has a default method implementation at the end of it
     */
    public boolean hasDefault()
        {
        // the only way to have a default method body is to have it at the very end of the chain
        return m_aBody[m_aBody.length-1].getImplementation() == Implementation.Default;
        }

    /**
     * @return the default implementation from the end of this method chain, or null
     */
    public MethodBody getDefault()
        {
        MethodBody body = m_aBody[m_aBody.length-1];
        return body.getImplementation() == Implementation.Default
                ? body
                : null;
        }

    /**
     * @return the constant to use to invoke the method
     */
    public MethodConstant getMethodConstant()
        {
        return m_aBody[0].getMethodConstant();
        }

    /**
     * @return the access of the first method in the chain
     */
    public Access getAccess()
        {
        return m_aBody[0].getMethodStructure().getAccess();
        }

    /**
     * @return true iff this MethodInfo represents an "@Auto" auto-conversion method
     */
    public boolean isAuto()
        {
        return m_aBody[0].isAuto();
        }

    /**
     * @return true iff this MethodInfo represents an "@Op" operator method
     */
    public boolean isOp()
        {
        return m_aBody[0].findAnnotation(m_constSig.getConstantPool().clzOp()) != null;
        }

    /**
     * @return true iff this MethodInfo represents the specified "@Op" operator method
     */
    public boolean isOp(String sName, String sOp, int cParams)
        {
        return m_aBody[0].isOp(sName, sOp, cParams);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constSig.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof MethodInfo))
            {
            return false;
            }

        MethodInfo that = (MethodInfo) obj;
        return this.m_constSig.equals(that.m_constSig)
                && Arrays.equals(this.m_aBody, that.m_aBody);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_constSig.getValueString());

        int i = 0;
        for (MethodBody body : m_aBody)
            {
            sb.append("\n    [")
              .append(body.isConcrete() ? String.valueOf(i++) : "*")
              .append("] ")
              .append(body);
            }

        return sb.toString();
        }


    // -----fields ---------------------------------------------------------------------------------

    /**
     * The method signature that identifies the MethodInfo.
     */
    private SignatureConstant m_constSig;

    /**
     * The method chain.
     */
    private MethodBody[] m_aBody;
    }
