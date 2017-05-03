package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;
import org.xvm.proto.template.xFunction.FullyBoundHandle;

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
        ExceptionHandle hException;
        FullyBoundHandle hfnFinally = null;

        try
            {
            ConstructTemplate constructor = (ConstructTemplate) getFunctionTemplate(frame, f_nConstructId);

            ObjectHandle[] ahVar = frame.getArguments(f_anArgValue, constructor.getVarCount(), 1);
            ahVar[0] = frame.getArgument(0);

            hfnFinally = constructor.makeFinalizer(ahVar);

            hException = frame.call1(constructor, null, ahVar, Frame.R_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

        frame.f_framePrev.m_hfnFinally =
                FullyBoundHandle.resolveFinalizer(frame.m_hfnFinally, hfnFinally);

        if (hException == null)
            {
            return iPC + 1;
            }
        else
            {
            frame.m_hException = hException;
            return RETURN_EXCEPTION;
            }
        }
    }