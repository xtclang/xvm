package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * IVAR CONST_CLASS, rvalue-src ; (next register is an initialized anonymous variable)
 */
public class IVar
        extends Op
    {
    final private int f_nClassConstId;
    final private int f_nArgValue;

    public IVar(int nClassConstId, int nValue)
        {
        f_nClassConstId = nClassConstId;
        f_nArgValue = nValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IVar(DataInput in, Constant[] aconst)
            throws IOException
        {
        f_nClassConstId = readPackedInt(in);
        f_nArgValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IVAR);
        writePackedLong(out, f_nClassConstId);
        writePackedLong(out, f_nArgValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_IVAR;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        TypeComposition clazz = frame.f_context.f_types.ensureComposition(
                f_nClassConstId, frame.getActualTypes());

        try
            {
            ObjectHandle hArg = frame.getArgument(f_nArgValue);
            if (hArg == null)
                {
                return R_REPEAT;
                }
            frame.introduceVar(clazz, null, Frame.VAR_STANDARD, hArg);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    }
