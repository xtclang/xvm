package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A Parameter is a combination of a type and a name, representing a type parameter, a method
 * invocation parameter, and a return value.
 */
public class Parameter
        extends XvmStructure
        implements Cloneable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool      the ConstantPool that will contain this Constant
     * @param in        the DataInput stream to read the Constant value from
     * @param fReturn   true iff this is a return value; false iff this is a parameter
     * @param index     index of the return value or parameter
     * @param fSpecial  true iff the "condition" return value or a type-param parameter
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public Parameter(ConstantPool pool, DataInput in, boolean fReturn, int index, boolean fSpecial)
            throws IOException
        {
        super(pool);

        int          cAnnos = readPackedInt(in);
        Annotation[] aAnnos = cAnnos == 0 ? Annotation.NO_ANNOTATIONS : new Annotation[cAnnos];
        for (int i = 0; i < cAnnos; ++i)
            {
            aAnnos[i] = (Annotation) pool.getConstant(readMagnitude(in));
            }

        int iType      = readMagnitude(in);
        int iName      = fReturn ? readIndex(in) : readMagnitude(in);
        int iDefault   = readIndex(in);

        m_aAnnotations = aAnnos;
        m_constType    = (TypeConstant)   pool.getConstant(iType   );
        m_constName    = (StringConstant) pool.getConstant(iName   );
        m_constDefault =                  pool.getConstant(iDefault);

        f_iParam       = fReturn ? -1 - index : index;
        f_fOrdinary    = !fSpecial;
        }

    /**
     * Construct a Parameter definition structure.
     *
     * @param pool          the ConstantPool that will contain this Constant
     * @param constType     the type of the parameter
     * @param sName         the parameter name
     * @param constDefault  the default value for the parameter
     * @param fReturn       true iff this is a return value; false iff this is a parameter
     * @param index         index of the return value or parameter
     * @param fSpecial      true iff the "condition" return value or a type-param parameter
     */
    public Parameter(ConstantPool pool, TypeConstant constType, String sName,
                     Constant constDefault, boolean fReturn, int index, boolean fSpecial)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("parameter type required");
            }

        if (sName == null && !fReturn)
            {
            throw new IllegalArgumentException("parameter name required");
            }

        m_aAnnotations = Annotation.NO_ANNOTATIONS;
        m_constType    = constType;
        m_constName    = sName == null ? null : pool.ensureStringConstant(sName);
        m_constDefault = constDefault;

        f_iParam       = fReturn ? -1 - index : index;
        f_fOrdinary    = !fSpecial;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the index of the parameter in the parameter list (including type parameters), or the
     *         index of the return value in the return value list (including the leading boolean in
     *         the case of a conditional method)
     */
    public int getIndex()
        {
        int iParam = f_iParam;
        return iParam >= 0
                ? iParam
                : -1 - iParam;
        }

    /**
     * @return true iff this is a parameter; false iff this is a return value
     */
    public boolean isParameter()
        {
        return f_iParam >= 0;
        }

    /**
     * @return an array of Annotation structures that represent all annotations of the parameter
     */
    public Annotation[] getAnnotations()
        {
        return m_aAnnotations;
        }

    /**
     * Add an annotation to this parameter.
     *
     * @param anno  the annotation to add
     */
    public void addAnnotation(Annotation anno)
        {
        assert isParameter();

        int cAnnos = m_aAnnotations.length;
        if (cAnnos == 0)
            {
            m_aAnnotations = new Annotation[] {anno};
            }
        else
            {
            Annotation[] aAnnos = new Annotation[cAnnos + 1];
            System.arraycopy(m_aAnnotations, 0, aAnnos, 0, cAnnos);
            aAnnos[cAnnos] = anno;
            m_aAnnotations = aAnnos;
            }
        }

    /**
     * Check if all annotations are resolved; extract those that apply to the Parameter itself.
     *
     * @return true if the annotations have been resolved; false if this method has to be called
     *         later in order to resolve annotations
     */
    public boolean resolveAnnotations()
        {
        TypeConstant typeParam = m_constType;
        if (typeParam.containsUnresolved())
            {
            return false;
            }

        if (!typeParam.isAnnotated())
            {
            return true;
            }

        int          cExtract = 0;
        TypeConstant typeBase = typeParam.resolveTypedefs();
        while (typeBase instanceof AnnotatedTypeConstant typeAnno)
            {
            Annotation   anno      = typeAnno.getAnnotation();
            TypeConstant typeMixin = anno.getAnnotationType();

            if (typeMixin.getExplicitClassFormat() != Component.Format.MIXIN)
                {
                // no need to do anything; an error will be reported later
                return true;
                }

            TypeConstant typeInto = typeMixin.getExplicitClassInto();
            if (typeInto.containsUnresolved())
                {
                return false;
                }

            if (typeInto.isIntoMethodParameterType())
                {
                ++cExtract;
                }
            typeBase = typeAnno.getUnderlyingType();
            }

        if (cExtract == 0)
            {
            return true;
            }

        Annotation[] aAnnos = typeParam.getAnnotations();
        int          cAll   = aAnnos.length;
        if (cExtract == cAll)
            {
            m_aAnnotations = aAnnos;
            m_constType    = typeBase;
            return true;
            }

        int          cKeep = cAll - cExtract;
        Annotation[] aKeep = new Annotation[cKeep];
        Annotation[] aMove = new Annotation[cExtract];
        int          iKeep = 0;
        int          iMove = 0;

        for (Annotation annotation : aAnnos)
            {
            if (annotation.getAnnotationType().getExplicitClassInto().isIntoMethodParameterType())
                {
                aMove[iMove++] = annotation;
                }
            else
                {
                aKeep[iKeep++] = annotation;
                }
            }

        m_constType    = getConstantPool().ensureAnnotatedTypeConstant(typeBase, aKeep);
        m_aAnnotations = aMove;
        return true;
        }

    /**
     * Get the type of the parameter.
     *
     * @return the parameter type
     */
    public TypeConstant getType()
        {
        return m_constType;
        }

    /**
     * @return true iff the method takes type parameters and this is one of them
     */
    public boolean isTypeParameter()
        {
        return isParameter() && !f_fOrdinary;
        }

    /**
     * @return the TypeParameterConstant that corresponds to this register being used as a type param
     */
    public TypeParameterConstant asTypeParameterConstant(MethodConstant constMethod)
        {
        assert isTypeParameter();
        return getConstantPool().ensureRegisterConstant(constMethod, f_iParam, getName());
        }

    /**
     * @return the TerminalTypeConstant that corresponds to this register being used as a type param
     */
    public TypeConstant asTypeParameterType(MethodConstant constMethod)
        {
        return getConstantPool().ensureTerminalTypeConstant(asTypeParameterConstant(constMethod));
        }

    /**
     * @return true iff the method has a conditional return and this represents a return value that
     *         is only available if the condition is true
     */
    public boolean isConditionalReturn()
        {
        return !isParameter() && !f_fOrdinary;
        }

    /**
     * @return true iff the parameter or return value has a name
     */
    public boolean isNamed()
        {
        return m_constName != null;
        }

    /**
     * @return the name of the parameter or return value, or null if there is none
     */
    public String getName()
        {
        return m_constName == null ? null : m_constName.getValue();
        }

    /**
     * Specify that the parameter will have a default value.
     * <p/>
     * This is a temporary value that is used as a place-holder until the property's actual value is
     * available.
     */
    public void markDefaultValue()
        {
        m_fHasDefault = true;
        }

    /**
     * @return true iff the Property is known to have a value, even if the value has not yet been
     *         determined
     */
    public boolean hasDefaultValue()
        {
        return m_fHasDefault || m_constDefault != null;
        }

    /**
     * @return the default value of the parameter or return value, if it is a compile-time constant,
     *         or null if there is none or the default value is computed by an initializer
     */
    public Constant getDefaultValue()
        {
        return m_constDefault;
        }

    /**
     * Fill in the default value of the parameter.
     *
     * @param constDefault  the default value of the parameter
     */
    public void setDefaultValue(Constant constDefault)
        {
        assert hasDefaultValue();
        assert constDefault != null;
        m_constDefault = constDefault;
        }

    /**
     * Mark the parameter as representing a Ref/Var that must be implicitly de-referenced on every
     * access.
     */
    public void markImplicitDeref()
        {
        assert getType().isA(getConstantPool().typeRef());
        m_fImplicitDeref = true;
        }

    /**
     * @return true iff the parameter has been marked as requiring an implicit de-reference on every
     *         access
     */
    public boolean isImplicitDeref()
        {
        return m_fImplicitDeref;
        }

    /**
     * Obtain the register that de-references the implicitly de-referenced register.
     *
     * @param regVar  the implicitly de-referenced register
     * @param method  the containing method
     *
     * @return the register representing the de-reference of the passed register
     */
    public Register deref(Register regVar, MethodStructure method)
        {
        assert isImplicitDeref();

        if (m_regDeref == null)
            {
            TypeConstant typeVar = getType();
            TypeConstant typeVal = typeVar.getParamType(0);
            Register     reg     = new Register(typeVal, method);
            reg.specifyRegType(typeVar);

            m_regDeref = reg;
            }

        return m_regDeref;
        }

    /**
     * Clone this Parameter.
     *
     * @return a clone of this Parameter
     */
    protected Parameter cloneBody()
        {
        Parameter that;
        try
            {
            that = (Parameter) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException(e);
            }

        m_fImplicitDeref = false;
        m_regDeref       = null;
        return that;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void markModified()
        {
        // parameters are basically constants
        throw new UnsupportedOperationException();
        }

    @Override
    protected void disassemble(DataInput in)
        {
        throw new IllegalStateException();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_aAnnotations = (Annotation[])   Constant.registerConstants(pool, m_aAnnotations);
        m_constType    = (TypeConstant)   pool.register(m_constType   );
        m_constName    = (StringConstant) pool.register(m_constName   );
        m_constDefault =                  pool.register(m_constDefault);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        writePackedLong(out, m_aAnnotations.length);
        for (Annotation anno : m_aAnnotations)
            {
            writePackedLong(out, anno.getPosition());
            }

        writePackedLong(out, Constant.indexOf(m_constType));
        writePackedLong(out, Constant.indexOf(m_constName));
        writePackedLong(out, Constant.indexOf(m_constDefault));
        }

    @Override
    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(isParameter() ? "param" : "return")
          .append("-index=")
          .append(getIndex());

        if (isTypeParameter())
            {
            sb.append(" (type parameter)");
            }
        else if (isConditionalReturn())
            {
            sb.append(" (conditional return)");
            }

        sb.append(", type=")
          .append(m_constType.getValueString());

        if (isNamed())
            {
            sb.append(", name=")
              .append(getName());
            }

        if (hasDefaultValue())
            {
            sb.append(", has default");

            if (m_constDefault != null)
                {
                sb.append("=")
                  .append(m_constDefault.getValueString());
                }
            }

        return sb.toString();
        }

    @Override
    protected void dump(PrintWriter out, String sIndent)
        {
        out.print(sIndent);
        out.println(this);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return f_iParam;
        }

    @Override
    public boolean equals(Object obj)
        {
        if (this == obj)
            {
            return true;
            }

        if (!(obj instanceof Parameter that))
            {
            return false;
            }

        return this.f_iParam == that.f_iParam && this.f_fOrdinary == that.f_fOrdinary
                && this.m_constType.equals(that.m_constType)
                && Handy.equals(this.m_constName, that.m_constName)
                && Handy.equals(this.m_constDefault, that.m_constDefault);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty array of Parameters.
     */
    public static final Parameter[] NO_PARAMS = new Parameter[0];

    /**
     * The parameter annotations.
     */
    private Annotation[] m_aAnnotations;

    /**
     * The constant that represents the type of this parameter.
     */
    private TypeConstant m_constType;

    /**
     * The constant that holds the name of the parameter.
     */
    private StringConstant m_constName;

    /**
     * The default value.
     */
    private Constant m_constDefault;

    /**
     * The parameter index, if {@code (n >= 0)}; otherwise, {@code (-1 - return_value_index)}
     */
    private final int f_iParam;

    /**
     * True iff the parameter represents an "ordinary" parameter or return value, and not one of the
     * "formal type parameter" or the boolean "conditional" return value.
     */
    private final boolean f_fOrdinary;

    /**
     * True if there will be a default value, even if it is not yet known.
     */
    private transient boolean m_fHasDefault;

    /**
     * True if the parameter represents a Ref or Var that must be implicitly de-referenced on each
     * access.
     */
    private transient boolean  m_fImplicitDeref;

    /**
     * The register that we create to act as the implicit de-ref.
     */
    private transient Register m_regDeref;
    }