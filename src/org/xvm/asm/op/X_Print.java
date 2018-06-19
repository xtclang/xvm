package org.xvm.asm.op;


import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;
import org.xvm.asm.OpInvocable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.types.xProperty.DeferredPropertyHandle;
import org.xvm.runtime.template.xString;

/**
 * Debugging only.
 */
public class X_Print
        extends OpInvocable
    {
    public X_Print(int nValue)
        {
        super((Argument) null, null);

        m_nValue = nValue;
        }

    @Override
    public int getOpCode()
        {
        return OP_X_PRINT;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int nValue = m_nValue;

        StringBuilder sb = new StringBuilder();

        try
            {
            if (nValue >= 0)
                {
                Frame.VarInfo info = frame.getVarInfo(nValue);

                if (info.isDynamic())
                    {
                    sb.append("<dynamic> ");
                    }

                sb.append(info.getName()).append("=");
                }

            ObjectHandle hValue = frame.getArgument(nValue);
            if (hValue == null)
                {
                sb.append("<waiting>...");
                Utils.log(frame, sb.toString());
                return R_REPEAT;
                }

            if (hValue instanceof DeferredPropertyHandle)
                {
                DeferredPropertyHandle hProp = (DeferredPropertyHandle) hValue;
                sb.append("Local property: ").append(hProp.m_property.getName());
                return R_NEXT;
                }

            // call the "to<String>()" method for the object to get the value
            ConstantPool pool = frame.f_context.f_pool;
            CallChain chain = hValue.getComposition().getMethodCallChain(pool.sigToString());

            int iResult;
            if (chain.isNative())
                {
                iResult = hValue.getTemplate().invokeNativeN(frame, chain.getTop(), hValue,
                        Utils.OBJECTS_NONE, A_LOCAL);
                if (iResult == R_NEXT)
                    {
                    sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue());
                    }
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

                iResult = hValue.getTemplate().invoke1(frame, chain, hValue, ahVar, A_LOCAL);
                }

            if (iResult == R_CALL)
                {
                frame.m_frameNext.setContinuation(frameCaller->
                    {
                    sb.append(((xString.StringHandle) frameCaller.getFrameLocal()).getValue());
                    Utils.log(frame, sb.toString());
                    return Op.R_NEXT;
                    });
                return R_CALL;
                }
            }
        catch (ObjectHandle.ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }

        Utils.log(frame, sb.toString());

        return iPC + 1;
        }

    private int m_nValue;
    }