package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ServiceContext;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xArray;

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

    @Override
    public int process(Frame frame, int iPC)
        {
        ServiceContext context = frame.f_context;

        TypeComposition clazz = context.f_types.ensureComposition(frame, f_nClassConstId);
        int[] anArg = f_anArgValue;
        int cArgs = anArg.length;

        try
            {
            ObjectHandle[] ahArg = new ObjectHandle[cArgs];

            for (int i = 0; i < cArgs; i++)
                {
                ObjectHandle hArg = frame.getArgument(anArg[i]);
                if (hArg == null)
                    {
                    return R_REPEAT;
                    }

                ahArg[i] = hArg;
                }

            ArrayHandle hArray = xArray.makeHandle(clazz.ensurePublicType(), ahArg);
            hArray.makeImmutable();

            frame.introduceVar(hArray.f_clazz, null, Frame.VAR_STANDARD, hArray);

            return iPC + 1;
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }

    }
