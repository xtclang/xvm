package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.collections.xTuple;
import org.xvm.proto.template.collections.xTuple.TupleHandle;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * TVAR #values:(TYPE_CONST, rvalue-src) ; next register is an initialized anonymous Tuple variable
 *
 * @author gg 2017.03.08
 */
public class TVar extends Op
    {
    final private int[] f_anClassConstId;
    final private int[] f_anArgValue;

    public TVar(int[] anClassConstId, int[] anValue)
        {
        f_anClassConstId = anClassConstId;
        f_anArgValue = anValue;
        }

    public TVar(DataInput in)
            throws IOException
        {
        int c = in.readUnsignedByte();

        f_anClassConstId = new int[c];
        f_anArgValue = new int[c];
        for (int i = 0; i < c; i++)
            {
            f_anClassConstId[i] = in.readInt();
            f_anArgValue[i] = in.readInt();
            }
        }

    @Override
    public void write(DataOutput out)
            throws IOException
        {
        out.write(OP_TVAR);

        int c = f_anArgValue.length;
        out.write(c);
        for (int i = 0; i < c; i++)
            {
            out.writeInt(f_anClassConstId[i]);
            out.writeInt(f_anArgValue[i]);
            }
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        int[] anClassId = f_anClassConstId;

        int cArgs = anClassId.length;
        assert cArgs == f_anArgValue.length;

        try
            {
            ObjectHandle[] ahArg = frame.getArguments(f_anArgValue, cArgs);
            if (ahArg == null)
                {
                return R_REPEAT;
                }

            Type[] aType = new Type[cArgs];
            for (int i = 0; i < cArgs; i++)
                {
                TypeComposition clazz = context.f_types.ensureComposition(anClassId[i]);

                aType[i] = clazz.ensurePublicType();
                }

            TupleHandle hTuple = xTuple.makeHandle(aType, ahArg);

            frame.introduceVar(hTuple.f_clazz, null, Frame.VAR_STANDARD, hTuple);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }

    }
