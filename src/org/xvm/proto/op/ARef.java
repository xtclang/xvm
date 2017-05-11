package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;

import org.xvm.proto.template.xRef;

/**
 * A_REF rvalue-target, rvalue-index, lvalue-return ; Ref<T> = &T[Ti]
 *
 * @author gg 2017.03.08
 */
public class ARef extends Op
    {
    private final int f_nTargetValue;
    private final int f_nIndexValue;
    private final int f_nRetValue;

    public ARef(int nTarget, int nIndex, int nRet)
        {
        f_nTargetValue = nTarget;
        f_nIndexValue = nIndex;
        f_nRetValue = nRet;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ExceptionHandle hException;

        try
            {
            ArrayHandle hArray = (ArrayHandle) frame.getArgument(f_nTargetValue);
            Type typeReferent = hArray.f_clazz.f_atGenericActual[0];

            TypeComposition clzRef = xRef.INSTANCE.resolve(new Type[]{typeReferent});

            xRef.ArrayRefHandle hRef = new xRef.ArrayRefHandle(clzRef,
                    hArray, frame.getIndex(f_nIndexValue));

            hException = frame.assignValue(f_nRetValue, hRef);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            hException = e.getExceptionHandle();
            }

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
