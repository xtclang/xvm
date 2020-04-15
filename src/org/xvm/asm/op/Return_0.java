package org.xvm.asm.op;


import java.io.DataInput;

import org.xvm.asm.Constant;
import org.xvm.asm.OpReturn;

import org.xvm.runtime.Frame;


/**
 * RETURN_0 ; (no return value)
 */
public class Return_0
        extends OpReturn
    {
    /**
     * Construct a RETURN_0 op.
     */
    public Return_0()
        {
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Return_0(DataInput in, Constant[] aconst)
        {
        }

    @Override
    public int getOpCode()
        {
        return OP_RETURN_0;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        return m_fCallFinally
            ? frame.processAllGuard(new Return0Action(m_ixAllGuard))
            : frame.returnVoid();
        }

    protected static class Return0Action
            extends Frame.DeferredGuardAction
        {
        protected Return0Action(int ixAllGuard)
            {
            super(ixAllGuard);
            }

        @Override
        public int complete(Frame frame)
            {
            return frame.returnVoid();
            }
        }

    public static final Return_0 INSTANCE = new Return_0();
    }