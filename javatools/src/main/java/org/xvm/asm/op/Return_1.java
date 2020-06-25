package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpReturn;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * RETURN_1 rvalue
 */
public class Return_1
        extends OpReturn
    {
    /**
     * Construct a RETURN_1 op.
     *
     * @param arg  the value to return
     */
    public Return_1(Argument arg)
        {
        m_arg = arg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Return_1(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nArg = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_arg != null)
            {
            m_nArg = encodeArgument(m_arg, registry);
            }

        writePackedLong(out, m_nArg);
        }

    @Override
    public int getOpCode()
        {
        return OP_RETURN_1;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hArg = frame.getReturnValue(m_nArg);

            return isDeferred(hArg)
                    ? hArg.proceed(frame, frameCaller ->
                        complete(frameCaller, frameCaller.popStack(), false))
                    : complete(frame, hArg, frame.isDynamicVar(m_nArg));
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int complete(Frame frame, ObjectHandle hValue, boolean fDynamic)
        {
        return m_fCallFinally
            ? frame.processAllGuard(new Return1Action(hValue, fDynamic, m_ixAllGuard))
            : frame.returnValue(hValue, fDynamic);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        m_arg = registerArgument(m_arg, registry);
        }

    @Override
    public String toString()
        {
        return super.toString() + ' ' + Argument.toIdString(m_arg, m_nArg);
        }

    protected static class Return1Action
            extends Frame.DeferredGuardAction
        {
        public Return1Action(ObjectHandle hValue, boolean fDynamic, int ixAllGuard)
            {
            super(ixAllGuard);

            this.m_hValue   = hValue;
            this.m_fDynamic = fDynamic;
            }

        @Override
        public int complete(Frame frame)
            {
            return frame.returnValue(m_hValue, m_fDynamic);
            }

        private final ObjectHandle m_hValue;
        private final boolean      m_fDynamic;
        }

    private int      m_nArg;
    private Argument m_arg;
    }
