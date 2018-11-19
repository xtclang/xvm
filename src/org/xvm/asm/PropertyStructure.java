package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

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
    protected void setType(TypeConstant type)
        {
        assert type != null;
        m_type = type;
        }

    /**
     * @return true iff this property is a type parameter
     */
    public boolean isTypeParameter()
        {
        return isAuxiliary();
        }

    /**
     * @return mark this property as a type parameter
     */
    public void markAsTypeParameter()
        {
        assert !isStatic()            // never a constant
            && !isSynthetic()         // never synthetic
            && m_type.isTypeOfType(); // must be "Type"

        setAuxiliary(true);
        }

    /**
     * @return the base type for the type parameter, from the "extends" clause, or Object if no
     *         explicit "extends" clause was present, or null if the property is not a type param
     */
    public TypeConstant getTypeParameterExtendsType()
        {
        if (!isTypeParameter())
            {
            return null;
            }

        return m_type.isParamsSpecified()
                ? m_type.getParamTypesArray()[0]
                : m_type.getConstantPool().typeObject();
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
     * @return true iff the property contains any reference annotations
     */
    public boolean isRefAnnotated()
        {
        return getRefAnnotations().length > 0;
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
        ConstantPool pool = getConstantPool();
        if (getParent().getIdentityConstant().equals(pool.clzObject()))
            {
            // the only property on Object is "meta" and it's native no matter what the source says
            m_aPropAnno = Annotation.NO_ANNOTATIONS;
            m_aRefAnno  = Annotation.NO_ANNOTATIONS;
            return;
            }

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
                : listPropAnno.toArray(new Annotation[listPropAnno.size()]);
        m_aRefAnno = listRefAnno == null
                ? Annotation.NO_ANNOTATIONS
                : listRefAnno.toArray(new Annotation[listRefAnno.size()]);
        }

    public void indicateInitialValue()
        {
        m_fHasValue = true;
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
        return m_fHasValue || m_constVal != null;
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
        m_fHasValue = false;
        m_constVal  = constVal;
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
     * @return true iff the getter for the property is native
     */
    public boolean isNativeGetter()
        {
        return m_fNativeGetter;
        }

    /**
     * Mark the getter for the property as native.
     */
    public void markNativeGetter()
        {
        m_fNativeGetter = true;
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
        assert sigThat.getRawParams().length == 0;
        assert sigThat.getRawReturns().length == 1;

        SignatureConstant sigThis = getIdentityConstant().getSignature();
        if (!listActual.isEmpty())
            {
            ClassStructure clzThis = (ClassStructure) getParent();
            sigThis = sigThis.resolveGenericTypes(pool, clzThis.new SimpleTypeResolver(listActual));
            }

        // TODO: if read-only then isA() would suffice
        return sigThat.equals(sigThis);
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
                if (annotation.containsUnresolved())
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
                        if (constExtends instanceof IdentityConstant)
                            {
                            structMixin = (ClassStructure) ((IdentityConstant) constExtends).getComponent();
                            }
                        }
                    }

                // see if the mixin applies to a Property or a Ref/Var, in which case it stays in
                // this list; otherwise (e.g. UncheckedInt), move it into the type itself
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
    public ResolutionResult resolveName(String sName, ResolutionCollector collector)
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

        SimpleCollector  collectorTemp = new SimpleCollector();
        ResolutionResult result        = getType().resolveContributedName(sName, collectorTemp);
        if (result == ResolutionResult.RESOLVED)
            {
            // we only allow static properties (constants) and singleton classes
            result = ResolutionResult.UNKNOWN;

            Constant constant = collectorTemp.getResolvedConstant();
            switch (constant.getFormat())
                {
                case Property:
                    {
                    PropertyConstant  idProp = (PropertyConstant) constant;
                    PropertyStructure prop   = (PropertyStructure) idProp.getComponent();
                    if (prop.isConstant())
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
            }
        return result;
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
            m_constVal  = pool.getConstant(nValue);
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_type = (TypeConstant) pool.register(m_type);
        if (m_constVal != null)
            {
            m_constVal = pool.register(m_constVal);
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        // the value should have already been resolved by this point
        // assert !m_fHasValue; TODO: re-introduce the assert

        super.assemble(out);

        out.writeByte(m_accessVar == null ? -1 : m_accessVar.ordinal());
        writePackedLong(out, m_type.getPosition());
        if (m_constVal != null)
            {
            writePackedLong(out, m_constVal.getPosition());
            }
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder()
                .append("id=")
                .append(getIdentityConstant().getValueString())
                .append(isTypeParameter() ? ", constraint=" : ", type=")
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
     * Indicates that the property has a native getter.
     */
    private transient boolean m_fNativeGetter;

    /**
     * Indicates that the property has a value, even if it hasn't been determined yet.
     */
    private transient boolean m_fHasValue;

    /**
     * A cached array of the annotations that apply to the property itself.
     */
    private transient Annotation[] m_aPropAnno;

    /**
     * A cached array of the annotations that apply to the Ref/Var of the property.
     */
    private transient Annotation[] m_aRefAnno;
    }
