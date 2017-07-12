package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xTuple;
import org.xvm.proto.template.xTuple.TupleHandle;

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

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        int[] anClassId = f_anClassConstId;
        int[] anArg = f_anArgValue;

        int cArgs = anClassId.length;
        assert cArgs == anArg.length;

        try
            {
            Type[] aType = new Type[cArgs];
            ObjectHandle[] ahArg = new ObjectHandle[cArgs];

            for (int i = 0; i < cArgs; i++)
                {
                ObjectHandle hArg = frame.getArgument(anArg[i]);
                if (hArg == null)
                    {
                    return R_REPEAT;
                    }
                TypeComposition clazz = context.f_types.ensureComposition(anClassId[i]);

                aType[i] = clazz.ensurePublicType();
                ahArg[i] = hArg;
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
