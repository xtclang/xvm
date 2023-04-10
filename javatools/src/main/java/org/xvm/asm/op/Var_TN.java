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
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xTuple;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_TN STRING, #values:(TYPE, rvalue-src) ; next register is an initialized named Tuple variable
 */
public class Var_TN
        extends OpVar
    {
    /**
     * Construct a VAR_TN op for the specified register, name and arguments.
     *
     * @param reg        the register
     * @param constName  the name constant
     * @param aArgValue  the value arguments
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

        m_nNameId    = readPackedInt(in);
        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_constName != null)
            {
            m_nNameId    = encodeArgument(m_constName, registry);
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

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    {
                    frameCaller.introduceVar(m_nVar, convertId(m_nType), m_nNameId,
                        Frame.VAR_STANDARD, xTuple.makeHandle(clzTuple, ahArg));
                    return iPC + 1;
                    };

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            frame.introduceVar(m_nVar, convertId(m_nType), m_nNameId,
                Frame.VAR_STANDARD, xTuple.makeHandle(clzTuple, ahArg));
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
        registerArguments(m_aArgValue, registry);
        }

    @Override
    public String getName(Constant[] aconst)
        {
        return getName(aconst, m_constName, m_nNameId);
        }

    private int   m_nNameId;
    private int[] m_anArgValue;

    private StringConstant m_constName;
    private Argument[]     m_aArgValue;
    }