package org.xvm.asm;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for property in-place assign op codes (PIP_ADD, PIP_SUB, etc.).
 */
public abstract class OpPropInPlaceAssign
        extends OpProperty
    {
    /**
     * Construct a "property in-place and assign" op for the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     * @param argVal         the second Argument
     */
    protected OpPropInPlaceAssign(PropertyConstant constProperty, Argument argTarget, Argument argVal)
        {
        super(constProperty);

        m_argTarget = argTarget;
        m_argValue = argVal;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpPropInPlaceAssign(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nTarget = readPackedInt(in);
        m_nValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argTarget != null)
            {
            m_nTarget = encodeArgument(m_argTarget, registry);
            m_nValue = encodeArgument(m_argValue,  registry);
            }

        writePackedLong(out, m_nTarget);
        writePackedLong(out, m_nValue);
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

            ObjectHandle hValue = frame.getArgument(m_nValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hTarget) || isDeferred(hValue))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hTarget, hValue};
                Frame.Continuation stepNext = frameCaller ->
                    processProperty(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }
            return processProperty(frame, hTarget, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    /**
     * Continuation of the processing with resolved arguments.
     */
    protected int processProperty(Frame frame, ObjectHandle hTarget, ObjectHandle hValue)
        {
        PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);
        String sPropName = constProperty.getName();

        return complete(frame, hTarget, sPropName, hValue);
        }

    /**
     * The completion of processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argTarget = registerArgument(m_argTarget, registry);
        m_argValue = registerArgument(m_argValue, registry);
        }

    // the lambda for the binary actions
    protected interface Action
        {
        int action(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn);
        }

    /**
     * Helper class for in-place property mutations.
     */
    protected static class InPlace
            implements Frame.Continuation
        {
        private final ClassTemplate template;
        private final ObjectHandle hTarget;
        private final ObjectHandle hValue;
        private final String sPropName;
        private final Action action;

        private ObjectHandle hValueOld;
        private ObjectHandle hValueNew;
        private int ixStep = -1;

        public InPlace(ClassTemplate template, ObjectHandle hTarget,
                       ObjectHandle hValue, String sPropName, Action action)
            {
            this.template = template;
            this.hTarget = hTarget;
            this.hValue = hValue;
            this.sPropName = sPropName;
            this.action = action;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            switch (ixStep)
                {
                case 0: // getProperty
                    hValueOld = frameCaller.popStack();
                    break;

                case 1: // the action
                    hValueNew = frameCaller.popStack();
                    break;

                default:
                    throw new IllegalStateException();
                }
            }

        public int doNext(Frame frameCaller)
            {
            while (true)
                {
                int nStep = ++ixStep;

                int iResult;
                switch (nStep)
                    {
                    case 0: // get
                        iResult = template.getPropertyValue(frameCaller, hTarget, sPropName, A_STACK);
                        break;

                    case 1:
                        iResult = action.action(frameCaller, hValueOld, hValue, A_STACK);
                        break;

                    case 2:
                        return template.setPropertyValue(frameCaller, hTarget, sPropName, hValueNew);

                    default:
                        throw new IllegalStateException();
                    }


                switch (iResult)
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalArgumentException();
                    }
                }
            }
        }


    // ----- data fields ---------------------------------------------------------------------------

    protected int m_nTarget;
    protected int m_nValue;

    private Argument m_argTarget;
    private Argument m_argValue;
    }
