package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;


/**
 * VAR_T #values:(TYPE, rvalue-src) ; next register is an initialized anonymous Tuple variable
 */
public class Var_T
        extends OpVar
    {
    /**
     * Construct a VAR_T op.
     *
     * @param nType      the type id of the Tuple variable
     * @param anValueId  the value ids for the sequence
     *
     * @deprecated
     */
    public Var_T(int nType, int[] anValueId)
        {
        super();

        m_nType      = nType;
        m_anArgValue = anValueId;
        }

    /**
     * Construct a VAR_T op for the specified Tuple type and arguments.
     *
     * @param constType  the Tuple type
     * @param aArgValue  the value argument
     */
    public Var_T(TypeConstant constType, Argument[] aArgValue)
        {
        super(constType);

        if (aArgValue == null)
            {
            throw new IllegalArgumentException("values required");
            }

        m_aArgValue = aArgValue;
        }

    /**
     * Construct a VAR_T op for the specified register and arguments.
     *
     * @param reg        the register
     * @param aArgValue  the value argument
     */
    public Var_T(Register reg, Argument[] aArgValue)
        {
        super(reg);

        if (aArgValue == null)
            {
            throw new IllegalArgumentException("values required");
            }

        m_aArgValue = aArgValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Var_T(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArgValue != null)
            {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
            }

        writeIntArray(out, m_anArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_VAR_T;
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

            frame.introduceResolvedVar(clzTuple.ensurePublicType(), null, Frame.VAR_STANDARD, hTuple);

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

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
    }
