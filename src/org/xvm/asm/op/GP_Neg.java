package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.Register;
import org.xvm.asm.Scope;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * GP_NEG rvalue, lvalue   ; -T -> T
 */
public class GP_Neg
        extends Op
    {
    /**
     * Construct a GP_NEG op.
     *
     * @param nArg  the r-value target to negate
     * @param nRet  the l-value to store the result in
     *
     * @deprecated
     */
    public GP_Neg(int nArg, int nRet)
        {
        m_nArgValue = nArg;
        m_nRetValue = nRet;
        }

    /**
     * Construct a GP_NEG op for the passed arguments.
     *
     * @param argValue  the Argument to negate
     * @param regResult  the Register to put the result in
     */
    public GP_Neg(Argument argValue, Register regResult)
        {
        if (argValue == null || regResult == null)
            {
            throw new IllegalArgumentException("arguments required");
            }
        m_argValue = argValue;
        m_regResult = regResult;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Neg(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argValue != null)
            {
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nRetValue = encodeArgument(m_regResult, registry);
            }

        out.writeByte(OP_GP_NEG);

        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_NEG;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nArgValue);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> complete(frameCaller, ahTarget[0]);

                return new Utils.GetTarget(ahTarget, stepNext).doNext(frame);
                }

            return complete(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget)
        {
        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceVarCopy(m_nArgValue);
            }
        return hTarget.f_clazz.f_template.invokeNeg(frame, hTarget, m_nRetValue);
        }

    @Override
    public void simulate(Scope scope)
        {
        if (scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argValue, registry);
        }

    private int m_nArgValue;
    private int m_nRetValue;

    private Argument m_argValue;
    private Register m_regResult;

    }
