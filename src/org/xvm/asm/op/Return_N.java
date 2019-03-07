package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;


/**
 * RETURN_N #vals:(rvalue)
 */
public class Return_N
        extends Op
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

        out.writeByte(OP_RETURN_N);
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

        for (int i = 0; i < cArgs; i++)
            {
            int          nArg = m_anArg[i];
            ObjectHandle hArg = frame.getReturnValue(nArg);

            ahArg[i] = hArg;

            if (isDeferred(hArg))
                {
                fAnyProp = true;
                }
            else if (frame.isDynamicVar(nArg))
                {
                if (afDynamic != null)
                    {
                    afDynamic = new boolean[cArgs];
                    }
                afDynamic[i] = true;
                }
            }

        if (fAnyProp)
            {
            final boolean[] af = afDynamic;
            Frame.Continuation stepNext = frameCaller -> frameCaller.returnValues(ahArg, af);

            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }

        return frame.returnValues(ahArg, afDynamic);
        }

    @Override
    public boolean advances()
        {
        return false;
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArguments(m_aArg, registry);
        }

    @Override
    public String toString()
        {
        StringBuffer sb = new StringBuffer();

        sb.append(super.toString())
          .append(" (");

        int cArgs = m_anArg.length;
        for (int i = 0; i < cArgs; i++)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(Argument.toIdString(m_aArg[i], m_anArg[i]));
            }

        sb.append(')');
        return sb.toString();
        }

    private int[] m_anArg;

    private Argument[] m_aArg;
    }
