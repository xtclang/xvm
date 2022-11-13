package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;

import org.xvm.util.Hash;
import org.xvm.util.TransientThreadLocal;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a type parameter constant, which specifies a particular virtual machine register.
 */
public class TypeParameterConstant
        extends FormalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a type parameter identifier.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param sName  the type parameter name
     * @param iReg   the register number
     */
    public TypeParameterConstant(ConstantPool pool, MethodConstant constMethod, String sName, int iReg)
        {
        super(pool, constMethod, sName);

        if (iReg < 0 || iReg > 0xFF)    // arbitrary limit; basically just a sanity assertion
            {
            throw new IllegalArgumentException("register (" + iReg + ") out of range");
            }

        f_iReg = iReg;
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
    public TypeParameterConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        f_iReg = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the MethodConstant that the register belongs to
     */
    public MethodConstant getMethod()
        {
        return (MethodConstant) getParentConstant();
        }

    /**
     * @return the register number (zero based)
     */
    public int getRegister()
        {
        return f_iReg;
        }


    // ----- FormalConstant methods ----------------------------------------------------------------

    @Override
    public TypeConstant getConstraintType()
        {
        TypeConstant typeConstraint = m_typeConstraint;
        if (typeConstraint != null)
            {
            return typeConstraint;
            }

        // the type points to a register, which means that the type is a parameterized type;
        // the type of the register will be "Type<X>" or a formal type
        MethodConstant constMethod = getMethod();
        if (constMethod.isNascent())
            {
            return getConstantPool().typeObject();
            }

        int            nReg        = getRegister();
        TypeConstant[] atypeParams = constMethod.getRawParams();

        assert atypeParams.length > nReg;

        typeConstraint = atypeParams[nReg];
        if (typeConstraint.isGenericType())
            {
            return m_typeConstraint = typeConstraint;
            }

        assert typeConstraint.isTypeOfType() && typeConstraint.isParamsSpecified();

        typeConstraint = typeConstraint.getParamType(0);
        if (typeConstraint.containsTypeParameter(true))
            {
            return typeConstraint.resolveConstraints();
            }

        if (!typeConstraint.isParamsSpecified() && typeConstraint.isExplicitClassIdentity(true))
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
        return m_typeConstraint = typeConstraint;
        }

    @Override
    public TypeConstant resolve(GenericTypeResolver resolver)
        {
        MethodStructure method = (MethodStructure) getMethod().getComponent();
        return method == null
                ? null
                : resolver.resolveFormalType(this);
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        return that.getConstantPool().ensureRegisterConstant(
                (MethodConstant) that, getRegister(), getName());
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
        // even if the parent method is unresolved, the TypeParameter is not the reason
        return false;
        }

    @Override
    public boolean canResolve()
        {
        // as soon as the containing MethodConstant knows where it exists in the universe, then we
        // can safely resolve names
        return getParentConstant().getParentConstant().canResolve();
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
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof TypeParameterConstant that))
            {
            return -1;
            }

        int nDif = this.f_iReg - that.f_iReg;
        if (nDif != 0 || f_tloReEntry.get() != null)
            {
            return nDif;
            }

        try (var ignore = f_tloReEntry.push(true))
            {
            return getParentConstant().compareTo(that.getParentConstant());
            }
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        // invalidate cached type
        m_typeConstraint = null;
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, f_iReg);
        }

    @Override
    public String getDescription()
        {
        return "method=" + getMethod().getName() + ", register=" + f_iReg;
        }


    // ----- Object methods ------------------------------------------------------------------------


    @Override
    public int computeHashCode()
        {
        return Hash.of(getName(), Hash.of(f_iReg));

// TODO MF: significantly faster but breaks clean compilation
//        if (f_tloReEntry.get() != null)
//            {
//            return Hash.of(getName(), Hash.of(f_iReg));
//            }
//
//        try (var ignore = f_tloReEntry.push(true))
//            {
//            return Hash.of(f_iReg, super.computeHashCode());
//            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The register index.
     */
    private final int f_iReg;

    /**
     * Cached constraint type.
     */
    private transient TypeConstant m_typeConstraint;

    private final TransientThreadLocal<Boolean> f_tloReEntry = new TransientThreadLocal<>();
    }