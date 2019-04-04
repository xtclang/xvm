package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a type parameter constant, which specifies a particular virtual machine register.
 */
public class TypeParameterConstant
        extends    PseudoConstant
        implements FormalConstant
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
    public TypeParameterConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iMethod = readMagnitude(in);
        m_iReg    = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a register identifier.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param iReg  the register number
     */
    public TypeParameterConstant(ConstantPool pool, MethodConstant constMethod, int iReg)
        {
        super(pool);

        if (constMethod == null)
            {
            throw new IllegalArgumentException("method required");
            }

        if (iReg < 0 || iReg > 0xFF)    // arbitrary limit; basically just a sanity assertion
            {
            throw new IllegalArgumentException("register (" + iReg + ") out of range");
            }

        m_constMethod = constMethod;
        m_iReg        = iReg;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the MethodConstant that the register belongs to
     */
    public MethodConstant getMethod()
        {
        return m_constMethod;
        }

    /**
     * Bind this {@link TypeParameterConstant} to the specified MethodConstant
     */
    public void bindMethod(MethodConstant method)
        {
        assert !method.containsUnresolved();
        assert m_constMethod.equals(method) && method.equals(m_constMethod);

        m_constMethod = method;
        }

    /**
     * @return the register number (zero based)
     */
    public int getRegister()
        {
        return m_iReg;
        }


    // ----- FormalConstant methods ----------------------------------------------------------------

    @Override
    public TypeConstant getConstraintType()
        {
        // the type points to a register, which means that the type is a parameterized type;
        // the type of the register will be "Type<X>", so return X
        MethodConstant   constMethod = getMethod();
        int              nReg        = getRegister();
        TypeConstant[]   atypeParams = constMethod.getRawParams();
        assert atypeParams.length > nReg;
        TypeConstant typeConstraint  = atypeParams[nReg];
        assert typeConstraint.isEcstasy("Type") && typeConstraint.isParamsSpecified();

        typeConstraint = typeConstraint.getParamTypesArray()[0];
        if (!typeConstraint.isParamsSpecified() && typeConstraint.isSingleUnderlyingClass(true))
            {
            // create a normalized formal type
            ConstantPool   pool = getConstantPool();
            ClassStructure clz  = (ClassStructure) typeConstraint.getSingleUnderlyingClass(true).getComponent();
            if (clz.isParameterized())
                {
                Set<StringConstant> setFormalNames = clz.getTypeParams().keySet();
                TypeConstant[]      atypeFormal    = new TypeConstant[setFormalNames.size()];
                int ix = 0;
                for (StringConstant constName : setFormalNames)
                    {
                    Constant constant = pool.ensureFormalTypeChildConstant(this, constName.getValue());
                    atypeFormal[ix++] = constant.getType();
                    }
                typeConstraint = pool.ensureParameterizedTypeConstant(typeConstraint, atypeFormal);
                }
            }
        return typeConstraint;
        }


    // ----- Constant methods ----------------------------------------------------------------------


    @Override
    public TypeConstant getType()
        {
        return getConstantPool().ensureTerminalTypeConstant(this);
        }

    @Override
    public Format getFormat()
        {
        return Format.TypeParameter;
        }

    @Override
    public boolean containsUnresolved()
        {
        if (fResolved || fReEntry)
            {
            return false;
            }

        fReEntry = true;
        try
            {
            return !(fResolved = !m_constMethod.containsUnresolved());
            }
        finally
            {
            fReEntry = false;
            }
        }

    @Override
    public boolean canResolve()
        {
        // as soon as the containing MethodConstant knows where it exists in the universe, then we
        // can safely resolve names
        return m_constMethod.getParentConstant().canResolve();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        // the method constant is not "a child"; this would cause an infinite loop
        }

    @Override
    public TypeParameterConstant resolveTypedefs()
        {
        // There is a circular dependency that involves TypeParameterConstant:
        //
        // MethodConstant -> SignatureConstant -> TerminalTypeConstant -> TypeParameterConstant -> MethodConstant
        //
        // To break the circle, TypeParameterConstant is not being responsible for resolving the
        // corresponding MethodConstant; instead it's the MethodConstant's duty to call bindMethod()
        // on TypeParameterConstant (via TypeConstant#bindTypeParameters() API) as soon as the
        // MethodConstant is resolved (see MethodConstant#resolveTypedefs()).
        return this;
        }

    @Override
    protected Object getLocator()
        {
        return m_iReg == 0
                ? m_constMethod
                : null;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof TypeParameterConstant))
            {
            return -1;
            }

        TypeParameterConstant regThat = (TypeParameterConstant) that;
        int nDif = this.m_iReg - regThat.m_iReg;
        if (nDif != 0 || fReEntry)
            {
            return nDif;
            }

        fReEntry = true;
        try
            {
            return m_constMethod.compareTo(regThat.m_constMethod);
            }
        finally
            {
            fReEntry = false;
            }
        }

    @Override
    public String getValueString()
        {
        MethodStructure method = (MethodStructure) m_constMethod.getComponent();
        return method == null
                ? m_constMethod.getName() + "<#" + m_iReg + ">"
                : m_constMethod.getName() + "." + method.getParam(m_iReg).getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constMethod = (MethodConstant) getConstantPool().getConstant(m_iMethod);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constMethod = (MethodConstant) pool.register(m_constMethod);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constMethod.getPosition());
        writePackedLong(out, m_iReg);
        }

    @Override
    public String getDescription()
        {
        return "method=" + m_constMethod.getName() + ", register=" + m_iReg;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        if (fReEntry)
            {
            return m_iReg;
            }

        fReEntry = true;
        try
            {
            return m_constMethod.hashCode() + m_iReg;
            }
        finally
            {
            fReEntry = false;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The index of the MethodConstant while the TypeParameterConstant is being deserialized.
     */
    private transient int m_iMethod;

    /**
     * The MethodConstant for the method containing the register.
     */
    private MethodConstant m_constMethod;

    /**
     * The register index.
     */
    private int m_iReg;

    private transient boolean fReEntry;
    private transient boolean fResolved;
    }
