package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.collections.xArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * SVAR TYPE_CONST, #values:(rvalue-src) ; next register is an initialized anonymous Sequence variable
 *
 * @author gg 2017.03.08
 */
public class SVar extends Op
    {
    final private int f_nClassConstId;
    final private int[] f_anArgValue;

    public SVar(int nClassConstId, int[] anValue)
        {
        f_nClassConstId = nClassConstId;
        f_anArgValue = anValue;
        }

    public SVar(DataInput in)
            throws IOException
        {
        f_nClassConstId = in.readInt();
        f_anArgValue = readIntArray(in);
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_SVAR);
        writeIntArray(out, f_anArgValue);
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        TypeComposition clazzEl = context.f_types.ensureComposition(f_nClassConstId);

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(f_anArgValue, f_anArgValue.length);
            if (ahArg == null)
                {
                return R_REPEAT;
                }

            ArrayHandle hArray = xArray.makeHandle(clazzEl.ensurePublicType(), ahArg);
            hArray.makeImmutable();

            frame.introduceVar(hArray.f_clazz, null, Frame.VAR_STANDARD, hArray);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
