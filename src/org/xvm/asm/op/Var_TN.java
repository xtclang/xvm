package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_TN STRING, #values:(TYPE, rvalue-src) ; next register is an initialized anonymous Tuple variable
 */
public class Var_TN
        extends OpVar
    {
    /**
     * Construct a VAR_TN op for the specified type, name and arguments.
     *
     * @param constType the variable type
     * @param constName  the name constant
     * @param aArgValue  the value argument
     */
    public Var_TN(TypeConstant constType, StringConstant constName, Argument[] aArgValue)
        {
        super(constType);

        if (constName == null || aArgValue == null)
            {
            throw new IllegalArgumentException("name and values required");
            }

        m_constName = constName;
        m_aArgValue = aArgValue;
        }

    /**
     * Construct a VAR_TN op for the specified register, name and arguments.
     *
     * @param reg        the register
     * @param constName  the name constant
     * @param aArgValue  the value argument
     */
    public Var_TN(Register reg, StringConstant constName, Argument[] aArgValue)
        {
        super(reg);

        if (constName == null || aArgValue == null)
            {
            throw new IllegalArgumentException("name and values required");
            }

        m_constName = constName;
        m_aArgValue = aArgValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_TN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nNameId = readPackedInt(in);
        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constName != null)
            {
            m_nNameId = encodeArgument(m_constName, registry);
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            }

        writePackedLong(out, m_nNameId);
        writeIntArray(out, m_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_TN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clzTuple = frame.resolveClass(m_nType);

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, m_anArgValue.length);
            if (ahArg == null)
                {
                return R_REPEAT;
                }

            TupleHandle hTuple = xTuple.makeHandle(clzTuple, ahArg);

            frame.introduceVar(m_nVar, convertId(m_nType), m_nNameId, Frame.VAR_STANDARD, hTuple);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' ' + Argument.toIdString(m_constName, m_nNameId);
        // TODO arguments
        }

    private int   m_nNameId;
    private int[] m_anArgValue;

    private StringConstant m_constName;
    private Argument[]     m_aArgValue;
    }
