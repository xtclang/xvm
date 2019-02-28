package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;


/**
 * CATCH ; begin an exception handler (implicit ENTER)
 */
public class CatchStart
        extends OpVar
    {
    /**
     * Construct a CATCH op.
     *
     * @param reg        the register that will hold the caught exception
     * @param constName  the name constant for the catch exception variable
     */
    public CatchStart(Register reg, StringConstant constName)
        {
        super(reg);

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
    protected CatchStart(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        // TODO this doesn't go here; it belongs on GuardStart (since that's the place that will write it out)
//        if (m_constName != null)
//            {
//            m_nNameId = encodeArgument(m_constName, registry);
//            }
//        writePackedLong(out, m_nNameId);
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
    public int process(Frame frame, int iPC)
        {
        // all the logic is actually implemented by Frame.findGuard()
        return iPC + 1;
        }

    @Override
    public void simulate(Scope scope)
        {
        scope.enter();
        super.simulate(scope);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        }

    @Override
    protected String getName()
        {
        return Argument.toIdString(m_constName, m_nNameId);
        }

    private int m_nNameId;

    private StringConstant m_constName;
    }
