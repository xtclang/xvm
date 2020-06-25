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
 * Base class for property in-place assign op codes (PIP_ADD, PIP_SUB, etc.).
 */
public abstract class OpPropInPlaceAssign
        extends OpProperty
    {
    /**
     * Construct a "property in-place and assign" op for the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argVal     the second Argument
     */
    protected OpPropInPlaceAssign(PropertyConstant idProp, Argument argTarget, Argument argVal)
        {
        super(idProp);

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
            ObjectHandle[] ahArg = frame.getArguments(new int[] {m_nTarget, m_nValue}, 2);

            if (anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    processProperty(frameCaller, ahArg[0], ahArg[1]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }
            return processProperty(frame, ahArg[0], ahArg[1]);
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
        PropertyConstant idProp = (PropertyConstant) frame.getConstant(m_nPropId);

        return complete(frame, hTarget, idProp, hValue);
        }

    /**
     * The completion of processing.
     */
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue)
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

    @Override
    public String toString()
        {
        return super.toString()
                + ", " + Argument.toIdString(m_argTarget, m_nTarget)
                + ", " + Argument.toIdString(m_argValue, m_nValue);
        }



    // ----- data fields ---------------------------------------------------------------------------

    protected int m_nTarget;
    protected int m_nValue;

    private Argument m_argTarget;
    private Argument m_argValue;
    }
