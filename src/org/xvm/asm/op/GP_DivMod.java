package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * GP_DIVMOD rvalue1, rvalue2, lvalue1, lvalue2 ; T /% T -> T, T
 */
public class GP_DivMod
        extends Op
    {
    /**
     * Construct a GP_DIVMOD op for the passed arguments.
     *
     * @param argTarget   the target Argument
     * @param argValue    the second value Argument
     * @param aargReturn  the two Arguments to store the results into
     */
    public GP_DivMod(Argument argTarget, Argument argValue, Argument[] aargReturn)
        {
        m_argTarget  = argTarget;
        m_argValue   = argValue;
        m_aargReturn = aargReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_DivMod(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget    = readPackedInt(in);
        m_nArgValue  = readPackedInt(in);
        m_anRetValue = readIntArray(in);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_DIVMOD;
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTarget != null)
            {
            m_nTarget    = encodeArgument(m_argTarget, registry);
            m_nArgValue  = encodeArgument(m_argValue,  registry);
            m_anRetValue = encodeArguments(m_aargReturn, registry);
            }

        out.writeByte(getOpCode());

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nArgValue);
        writeIntArray(out, m_anRetValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg    = frame.getArgument(m_nArgValue);
            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (frame.isNextRegister(m_anRetValue[0]))
                {
                frame.introduceVarCopy(m_anRetValue[0], m_nTarget); // TODO GG review this (type comes from op method)
                }

            if (frame.isNextRegister(m_anRetValue[1]))
                {
                frame.introduceVarCopy(m_anRetValue[1], m_nTarget);
                }

            if (isDeferred(hTarget) || isDeferred(hArg))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hTarget, hArg};
                Frame.Continuation stepNext = frameCaller ->
                        complete(frameCaller, ahValue[0], ahValue[1]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return complete(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        return hTarget.getOpSupport().invokeDivMod(frame, hTarget, hArg, m_anRetValue);
        }

    @Override
    public void simulate(Scope scope)
        {
        if (m_aargReturn != null)
            {
            checkNextRegisters(scope, m_aargReturn);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_argTarget = registerArgument(m_argTarget, registry);
        m_argValue  = registerArgument(m_argValue, registry);
        registerArguments(m_aargReturn, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ' '  + Argument.toIdString(m_argTarget    , m_nTarget)
                + ", " + Argument.toIdString(m_argValue     , m_nArgValue)
                + ", " + Argument.toIdString(m_aargReturn[0], m_anRetValue[0])
                + ", " + Argument.toIdString(m_aargReturn[1], m_anRetValue[1]);
        }

    protected int   m_nTarget;
    protected int   m_nArgValue;
    protected int[] m_anRetValue;

    private Argument   m_argTarget;
    private Argument   m_argValue;
    private Argument[] m_aargReturn;
    }
