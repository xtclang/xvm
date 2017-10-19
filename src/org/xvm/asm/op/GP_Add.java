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
 * GP_ADD rvalue1, rvalue2, lvalue ; T + T -> T
 */
public class GP_Add
        extends Op
    {
    /**
     * Construct a GP_ADD op.
     *
     * @param nTarget  the first r-value, which will implement the add
     * @param nArg     the second r-value
     * @param nRet     the l-value to store the result into
     *
     * @deprecated
     */
    public GP_Add(int nTarget, int nArg, int nRet)
        {
        m_nTarget   = nTarget;
        m_nArgValue = nArg;
        m_nRetValue = nRet;
        }

    /**
     * Construct a GP_ADD op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param regReturn  the Register to move the result into
     */
    public GP_Add(Argument argTarget, Argument argValue, Register regReturn)
        {
        if (argTarget == null || argValue == null || regReturn == null)
            {
            throw new IllegalArgumentException("arguments required");
            }

        m_argTarget = argTarget;
        m_argValue  = argValue;
        m_regReturn = regReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Add(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nTarget   = readPackedInt(in);
        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        if (m_argTarget != null)
            {
            m_nTarget   = encodeArgument(m_argTarget, registry);
            m_nArgValue = encodeArgument(m_argValue,  registry);
            m_nRetValue = encodeArgument(m_regReturn, registry);
            }

        out.writeByte(OP_GP_ADD);
        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_GP_ADD;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg = frame.getArgument(m_nArgValue);

            if (hTarget == null || hArg == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    resolveArg(frameCaller, ahTarget[0], hArg);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return resolveArg(frame, hTarget, hArg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int resolveArg(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        if (isProperty(hArg))
            {
            ObjectHandle[] ahArg = new ObjectHandle[] {hArg};
            Frame.Continuation stepNext = frameCaller -> complete(frame, hTarget, ahArg[0]);

            return new Utils.GetArgument(ahArg, stepNext).doNext(frame);
            }
        return complete(frame, hTarget, hArg);
        }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg)
        {
        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceVarCopy(m_nTarget);
            }
        return hTarget.f_clazz.f_template.invokeAdd(frame, hTarget, hArg, m_nRetValue);
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
        registerArgument(m_argTarget, registry);
        registerArgument(m_argValue, registry);
        }

    private int m_nTarget;
    private int m_nArgValue;
    private int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argValue;
    private Register m_regReturn;
    }
