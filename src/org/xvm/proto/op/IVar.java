package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * IVAR CONST_CLASS, rvalue-src ; (next register is an initialized anonymous variable)
 *
 * @author gg 2017.03.08
 */
public class IVar extends Op
    {
    final private int f_nClassConstId;
    final private int f_nArgValue;

    public IVar(int nClassConstId, int nValue)
        {
        f_nClassConstId = nClassConstId;
        f_nArgValue = nValue;
        }

    public IVar(DataInput in)
            throws IOException
        {
        f_nClassConstId = in.readInt();
        f_nArgValue = in.readInt();
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_IVAR);
        out.writeInt(f_nClassConstId);
        out.writeInt(f_nArgValue);
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
