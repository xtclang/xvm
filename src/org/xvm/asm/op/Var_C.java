package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_C rvalue-src ; (next register is a local variable representation of the specified Ref/Var)
 */
public class Var_C
        extends OpVar
    {
    /**
     * Construct a VAR_C op for the specified register and argument.
     *
     * @param reg       the register
     * @param argValue   the value argument of type Ref or Var
     */
    public Var_C(Register reg, Argument argValue)
        {
        super(reg);

        if (argValue == null)
            {
            throw new IllegalArgumentException("value required");
            }

        if (!argValue.getType().isA(argValue.getType().getConstantPool().typeRef()))
            {
            throw new IllegalArgumentException("value must be a Ref or Var");
            }

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_C(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nValueId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nValueId = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nValueId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_C;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        // TODO GG frame.introduceVar(m_nVar, ...
        return iPC + 1;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' ' + Argument.toIdString(m_argValue, m_nValueId);
        }

    private int m_nValueId;

    private Argument m_argValue;
    }
