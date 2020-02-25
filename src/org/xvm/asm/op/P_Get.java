package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.OpProperty;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * P_GET PROPERTY, rvalue-target, lvalue
 */
public class P_Get
        extends OpProperty
    {
    /**
     * Construct a P_GET op based on the specified arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argReturn  the return Argument
     */
    public P_Get(PropertyConstant idProp, Argument argTarget, Argument argReturn)
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
    public P_Get(DataInput in, Constant[] aconst)
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
        return OP_P_GET;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);

            if (frame.isNextRegister(m_nRetValue))
                {
                if (m_nTarget == A_STACK)
                    {
                    // TODO GG: the actual type needs to be injected by the compiler/verifier
                    TypeConstant typeTarget = hTarget.getType();
                    ConstantPool pool       = frame.poolContext();
                    TypeConstant typeProp   = typeTarget.containsGenericParam(constProperty.getName())
                            ? constProperty.getFormalType().resolveGenerics(pool, typeTarget).getType()
                            : constProperty.getType().resolveGenerics(pool, typeTarget);

                    frame.introduceResolvedVar(m_nRetValue, typeProp);
                    }
                else
                    {
                    frame.introducePropertyVar(m_nRetValue, m_nTarget, constProperty);
                    }
                }

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    ahTarget[0].getTemplate().getPropertyValue(
                        frame, ahTarget[0], constProperty, m_nRetValue);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }
            return hTarget.getTemplate().getPropertyValue(
                    frame, hTarget, constProperty, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
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
