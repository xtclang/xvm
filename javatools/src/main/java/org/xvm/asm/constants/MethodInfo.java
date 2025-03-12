package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody.Implementation;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.appendList;
import static org.xvm.util.Handy.startList;


/**
 * Represents all the information about a method (or function). For methods, this includes a method
 * chain, which is a sequence of method bodies representing implementations of the method.
 */
public class MethodInfo
        implements Constants
    {
    /**
     * Construct a MethodInfo for a method body.
     *
     * @param body   the initial method body
     * @param nRank  the rank of the method
     */
    public MethodInfo(MethodBody body, int nRank)
        {
        this(new MethodBody[] {body}, nRank);
        }

    /**
     * Internal: Construct a MethodInfo.
     *
     * @param aBody  the array of method bodies that make up the method chain
     * @param nRank  the rank of the method
     */
    protected MethodInfo(MethodBody[] aBody, int nRank)
        {
        assert aBody != null && aBody.length >= 1;

        m_aBody = aBody;
        f_nRank = nRank;
        }

    /**
     * Cap this method chain with a redirection to a narrowed method chain. Error checking is the
     * responsibility of the caller.
     *
     * @param typeCtx  the type, for which the MethodInfo is built
     * @param that     the method chain to redirect to, which is the narrowed form of this MethodInfo
     *
     * @return a capped version of this method chain
     */
    MethodInfo capWith(TypeConstant typeCtx, MethodInfo that)
        {
        // both method chains must be virtual, and neither can already be capped
        assert this.isOverridable() || this.containsVirtualConstructor();
        assert that.isOverridable() || that.isPotentialPropertyOverlay()
                                    || that.containsVirtualConstructor();

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

        ConstantPool   pool  = pool();
        MethodConstant idCap = pool.ensureMethodConstant(idThat.getParentConstant(), sigThis);

        MethodBody[] aOld = m_aBody;
        int          cOld = aOld.length;
        MethodBody[] aNew = new MethodBody[cOld+1];

        aNew[0] = new MethodBody(idCap, sigThis, Implementation.Capped,
                                    idThat.resolveNestedIdentity(pool, typeCtx));
        System.arraycopy(aOld, 0, aNew, 1, cOld);

        return new MethodInfo(aNew, f_nRank);
        }

    /**
     * In terms of the "glass planes" metaphor, the glass planes from "that" are to be layered on to
     * the glass planes of "this", with the resulting combination of glass planes returned as a
     * MethodInfo.
     *
     * @param that   the "contribution" MethodInfo to layer onto this MethodInfo
     * @param fSelf  true if the layer being added represents the "Equals" contribution of the type
     * @param errs   the error list to log any conflicts to
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo layerOn(MethodInfo that, boolean fSelf, ErrorListener errs)
        {
        assert this.getIdentity().getName().equals(that.getIdentity().getName());
        assert !this.isFunction() && !that.isFunction();

        if (!this.getAccess().isAsAccessibleAs(Access.PROTECTED) ||
            !that.getAccess().isAsAccessibleAs(Access.PROTECTED) ||
            (this.isConstructor() && !this.containsVirtualConstructor()) ||
            (that.isConstructor() && !that.isVirtualConstructor()))
            {
            MethodConstant id = getIdentity();
            id.log(errs, Severity.ERROR, Constants.VE_METHOD_OVERRIDE_ILLEGAL,
                    that.getIdentity().getNamespace().getValueString(),
                    id.getSignature().getValueString(),
                    id.getNamespace().getValueString());
            return this;
            }

        if (this.equals(that))
            {
            return this;
            }

        MethodBody[] aBase = this.m_aBody;
        MethodBody[] aAdd  = that.m_aBody;
        int          cBase = aBase.length;
        int          cAdd  = aAdd.length;

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

            if (!that.getAccess().isAsAccessibleAs(this.getAccess()))
                {
                IdentityConstant idThat = that.getIdentity();
                idThat.log(errs, Severity.ERROR, VE_METHOD_ACCESS_LESSENED,
                        idThat.getNamespace().getValueString(),
                        idThat.getValueString());
                return that;
                }

            MethodStructure methodThat = bodyAdd.getMethodStructure();
            MethodStructure methodThis = aBase[cBase-1].getMethodStructure();
            if (methodThis != null && methodThat != null)
                {
                int cParamsAll  = methodThis.getVisibleParamCount();
                int cParamsReq  = methodThis.getRequiredParamCount();
                int cParamsThat = methodThat.getRequiredParamCount();
                if (cParamsReq < cParamsThat && cParamsThat <= cParamsAll)
                    {
                    Parameter      param  = methodThat.getParam(cParamsReq);
                    MethodConstant idThat = that.getIdentity();
                    idThat.log(errs, Severity.ERROR, Constants.VE_DEFAULT_VALUE_MISSING,
                            idThat.getClassIdentity().getValueString(),
                            idThat.getValueString(),
                            param.getName());
                    }
                }
            }

        ArrayList<MethodBody> listMerge = null;
        NextLayer: for (int iThat = 0; iThat < cAdd; ++iThat)
            {
            MethodBody bodyThat = aAdd[iThat];
            // allow duplicate interface methods to survive (we need the correct "default" to be on
            // top, and we don't want to yank its duplicate from underneath), except when the
            // equivalent bodies would sit next to each other
            boolean fAllowDuplicate =
                bodyThat.getImplementation().getExistence() == MethodBody.Existence.Interface;

            for (int iThis = 0; iThis < cBase; ++iThis)
                {
                // discard duplicate "into" and class methods
                if (bodyThat.equals(aBase[iThis]))
                    {
                    if (fAllowDuplicate && !(iThis == 0 && iThat == cAdd - 1))
                        {
                        continue;
                        }

                    // we found a duplicate, so we can ignore it (it'll get added when we add
                    // all the bodies from this)
                    if (cBase == 1)
                        {
                        // the duplicate was the only body at the base
                        return that;
                        }
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
            if (fSelf)
                {
                // each type is responsible for reporting the errors only at the "self" level
                MethodConstant id = getIdentity();
                id.log(errs, Severity.ERROR, VE_METHOD_OVERRIDE_ILLEGAL,
                        that.getIdentity().getNamespace().getValueString(),
                        id.getSignature().getValueString(),
                        id.getNamespace().getValueString());
                }
            return this;
            }

        Collections.addAll(listMerge, aBase);
        return new MethodInfo(listMerge.toArray(MethodBody.NO_BODIES), f_nRank);
        }

    /**
     * Layer the MethodInfo for the validator.
     * <p/>
     * This method is different from the "layerOn" method above since the validator is quite special:
     * it is not virtual (not callable directly), but the runtime needs to have the full list
     * of the validators to perform the post-construction validation.
     *
     * @param that  the "contribution" MethodInfo to layer onto this MethodInfo
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo layerOnValidator(MethodInfo that)
        {
        assert this.isValidator() && that.isValidator();

        MethodBody[] aBase = this.m_aBody;
        MethodBody[] aAdd  = that.m_aBody;

        ArrayList<MethodBody> listMerge = null;
        NextLayer: for (MethodBody bodyThat : aAdd)
            {
            for (MethodBody methodBody : aBase)
                {
                if (bodyThat.equals(methodBody))
                    {
                    // ignore a duplicate
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
            return this;
            }

        Collections.addAll(listMerge, aBase);
        return new MethodInfo(listMerge.toArray(MethodBody.NO_BODIES), f_nRank);
        }

    /**
     * Layer the MethodInfo for the virtual constructor.
     * <p/>
     * This method is different from the "layerOn" method above since the virtual constructors are
     * quite special: they are not virtual (not callable directly), but the compiler and validator
     * need this information to enforce the virtual constructor's contract.
     *
     * @param that  the "contribution" MethodInfo to layer onto this MethodInfo
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo layerOnVirtualConstructor(MethodInfo that)
        {
        MethodBody[] aThis = m_aBody;
        int          cThis = -1;
        for (int i = aThis.length - 1; i >=0; --i)
            {
            if (aThis[i].isVirtualConstructor())
                {
                // keep all on top of the first virtual
                cThis = i + 1;
                break;
                }
            }
        assert cThis > 0;

        // add the base virtual constructor at the bottom
        MethodBody[] aThat = that.m_aBody;
        int          cThat = aThat.length;
        MethodBody[] aNew  = new MethodBody[cThat + cThis];

        System.arraycopy(aThat, 0, aNew, 0, cThat);
        System.arraycopy(aThis, 0, aNew, cThat, cThis);

        return new MethodInfo(aNew, f_nRank);
        }

    /**
     * In terms of the "glass planes" metaphor, the glass plane from "this" (contribution) is to
     * replace the glass plane of "that" (base), with the resulting combination of glass planes
     * returned as a MethodInfo.
     *
     * Note, that unlike the virtual method scenario above, the contribution is going to
     * completely replace the base, collecting the replaced information only in order to handle
     * (ignore) repetitive contributions.
     *
     * @param that  the "base" MethodInfo to layer this "contribution" MethodInfo onto
     *
     * @return the resulting MethodInfo
     */
    public MethodInfo subsumeFunction(MethodInfo that)
        {
        assert this.isFunction();
        assert that.isFunction();

        MethodBody[] aBase = that.m_aBody;
        MethodBody[] aAdd  = this.m_aBody;

        ArrayList<MethodBody> listMerge = null;
        NextLayer: for (MethodBody bodyAdd : aAdd)
            {
            for (MethodBody bodyBase : aBase)
                {
                // discard duplicates or an abstract replacing a non-abstract
                if (bodyAdd.equals(bodyBase) ||
                    (bodyAdd.isAbstract() && !bodyBase.isAbstract()))
                    {
                    continue NextLayer;
                    }
                }

            if (listMerge == null)
                {
                listMerge = new ArrayList<>();
                }
            listMerge.add(bodyAdd);
            }

        if (listMerge == null)
            {
            // all the bodies in "this" were duplicates of bodies in "base"
            return that;
            }

        Collections.addAll(listMerge, aBase);
        return new MethodInfo(listMerge.toArray(MethodBody.NO_BODIES), f_nRank);
        }

    /**
     * Retain only method bodies that originate from the identities specified in the passed sets.
     *
     * @param constId     the identity of the method for this operation
     * @param setClass    the set of identities that call chain bodies can come from
     * @param setDefault  the set of identities that default bodies can come from
     *
     * @return the resulting MethodInfo, or null if nothing has been retained
     */
    public MethodInfo retainOnly(MethodConstant        constId,
                                 Set<IdentityConstant> setClass,
                                 Set<IdentityConstant> setDefault)
        {
        // functions are, by definition, non-virtual, so they are not affected by yanking,
        // de-duping (they naturally de-dup by having a fixed identity), etc.
        if (isFunction() || isConstructor())
            {
            return this;
            }

        ArrayList<MethodBody> list  = null;
        MethodBody[]          aBody = m_aBody;
        for (int i = 0, c = aBody.length; i < c; ++i)
            {
            MethodBody       body     = aBody[i];
            IdentityConstant constClz = constId.getClassIdentity();

            boolean fRetain = switch (body.getImplementation())
                {
                // allow these duplicates to survive (ignore retain set)
                case Implicit, SansCode, Declared, Default, Native
                    -> true;
                default
                    -> setClass.contains(constClz) || setDefault.contains(constClz);
                };

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
                : new MethodInfo(list.toArray(MethodBody.NO_BODIES), f_nRank);
        }

    /**
     * When a method on a class originates on an interface which is then implemented by (or
     * otherwise picked up by) a native rebase class, the method isn't marked as native naturally
     * if nothing on the rebase class overrode (or otherwise declared) the method.
     *
     * @param fNative  true iff the type being assembled is a native rebase class
     * @param errs     the error list to log any errors to
     *
     * @return a MethodInfo to use in place of this
     */
    public MethodInfo finishAdoption(boolean fNative, ErrorListener errs)
        {
        // param retained only to match PropertyInfo
        assert fNative;

        if (isFunction() || isConstructor())
            {
            return this;
            }

        MethodBody bodyFirstNonDefault = null;
        for (MethodBody body : m_aBody)
            {
            switch (body.getImplementation())
                {
                case Implicit:
                case Declared:
                case Abstract:
                case SansCode:
                    // methods that are declared but have no bodies (not even a default body) will
                    // automatically be marked as native
                    if (bodyFirstNonDefault == null)
                        {
                        bodyFirstNonDefault = body;
                        }
                    break;

                case Default:
                case Native:
                case Explicit:
                case Capped:
                    return this;

                case Delegating:
                case Field:
                default:
                    // it's not that this is a problem; it's just that it's unexpected, so this
                    // acts as an assertion to flag any occurrences
                    throw new IllegalStateException("Unexpected native class declaration: "
                            + body.getSignature());
                }
            }

        MethodBody bodyResult = new MethodBody(bodyFirstNonDefault.getIdentity(),
                bodyFirstNonDefault.getSignature(), Implementation.Native);
        return layerOn(new MethodInfo(bodyResult, f_nRank), true, errs);
        }

    /**
     * For a capped method info, "indent" the narrowing identity with the nested identity of the
     * specified property.
     *
     * @param pool    the ConstantPool to use
     * @param idProp  the property id
     *
     * @return a new MethodInfo
     */
    public MethodInfo nestNarrowingIdentity(ConstantPool pool, PropertyConstant idProp)
        {
        assert isCapped();

        MethodBody bodyCap    = getHead();
        Object     nidTarget  = idProp.appendNestedIdentity(pool, bodyCap.getNarrowingNestedIdentity()).
                                    resolveNestedIdentity(pool, null);
        MethodBody[] chainNew = getChain().clone();
        chainNew[0] = new MethodBody(bodyCap.getIdentity(), bodyCap.getSignature(),
                                     Implementation.Capped, nidTarget);
        return new MethodInfo(chainNew, f_nRank);
        }

    /**
     * @return the "into" version of this MethodInfo
     */
    public MethodInfo asInto()
        {
        // if the method is a function, constructor or a "cap", it stays as-is (unless it's a function
        // on the Object class); otherwise, it needs to be turned into a chain of implicit entries
        if ((isFunction() || isConstructor() || isCapped()) &&
                    !getIdentity().getNamespace().equals(pool().clzObject()))
            {
            return this;
            }

        return markImplicit();
        }

    /**
     * @return the MethodInfo that marks this constructor as "implicit"
     */
    public MethodInfo markImplicitConstructor()
        {
        assert isConstructor();

        return markImplicit();
        }

    /**
     * Mark all method implementations as "implicit".
     */
    private MethodInfo markImplicit()
        {
        MethodBody[] aBodyOld = m_aBody;
        int          cBodies  = aBodyOld.length;
        MethodBody[] aBodyNew = new MethodBody[cBodies];
        for (int i = 0; i < cBodies; i++)
            {
            MethodBody body = aBodyOld[i];

            aBodyNew[i] = new MethodBody(body.getIdentity(), body.getSignature(), Implementation.Implicit);
            }
        return new MethodInfo(aBodyNew, f_nRank);
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
     * Get the topmost MethodStructure for the call chain represented by this MethodInfo.
     * Note, that the TypeInfo is necessary to be passed only if this method can be a "capped" one.
     *
     * @param infoType  the enclosing TypeInfo
     *
     * @return the first non-null MethodStructure for bodies in the call chain
     */
    public MethodStructure getTopmostMethodStructure(TypeInfo infoType)
        {
        for (MethodBody body : m_aBody)
            {
            if (body.getImplementation() == Implementation.Capped)
                {
                MethodInfo methodNarrowing = infoType.getNarrowingMethod(this);
                assert methodNarrowing != this;
                return methodNarrowing.getTopmostMethodStructure(infoType);
                }

            MethodStructure method = body.getMethodStructure();
            if (method != null)
                {
                return method;
                }
            }

        // we must find something real
        throw new IllegalStateException();
        }

    /**
     * Get an id of the method that this capped method is narrowed by.
     *
     * Note: this method if very similar to {@link TypeInfo#getNarrowingMethod}, except it only
     *       chooses the narrowing methods from the specified map.
     *
     * @param mapVirtMethods  a map of methods keyed by their nids
     *
     * @return the id of the narrowing method or null if it cannot be found
     */
    public Object getNarrowingMethod(Map<Object, MethodInfo> mapVirtMethods)
        {
        assert isCapped();

        Object nidNarrowing = getHead().getNarrowingNestedIdentity();
        for (int i = 0; i < 32; i++)
            {
            MethodInfo methodCapped = mapVirtMethods.get(nidNarrowing);
            if (methodCapped == null || !methodCapped.isCapped())
                {
                break;
                }
            nidNarrowing = methodCapped.getHead().getNarrowingNestedIdentity();
            }
        return nidNarrowing;
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
        boolean fIgnoreAbstract = false;
        int     cDeclParams     = -1;
        int     cDeclReturns    = -1;
        for (MethodBody body : m_aBody)
            {
            switch (body.getImplementation())
                {
                case Implicit:
                    if (isConstructor())
                        {
                        // constructors can only be marked as implicit on virtual child classes
                        // by TypeConstant#layerOnMethods (see an extended explanation there)
                        return false;
                        }
                    break;

                case Declared:
                    cDeclParams  = Math.max(cDeclParams, body.getSignature().getParamCount());
                    cDeclReturns = Math.max(cDeclReturns, body.getSignature().getReturnCount());
                    break;

                case Abstract:
                    if (fIgnoreAbstract)
                        {
                        break;
                        }
                    return true;

                case SansCode:
                    fIgnoreAbstract = true;
                    break;

                case Capped:
                case Default:
                case Delegating:
                case Field:
                case Native:
                case Explicit:
                    if (cDeclParams != -1)
                        {
                        // a non-abstract implementation must have at least as many parameters and
                        // return values as the narrowest declaration
                        if (body.getSignature().getParamCount() < cDeclParams ||
                            body.getSignature().getReturnCount() < cDeclReturns)
                            {
                            break;
                            }
                        }
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
     * @return true iff this is a constructor (not a method or function)
     */
    public boolean isConstructor()
        {
        // virtual constructor could be capped
        return getTail().isConstructor();
        }

    /**
     * @return true iff this is a validator
     */
    public boolean isValidator()
        {
        return getHead().isValidator();
        }

    /**
     * @return true iff the method can exist across multiple "glass panes" of the type's composition
     */
    public boolean isVirtual()
        {
        if (getHead().isVirtualConstructor())
            {
            return true;
            }

        // it can only be virtual if it is non-private and non-function, and if it is not contained
        // within a method or a private property
        if (isFunction() || isConstructor() || getAccess() == Access.PRIVATE)
            {
            return false;
            }

        IdentityConstant id = getIdentity();
        for (int i = 1, c = id.getNestedDepth(); i < c; ++i)
            {
            id = id.getParentConstant();

            if (id instanceof MethodConstant)
                {
                return false;
                }

            if (id instanceof PropertyConstant)
                {
                PropertyStructure prop = (PropertyStructure) id.getComponent();

                // absence of the component indicates that the property was created as "synthetic"
                // via "PropertyConstant.appendNestedIdentity", which means it's not private;
                // a non-simple property may expend into a class-like structure, which may require
                // virtuality
                if (prop != null && prop.getAccess() == Access.PRIVATE && prop.isSimple())
                    {
                    return false;
                    }
                }
            }

        return true;
        }

    /**
     * @return true iff the method represents a constructor that must be overridden by
     *         all extending classes
     */
    public boolean isVirtualConstructor()
        {
        return getHead().isVirtualConstructor();
        }

    /**
     * @return true iff the method covers a virtual constructor
     */
    public boolean containsVirtualConstructor()
        {
        for (MethodBody body : getChain())
            {
            if (body.isVirtualConstructor())
                {
                return true;
                }
            }
        return false;
        }

    /**
     * @return true iff this method is a virtual constructor that <b>is not implemented</b>
     *         by the class represented by the specified TypeInfo
     */
    public boolean isUncoveredVirtualConstructor(TypeInfo info)
        {
        return containsVirtualConstructor() && !isCapped() &&
            getTopmostMethodStructure(info).getContainingClass() != info.getClassStructure();
        }

    /**
     * @return true iff this is an abstract function (declared on an interface)
     */
    public boolean isAbstractFunction()
        {
        MethodBody head = getHead();
        return head.isFunction() && head.getImplementation() == Implementation.Declared;
        }

    /**
     * @return true iff this non-virtual method may be a part of non-trivial call chain
     */
    public boolean isPotentialPropertyOverlay()
        {
        assert !isVirtual();
        return getIdentity().getNamespace() instanceof PropertyConstant;
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
        MethodConstant id = getHead().getIdentity();
        if (id.getNestedDepth() == idMethod.getNestedDepth())
            {
            mapMethods.putIfAbsent(id, this);
            // TODO GG: there is some unpredictability in what MethodInfo gets into the cache first,
            //          which could change the result of the compilation; need to figure out the
            //          best way making it deterministic; after that we may want to restore the
            //          old logic of priming the cache using **all bodies**, not just the head

//            MethodInfo infoOld = mapMethods.putIfAbsent(id, this);
//            if (infoOld != null && !infoOld.equals(this))
//                {
//                System.err.println("*** choose best info for " + idMethod.getValueString());
//                }
            if (isVirtual())
                {
                mapVirtMethods.putIfAbsent(id.getNestedIdentity(), this);
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
     * @param infoType  the TypeInfo that contains this method
     *
     * @return a chain of bodies, each representing functionality to invoke, in their "super" order
     */
    public MethodBody[] ensureOptimizedMethodChain(TypeInfo infoType)
        {
        MethodBody[] chain = m_aBodyResolved;
        if (chain == null)
            {
            // grab the "raw" (unoptimized) chain
            chain = getChain();

            MethodBody bodyHead = chain[0];

            // first, see if this chain was capped, which means that it (virtually) redirects to
            // a different method chain, which (somewhere at the bottom of that chain) will contain
            // the method bodies that are "hidden" under this chain's cap
            if (bodyHead.getImplementation() == Implementation.Capped)
                {
                // note: turtles
                return infoType.getOptimizedMethodChain(bodyHead.getNarrowingNestedIdentity());
                }

            boolean fAnno  = infoType.getFormat() == Component.Format.ANNOTATION;
            boolean fMixin = infoType.getFormat() == Component.Format.MIXIN;

            // see if the chain will work as-is
            ArrayList<MethodBody> listNew     = null;
            ArrayList<MethodBody> listDefault = null;
            forAll:
            for (int i = 0, c = chain.length; i < c; ++i)
                {
                MethodBody     body = chain[i];
                Implementation impl;
                switch (impl = body.getImplementation())
                    {
                    case Implicit:
                        if (fAnno || fMixin)
                            {
                            // since annotations and mixins themselves are not concrete
                            // (instantiatable) we cannot discard the Implicit body; it will be done
                            // by the concrete types
                            if (listNew != null)
                                {
                                listNew.add(body);
                                }
                            break;
                            }
                        // fall through
                    case Declared:
                    case Abstract:
                    case SansCode:
                        // this body will be discarded (no code to run), so that alters the chain,
                        // so start building the optimized chain (if it has not already started)
                        if (listNew == null)
                            {
                            listNew = startList(chain, i);
                            }
                        break;

                    case Default:
                        // all defaults should be placed below any explicit or delegating methods
                        if (listDefault == null)
                            {
                            // check if there's anything else below this point
                            boolean fAllDefaults = true;
                            for (int j = i + 1; j < c; j++)
                                {
                                if (chain[j].getImplementation() != Implementation.Default)
                                    {
                                    fAllDefaults = false;
                                    break;
                                    }
                                }
                            if (fAllDefaults)
                                {
                                if (listNew != null)
                                    {
                                    appendList(listNew, chain, i, c - i);
                                    }
                                break forAll;
                                }
                            listDefault = new ArrayList<>();
                            }
                        // ignore dupes
                        if (!listDefault.contains(body))
                            {
                            listDefault.add(body);
                            }
                        if (listNew == null)
                            {
                            listNew = startList(chain, i);
                            }
                        break;

                    case Delegating:
                    case Field:
                    case Explicit:
                        MethodStructure method = body.getMethodStructure();
                        if (method == null)
                            {
                            assert impl == Implementation.Delegating;

                            IdentityConstant idHost  = body.getIdentity().getNamespace();
                            ClassStructure   clzHost = (ClassStructure) idHost.getComponent();
                            method = clzHost.ensureMethodDelegation(
                                    getTopmostMethodStructure(infoType),
                                    body.getPropertyConstant().getName());
                            body.setMethodStructure(method);
                            }
                        // it's possible the method was marked as "native" after the body
                        // was constructed; re-check it
                        else if (method.isNative())
                            {
                            if (listNew == null)
                                {
                                listNew = startList(chain, i);
                                }
                            body = new MethodBody(body, Implementation.Native);
                            }
                        if (listNew != null)
                            {
                            listNew.add(body);
                            }
                        break;

                    case Native:
                        // interfaces cannot "super" to native implementations; if there are any
                        // default implementations (interface), we can ignore the native one;
                        // moreover, native never "supers" either, so it must be the last one
                        if (listDefault == null && listNew != null)
                            {
                            listNew.add(body);
                            break forAll;
                            }
                        break;

                    case Capped:
                    default:
                        throw new IllegalStateException();
                    }
                }

            // see if any changes were made to the chain, which will have been collected in listNew
            if (listNew != null)
                {
                if (listDefault != null)
                    {
                    listNew.addAll(listDefault);
                    }
                chain = listNew.isEmpty()
                        ? MethodBody.NO_BODIES
                        : listNew.toArray(MethodBody.NO_BODIES);
                }

            // cache the optimized chain (no worries about race conditions, as the result is
            // idempotent)
            m_aBodyResolved = chain;
            }

        return chain;
        }

    /**
     * @return true iff the method has a super for the specified type
     */
    public boolean hasSuper(TypeInfo infoType)
        {
        return getSuper(infoType) != null;
        }

    /**
     * @return the "super" signature
     */
    public SignatureConstant getSuper(TypeInfo infoType)
        {
        MethodBody[] chain = m_aBodyResolved;
        if (chain != null)
            {
            return chain.length > 1 ? chain[1].getSignature() : null;
            }

        // the logic below is a specialized version of the ensureOptimizedMethodChain() method
        chain = getChain();

        MethodBody bodyHead = chain[0];

        if (bodyHead.getImplementation() == Implementation.Capped)
            {
            return infoType.getMethodByNestedId(bodyHead.getNarrowingNestedIdentity()).getSuper(infoType);
            }

        // an accessor for a property with a field always has super()
        MethodStructure method    = bodyHead.getMethodStructure();
        Component       container = method.getParent().getParent();
        if (container instanceof PropertyStructure property)
            {
            if (method == property.getGetter() || method == property.getSetter())
                {
                PropertyInfo infoProp = infoType.findProperty(property.getIdentityConstant());
                if (infoProp.hasField())
                    {
                    return method.getIdentityConstant().getSignature();
                    }
                }
            }

        MethodBody bodySuper = findSuper(infoType, chain);
        if (bodySuper == null)
            {
            return null;
            }

        SignatureConstant sigSuper = bodySuper.getSignature();
        if (sigSuper.containsAutoNarrowing(false))
            {
            sigSuper = sigSuper.resolveAutoNarrowing(pool(), infoType.getType(), null);
            }
        if (sigSuper.containsTypeParameters())
            {
            // formal type parameters need to be resolved by the "head"
            sigSuper = sigSuper.resolveGenericTypes(pool(), getHead().getIdentity());
            }

        return sigSuper;
        }

    private MethodBody findSuper(TypeInfo infoType, MethodBody[] chain)
        {
        boolean    fAnno     = infoType.getFormat() == Component.Format.ANNOTATION;
        boolean    fMixin    = infoType.getFormat() == Component.Format.MIXIN;
        MethodBody bodySuper = null;
        for (int i = 0, cMethods = 0, cAll = chain.length; i < cAll; ++i)
            {
            MethodBody body = chain[i];
            switch (body.getImplementation())
                {
                case Implicit:
                    if (fAnno || fMixin)
                        {
                        cMethods++;
                        }
                    break;

                case Declared:
                case Abstract:
                case SansCode:
                case Capped:
                    break;

                case Default:
                case Delegating:
                    // only the first one is kept; so our count could be a bit off
                    cMethods++;
                    break;

                case Field:
                case Native:
                case Explicit:
                    if (cMethods > 0 && !fAnno && !fMixin)
                        {
                        // some of the bodies could represent an annotation or mixin methods
                        // (e.g. @M class C {}, where both C and M have this method),
                        // in which case we need to restart the count at "this type" level
                        TypeConstant typeThis = infoType.getType();
                        if (typeThis.isSingleDefiningConstant() &&
                            typeThis.getDefiningConstant().equals(body.getIdentity().getNamespace()))
                            {
                            cMethods  = 0;
                            bodySuper = null;
                            }
                        }
                    cMethods++;
                    break;

                default:
                    throw new IllegalStateException();
                }
            if (cMethods == 2 && bodySuper == null)
                {
                bodySuper = body;
                }
            }
        return bodySuper;
        }

    /**
     * @return the access of the first method in the chain; Public if there are no "real" bodies
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

        return Access.PUBLIC;
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
     * @return the current rank
     */
    public int getRank()
        {
        return f_nRank;
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
        return getSignature().hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        if (obj == this)
            {
            return true;
            }

        if (!(obj instanceof MethodInfo that))
            {
            return false;
            }

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


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Rank comparator for Map.Entry<MethodConstant, MethodInfo> objects.
     */
    public static final Comparator<Map.Entry<MethodConstant, MethodInfo>> RANKER =
            Comparator.comparingInt(e -> e.getValue().getRank());

    /**
     * The method chain.
     */
    private final MethodBody[] m_aBody;

    /**
     * This value represents a relative order of method's appearance in the containing class. It's
     * used only to preserve a natural (in the order of introduction) enumeration of methods by the
     * reflection API (e.g. Type.methods, Type.multimethods).
     */
    private final int f_nRank;

    /**
     * The "optimized" (resolved) method chain.
     */
    private transient MethodBody[] m_aBodyResolved;
    }