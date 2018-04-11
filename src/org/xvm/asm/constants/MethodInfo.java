package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodBody.Implementation;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.startList;


/**
 * Represents all of the information about a method (or function). For methods, this includes a
 * method chain, which is a sequence of method bodies representing implementations of the method.
 */
public class MethodInfo
        implements Constants
    {
    /**
     * Construct a MethodInfo for a method body.
     *
     * @param body  the initial method body
     */
    public MethodInfo(MethodBody body)
        {
        this(new MethodBody[] {body});
        }

    /**
     * Internal: Construct a MethodInfo.
     *
     * @param aBody  the array of method bodies that make up the method chain
     */
    protected MethodInfo(MethodBody[] aBody)
        {
        assert aBody != null && aBody.length >= 1;

        m_aBody = aBody;
        }

    /**
     * Cap this method chain with a redirection to a narrowed method chain. Error checking is the
     * responsibility of the caller.
     *
     * @param that  the method chain to redirect to, which is the narrowed form of this MethodInfo
     *
     * @return a capped version of this method chain
     */
    MethodInfo cappedBy(MethodInfo that)
        {
        // both method chains must be virtual, and neither can already be capped
        assert this.isOverridable();
        assert that.isOverridable();

        // create a MethodConstant for the cap that will sit next to the method body that caused the
        // narrowing to occur
        SignatureConstant sigThis = this.getSignature();
        SignatureConstant sigThat = that.getSignature();
        MethodConstant    idThat  = null;
        for (MethodBody bodyThat : that.getChain())
            {
            if (bodyThat.getSignature().equals(sigThat))
                {
                idThat = bodyThat.getIdentity();
                }
            else
                {
                assert idThat != null;
                break;
                }
            }

        MethodConstant idCap = idThat.getConstantPool().ensureMethodConstant(idThat.getParentConstant(), sigThis);

        MethodBody[] aOld = m_aBody;
        int          cOld = aOld.length;
        MethodBody[] aNew = new MethodBody[cOld+1];

        aNew[0] = new MethodBody(idCap, sigThis, Implementation.Capped, sigThat);
        System.arraycopy(aOld, 0, aNew, 1, cOld);

        return new MethodInfo(aNew);
        }

    /**
     * In terms of the "glass planes" metaphor, the glass planes from "that" are to be layered on to
     * the glass planes of "this", with the resulting combination of glass planes returned as a
     * MethodInfo.
     *
     * @param that   the MethodInfo to layer onto this MethodInfo
     * @param fSelf  true if the layer being added represents the "Equals" contribution of the type
     * @param errs   the error list to log any conflicts to
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo layerOn(MethodInfo that, boolean fSelf, ErrorListener errs)
        {
        assert this.getIdentity().getName().equals(that.getIdentity().getName());
        assert !this.isFunction() && !that.isFunction();
        assert this.getAccess().isAsAccessibleAs(Access.PROTECTED) && that.getAccess().isAsAccessibleAs(Access.PROTECTED);

        MethodBody[] aBase = this.m_aBody;
        MethodBody[] aAdd  = that.m_aBody;
        int          cBase = aBase.length;
        int          cAdd  = aAdd.length;

        // check @Override
        if (fSelf)
            {
            // should only have one layer (or zero layers, in which case we wouldn't have been
            // called) of method body for the "self" layer
            assert cAdd == 1;

            // check @Override
            MethodBody bodyAdd = aAdd[0];
            if (!bodyAdd.isOverride() && !this.containsBody(bodyAdd.getIdentity()))
                {
                MethodConstant id = getIdentity();
                id.log(errs, Severity.ERROR, VE_METHOD_OVERRIDE_REQUIRED,
                        that.getIdentity().getNamespace().getValueString(),
                        id.getSignature().getValueString(),
                        id.getNamespace().getValueString());
                }
            }

        ArrayList<MethodBody> listMerge = null;
        NextLayer: for (int iThat = 0; iThat < cAdd; ++iThat)
            {
            MethodBody bodyThat = aAdd[iThat];
            for (int iThis = 0; iThis < cBase; ++iThis)
                {
                if (bodyThat.equals(aBase[iThis]))
                    {
                    // we found a duplicate, so we can ignore it (it'll get added when we add all of
                    // the bodies from this)
                    continue NextLayer;
                    }
                }
            if (listMerge == null)
                {
                listMerge = new ArrayList<>();
                }
            listMerge.add(bodyThat);
            }

        if (listMerge == null)
            {
            // all the bodies in "that" were duplicates of bodies in "this"
            return this;
            }

        if (!isOverridable())
            {
            // it is not possible to override the base method
            MethodConstant id = getIdentity();
            id.log(errs, Severity.ERROR, VE_METHOD_OVERRIDE_ILLEGAL,
                    that.getIdentity().getNamespace().getValueString(),
                    id.getSignature().getValueString(),
                    id.getNamespace().getValueString());
            return this;
            }

        Collections.addAll(listMerge, aBase);
        return new MethodInfo(listMerge.toArray(new MethodBody[listMerge.size()]));
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
        // functions are, by definition, non-virtual, so they are not affected by yanking,
        // de-duping (they naturally de-dup by having a fixed identity), etc.
        if (isFunction())
            {
            return this;
            }

        ArrayList<MethodBody> list  = null;
        MethodBody[]          aBody = m_aBody;
        for (int i = 0, c = aBody.length; i < c; ++i)
            {
            MethodBody       body     = aBody[i];
            IdentityConstant constClz = body.getIdentity().getClassIdentity();
            boolean fRetain;

            switch (body.getImplementation())
                {
                case Implicit:
                case Native:
                    fRetain = true;
                    break;

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
                list = startList(aBody, i);
                }
            }

        if (list == null)
            {
            return this;
            }

        return list.isEmpty()
                ? null
                : new MethodInfo(list.toArray(new MethodBody[list.size()]));
        }

    /**
     * @return the "into" version of this MethodInfo
     */
    public MethodInfo asInto()
        {
        // basically, if the method is a function, it stays as-is; otherwise, it needs to be
        // "flattened" into a single implicit entry with the right signature
        return isFunction()
                ? this
                : new MethodInfo(new MethodBody(getIdentity(), getSignature(), Implementation.Implicit));
        }

    /**
     * @return the identity of the call chain, which is the MethodConstant that identifies the first
     *         body (which may <i>or may not</i> refer to an actual MethodStructure)
     */
    public MethodConstant getIdentity()
        {
        return getHead().getIdentity();
        }

    /**
     * @return true iff any body of this info has the specified method id
     */
    public boolean containsBody(MethodConstant id)
        {
        for (MethodBody body : m_aBody)
            {
            if (id.equals(body.getIdentity()))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * @return the <i>resolved</i> method signature represented by the call chain; all method bodies
     *         in the call chain have this method signature, or a signature which was narrowed by
     *         this MethodInfo (and so on)
     */
    public SignatureConstant getSignature()
        {
        return getHead().getSignature();
        }

    /**
     * @return the first MethodBody in the call chain
     */
    public MethodBody getHead()
        {
        return m_aBody[0];
        }

    /**
     * @return the last MethodBody in the call chain
     */
    public MethodBody getTail()
        {
        return m_aBody[m_aBody.length-1];
        }

    /**
     * Determine if the method is abstract, which means that its chain must not begin with an
     * abstract method body.
     *
     * @return true iff this is an abstract method
     */
    public boolean isAbstract()
        {
        MethodBody[] abody = m_aBody;
        boolean fIgnoreAbstract = false;
        for (int i = 0, c = abody.length; i < c; ++i)
            {
            MethodBody body = abody[i];
            if (body.isAbstract())
                {
                if (body.getImplementation() == Implementation.SansCode)
                    {
                    fIgnoreAbstract = true;
                    }
                else if (!fIgnoreAbstract || body.getImplementation() != Implementation.Abstract)
                    {
                    return true;
                    }
                // else ... a SansCode followed by any number of Abstract bodies are ignored
                }
            else
                {
                return false;
                }
            }

        return true;
        }

    /**
     * @return true iff this is a function (not a method)
     */
    public boolean isFunction()
        {
        return getHead().isFunction();
        }

    /**
     * @return true iff the method can exist across multiple "glass panes" of the type's composition
     */
    public boolean isVirtual()
        {
        // it can only be virtual if it is non-private and non-function, and if it is not contained
        // within a method or a private property
        if (isFunction() || getAccess() == Access.PRIVATE)
            {
            return false;
            }

        IdentityConstant id = getIdentity();
        for (int i = 1, c = id.getNestedDepth(); i < c; ++i)
            {
            id = id.getParentConstant();
            if ((id instanceof PropertyConstant && id.getComponent().getAccess() == Access.PRIVATE)
                    || id instanceof MethodConstant)
                {
                return false;
                }
            }

        return true;
        }

    /**
     * @return true iff the method chain is capped
     */
    public boolean isCapped()
        {
        return getHead().getImplementation() == Implementation.Capped;
        }

    /**
     * Determine if the method can be overridden.
     *
     * @return true iff the method is virtual and not capped
     */
    public boolean isOverridable()
        {
        return isVirtual() && !isCapped();
        }

    /**
     * @return true iff the last method body in the chain is an override
     */
    public boolean isOverride()
        {
        return getTail().isOverride();
        }

    /**
     * Pre-populate the "info by id" and "info by nid" lookup caches. The caches may contain many
     * entries for the same MethodInfo.
     *
     * @param mapMethods      lookup cache by id
     * @param mapVirtMethods  lookup cache by nid
     */
    public void populateCache(
            MethodConstant                  idMethod,
            Map<MethodConstant, MethodInfo> mapMethods,
            Map<Object, MethodInfo>         mapVirtMethods)
        {
        int cDepth = idMethod.getNestedDepth();
        for (MethodBody body : m_aBody)
            {
            MethodConstant id = body.getIdentity();
            if (id.getNestedDepth() == cDepth)
                {
                mapMethods.putIfAbsent(id, this);
                if (isVirtual())
                    {
                    mapVirtMethods.putIfAbsent(id.getNestedIdentity(), this);
                    }
                }
            }
        }

    /**
     * @return the method chain
     */
    public MethodBody[] getChain()
        {
        return m_aBody;
        }

    /**
     * Obtain the optimized (resolved) call chain for the method. An optimized chain contains the
     * method bodies in the order that they should be invoked, and ending with the first encountered
     * default method. An optimized chain contains only method bodies with the implementation types
     * of: Explicit, Native, Delegating, Field, and Default.
     *
     * @param type  the TypeInfo that contains this method
     *
     * @return a chain of bodies, each representing functionality to invoke, in their "super" order
     */
    public MethodBody[] ensureOptimizedMethodChain(TypeInfo type)
        {
        MethodBody[] aBody = m_aBodyResolved;
        if (aBody == null)
            {
            // grab the "raw" (unoptimized) chain
            aBody = getChain();

            // first, see if this chain was capped, which means that it (virtually) redirects to
            // a different method chain, which (somewhere at the bottom of that chain) will contain
            // the method bodies that are "hidden" under this chain's cap
            if (aBody[0].getImplementation() == Implementation.Capped)
                {
                // note: turtles
                return type.getOptimizedMethodChain(aBody[0].getNarrowingNestedIdentity());
                }

            // see if the chain will work as-is
            ArrayList  listNew     = null;
            MethodBody bodyDefault = null;
            for (int i = 0, c = aBody.length; i < c; ++i)
                {
                MethodBody body = aBody[i];
                switch (body.getImplementation())
                    {
                    case Implicit:
                    case Declared:
                    case Abstract:
                    case SansCode:
                        // this body will be discarded (no code to run), so that alters the chain,
                        // so start building the optimized chain (if it has not already started)
                        if (listNew == null)
                            {
                            listNew = startList(aBody, i);
                            }
                        break;

                    case Default:
                        // only the first one is kept, and it will be placed at the end of the chain
                        if (bodyDefault == null)
                            {
                            bodyDefault = body;
                            }
                        if (listNew == null && i != c-1)
                            {
                            listNew = startList(aBody, i);
                            }
                        break;

                    case Delegating:
                    case Field:
                    case Native:
                    case Explicit:
                        if (listNew != null)
                            {
                            listNew.add(body);
                            }
                        break;

                    default:
                    case Capped:
                        throw new IllegalStateException();
                    }
                }

            // see if any changes were made to the chain, which will have been collected in listNew
            if (listNew != null)
                {
                if (bodyDefault != null)
                    {
                    listNew.add(bodyDefault);
                    }
                int c = listNew.size();
                aBody = c == 0
                        ? MethodBody.NO_BODIES
                        : (MethodBody[]) listNew.toArray(new MethodBody[c]);
                }

            // cache the optimized chain (no worries about race conditions, as the result is
            // idempotent)
            m_aBodyResolved = aBody;
            }

        return aBody;
        }

    /**
     * @return true iff the method chain contains no redirections, abstract bodies, etc.
     */
    public boolean isChainOptimized()
        {
        boolean fHasDefault = false;
        for (MethodBody body : getChain())
            {
            if (!body.isOptimized())
                {
                return false;
                }

            if (body.getImplementation() == Implementation.Default)
                {
                if (fHasDefault)
                    {
                    return false;
                    }
                fHasDefault = true;
                }
            }

        return true;
        }

    /**
     * @return the access of the first method in the chain
     */
    public Access getAccess()
        {
        for (MethodBody body : m_aBody)
            {
            MethodStructure struct = body.getMethodStructure();
            if (struct != null)
                {
                return struct.getAccess();
                }
            }

        throw new IllegalStateException();
        }

    /**
     * @return true iff this MethodInfo represents an "@Auto" auto-conversion method
     */
    public boolean isAuto()
        {
        return getHead().isAuto();
        }

    /**
     * @return true iff this MethodInfo represents an "@Op" operator method
     */
    public boolean isOp()
        {
        return getHead().findAnnotation(pool().clzOp()) != null;
        }

    /**
     * @return true iff this MethodInfo represents the specified "@Op" operator method
     */
    public boolean isOp(String sName, String sOp, int cParams)
        {
        return getHead().isOp(sName, sOp, cParams);
        }

    /**
     * @return the ConstantPool
     */
    ConstantPool pool()
        {
        return getHead().pool();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return getSignature().hashCode();
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
        return Arrays.equals(this.m_aBody, that.m_aBody);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(getSignature().getValueString());

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
     * The method chain.
     */
    private final MethodBody[] m_aBody;

    /**
     * The "optimized" (resolved) method chain.
     */
    private transient MethodBody[] m_aBodyResolved;
    }
