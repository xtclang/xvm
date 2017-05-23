package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;

/**
 * CONSTR_N CONST-CONSTRUCT, #params:(rvalue)
 *
 * @author gg 2017.03.08
 */
public class Construct_N extends OpCallable
    {
    private final int f_nConstructId;
    private final int[] f_anArgValue;

    public Construct_N(int nConstructorId, int[] anArg)
        {
        f_nConstructId = nConstructorId;
        f_anArgValue = anArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ConstructTemplate constructor = (ConstructTemplate) getFunctionTemplate(frame, f_nConstructId);

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, constructor.getVarCount(), 1);
            if (ahVar == null)
                {
                return R_WAIT;
                }

            frame.chainFinalizer(constructor.makeFinalizer(ahVar));

            return frame.call1(constructor, null, ahVar, Frame.R_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }