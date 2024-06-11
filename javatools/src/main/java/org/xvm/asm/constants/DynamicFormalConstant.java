package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.LinkedList;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.PropertyExprAST;

import org.xvm.compiler.ast.Context;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Constant whose purpose is to represent a run-time formal type calculation based on a target
 * register.
 */
public class DynamicFormalConstant
        extends FormalConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param idMethod     the containing method constant
     * @param sName        the variable (register) name
     * @param reg          the register this dynamic type applies to
     * @param constFormal  the formal constant that applies to the RegisterConstant's type
     */
    public DynamicFormalConstant(ConstantPool pool, MethodConstant idMethod, String sName,
                                 Register reg, FormalConstant constFormal)
        {
        super(pool, idMethod, sName);

        assert reg.getType().removeAccess().isA(constFormal.getNamespace().getType());

        // if the register is a shadow, take the original register, but the shadow's type
        m_reg         = reg.getOriginalRegister();
        m_typeReg     = reg.getType();
        m_constFormal = constFormal;
        f_nReg        = reg.getIndex();
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param in    the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public DynamicFormalConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        f_nReg    = readPackedInt(in);
        m_nRegId  = readPackedInt(in);
        m_iType   = readMagnitude(in);
        m_iFormal = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();

        ConstantPool pool = getConstantPool();

        m_typeReg     = (TypeConstant)   pool.getConstant(m_iType);
        m_constFormal = (FormalConstant) pool.getConstant(m_iFormal);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the underlying FormalConstant
     */
    public FormalConstant getFormalConstant()
        {
        return m_constFormal;
        }

    /**
     * @return the register used by this constant
     */
    public Register getRegister()
        {
        return m_reg;
        }

    /**
     * @return the register index used by this constant
     */
    public int getRegisterIndex()
        {
        return m_reg == null
                ? f_nReg
                : m_reg.getIndex();
        }

    /**
     * @return the containing MethodConstant
     */
    public MethodConstant getMethod()
        {
        return (MethodConstant) getParentConstant();
        }


    // ----- DynamicConstant methods ---------------------------------------------------------------


    @Override
    public TypeConstant getConstraintType()
        {
        TypeConstant typeConstraint = m_typeReg.resolveFormalType(m_constFormal);
        return typeConstraint == null
                ? m_constFormal.getConstraintType().resolveConstraints()
                : typeConstraint;
        }

    @Override
    public TypeConstant resolve(GenericTypeResolver resolver)
        {
        if (resolver instanceof Frame frame)
            {
            try
                {
                // there are some rare instances (e.g. lambdas), when this FTC refers to a register
                // from a caller's frame, in which case we need to follow the call chain
                do
                    {
                    if (getMethod().equals(frame.f_function.getIdentityConstant()))
                        {
                        ObjectHandle hTarget    = frame.getArgument(getRegisterIndex());
                        TypeConstant typeTarget = hTarget.getType();

                        return typeTarget.isShared(frame.poolContext())
                                ? m_constFormal.resolve(typeTarget)
                                : frame.poolContext().typeObject();
                        }
                    frame = frame.f_framePrev;
                    }
                while (frame != null);
                }
            catch (ExceptionHandle.WrapperException ignore)
                {
                }
            }
        return resolver.resolveFormalType(this);
        }

    @Override
    public ExprAST toExprAst(Context ctx)
        {
        if (!getMethod().equals(ctx.getMethod().getIdentityConstant()))
            {
            // the register doesn't belong to this method; this may require help from the run-time
            return null;
            }

        FormalConstant constFormal = getFormalConstant();

        // if the underlying constant is A.B.C, collect the names in reverse order (FILO)
        LinkedList<FormalTypeChildConstant> listNames = null;
        while (constFormal instanceof FormalTypeChildConstant idChild)
            {
            if (listNames == null)
                {
                listNames = new LinkedList<>();
                }

            listNames.addFirst(idChild);
            constFormal = idChild.getParentConstant();
            }

        // we've reached the top; it must be a PropertyConstant
        if (constFormal instanceof PropertyConstant idProp)
            {
            ExprAST astExpr = new PropertyExprAST(getRegister().getRegisterAST(), idProp);
            if (listNames != null)
                {
                for (FormalTypeChildConstant idNext : listNames)
                    {
                    astExpr = new PropertyExprAST(astExpr, idNext);
                    }
                }
            return astExpr;
            }

        throw new IllegalStateException("Unsupported formal: " + constFormal);
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        throw new IllegalStateException();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.DynamicFormal;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constFormal);
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && (super.containsUnresolved()
                                   || m_typeReg.containsUnresolved()
                                   || m_constFormal.containsUnresolved());
        }

    @Override
    public String getValueString()
        {
        return getName() + '.' + m_constFormal.getName();
        }

    @Override
    public String getDescription()
        {
        return "Dynamic " + getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_typeReg     = (TypeConstant)   pool.register(m_typeReg);
        m_constFormal = (FormalConstant) pool.register(m_constFormal);
        }

    @Override
    protected void assemble(DataOutput out)
        throws IOException
        {
        super.assemble(out);

        if (m_reg == null)
            {
            writePackedLong(out, f_nReg);
            writePackedLong(out, m_nRegId);
            }
        else
            {
            assert !m_reg.isUnknown();

            writePackedLong(out, m_reg.getIndex());
            writePackedLong(out, m_reg.getId());
            }
        writePackedLong(out, m_typeReg.getPosition());
        writePackedLong(out, m_constFormal.getPosition());
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof DynamicFormalConstant that))
            {
            return -1;
            }

        int n = super.compareDetails(that);
        if (n != 0)
            {
            return n;
            }

        n = this.m_constFormal.compareDetails(that.m_constFormal);
        if (n != 0)
            {
            return n;
            }

        n = this.m_typeReg.compareTo(that.m_typeReg);
        if (n != 0)
            {
            return n;
            }

        if (this.m_reg == null)
            {
            return that.m_reg == null
                ? (this.f_nReg - that.f_nReg) | (this.m_nRegId - that.m_nRegId)
                : -1;
            }

        return that.m_reg == null
            ? 1
            : this.m_reg == that.m_reg ? 0 : -1;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constFormal,
               Hash.of(m_typeReg,
               super.computeHashCode()));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The register index.
     */
    private final int f_nReg;

    /**
     * The register unique id within the enclosing method (not used during the compilation).
     */
    private int m_nRegId;

    /**
     * During disassembly, this holds the index of the register type.
     */
    private int m_iType;

    /**
     * During disassembly, this holds the index of the formal constant.
     */
    private int m_iFormal;

    /**
     * The register type.
     */
    private TypeConstant m_typeReg;

    /**
     * The formal constant.
     */
    private FormalConstant m_constFormal;

    /**
     * The register (used only during compilation).
     */
    private transient Register m_reg;
    }