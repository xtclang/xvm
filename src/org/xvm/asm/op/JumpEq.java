package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean.BooleanHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * JMP_EQ rvalue, rvalue, rel-addr ; jump if value is equal
 */
public class JumpEq
        extends Op
    {
    /**
     * Construct a JMP_EQ op.
     *
     * @param nValue1   the first value to compare
     * @param nValue2   the second value to compare
     * @param nRelAddr  the relative address to jump to.
     */
    public JumpEq(int nValue1, int nValue2, int nRelAddr)
        {
        f_nValue1  = nValue1;
        f_nValue2  = nValue2;
        f_nRelAddr = nRelAddr;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpEq(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue1  = readPackedInt(in);
        f_nValue2  = readPackedInt(in);
        f_nRelAddr = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_JMP_EQ);
        writePackedLong(out, f_nValue1);
        writePackedLong(out, f_nValue2);
        writePackedLong(out, f_nRelAddr);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_EQ;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTest1 = frame.getArgument(f_nValue1);
            ObjectHandle hTest2 = frame.getArgument(f_nValue2);
            if (hTest1 == null || hTest2 == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz1 = frame.getArgumentClass(f_nValue1);
            TypeComposition clz2 = frame.getArgumentClass(f_nValue2);
            if (clz1 != clz2)
                {
                // this shouldn't have compiled
                throw new IllegalStateException();
                }

            switch (clz1.callEquals(frame, hTest1, hTest2, Frame.RET_LOCAL))
                {
                case R_EXCEPTION:
                    return R_EXCEPTION;

                case R_NEXT:
                    {
                    BooleanHandle hResult = (BooleanHandle) frame.getFrameLocal();
                    return hResult.get() ? iPC + f_nRelAddr : iPC + 1;
                    }

                case R_CALL:
                    frame.m_frameNext.setContinuation(frameCaller ->
                        {
                        BooleanHandle hResult = (BooleanHandle) frame.getFrameLocal();
                        return hResult.get() ? iPC + f_nRelAddr : iPC + 1;
                        });
                    return R_CALL;

                default:
                    throw new IllegalStateException();
                }
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nValue1;
    private final int f_nValue2;
    private final int f_nRelAddr;
    }
