package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;


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
     * Construct a RETURN_N op.
     *
     * @param anValue  the values to return
     *
     * @deprecated
     */
    public Return_N(int[] anValue)
        {
        m_anArg = anValue;
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
        int iRet = frame.f_iReturn;
        if (iRet >= 0 || iRet == Frame.RET_LOCAL)
            {
            throw new IllegalStateException(); // assertion
            }

        switch (iRet)
            {
            case Frame.RET_UNUSED:
                break;

            case Frame.RET_MULTI:
                int[] aiRet = frame.f_aiReturn;

                // it's possible that the caller doesn't care about some of the return values
                boolean fBlock = false;
                for (int i = 0, c = aiRet.length; i < c; i++)
                    {
                    int iResult = frame.returnValue(aiRet[i], m_anArg[i]);
                    switch (iResult)
                        {
                        case Op.R_RETURN_EXCEPTION:
                            return Op.R_RETURN_EXCEPTION;

                        case Op.R_BLOCK_RETURN:
                            fBlock = true;
                            break;

                        case Op.R_RETURN:
                            continue;

                        default:
                            throw new IllegalStateException();
                        }
                    }

                if (fBlock)
                    {
                    return R_BLOCK_RETURN;
                    }
                break;

            default:
                // the caller needs a tuple
                return frame.returnTuple(-iRet - 1, m_anArg);
            }
        return R_RETURN;
        }

    private Argument[] m_aArg;
    private int[]      m_anArg;
    }
