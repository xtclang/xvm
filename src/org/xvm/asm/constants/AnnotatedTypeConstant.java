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

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xRef;

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

        m_annotation = pool.ensureAnnotation(constClass, aconstParam);
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

        m_iAnno = readIndex(in);
        m_iType = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        ConstantPool pool = getConstantPool();

        m_annotation = (Annotation) pool.getConstant(m_iAnno);
        m_constType  = (TypeConstant) pool.getConstant(m_iType);
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

        ConstantPool     pool           = getConstantPool();
        int              cInvals        = pool.getInvalidationCount();
        List<Annotation> listClassAnnos = new ArrayList<>();
        TypeConstant     typeBase       = extractClassAnnotation(listClassAnnos, errs);
        TypeConstant     typePrivate    = pool.ensureAccessTypeConstant(this, Access.PRIVATE);

        if (typeBase instanceof AnnotatedTypeConstant)
            {
            AnnotatedTypeConstant typeAnnoBase = (AnnotatedTypeConstant) typeBase;

            TypeConstant typeMixin        = typeAnnoBase.getAnnotationType();
            TypeConstant typeMixinPrivate = pool.ensureAccessTypeConstant(typeMixin, Access.PRIVATE);
            TypeInfo     infoMixin        = typeMixinPrivate.ensureTypeInfoInternal(errs);
            if (infoMixin == null)
                {
                return null;
                }

            typeBase = pool.ensureAccessTypeConstant(typeAnnoBase.getUnderlyingType(), Access.PRIVATE);

            TypeInfo infoBase = typeBase.ensureTypeInfoInternal(errs);

            return infoBase == null
                    ? null
                    : typeBase.mergeMixinTypeInfo(typePrivate, cInvals, idBase,
                        infoBase.getClassStructure(), infoBase, infoMixin, listClassAnnos, errs);
            }
        else
            {
            // there are no other annotations except the "into Class" tags
            assert !listClassAnnos.isEmpty();

            typeBase = pool.ensureAccessTypeConstant(typeBase, Access.PRIVATE);

            TypeInfo info = typeBase.ensureTypeInfoInternal(errs);

            return info == null
                    ? null
                    : new TypeInfo(typePrivate, cInvals, info.getClassStructure(), 0, false,
                        info.getTypeParams(), listClassAnnos.toArray(Annotation.NO_ANNOTATIONS),
                        info.getExtends(), info.getRebases(), info.getInto(),
                        info.getContributionList(), info.getClassChain(), info.getDefaultChain(),
                        info.getProperties(), info.getMethods(),
                        info.getVirtProperties(), info.getVirtMethods(),
                        info.getChildInfosByName(), info.getProgress());
            }
        }

    /**
     * Extract the type annotations in front of this type that have an "into" clause of "Class".
     *
     * For example, if this type is @A->@B->@C->X and "@A" and "@B" are into class, then collect
     * @A and @B into the listClassAnnos and return @C->X.
     *
     * If "@A" is not into class then collect nothing and return itself.
     *
     * @return the first underlying type that follows extracted "Class" annotations
     */
    private TypeConstant extractClassAnnotation(List<Annotation> listClassAnnos, ErrorListener errs)
        {
        List<Constant> listAnnoClz = new ArrayList<>();
        TypeConstant   typeCurr    = this;
        TypeConstant   typeBase    = null;

        while (true)
            {
            TypeConstant typeNext;

            switch (typeCurr.getFormat())
                {
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
                            typeNext.getValueString(), typeMixin.getValueString());
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
                        typeMixin = typeMixin.resolveAutoNarrowing(getConstantPool(), false, null);
                        }

                    // check for duplicate annotation
                    if (listAnnoClz.contains(annotation.getAnnotationClass()))
                        {
                        log(errs, Severity.ERROR, VE_DUP_ANNOTATION,
                            this.getValueString(), annotation.getAnnotationClass().getValueString());
                        break;
                        }

                    // the annotation could be a mixin "into Class", which means that it's a
                    // non-virtual, compile-time mixin (like @Abstract)
                    TypeConstant typeInto = typeMixin.getExplicitClassInto();
                    if (typeInto.isIntoClassType() && typeBase == null)
                        {
                        typeBase = typeNext;
                        listClassAnnos.add(annotation);
                        break;
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

                case ParameterizedType:
                case TerminalType:
                case VirtualChildType:
                case AnonymousClassType:
                    return typeBase == null ? this : typeBase;

                default:
                    typeNext = typeCurr.getUnderlyingType();
                    break;
                }
            typeCurr = typeNext;
            }
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
            TypeConstant     typeBase    = getUnderlyingType();
            IdentityConstant constIdAnno = (IdentityConstant) getAnnotation().getAnnotationClass();
            OpSupport        supportAnno = registry.getTemplate(constIdAnno);

            // if the annotation itself is native, it overrides the base type template (support);
            // for now all native Ref implementations extend xRef
            m_support = support = supportAnno instanceof xRef
                    ? supportAnno.getTemplate(typeBase)
                    : typeBase.getOpSupport(registry);
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

    @Override
    public MethodInfo findFunctionInfo(SignatureConstant sig)
        {
        // identical to UnionTypeConstant implementation
        MethodInfo info1 = m_annotation.getAnnotationType().findFunctionInfo(sig);
        MethodInfo info2 = m_constType.findFunctionInfo(sig);

        return info1 == null ? info2 :
               info2 == null ? info1 :
               info1.getIdentity().equals(info2.getIdentity())
                        ? info1
                        : null; // ambiguous
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
        return m_annotation.getValueString() + ' ' + m_constType.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

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
        writePackedLong(out, indexOf(m_annotation));
        writePackedLong(out, indexOf(m_constType));
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        if (!isValidated())
            {
            // an annotated type constant can modify a parameterized or a terminal type constant
            // that refers to a class/interface
            TypeConstant typeBase = m_constType.resolveTypedefs();
            if (!(typeBase instanceof AnnotatedTypeConstant || typeBase.isExplicitClassIdentity(true)))
                {
                log(errs, Severity.ERROR, VE_ANNOTATION_ILLEGAL, typeBase.getValueString());
                return true;
                }

            // validate the annotation itself
            boolean fBad = m_annotation.validate(errs);

            // make sure that this annotation is not repeated
            ClassConstant idAnno = (ClassConstant) m_annotation.getAnnotationClass();
            for (TypeConstant typeNext = typeBase;
                              typeNext instanceof AnnotatedTypeConstant;
                              typeNext = ((AnnotatedTypeConstant) typeNext).m_constType)
                {
                if (((AnnotatedTypeConstant) typeNext).m_annotation.getAnnotationClass().equals(idAnno))
                    {
                    log(errs, Severity.ERROR, VE_ANNOTATION_REDUNDANT, idAnno.getValueString());
                    fBad = true;
                    break;
                    }
                }

            if (!fBad)
                {
                TypeConstant typeMixin = getAnnotationType();
                TypeConstant typeInto  = typeMixin.getExplicitClassInto();
                if (!m_constType.isA(typeInto))
                    {
                    log(errs, Severity.ERROR, VE_ANNOTATION_INCOMPATIBLE,
                            m_constType.getValueString(),
                            idAnno.getValueString(),
                            typeInto.getValueString());
                    fBad = true;
                    }
                }

            if (!fBad)
                {
                return super.validate(errs);
                }
            }

        return false;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_annotation.hashCode() ^ m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the Annotation.
     */
    private int m_iAnno;

    /**
     * During disassembly, this holds the index of the type constant of the type being annotated.
     */
    private int m_iType;

    /**
     * The annotation.
     */
    private Annotation m_annotation;

    /**
     * The type being annotated.
     */
    private TypeConstant m_constType;

    /**
     * Cached OpSupport reference.
     */
    private transient OpSupport m_support;
    }
