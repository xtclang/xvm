package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for PIP_ (property in-place) op codes.
 *
 * Note: "property in-place assign" ops derive from {@link OpPropInPlaceAssign}.
 */
public abstract class OpPropInPlace
        extends OpProperty
    {
    /**
     * Construct a "property in-place" op for the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     */
    protected OpPropInPlace(PropertyConstant constProperty, Argument argTarget)
        {
        super(constProperty);

        assert(!isAssignOp());

        m_argTarget = argTarget;
        }

    /**
     * Construct a "property in-place and assign" op for the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     * @param argReturn      the Argument to store the result into
     */
    protected OpPropInPlace(PropertyConstant constProperty, Argument argTarget, Argument argReturn)
        {
        super(constProperty);

        assert(isAssignOp());

        m_argTarget = argTarget;
        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpPropInPlace(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nTarget = readPackedInt(in);
        if (isAssignOp())
            {
            m_nRetValue = readPackedInt(in);
            }
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            if (isAssignOp())
                {
                m_nRetValue = encodeArgument(m_argReturn,  registry);
                }
            }

        writePackedLong(out, m_nTarget);
        if (isAssignOp())
            {
            writePackedLong(out, m_nRetValue);
            }
        }

    /**
     * A "virtual constant" indicating whether or not this op is an assigning one.
     *
     * @return true iff the op is an assigning one
     */
    protected boolean isAssignOp()
        {
        // majority of the ops are assigning; let's default to that
        return true;
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

            if (isAssignOp() && frame.isNextRegister(m_nRetValue))
                {
                frame.introduceVarCopy(m_nPropId);
                }

            if (isProperty(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller ->
                    processProperty(frameCaller, ahTarget[0]);

                return new Utils.GetArgument(ahTarget, stepNext).doNext(frame);
                }

            return processProperty(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    /**
     * Continuation of the processing.
     */
    protected int processProperty(Frame frame, ObjectHandle hTarget)
        {
        PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);

        return complete(frame, hTarget, constProperty.getName());
        }

    /**
     * A completion of the processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, String sPropName)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void simulate(Scope scope)
        {
        if (isAssignOp())
            {
            checkNextRegister(scope, m_argReturn);
            }

        // TODO: remove when deprecated construction is removed
        if (isAssignOp() && scope.isNextRegister(m_nRetValue))
            {
            scope.allocVar();
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argTarget, registry);
        if (isAssignOp())
            {
            registerArgument(m_argReturn, registry);
            }
        }

    protected int m_nTarget;
    protected int m_nRetValue;

    private Argument m_argTarget;
    private Argument m_argReturn;
    }
