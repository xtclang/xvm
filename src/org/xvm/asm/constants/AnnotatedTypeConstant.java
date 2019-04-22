package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Consumer;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.runtime.AnnotationSupport;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.checkElementsNonNull;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the annotation of another type constant.
 */
public class AnnotatedTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public AnnotatedTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_annotation = new Annotation(pool, in);
        m_iType      = readIndex(in);
        }

    /**
     * Construct a constant whose value is an annotated data type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constClass   the class of the annotation
     * @param aconstParam  the parameters of the annotation, or null
     * @param constType    the type being annotated
     */
    public AnnotatedTypeConstant(ConstantPool pool, Constant constClass,
            Constant[] aconstParam, TypeConstant constType)
        {
        super(pool);

        if (constClass == null)
            {
            throw new IllegalArgumentException("annotation class required");
            }

        if (aconstParam != null)
            {
            checkElementsNonNull(aconstParam);
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("annotated type required");
            }

        m_annotation = new Annotation(pool, constClass, aconstParam);
        m_constType  = constType;
        }

    /**
     * Construct a constant whose value is an annotated data type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param annotation   the annotation
     * @param constType    the type being annotated
     */
    public AnnotatedTypeConstant(ConstantPool pool, Annotation annotation, TypeConstant constType)
        {
        super(pool);

        if (annotation == null)
            {
            throw new IllegalArgumentException("annotation required");
            }

        if (constType == null)
            {
            throw new IllegalArgumentException("annotated type required");
            }

        m_annotation = annotation;
        m_constType  = constType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the annotation
     */
    public Annotation getAnnotation()
        {
        return m_annotation;
        }

    /**
     * Return the annotation type with any type parameters resolved that overlap with the
     * underlying TypeConstant.
     *
     * For example, an "@Atomic Var<Int>" type should yield AtomicVar<Int>.
     *
     * @return the resolved annotation type
     */
    public TypeConstant getAnnotationType()
        {
        Constant idAnno = getAnnotationClass();
        if (idAnno instanceof ClassConstant)
            {
            ConstantPool   pool  = ConstantPool.getCurrentPool();
            ClassStructure mixin = (ClassStructure) ((ClassConstant) idAnno).getComponent();

            // here we assume that the type parameters for the annotation mixin are
            // structurally and semantically congruent with the type parameters for the
            // incorporating class the annotation is mixing into (regardless of the parameter name)
            Map<StringConstant, TypeConstant> mapFormal   = mixin.getTypeParams();
            Map<String, TypeConstant>         mapResolved = new HashMap<>(mapFormal.size());
            List<TypeConstant>                listActual  = m_constType.getParamTypes();

            for (StringConstant constName : mapFormal.keySet())
                {
                String sFormalName = constName.getValue();

                TypeConstant typeResolved = mixin.getGenericParamType(pool, sFormalName, listActual);
                if (typeResolved != null)
                    {
                    mapResolved.put(sFormalName, typeResolved);
                    }
                }

            return mixin.getFormalType().resolveGenerics(pool, mapResolved::get);
            }

        // REVIEW the only other option is the constAnno to be a PseudoConstant (referring to a virtual
        //        child / sibling that is a mix-in, so some form of "virtual annotation" that has not
        //        yet been defined / evaluated for inclusion in the language)

        return m_annotation.getAnnotationType();
        }

    /**
     * @return the class of the annotation
     */
    public Constant getAnnotationClass()
        {
        return m_annotation.getAnnotationClass();
        }

    /**
     * @return an array of constants which are the parameters for the annotation
     */
    public Constant[] getAnnotationParams()
        {
        return m_annotation.getParams();
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType;
        }

    @Override
    public boolean isAnnotated()
        {
        return true;
        }

    @Override
    public boolean isNullable()
        {
        return m_constType.isNullable();
        }

    @Override
    public TypeConstant removeNullable(ConstantPool pool)
        {
        return isNullable()
                ? pool.ensureAnnotatedTypeConstant(getAnnotationClass(),
                        getAnnotationParams(), m_constType.removeNullable(pool))
                : this;
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureAnnotatedTypeConstant(m_annotation.getAnnotationClass(),
            m_annotation.getParams(), type);
        }

    /**
     * Create a TypeInfo for the private access type of this type.
     *
     * @param idBase  the identity of the class (etc) that is being annotated
     * @param errs    the error list to log any errors to
     *
     * @return a new TypeInfo representing this annotated type
     */
    TypeInfo buildPrivateInfo(IdentityConstant idBase, ErrorListener errs)
        {
        // this can only be called from TypeConstant.buildTypeInfoImpl()
        assert getAccess() == Access.PUBLIC;

        ConstantPool pool        = getConstantPool();
        int          cInvals     = pool.getInvalidationCount();
        TypeConstant typeThis    = pool.ensureAccessTypeConstant(this, Access.PRIVATE);
        TypeConstant typeMixin   = getAnnotationType();
        TypeConstant typePrivate = pool.ensureAccessTypeConstant(typeMixin, Access.PRIVATE);
        TypeInfo     infoMixin   = typePrivate.ensureTypeInfoInternal(errs);
        if (infoMixin == null)
            {
            return null;
            }

        IdentityConstant idMixin;
        ClassStructure   structMixin;
        try
            {
            idMixin     = (IdentityConstant) typeMixin.getDefiningConstant();
            structMixin = (ClassStructure)   idMixin.getComponent();
            }
        catch (RuntimeException e)
            {
            throw new IllegalStateException("Unable to determine class for " + getValueString(), e);
            }

        List<Annotation> listAnnos = new ArrayList<>();
        TypeConstant     typeBase  = extractClassAnnotation(pool, idMixin, listAnnos, errs);

        TypeInfo infoBase = typeBase.ensureTypeInfoInternal(errs);
        if (infoBase == null)
            {
            return null;
            }

        // merge the private view of the annotation on top if the specified view of the underlying type
        Map<PropertyConstant, PropertyInfo> mapMixinProps   = infoMixin.getProperties();
        Map<MethodConstant  , MethodInfo  > mapMixinMethods = infoMixin.getMethods();
        Map<Object          , ParamInfo   > mapMixinParams  = infoMixin.getTypeParams();

        Map<PropertyConstant, PropertyInfo> mapProps       = new HashMap<>(infoBase.getProperties());
        Map<MethodConstant  , MethodInfo  > mapMethods     = new HashMap<>(infoBase.getMethods());
        Map<Object          , PropertyInfo> mapVirtProps   = new HashMap<>(infoBase.getVirtProperties());
        Map<Object          , MethodInfo  > mapVirtMethods = new HashMap<>(infoBase.getVirtMethods());

        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapMixinProps.entrySet())
            {
            layerOnProp(pool, idBase, mapProps, mapVirtProps, entry.getKey(), entry.getValue(), errs);
            }

        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMixinMethods.entrySet())
            {
            layerOnMethod(pool, idBase, mapMethods, mapVirtMethods, entry.getKey(), entry.getValue(), errs);
            }

        return new TypeInfo(typeThis, cInvals, structMixin, 0, false, mapMixinParams,
                listAnnos.toArray(Annotation.NO_ANNOTATIONS),
                infoMixin.getExtends(), infoMixin.getRebases(), infoMixin.getInto(),
                infoMixin.getContributionList(), infoMixin.getClassChain(), infoMixin.getDefaultChain(),
                mapProps, mapMethods, mapVirtProps, mapVirtMethods,
                TypeInfo.Progress.Complete);
        }

    /**
     * Extract the type annotations in front of this type that have an "into" clause of "Class".
     *
     * For example, if this type is @A->@B->@C->X and "@A" and "@B" are into class, then collect
     * @A and @B into the listClassAnnos and return @C->X.
     *
     * If "@A" is not into class then collect nothing and return @B->@C->X
     *
     * @return the first underlying type that follows extracted "Class" annotations
     */
    private TypeConstant extractClassAnnotation(ConstantPool pool, IdentityConstant idMixin,
                                                List<Annotation> listClassAnnos, ErrorListener errs)
        {
        List<Constant> listAnnoClz = new ArrayList<>();
        TypeConstant   typeCurr    = this;
        TypeConstant   typeBase    = null;

        while (true)
            {
            TypeConstant typeNext;

            switch (typeCurr.getFormat())
                {
                case ParameterizedType:
                case TerminalType:
                case VirtualChildType:
                case AnonymousClassType:
                    return typeBase == null ? typeCurr : typeBase;

                case AnnotatedType:
                    {
                    AnnotatedTypeConstant typeAnno   = (AnnotatedTypeConstant) typeCurr;
                    Annotation            annotation = typeAnno.getAnnotation();
                    TypeConstant          typeMixin  = typeAnno.getAnnotationType();

                    typeNext = typeCurr.getUnderlyingType();

                    // has to be an explicit class identity
                    if (!typeMixin.isExplicitClassIdentity(true))
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_NOT_CLASS,
                                idMixin.getPathString(), typeMixin.getValueString());
                        break;
                        }

                    // has to be a mixin
                    if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_NOT_MIXIN,
                                typeMixin.getValueString());
                        break;
                        }

                    if (typeMixin.isAutoNarrowing(false))
                        {
                        log(errs, Severity.WARNING, VE_UNEXPECTED_AUTO_NARROW,
                                typeMixin.getValueString(), this.getValueString());
                        typeMixin = typeMixin.resolveAutoNarrowing(pool, false, null);
                        }

                    // check for duplicate annotation
                    if (listAnnoClz.contains(annotation.getAnnotationClass()))
                        {
                        log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                                idMixin.getPathString(), annotation.getAnnotationClass().getValueString());
                        break;
                        }

                    // the annotation could be a mixin "into Class", which means that it's a
                    // non-virtual, compile-time mixin (like @Abstract)
                    TypeConstant typeInto = typeMixin.getExplicitClassInto();
                    if (typeInto.isIntoClassType() && typeBase == null)
                        {
                        listClassAnnos.add(annotation);
                        break;
                        }

                    if (typeBase == null)
                        {
                        typeBase = typeNext;
                        }

                    // the mixin has to be able to apply to the remainder of the type constant chain
                    if (!getUnderlyingType().isA(typeInto))
                        {
                        log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                                typeCurr.getUnderlyingType().getValueString(),
                                typeMixin.getValueString(),
                                typeInto.getValueString());
                        break;
                        }

                    listAnnoClz.add(typeAnno.getAnnotation().getAnnotationClass());
                    break;
                    }

                default:
                    typeNext = typeCurr.getUnderlyingType();
                    break;
                }
            typeCurr = typeNext;
            }
        }

    /**
     * Layer on the passed annotation (mixin) property contributions onto the base properties.
     *
     * @param pool          the constant pool to use
     * @param idBaseClass   the identity of the class (etc) that is being annotated
     * @param mapProps      properties already collected from the base
     * @param mapVirtProps  virtual properties already collected from the base
     * @param idMixin       the identity of the property at the mixin
     * @param propMixin     the property info from the mixin
     * @param errs          the error listener
     */
    protected void layerOnProp(ConstantPool pool, IdentityConstant idBaseClass,
                               Map<PropertyConstant, PropertyInfo> mapProps,
                               Map<Object, PropertyInfo> mapVirtProps,
                               PropertyConstant idMixin, PropertyInfo propMixin, ErrorListener errs)
        {
        if (!propMixin.isVirtual())
            {
            mapProps.put(idMixin, propMixin);
            return;
            }

        Object           nidContrib = idMixin.resolveNestedIdentity(pool, this);
        PropertyConstant idResult   = (PropertyConstant) idBaseClass.appendNestedIdentity(pool, nidContrib);
        PropertyInfo     propBase   = mapVirtProps.get(nidContrib);
        if (propBase != null && propMixin.getIdentity().equals(propBase.getIdentity()))
            {
            // keep whatever the base has got
            return;
            }

        PropertyInfo propResult;
        if (propBase == null)
            {
            propResult = propMixin;
            }
        else
            {
            propResult = propBase.layerOn(propMixin, false, true, errs);
            }

        mapProps.put(idResult, propResult);
        mapVirtProps.put(nidContrib, propResult);
        }

    /**
     * Layer on the passed annotation (mixin) method contributions onto the base methods.
     *
     * @param pool            the constant pool to use
     * @param idBaseClass     the identity of the class (etc) that the annotation is being applied to
     * @param mapMethods      methods already collected from the base
     * @param mapVirtMethods  virtual methods already collected from the base
     * @param idMixin         the identity of the method at the mixin
     * @param methodMixin     the method info from the mixin
     * @param errs            the error listener
     */
    private void layerOnMethod(ConstantPool pool, IdentityConstant idBaseClass,
                               Map<MethodConstant, MethodInfo> mapMethods,
                               Map<Object, MethodInfo> mapVirtMethods, MethodConstant idMixin,
                               MethodInfo methodMixin, ErrorListener errs)
        {
        if (!methodMixin.isVirtual())
            {
            mapMethods.put(idMixin, methodMixin);
            return;
            }

        GenericTypeResolver resolver   = methodMixin.isFunction() || methodMixin.isConstructor() ? null : this;
        Object              nidContrib = idMixin.resolveNestedIdentity(pool, resolver);
        MethodConstant      idResult   = (MethodConstant) idBaseClass.appendNestedIdentity(pool, nidContrib);

        MethodInfo methodBase = mapVirtMethods.get(nidContrib);
        if (methodBase != null && methodBase.getIdentity().equals(methodMixin.getIdentity()))
            {
            // keep whatever the base has got
            return;
            }

        SignatureConstant sigContrib = methodMixin.getSignature();

        MethodInfo methodResult;
        if (methodBase == null)
            {
            methodResult = methodMixin;
            }
        else
            {
            // it's possible that the base has a narrower method signature then the mixin,
            // in which case, the mixin's info should be ignored/replaced
            SignatureConstant sigBase  = methodBase.getSignature();
            SignatureConstant sigMixin = methodMixin.getSignature();
            if (!sigBase.equals(sigMixin) && sigBase.isSubstitutableFor(sigMixin, this))
                {
                methodResult = methodBase;
                }
            else
                {
                methodResult = methodBase.layerOn(methodMixin, false, errs);
                }
            }

        mapMethods.put(idResult, methodResult);
        mapVirtMethods.put(sigContrib, methodResult);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        // this logic is identical to the union of the annotation type and the underlying type
        if (typeLeft instanceof UnionTypeConstant || typeLeft.isAnnotated())
            {
            return super.calculateRelationToLeft(typeLeft);
            }
        TypeConstant typeAnno = getAnnotationType();
        TypeConstant typeOrig = getUnderlyingType();

        Relation rel1 = typeAnno.calculateRelation(typeLeft);
        Relation rel2 = typeOrig.calculateRelation(typeLeft);
        return rel1.bestOf(rel2);
        }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight)
        {
        // this logic is identical to the union of the annotation type and the underlying type
        TypeConstant typeAnno = getAnnotationType();
        TypeConstant typeOrig = getUnderlyingType();

        Relation rel1 = typeRight.calculateRelation(typeAnno);
        Relation rel2 = typeRight.calculateRelation(typeOrig);
        return rel1.worseOf(rel2);
        }

    @Override
    protected Relation findIntersectionContribution(IntersectionTypeConstant typeLeft)
        {
        // the annotation cannot be of an intersection type
        return Relation.INCOMPATIBLE;
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        OpSupport support = m_support;
        if (support == null)
            {
            support = m_support = new AnnotationSupport(this, registry);
            }
        return support;
        }

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callEqualsSequence(frame,
            m_annotation.getAnnotationType(), m_constType, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callCompareSequence(frame,
            m_annotation.getAnnotationType(), m_constType, hValue1, hValue2, iReturn);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.AnnotatedType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_annotation.containsUnresolved() || m_constType.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        m_annotation.forEachUnderlying(visitor);
        visitor.accept(m_constType);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof AnnotatedTypeConstant))
            {
            return -1;
            }
        AnnotatedTypeConstant that = (AnnotatedTypeConstant) obj;
        int n = this.m_annotation.compareTo(that.m_annotation);

        if (n == 0)
            {
            n = this.m_constType.compareTo(that.m_constType);
            }

        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(m_annotation.toString())
          .append(' ')
          .append(m_constType.getValueString());

        return sb.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_annotation.disassemble(in);
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_annotation = (Annotation)   pool.register(m_annotation);
        m_constType  = (TypeConstant) pool.register(m_constType);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        m_annotation.assemble(out);
        writePackedLong(out, indexOf(m_constType));
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        boolean fBad  = false;
        boolean fHalt = false;

        if (!isValidated())
            {
            fHalt |= super.validate(errs);

            // an annotated type constant can modify a parameterized or a terminal type constant
            // that refers to a class/interface
            TypeConstant typeBase = m_constType.resolveTypedefs();
            if (!(typeBase instanceof AnnotatedTypeConstant || typeBase.isExplicitClassIdentity(true)))
                {
                fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_ILLEGAL, typeBase.getValueString());
                fBad   = true;
                }

            // validate the annotation itself
            fHalt |= m_annotation.validate(errs);

            // make sure that this annotation is not repeated
            ClassConstant idAnno = (ClassConstant) m_annotation.getAnnotationClass();
            for (TypeConstant typeNext = typeBase;
                              typeNext instanceof AnnotatedTypeConstant;
                              typeNext = ((AnnotatedTypeConstant) typeNext).m_constType)
                {
                if (((AnnotatedTypeConstant) typeNext).m_annotation.getAnnotationClass().equals(idAnno))
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_REDUNDANT, idAnno.getValueString());
                    fBad   = true;
                    break;
                    }
                }

            if (!fBad && !fHalt)
                {
                TypeConstant typeMixin = getAnnotationType();
                TypeConstant typeInto  = typeMixin.getExplicitClassInto();
                if (!m_constType.isA(typeInto))
                    {
                    fHalt |= log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                            m_constType.getValueString(),
                            idAnno.getValueString(),
                            typeInto.getValueString());
                    }
                }
            }

        return fHalt;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_annotation.hashCode() ^ m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The annotation.
     */
    private Annotation m_annotation;

    /**
     * During disassembly, this holds the index of the type constant of the type being annotated.
     */
    private int m_iType;

    /**
     * The type being annotated.
     */
    private TypeConstant m_constType;

    /**
     * Cached OpSupport reference.
     */
    private transient OpSupport m_support;
    }
