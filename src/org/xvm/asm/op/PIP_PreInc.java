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

import org.xvm.runtime.template.types.xProperty.PropertyHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * PIP_INCB PROPERTY, rvalue-target, lvalue ; same as IP_INCB for a register
 */
public class PIP_PreInc
        extends OpProperty
    {
    /**
     * Construct a PIP_INCB op.
     *
     * @param nPropId  the property to increment
     * @param nTarget  the object on which the property exists
     * @param nRet     the location to store the pre-incremented value
     */
    public PIP_PreInc(int nPropId, int nTarget, int nRet)
        {
        super(nPropId);
        m_nTarget      = nTarget;
        m_nRetValue    = nRet;
        }

    /**
     * Construct a PIP_INCB op based on the passed arguments.
     *
     * @param argProperty  the property Argument
     * @param argTarget    the target Argument
     * @param argReturn    the Argument to move the result into (Register or local property)
     */
    public PIP_PreInc(Argument argProperty, Argument argTarget, Argument argReturn)
        {
        super(argProperty);

        m_argTarget = argTarget;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_PreInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(readPackedInt(in));

        m_nTarget   = readPackedInt(in);
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
        return OP_PIP_INCA;
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

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, (PropertyHandle) hTarget, ahTarget[0]);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return complete(frame, null, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, PropertyHandle hProperty, ObjectHandle hTarget)
        {
        PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);

        if (frame.isNextRegister(m_nRetValue))
            {
            if (m_nTarget >= 0)
                {
                frame.introduceVarCopy(m_nTarget);
                }
            else // m_nTarget points to a local property, therefore hProperty is not null
                {
                int nTypeId = hProperty.m_constProperty.getType().getPosition();

                frame.introduceVar(nTypeId, 0, Frame.VAR_STANDARD, null);
                }
            }

        return hTarget.f_clazz.f_template.invokePreInc(
                frame, hTarget, constProperty.getName(), m_nRetValue);
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
        super.registerConstants(registry);

        registerArgument(m_argTarget, registry);
        }

    private int m_nTarget;
    private int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;    }