package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.PropertyConstant;

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
     * Construct a P_GET op.
     *
     * @param nPropId  the property to get
     * @param nTarget  the target object
     * @param nRet     the location to store the result
     *
     * @deprecated
     */
    public P_Get(int nPropId, int nTarget, int nRet)
        {
        super(null);

        m_nPropId = nPropId;
        m_nTarget = nTarget;
        m_nRetValue = nRet;
        }

    /**
     * Construct a P_GET op based on the specified arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     * @param argReturn      the return Argument
     */
    public P_Get(PropertyConstant constProperty, Argument argTarget,  Argument argReturn)
        {
        super(constProperty);

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
            String sProperty = constProperty.getName();

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVar(constProperty.getType());
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    hTarget.f_clazz.f_template.getPropertyValue(
                        frame, ahTarget[0], sProperty, m_nRetValue);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }
            return hTarget.f_clazz.f_template.getPropertyValue(
                frame, hTarget, sProperty, m_nRetValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argReturn);

        // TODO: remove when deprecated construction is removed
        if (scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argTarget, registry);
        registerArgument(m_argReturn, registry);
        }

    private int m_nTarget;
    private int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;
    }
