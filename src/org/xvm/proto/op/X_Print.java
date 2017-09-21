package org.xvm.proto.op;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.StringConstant;

import org.xvm.proto.CallChain;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.OpInvocable;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.Utils;

import org.xvm.proto.template.xString;

/**
 * Debugging only.
 *
 * @author gg 2017.03.08
 */
public class X_Print extends OpInvocable
    {
    private final int f_nValue;

    public X_Print(int nValue)
        {
        f_nValue = nValue;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        int nValue = f_nValue;

        StringBuilder sb = new StringBuilder();

        if (nValue >= 0)
            {
            try
                {
                Frame.VarInfo info = frame.getVarInfo(nValue);

                switch (info.m_nStyle)
                    {
                    case Frame.VAR_DYNAMIC_REF:
                        sb.append("<dynamic> ");
                        break;

                    case Frame.VAR_WAITING:
                        // must not happen
                        throw new IllegalStateException();
                    }

                if (info.f_sVarName != null)
                    {
                    sb.append(info.f_sVarName).append("=");
                    }

                ObjectHandle hValue = frame.getArgument(nValue);
                if (hValue == null)
                    {
                    sb.append("<waiting>...");
                    Utils.log(sb.toString());
                    return R_REPEAT;
                    }

                // call the "to<String>()" method for the object to get the value
                TypeComposition clz = hValue.f_clazz;
                CallChain chain = getCallChain(frame, clz,
                        frame.f_adapter.getMethodConstId("Object", "to",
                                ClassTemplate.VOID, ClassTemplate.STRING));

                int iResult;
                if (chain.isNative())
                    {
                    iResult = clz.f_template.invokeNativeN(frame, chain.getTop(), hValue,
                            Utils.OBJECTS_NONE, Frame.RET_LOCAL);
                    if (iResult == R_NEXT)
                        {
                        sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue());
                        }
                    }
                else
                    {
                    ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

                    iResult = clz.f_template.invoke1(frame, chain, hValue, ahVar, Frame.RET_LOCAL);
                    }

                if (iResult == R_CALL)
                    {
                    frame.m_frameNext.setContinuation(frameCaller->
                        {
                        sb.append(((xString.StringHandle) frameCaller.getFrameLocal()).getValue());
                        Utils.log(sb.toString());
                        return Op.R_NEXT;
                        });
                    return R_CALL;
                    }
                }
            catch (ObjectHandle.ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }
        else
            {
            Constant constValue = frame.f_context.f_pool.getConstant(-nValue);

            if (constValue instanceof StringConstant)
                {
                sb.append(((StringConstant) constValue).getValue());
                }
            else
                {
                sb.append(constValue.getValueString());
                }
            }

        Utils.log(sb.toString());

        return iPC + 1;
        }
    }