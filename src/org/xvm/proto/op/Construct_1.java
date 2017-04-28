package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;
import org.xvm.proto.template.xFunction.FullyBoundHandle;

/**
 * CONSTR_1 CONST-CONSTRUCT, rvalue
 *
 * @author gg 2017.03.08
 */
public class Construct_1 extends OpCallable
    {
    private final int f_nConstructId;
    private final int f_nArgValue;

    public Construct_1(int nConstructorId, int anArg)
        {
        f_nConstructId = nConstructorId;
        f_nArgValue = anArg;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;
        FullyBoundHandle hfnFinally = null;

        try
            {
            ConstructTemplate constructor = (ConstructTemplate) getFunctionTemplate(frame, f_nConstructId);

            ObjectHandle[] ahVar = new ObjectHandle[constructor.getVarCount()];
            ahVar[0] = frame.getThis();
            ahVar[1] = frame.getArgument(f_nArgValue);

            hfnFinally = constructor.makeFinalizer(ahVar);

            hException = frame.call1(constructor, null, ahVar, -1);
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