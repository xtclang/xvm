package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_IN TYPE, STRING, rvalue-src ; (next register is an initialized named variable)
 */
public class Var_IN
        extends OpVar
    {
    /**
     * Construct a VAR_IN op for the specified register, name and argument.
     *
     * @param reg        the register
     * @param constName  the name constant
     * @param argValue   the value argument
     */
    public Var_IN(Register reg, StringConstant constName, Argument argValue)
        {
        super(reg);

        if (argValue == null || constName == null)
            {
            throw new IllegalArgumentException("name and value required");
            }

        m_constName = constName;
        m_argValue  = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_IN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId  = readPackedInt(in);
        m_nValueId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nNameId  = encodeArgument(m_constName, registry);
            m_nValueId = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nNameId);
        writePackedLong(out, m_nValueId);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_IN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getArgument(m_nValueId);

            if (isDeferred(hArg))
                {
                return hArg.proceed(frame, frameCaller ->
                    {
                    frameCaller.introduceVar(m_nVar, convertId(m_nType),
                            m_nNameId, Frame.VAR_STANDARD, frameCaller.popStack());
                    return iPC + 1;
                    });
                }

            frame.introduceVar(m_nVar, convertId(m_nType), m_nNameId, Frame.VAR_STANDARD, hArg);
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_constName = (StringConstant) registerArgument(m_constName, registry);
        m_argValue = registerArgument(m_argValue, registry);
        }

    @Override
    public String getName(Constant[] aconst)
        {
        return getName(aconst, m_constName, m_nNameId);
        }

    private int m_nNameId;
    private int m_nValueId;

    private StringConstant m_constName;
    private Argument       m_argValue;
    }