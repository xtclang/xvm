package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.ComparisonSupport;
import org.xvm.proto.template.xBoolean;

import java.util.concurrent.CompletableFuture;

/**
 * IS_GT rvalue, rvalue, lvalue-return ; T > T -> Boolean
 *
 * @author gg 2017.03.08
 */
public class IsGt extends Op
    {
    private final int f_nValue1;
    private final int f_nValue2;
    private final int f_nRetValue;

    public IsGt(int nValue1, int nValue2, int nRet)
        {
        f_nValue1 = nValue1;
        f_nValue2 = nValue2;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue1 = frame.getArgument(f_nValue1);
            ObjectHandle hValue2 = frame.getArgument(f_nValue2);

            if (hValue1 == null || hValue2 == null)
                {
                return R_REPEAT;
                }

            TypeComposition clz = frame.getArgumentClass(f_nValue1);
            assert (clz == frame.getArgumentClass(f_nValue2));

            ComparisonSupport template = (ComparisonSupport) clz.f_template;

            frame.assignValue(f_nRetValue,
                    template.compare(hValue1, hValue2) > 0 ?
                            xBoolean.TRUE : xBoolean.FALSE);
            return iPC + 1;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
