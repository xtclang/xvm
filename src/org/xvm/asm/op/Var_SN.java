package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * VAR_SN TYPE, STRING, #values:(rvalue-src) ; next register is a named initialized anonymous Sequence variable
 */
public class Var_SN
        extends OpVar
    {
    /**
     * Construct a VAR_SN op.
     *
     * @param nType      the variable type id
     * @param nNameId    the name of the variable id
     * @param anValueId  the value ids for the sequence
     */
    public Var_SN(int nType, int nNameId, int[] anValueId)
        {
        super(nType);

        m_nNameId = nNameId;
        m_anArgValue = anValueId;
        }

    /**
     * Construct a VAR_SN op for the specified type, name and arguments.
     *
     * @param constType the variable type
     * @param constName  the name constant
     * @param aArgValue  the value argument
     */
    public Var_SN(TypeConstant constType, StringConstant constName, Argument[] aArgValue)
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
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_SN(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(readPackedInt(in));

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
        return OP_VAR_SN;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clazzEl = frame.f_context.f_types.resolveClass(
            m_nType, frame.getActualTypes());

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, m_anArgValue.length);
            if (ahArg == null)
                {
                return R_REPEAT;
                }

            ArrayHandle hArray = xArray.makeHandle(clazzEl.ensurePublicType(), ahArg);
            hArray.makeImmutable();

            frame.introduceVar(hArray.m_type, frame.getString(m_nNameId), Frame.VAR_STANDARD, hArray);

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

        registerArgument(m_constName, registry);
        registerArguments(m_aArgValue, registry);
        }

    private int m_nNameId;
    private int[] m_anArgValue;

    private StringConstant m_constName;
    private Argument[] m_aArgValue;
    }
