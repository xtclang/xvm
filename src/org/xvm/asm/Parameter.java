package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintWriter;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A Parameter is a combination of a type and a name, representing a type parameter, a method
 * invocation parameter, and a return value.
 */
public class Parameter
        extends XvmStructure
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

        int iType      = readMagnitude(in);
        int iName      = fReturn ? readIndex(in) : readMagnitude(in);
        int iDefault   = readIndex(in);

        m_constType    = (TypeConstant)   pool.getConstant(iType   );
        m_constName    = (StringConstant) pool.getConstant(iName   );
        m_constDefault =                  pool.getConstant(iDefault);
        m_iParam       = fReturn ? -1 - index : index;
        m_fOrdinary    = !fSpecial;
        }

    /**
     * Construct a constant whose value is a Parameter definition.
     *
     * @param pool          the ConstantPool that will contain this Constant
     * @param constType     the type of the parameter
     * @param sName         the parameter name
     * @param constDefault  the default value for the parameter
     * @param fReturn       true iff this is a return value; false iff this is a parameter
     * @param index         index of the return value or parameter
     * @param fSpecial      true iff the "condition" return value or a type-param parameter
     */
    public Parameter(ConstantPool pool, TypeConstant constType, String sName, Constant constDefault,
            boolean fReturn, int index, boolean fSpecial)
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

        m_constType    = constType;
        m_constName    = sName == null ? null : pool.ensureStringConstant(sName);
        m_constDefault = constDefault;

        m_iParam       = fReturn ? -1 - index : index;
        m_fOrdinary    = !fSpecial;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the index of the parameter in the parameter list (including type parameters), or the
     *         index of the return value in the return value list (including the leading boolean in
     *         the case of a conditional method)
     */
    public int getIndex()
        {
        int iParam = m_iParam;
        return iParam >= 0
                ? iParam
                : -1 - iParam;
        }

    /**
     * @return true iff this is a parameter; false iff this is a return value
     */
    public boolean isParameter()
        {
        return m_iParam >= 0;
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
        return isParameter() && !m_fOrdinary;
        }

    /**
     * @return the TypeParameterConstant that corresponds to this register being used as a type param
     */
    public TypeParameterConstant asTypeParameterConstant(MethodConstant constMethod)
        {
        assert isTypeParameter();
        return getConstantPool().ensureRegisterConstant(constMethod, m_iParam, getName());
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
        return !isParameter() && !m_fOrdinary;
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
     *
     * @return the register representing the de-reference of the passed register
     */
    public Register deref(Register regVar)
        {
        assert isImplicitDeref();

        if (m_regDeref == null)
            {
            TypeConstant typeVar = getType();
            TypeConstant typeVal = typeVar.getParamType(0);
            m_regDeref = new Register(typeVal);
            }

        return m_regDeref;
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
        m_constType    = (TypeConstant)   pool.register(m_constType   );
        m_constName    = (StringConstant) pool.register(m_constName   );
        m_constDefault =                  pool.register(m_constDefault);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
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
        out.println(toString());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_iParam;
        }

    @Override
    public boolean equals(Object obj)
        {
        if (this == obj)
            {
            return true;
            }

        if (!(obj instanceof Parameter))
            {
            return false;
            }

        Parameter that = (Parameter) obj;
        return this.m_iParam == that.m_iParam && this.m_fOrdinary == that.m_fOrdinary
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
    private int m_iParam;

    /**
     * True iff the parameter represents an "ordinary" parameter or return value, and not one of the
     * "formal type parameter" or the boolean "conditional" return value.
     */
    private boolean m_fOrdinary;

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
