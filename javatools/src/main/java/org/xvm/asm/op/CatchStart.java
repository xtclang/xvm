package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;


/**
 * CATCH ; begin an exception handler (implicit ENTER and VAR_IN)
 * <p/>
 * The CATCH op indicates the beginning of an exception handler. The exception handler concludes
 * with a matching CATCH_END op.
 */
public class CatchStart
        extends OpVar
    {
    /**
     * Construct a CATCH op for the specified exception type.
     *
     * @param type       the exception type to catch
     * @param constName  the name constant for the catch exception variable
     */
    public CatchStart(TypeConstant type, StringConstant constName)
        {
        this(new Register(type), constName);
        }

    /**
     * Construct a CATCH op.
     *
     * @param reg        the register that will hold the caught exception
     * @param constName  the name constant for the catch exception variable
     */
    public CatchStart(Register reg, StringConstant constName)
        {
        super(reg);

        if (!reg.getType().isA(reg.getType().getConstantPool().typeException()))
            {
            throw new IllegalArgumentException("catch type must be an exception type");
            }

        if (constName == null)
            {
            throw new IllegalArgumentException("name required");
            }

        m_constName = constName;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public CatchStart(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    void preWrite(ConstantRegistry registry)
        {
        m_nType = encodeArgument(getRegisterType(), registry);

        if (m_constName != null)
            {
            m_nNameId = encodeArgument(m_constName, registry);
            }
        }

    int getTypeId()
        {
        return m_nType;
        }

    void setTypeId(int nType)
        {
        m_nType = nType;
        }

    int getNameId()
        {
        return m_nNameId;
        }

    void setNameId(int nName)
        {
        m_nNameId = nName;
        }

    @Override
    protected boolean isTypeAware()
        {
        return false;
        }

    @Override
    public int getOpCode()
        {
        return OP_CATCH;
        }

    @Override
    public boolean isEnter()
        {
        return true;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
        }

    @Override
    public void markReachable(Op[] aop)
        {
        super.markReachable(aop);
        findCorrespondingOp(aop, OP_CATCH_END).markNecessary();
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter(this);
        super.simulate(scope);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        }

    @Override
    public String getName(Constant[] aconst)
        {
        return getName(aconst, m_constName, m_nNameId);
        }

    private int m_nNameId;

    private StringConstant m_constName;
    }
