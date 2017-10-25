package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Base class for I_ (index based) and IIP_ (index based in-place) op codes.
 */
public abstract class OpIndexInPlace
        extends OpIndex
    {
    /**
     * Construct an "index based" op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    protected OpIndexInPlace(Argument argTarget, Argument argIndex, Argument argValue)
        {
        super(argTarget, argIndex);

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpIndexInPlace(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nTarget);
        }

    @Override
    protected boolean isAssignOp()
        {
        return false;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hIndex  = frame.getArgument(m_nIndex);
            ObjectHandle hValue  = frame.getArgument(m_nValue);
            if (hTarget == null || hIndex == null || hValue == null)
                {
                return R_REPEAT;
                }

            if (isProperty(hTarget) || isProperty(hIndex) || isProperty(hValue))
                {
                ObjectHandle[] ahArg = new ObjectHandle[] {hTarget, hIndex, hValue};
                Frame.Continuation stepNext = frameCaller ->
                    complete(frameCaller, ahArg[0], (JavaLong) ahArg[1], ahArg[2]);

                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            return complete(frame, hTarget, (JavaLong) hIndex, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex, ObjectHandle hValue)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        }

    protected int m_nValue;

    private Argument m_argValue;
    }
