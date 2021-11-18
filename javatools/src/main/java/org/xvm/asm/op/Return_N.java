package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpReturn;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;


/**
 * RETURN_N #vals:(rvalue)
 */
public class Return_N
        extends OpReturn
    {
    /**
     * Construct a RETURN_N op.
     *
     * @param aArg  the arguments to return
     */
    public Return_N(Argument[] aArg)
        {
        m_aArg = aArg;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Return_N(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_anArg = readIntArray(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_aArg != null)
            {
            Argument[] aArg = m_aArg;
            int        cArgs = aArg.length;
            int[]      anArg = new int[cArgs];
            for (int i = 0; i < cArgs; ++i)
                {
                anArg[i] = encodeArgument(aArg[i], registry);
                }
            m_anArg = anArg;
            }

        writeIntArray(out, m_anArg);
        }

    @Override
    public int getOpCode()
        {
        return OP_RETURN_N;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int            cArgs = m_anArg.length;
        ObjectHandle[] ahArg = new ObjectHandle[cArgs];
        boolean[]  afDynamic = null;
        boolean    fAnyProp  = false;

        // retrieve in the reverse order to allow ReturnStatement to use stack collecting the arguments
        for (int i = cArgs - 1; i >= 0; --i)
            {
            int          nArg = m_anArg[i];
            ObjectHandle hArg;
            try
                {
                hArg = frame.getReturnValue(nArg);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }

            ahArg[i] = hArg;

            if (isDeferred(hArg))
                {
                fAnyProp = true;
                }
            else if (frame.isDynamicVar(nArg))
                {
                if (afDynamic == null)
                    {
                    afDynamic = new boolean[cArgs];
                    }
                afDynamic[i] = true;
                }
            }

        if (fAnyProp)
            {
            final boolean[] af = afDynamic;
            Frame.Continuation stepNext = frameCaller -> complete(frameCaller, ahArg, af);

            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

        return complete(frame, ahArg, afDynamic);
        }

    protected int complete(Frame frame, ObjectHandle[] ahValue, boolean[] afDynamic)
        {
        return m_fCallFinally
            ? frame.processAllGuard(new ReturnNAction(ahValue, afDynamic, m_ixAllGuard))
            : frame.returnValues(ahValue, afDynamic);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArguments(m_aArg, registry);
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append(" (");

        int cArgs = m_anArg == null ? m_aArg.length : m_anArg.length;
        for (int i = 0; i < cArgs; i++)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(Argument.toIdString(m_aArg  == null ? null             : m_aArg [i],
                                          m_anArg == null ? Register.UNKNOWN : m_anArg[i]));
            }

        sb.append(')');
        return sb.toString();
        }

    protected static class ReturnNAction
            extends Frame.DeferredGuardAction
        {
        public ReturnNAction(ObjectHandle[] ahValue, boolean[] afDynamic, int ixAllGuard)
            {
            super(ixAllGuard);

            this.m_ahValue   = ahValue;
            this.m_afDynamic = afDynamic;
            }

        @Override
        public int complete(Frame frame)
            {
            return frame.returnValues(m_ahValue, m_afDynamic);
            }

        private final ObjectHandle[] m_ahValue;
        private final boolean[] m_afDynamic;
        }

    private int[]      m_anArg;
    private Argument[] m_aArg;
    }
