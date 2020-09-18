package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpVar;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;


/**
 * VAR_S TYPE, #values:(rvalue-src) ; next register is an initialized anonymous Array variable
 */
public class Var_S
        extends OpVar
    {
    /**
     * Construct a VAR_S op for the specified type and arguments.
     *
     * @param constType the variable type
     * @param aArgValue  the value argument
     */
    public Var_S(TypeConstant constType, Argument[] aArgValue)
        {
        super(constType);

        if (aArgValue == null)
            {
            throw new IllegalArgumentException("values required");
            }

        m_aArgValue = aArgValue;
        }

    /**
     * Construct a VAR_S op for the specified register and arguments.
     *
     * @param reg        the register
     * @param aArgValue  the value argument
     */
    public Var_S(Register reg, Argument[] aArgValue)
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
    public Var_S(DataInput in, Constant[] aconst)
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
        return OP_VAR_S;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, m_anArgValue.length);

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, iPC, ahArg);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, iPC, ahArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, int iPC, ObjectHandle[] ahArg)
        {
        TypeConstant     typeList = frame.resolveType(m_nType);
        ClassComposition clzArray = getArrayClass(frame, typeList);

        ArrayHandle hArray = ((xArray) clzArray.getTemplate()).createArrayHandle(clzArray, ahArg);

        frame.introduceResolvedVar(m_nVar, typeList, null, Frame.VAR_STANDARD, hArray);

        return iPC + 1;
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
