package org.xvm.asm.constants;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represents the compile time and runtime information (aggregated across all contributions and
 * virtual levels) about a single property as it appears in a particular type.
 */
public class PropertyInfo
    {
    /**
     * Construct a PropertyInfo from the passed information.
     *
     * @param constParent  null if the property is a public or protected property of the type
     *                       itself; otherwise, the identity of the containing structure
     * @param sName          the property name (required)
     * @param type           the type of the property, including any type annotations (required)
     * @param fRO            true iff the property is a Ref; false iff the property is a Var
     * @param fReqField      true iff the property requires the presence of a field
     * @param aPropAnno      an array of non-virtual annotations on the property declaration itself
     * @param aRefAnno       an array of annotations that apply to the Ref/Var of the property
     * @param fCustomCode    true to indicate that the property has custom code that overrides the
     *                       underlying Ref/Var implementation
     */
    public PropertyInfo(Constant constParent, String sName, TypeConstant type, boolean fRO,
            Annotation[] aPropAnno, Annotation[] aRefAnno, boolean fCustomCode, boolean fReqField)
        {
        assert sName != null;
        assert type != null;

        m_constParent   = constParent;
        m_sName         = sName;
        m_type          = type;
        m_fParam        = false;
        m_fRO           = fRO;
        m_aPropAnno     = validateAnnotations(aPropAnno);
        m_aRefAnno      = validateAnnotations(aRefAnno);
        m_fCustom       = fCustomCode;
        m_fField        = fReqField;
        }

    public PropertyInfo(ParamInfo param)
        {
        ConstantPool pool = param.getConstraintType().getConstantPool();

        m_constParent   = null;
        m_sName         = param.getName();
        m_type          = pool.ensureParameterizedTypeConstant(pool.typeType(), param.getConstraintType());
        m_fParam        = true;
        m_fRO           = true;
        m_aPropAnno     = Annotation.NO_ANNOTATIONS;
        m_aRefAnno      = Annotation.NO_ANNOTATIONS;
        m_fCustom       = false;
        m_fField        = false;
        }

    public PropertyInfo combineWithSuper(PropertyInfo that)
        {
        if (this.isTypeParam() || that.isTypeParam())
            {
            throw new IllegalStateException(
                    "cannot combine PropertyInfo objects if either represents a type parameter");
            }

        assert (this.getParent() == null) == (that.getParent() == null);
        assert this.getName().equals(that.getName());
        assert this.getType().isA(that.getType());

        return new PropertyInfo(
                this.getParent(),
                this.getName(),
                this.getType(),
                this.isRO() & that.isRO(),                  // read-only Ref if both are read-only
                this.getPropertyAnnotations(),              // property annotations NOT inherited
                mergeAnnotations(this.getRefAnnotations(), that.getRefAnnotations()),
                this.isCustomLogic() | that.isCustomLogic(),// custom logic if either is custom
                this.hasField() | that.hasField());         // field present if either has field
        }

    /**
     * @return this property as it would appear on a class (not on an interface)
     */
    public PropertyInfo finalizeNonInterfaceProperty()
        {
        return hasField() || isCustomLogic() || getRefAnnotations().length > 0
                ? this
                : new PropertyInfo(m_constParent, m_sName, m_type, false, m_aPropAnno, m_aRefAnno, m_fCustom, true);
        }

    /**
     * @return the container of the property; null iff the peropty is a public or protected property
     *         of the type itself
     */
    public Constant getParent()
        {
        return m_constParent;
        }

    /**
     * @return the property name
     */                                 s
    public String getName()
        {
        return m_sName;
        }

    /**
     * @return the property type
     */
    public TypeConstant getType()
        {
        return m_type;
        }

    /**
     * @return true iff this property represents a type parameter type
     */
    public boolean isTypeParam()
        {
        return m_fParam;
        }

    /**
     * @return true iff this property is a Ref; false iff this property is a Var
     */
    public boolean isRO()
        {
        return m_fRO;
        }

    /**
     * @return true iff this property has a field, whether or not that field is reachable
     */
    public boolean hasField()
        {
        return m_fField;
        }

    /**
     * @return an array of the non-virtual annotations on the property declaration itself
     */
    public Annotation[] getPropertyAnnotations()
        {
        return m_aPropAnno;
        }

    /**
     * @return an array of the annotations that apply to the Ref/Var of the property
     */
    public Annotation[] getRefAnnotations()
        {
        return m_aRefAnno;
        }

    /**
     * @return true iff the property has any methods in addition to the underlying Ref or Var
     *         "rebasing" implementation, and in addition to any annotations
     */
    public boolean isCustomLogic()
        {
        return m_fCustom;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (m_fRO)
            {
            sb.append("@RO ");
            }

        sb.append(m_type.getValueString())
                .append(' ')
                .append(m_sName);

        return sb.toString();
        }


    // ----- internal helpers ----------------------------------------------------------------------

    private Annotation[] validateAnnotations(Annotation[] annotations)
        {
        if (annotations == null)
            {
            return Annotation.NO_ANNOTATIONS;
            }

        for (Annotation annotation : annotations)
            {
            if (annotation == null)
                {
                throw new IllegalStateException("null annotation");
                }
            }

        return annotations;
        }

    private Annotation[] mergeAnnotations(Annotation[] anno1, Annotation[] anno2)
        {
        if (anno1.length == 0)
            {
            return anno2;
            }

        if (anno2.length == 0)
            {
            return anno1;
            }

        ArrayList<Annotation> list = new ArrayList<>();
        Set<Constant> setPresent = new HashSet<>();
        appendAnnotations(list, anno1, setPresent);
        appendAnnotations(list, anno2, setPresent);
        return list.toArray(new Annotation[list.size()]);
        }

    private void appendAnnotations(ArrayList<Annotation> list, Annotation[] aAnno, Set<Constant> setPresent)
        {
        for (Annotation anno : aAnno)
            {
            if (setPresent.add(anno.getAnnotationClass()))
                {
                list.add(anno);
                }
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Null if the property is a public or protected property of the type itself; otherwise, the
     * identity of the containing structure.
     */
    private Constant m_constParent;

    /**
     * The property name.
     */
    private String m_sName;

    /**
     * Type of the property, including any annotations on the type.
     */
    private TypeConstant m_type;

    /**
     * True iff the property represents a type parameter.
     */
    private boolean m_fParam;

    /**
     * True iff the property is a Ref; false iff the property is a Var.
     */
    private boolean m_fRO;

    /**
     * An array of non-virtual annotations on the property declaration itself
     */
    private Annotation[] m_aPropAnno;

    /**
     * An array of annotations that apply to the Ref/Var of the property.
     */
    private Annotation[] m_aRefAnno;

    /**
     * True to indicate that the property has custom code that overrides the underlying Ref/Var
     * implementation.
     */
    private boolean m_fCustom;

    /**
     * True iff the property requires a field.
     */
    private boolean m_fField;
    }
