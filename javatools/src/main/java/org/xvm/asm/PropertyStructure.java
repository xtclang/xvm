package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.DeferredValueConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.MethodStructure.ConcurrencySafety;

import org.xvm.util.LinkedIterator;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An XVM Structure that represents a property.
 */
public class PropertyStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a PropertyStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Property
     * @param condition  the optional condition for this PropertyStructure
     */
    protected PropertyStructure(XvmStructure xsParent, int nFlags, PropertyConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        }

    /**
     * Construct a PropertyStructure with the specified identity, access, and type.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Property
     * @param condition  the optional condition for this PropertyStructure
     * @param access2
     * @param type
     */
    protected PropertyStructure(XvmStructure xsParent, int nFlags, PropertyConstant constId, ConditionalConstant condition, Access access2, TypeConstant type)
        {
        this(xsParent, nFlags, constId, condition);
        if (access2 != null)
            {
            setVarAccess(access2);
            }
        if (type != null)
            {
            setType(type);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public PropertyConstant getIdentityConstant()
        {
        return (PropertyConstant) super.getIdentityConstant();
        }

    @Override
    public void setAccess(Access access)
        {
        super.setAccess(access);
        if (access.ordinal() > getVarAccess().ordinal())
            {
            setVarAccess(access);
            }
        }

    /**
     * @return true iff the property is a named constant value
     */
    public boolean isConstant()
        {
        return isStatic();
        }

    /**
     * @return true iff the property value is going to be a constant at run time
     */
    public boolean isRuntimeConstant()
        {
        return isConstant() ||
               getParent() instanceof ClassStructure clzParent &&
               clzParent.isSingleton() && (hasInitialValue() || getInitializer() != null) &&
               !isTransient();
        }

    /**
     * The "Var" access of the property, which may be more restricted than the access of the
     * property (as a Ref). Null implies that the property does not have a "Var" access specified,
     * which means either that the "Var" access is the same as the "Ref" access, or that there is
     * no "Var" access.
     *
     * @return the access of the property as a Var if specified; otherwise null
     */
    public Access getVarAccess()
        {
        return m_accessVar;
        }

    public void setVarAccess(Access access)
        {
        assert access == null || access.ordinal() >= getAccess().ordinal();
        m_accessVar = access;
        }

    /**
     * Check if the "Ref" for property is accessible with the specified access policy.
     */
    public boolean isRefAccessible(Access access)
        {
        return getAccess().ordinal() <= access.ordinal();
        }

    /**
     * Check if the "Var" for this property is accessible with the specified access policy.
     */
    public boolean isVarAccessible(Access access)
        {
        Access accessVar = getVarAccess();
        return accessVar == null
                ? isRefAccessible(access)
                : accessVar.ordinal() <= access.ordinal();
        }

    /**
     * @return the TypeConstant representing the data type of the property value
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * Configure the property's type.
     *
     * @param type  the type constant that indicates the property's type
     */
    public void setType(TypeConstant type)
        {
        assert type != null;
        m_type = type;

        getIdentityConstant().invalidateCache();
        }

    /**
     * @return true iff this property is a generic type parameter
     */
    public boolean isGenericTypeParameter()
        {
        return isAuxiliary();
        }

    /**
     * Mark this property as a generic type parameter.
     */
    public void markAsGenericTypeParameter()
        {
        assert !isStatic()            // never a constant
            && !isSynthetic()         // never synthetic
            && m_type.isTypeOfType(); // must be "Type"

        markAuxiliary();
        }

    /**
     * @return true iff this property contains the specified property annotation
     */
    public boolean containsPropertyAnnotation(IdentityConstant idAnno)
        {
        for (Annotation anno : getPropertyAnnotations())
            {
            if (anno.getAnnotationClass().equals(idAnno))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * @return an array of all annotations that are <i>"{@code into Property}"</i> annotations
     */
    public Annotation[] getPropertyAnnotations()
        {
        Annotation[] aAnno = m_aPropAnno;
        if (aAnno == null)
            {
            buildAnnotationArrays();
            aAnno = m_aPropAnno;
            assert aAnno != null;
            }
        return aAnno;
        }

    /**
     * @return true if the property is annotated by "@RO"
     */
    public boolean isExplicitReadOnly()
        {
        return containsPropertyAnnotation(getConstantPool().clzRO());
        }

    /**
     * @return true if the property is annotated by "@Override"
     */
    public boolean isExplicitOverride()
        {
        return containsPropertyAnnotation(getConstantPool().clzOverride());
        }

    /**
     * @return true if the property is annotated by "@Transient"
     */
    public boolean isTransient()
        {
        return containsPropertyAnnotation(getConstantPool().clzTransient());
        }

    /**
     * @return true iff the property contains any reference annotations
     */
    public boolean isRefAnnotated()
        {
        return getRefAnnotations().length > 0;
        }

    /**
     * @return true iff the property has an Atomic annotation
     */
    public boolean isAtomic()
        {
        return containsRefAnnotation(getConstantPool().clzAtomic());
        }

    /**
     * @return true iff the property has a Future annotation
     */
    public boolean isFuture()
        {
        return containsRefAnnotation(getConstantPool().clzFuture());
        }

    /**
     * @return true iff the property has an Injected annotation
     */
    public boolean isInjected()
        {
        return containsRefAnnotation(getConstantPool().clzInject());
        }

    /**
     * @return true iff the property doesn't expand into a "class-like" structure
     */
    public boolean isSimple()
        {
        return !isRefAnnotated() && !hasChildren();
        }

    /**
     * @return return true iff this property is marked as "Unassigned" and has no other annotations
     */
    public boolean isSimpleUnassigned()
        {
        Annotation[] aAnnos = getRefAnnotations();
        return aAnnos.length == 1
            && (aAnnos[0].getAnnotationClass()).equals(getConstantPool().clzUnassigned());
        }

    /**
     * @return true iff this property contains the specified Ref annotation
     */
    public boolean containsRefAnnotation(IdentityConstant idAnno)
        {
        for (Annotation anno : getRefAnnotations())
            {
            if (anno.getAnnotationClass().equals(idAnno))
                {
                return true;
                }
            }
        return false;
        }

    /**
     * @return an array of all annotations that are <i>not</i> property annotations
     */
    public Annotation[] getRefAnnotations()
        {
        Annotation[] aAnno = m_aRefAnno;
        if (aAnno == null)
            {
            buildAnnotationArrays();
            aAnno = m_aRefAnno;
            assert aAnno != null;
            }
        return aAnno;
        }

    private void buildAnnotationArrays()
        {
        ConstantPool     pool         = getConstantPool();
        List<Annotation> listPropAnno = null;
        List<Annotation> listRefAnno  = null;
        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                Annotation   annotation = contrib.getAnnotation();
                Constant     constMixin = annotation.getAnnotationClass();
                TypeConstant typeMixin  = pool.ensureTerminalTypeConstant(constMixin);
                if (typeMixin.isExplicitClassIdentity(true)
                        && typeMixin.getExplicitClassFormat() == Component.Format.MIXIN
                        && pool.typeProperty().equals(typeMixin.getExplicitClassInto().getIntoPropertyType()))
                    {
                    if (listPropAnno == null)
                        {
                        listPropAnno = new ArrayList<>();
                        }
                    listPropAnno.add(annotation);
                    }
                else
                    {
                    if (listRefAnno == null)
                        {
                        listRefAnno = new ArrayList<>();
                        }
                    listRefAnno.add(annotation);
                    }
                }
            }

        m_aPropAnno = listPropAnno == null
                ? Annotation.NO_ANNOTATIONS
                : listPropAnno.toArray(Annotation.NO_ANNOTATIONS);
        m_aRefAnno = listRefAnno == null
                ? Annotation.NO_ANNOTATIONS
                : listRefAnno.toArray(Annotation.NO_ANNOTATIONS);
        }

    /**
     * This is a temporary value that is used as a place-holder until the property's actual value is
     * available.
     *
     * @return true iff the Property is known to have a value, even if the value has not yet been
     *         determined
     */
    public boolean hasInitialValue()
        {
        return m_constVal != null;
        }

    /**
     * @return the Constant representing the initial value of the property, or null if the property
     *         is not initialized using a Constant value, for example, if the property gets its
     *         initial value from an initializer function, from a constructor, etc.
     */
    public Constant getInitialValue()
        {
        return m_constVal;
        }

    /**
     * @param constVal  the value for the property, or null if an initializer function is going to
     *                  be used to provide the initial value for the property
     */
    public void setInitialValue(Constant constVal)
        {
        m_constVal = constVal;
        }

    /**
     * Mark this property as having an initial value that is unknown at this time and which will be
     * replaced with a real value later in the compilation cycle.
     */
    public void indicateInitialValue()
        {
        setInitialValue(new DeferredValueConstant(getConstantPool()));
        }

    /**
     * @return the MethodStructure representing the initializer of the property, or null if the
     *         property is not initialized using an initializer function
     */
    public MethodStructure getInitializer()
        {
        MultiMethodStructure mmInit = (MultiMethodStructure) getChild("=");
        if (mmInit != null)
            {
            Collection<MethodStructure> methods = mmInit.methods();
            if (methods != null && !methods.isEmpty())
                {
                return methods.iterator().next();
                }
            }
        return null;
        }

    /**
     * @return the MethodStructure for the "get" method; null if there is no getter
     */
    public MethodStructure getGetter()
        {
        MultiMethodStructure mms = (MultiMethodStructure) getChild("get");

        if (mms != null)
            {
            for (MethodStructure method : mms.methods())
                {
                if (method.getParamArray().length > 0)
                    {
                    continue;
                    }
                Parameter[] aParamReturn = method.getReturnArray();
                if (aParamReturn.length != 1)
                    {
                    continue;
                    }

                if (aParamReturn[0].getType().equals(getType()))
                    {
                    return method;
                    }
                }
            }
        return null;
        }

    /**
     * @return the MethodStructure for the "get" method; null if there is no getter
     */
    public MethodStructure getSetter()
        {
        MultiMethodStructure mms = (MultiMethodStructure) getChild("set");

        if (mms != null)
            {
            for (MethodStructure method : mms.methods())
                {
                if (method.getReturnArray().length > 0)
                    {
                    continue;
                    }
                Parameter[] aParam = method.getParamArray();
                if (aParam.length != 1)
                    {
                    continue;
                    }

                if (aParam[0].getType().equals(getType()))
                    {
                    return method;
                    }
                }
            }
        return null;
        }

    /**
     * @return true iff the accessors for the property are native
     */
    public boolean isNative()
        {
        return m_fNative;
        }

    /**
     * Mark the accessors for the property as native.
     */
    public void markNative()
        {
        m_fNative = true;
        }

    /**
     * Check if this property could be accessed via the specified signature.
     *
     * @param pool        the ConstantPool to place a potentially created new constant into
     * @param sigThat     the signature of the matching property (resolved)
     * @param listActual  the actual generic types
     */
    public boolean isSubstitutableFor(ConstantPool pool,
                                      SignatureConstant sigThat, List<TypeConstant> listActual)
        {
        assert getName().equals(sigThat.getName());
        assert sigThat.getParamCount() == 0;
        assert sigThat.getReturnCount() == 1;

        SignatureConstant sigThis = getIdentityConstant().getSignature();
        if (!listActual.isEmpty())
            {
            ClassStructure clzThis = (ClassStructure) getParent();
            sigThis = sigThis.resolveGenericTypes(pool, clzThis.new SimpleTypeResolver(pool, listActual));
            }

        return sigThat.equals(sigThis) ||
               sigThat.isSubstitutableFor(sigThis, null) && sigThis.isSubstitutableFor(sigThat, null);
        }

    /**
     * For a property structure that contains annotations whose types are now resolved, sort the
     * annotations into the annotations that apply to the property, the annotations that apply to
     * the Ref/Var, and the annotations that apply to the property type.
     * <p/>
     * This method isn't responsible for validating the annotations, so it doesn't log any errors.
     * Anything that it finds that is suspect, it leaves for someone else to validate later.
     *
     * @return true iff the annotations are resolved
     */
    public boolean resolveAnnotations()
        {
        // first, make sure that the property type and the annotations are all resolved
        List<Contribution> listContribs = getContributionsAsList();
        if (listContribs.isEmpty())
            {
            return true;
            }

        TypeConstant typeProp = getType();
        if (typeProp.containsUnresolved())
            {
            return false;
            }

        List<Contribution> listMove = null;
        for (Contribution contrib : listContribs)
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                Annotation annotation = contrib.getAnnotation();
                // annotations parameters don't need to be resolved for the compilation to move
                // forward; they will be checked later as a part of the property type resolution
                if (annotation.getAnnotationClass().containsUnresolved())
                    {
                    return false;
                    }

                // find the "into" of the mixin
                ClassStructure structMixin = (ClassStructure) ((IdentityConstant) annotation.getAnnotationClass()).getComponent();
                Contribution   contribInto = null;
                Contribution   contribExtends;
                while (structMixin != null && structMixin.getFormat() == Format.MIXIN
                        && (contribInto    = structMixin.findContribution(Composition.Into   )) == null
                        && (contribExtends = structMixin.findContribution(Composition.Extends)) != null)
                    {
                    TypeConstant typeExtends = contribExtends.getTypeConstant();
                    if (typeExtends.containsUnresolved())
                        {
                        return false;
                        }

                    structMixin = null;
                    if (typeExtends.isExplicitClassIdentity(true))
                        {
                        Constant constExtends = typeExtends.getDefiningConstant();
                        if (constExtends instanceof IdentityConstant idExtends)
                            {
                            structMixin = (ClassStructure) idExtends.getComponent();
                            }
                        }
                    }

                // see if the mixin applies to a Property or a Ref/Var, in which case it stays in
                // this list; otherwise (e.g. AutoFreezable), move it into the type itself
                boolean fMove = true;
                if (contribInto != null)
                    {
                    TypeConstant typeInto = contribInto.getTypeConstant();
                    if (typeInto.containsUnresolved())
                        {
                        return false;
                        }

                    fMove = !typeInto.isIntoPropertyType();
                    }

                if (fMove)
                    {
                    if (listMove == null)
                        {
                        listMove = new ArrayList<>();
                        }
                    listMove.add(contrib);
                    }
                }
            }

        // now that everything is figured out, do the actual move of any selected contributions
        if (listMove != null)
            {
            // go backwards to that the resulting type constant is built up (nested) correctly
            ConstantPool pool = getConstantPool();
            for (int i = listMove.size()-1; i >= 0; --i)
                {
                Contribution contrib    = listMove.get(i);
                Annotation   annotation = contrib.getAnnotation();
                removeContribution(contrib);
                typeProp = pool.ensureAnnotatedTypeConstant
                        (annotation.getAnnotationClass(), annotation.getParams(), typeProp);
                }
            setType(typeProp);
            }

        return true;
        }


    // ----- component methods ---------------------------------------------------------------------

    @Override
    public boolean isClassContainer()
        {
        return true;
        }

    @Override
    public ConcurrencySafety getConcurrencySafety()
        {
        ConstantPool pool = getConstantPool();
        if (containsPropertyAnnotation(pool.clzSynchronized()))
            {
            return ConcurrencySafety.Unsafe;
            }

        if (containsPropertyAnnotation(pool.clzConcurrent()))
            {
            return ConcurrencySafety.Safe;
            }

        return getParent().getConcurrencySafety();
        }

    @Override
    public boolean isMethodContainer()
        {
        return true;
        }

    @Override
    public boolean isAutoNarrowingAllowed()
        {
        // the property's type is allowed to auto-narrow, but only for non-static properties
        // belonging to a non-singleton class (even if nested inside another property); note that
        // a property inside a method is ALWAYS static
        return !isStatic() && getParent().isAutoNarrowingAllowed();
        }

    @Override
    public ResolutionResult resolveName(String sName, Access access, ResolutionCollector collector)
        {
        // allow the property to resolve names based on the property type;
        // it comes handy in the context inference scenario, allowing us to write:
        //    Color c = Red;
        // instead of
        //    Color c = Color.Red;
        // (see also AssignmentStatement.validate)
        //
        // Note; we don't call into the super since the PropertyStructure's children are
        // "invisible" without the name qualification

        SimpleCollector  collectorTemp = new SimpleCollector(collector.getErrorListener());
        ResolutionResult result        = getType().resolveContributedName(sName, access, null, collectorTemp);
        if (result == ResolutionResult.RESOLVED)
            {
            // we only allow static properties (constants) and singleton classes
            Constant constant = collectorTemp.getResolvedConstant();
            switch (constant.getFormat())
                {
                case Property:
                    {
                    PropertyConstant idProp = (PropertyConstant) constant;
                    if (idProp.isConstant())
                        {
                        collector.resolvedConstant(constant);
                        return ResolutionResult.RESOLVED;
                        }
                    break;
                    }

                case Class:
                    {
                    ClassConstant  idClz = (ClassConstant) constant;
                    ClassStructure clz   = (ClassStructure) idClz.getComponent();
                    if (clz.isSingleton())
                        {
                        collector.resolvedConstant(constant);
                        return ResolutionResult.RESOLVED;
                        }
                    break;
                    }
                }
            return ResolutionResult.UNKNOWN;
            }

        return super.resolveName(sName, access, collector);
        }

    @Override
    protected Iterator<IdentityConstant> potentialVirtualChildContributors()
        {
        List<IdentityConstant> list = null;

        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                TypeConstant typeAnno = contrib.getTypeConstant();
                if (typeAnno.containsUnresolved())
                    {
                    return null;
                    }
                if (typeAnno.isIntoVariableType())
                    {
                    if (list == null)
                        {
                        list = new ArrayList<>();
                        }
                    list.add(typeAnno.getSingleUnderlyingClass(true));
                    }
                }
            }

        return list == null
                ? Collections.emptyIterator()
                : list.iterator();
        }

    @Override
    public void collectInjections(Set<InjectionKey> setInjections)
        {
        TypeConstant type = getType();
        if (isRefAnnotated())
            {
            Annotation[] annos  = getRefAnnotations();
            Constant     idAnno = annos[0].getAnnotationClass();
            if (idAnno.equals(getConstantPool().clzInject()))
                {
                String     sName       = getName();
                Constant[] aconstParam = annos[0].getParams();
                if (aconstParam.length > 0)
                    {
                    sName = ((StringConstant) aconstParam[0]).getValue();
                    }
                setInjections.add(new InjectionKey(sName, type));
                }
            }
        }

    @Override
    public void addAnnotation(Annotation annotation)
        {
        super.addAnnotation(annotation);

        // clear the cache
        m_aPropAnno = null;
        m_aRefAnno  = null;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        super.disassemble(in);

        ConstantPool pool = getConstantPool();

        int nAccess = in.readByte();
        m_accessVar = nAccess < 0 ? null : Access.valueOf(nAccess);
        m_type      = (TypeConstant) pool.getConstant(readIndex(in));
        int nValue  = readIndex(in);
        if (nValue >= 0)
            {
            m_constVal = pool.getConstant(nValue);
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_type = (TypeConstant) pool.register(m_type);
        if (m_constVal != null)
            {
            assert !(m_constVal instanceof DeferredValueConstant);
            m_constVal = pool.register(m_constVal);
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        // the value should have already been resolved by this point
        assert !(m_constVal instanceof DeferredValueConstant);

        super.assemble(out);

        out.writeByte(m_accessVar == null ? -1 : m_accessVar.ordinal());
        writePackedLong(out, m_type.getPosition());
        writePackedLong(out, Constant.indexOf(m_constVal));
        }

    @Override
    public Iterator<? extends XvmStructure> getContained()
        {
        // we cannot use the "getPropertyAnnotation" API at this time, since our module may not yet
        // be linked
        List<Annotation> listAnno = null;
        for (Contribution contrib : getContributionsAsList())
            {
            if (contrib.getComposition() == Composition.Annotation)
                {
                if (listAnno == null)
                    {
                    listAnno = new ArrayList<>();
                    }
                listAnno.add(contrib.getAnnotation());
                }
            }

        return listAnno == null
                ? super.getContained()
                : new LinkedIterator(
                        super.getContained(),
                        listAnno.iterator());
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder()
                .append("id=")
                .append(getIdentityConstant().getValueString())
                .append(isGenericTypeParameter() ? ", constraint=" : ", type=")
                .append(m_type)
                .append(", ")
                .append("var-access=")
                .append(m_accessVar)
                .append(", ");

        return sb.append(super.getDescription()).toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The access for the Var (as opposed to the access to the Ref).
     */
    private Access m_accessVar;

    /**
     * The property type.
     */
    private TypeConstant m_type;

    /**
     * The initial value of the property, if it is a "static property" initialized to a constant.
     */
    private Constant m_constVal;

    /**
     * Indicates that the property has native accessors.
     */
    private transient boolean m_fNative;

    /**
     * A cached array of the annotations that apply to the property itself.
     */
    private transient Annotation[] m_aPropAnno;

    /**
     * A cached array of the annotations that apply to the Ref/Var of the property.
     */
    private transient Annotation[] m_aRefAnno;
    }