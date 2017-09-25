package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IS_TYPE  rvalue, rvalue-type, lvalue-return ; T instanceof Type -> Boolean
 */
public class IsType
        extends Op
    {
    /**
     * Construct an IS_TYPE op.
     *
     * @param nValue  the value to test
     * @param nType   the type to test for
     * @param nRet    the location to store the Boolean result
     */
    public IsType(int nValue, int nType, int nRet)
        {
        f_nValue    = nValue;
        f_nType     = nType;
        f_nRetValue = nRet;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IsType(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nValue    = readPackedInt(in);
        f_nType     = readPackedInt(in);
        f_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.writeByte(OP_IS_TYPE);
        writePackedLong(out, f_nValue);
        writePackedLong(out, f_nType);
        writePackedLong(out, f_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IS_TYPE;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(f_nValue);
            ObjectHandle hValue2 = frame.getArgument(f_nType);
            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            // TODO
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    private final int f_nValue;
    private final int f_nType;
    private final int f_nRetValue;
    }
