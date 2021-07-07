package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * P_VAR PROPERTY, rvalue-target, lvalue ; move Var-to-property to destination
 */
public class P_Var
        extends OpProperty
    {
    /**
     * Construct a P_VAR op based on the specified arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argReturn  the return Argument
     */
    public P_Var(PropertyConstant idProp, Argument argTarget, Argument argReturn)
        {
        super(idProp);

        m_argTarget = argTarget;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public P_Var(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nTarget = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_P_VAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                        complete(frameCaller, frameCaller.popStack()))
                    : complete(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget)
        {
        PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nRetValue, constProperty.getRefType(null));
            }

        return hTarget.getTemplate().
            createPropertyRef(frame, hTarget, constProperty, false, m_nRetValue);
        }

    @Override
    public void resetSimulation()
        {
        resetRegister(m_argReturn);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argReturn, m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argTarget = registerArgument(m_argTarget, registry);
        m_argReturn = registerArgument(m_argReturn, registry);
        }

    @Override
    public String toString()
        {
        return super.toString()
                + ", " + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argReturn, m_nRetValue);
        }

    private int m_nTarget;
    private int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;
    }
