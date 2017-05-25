package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.OpCallable;
import org.xvm.proto.TypeCompositionTemplate.ConstructTemplate;

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
        try
            {
            ConstructTemplate constructor = (ConstructTemplate) getFunctionTemplate(frame, f_nConstructId);

            ObjectHandle[] ahVar = frame.getArguments(new int[]{0, f_nArgValue}, constructor.getVarCount(), 0);
            if (ahVar == null)
                {
                return R_REPEAT;
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