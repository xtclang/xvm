package org.xvm.asm.op;


import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.types.xProperty.PropertyHandle;

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

        if (nValue >= 0)
            {
            try
                {
                Frame.VarInfo info = frame.getVarInfo(nValue);

                if (info.isDynamic())
                    {
                    sb.append("<dynamic> ");
                    }

                sb.append(info.getName()).append("=");

                ObjectHandle hValue = frame.getArgument(nValue);
                if (hValue == null)
                    {
                    sb.append("<waiting>...");
                    Utils.log(frame, sb.toString());
                    return R_REPEAT;
                    }

                if (isProperty(hValue))
                    {
                    PropertyHandle hProp = (PropertyHandle) hValue;
                    sb.append("Local property: ").append(hProp.m_property.getName());
                    return R_NEXT;
                    }

                // call the "to<String>()" method for the object to get the value
                m_nMethodId = frame.f_context.f_templates.f_adapter.getMethodConstId("Object", "to",
                    ClassTemplate.VOID, ClassTemplate.STRING);
                CallChain chain = getCallChain(frame, hValue);

                int iResult;
                if (chain.isNative())
                    {
                    iResult = hValue.getTemplate().invokeNativeN(frame, chain.getTop(), hValue,
                            Utils.OBJECTS_NONE, Frame.RET_LOCAL);
                    if (iResult == R_NEXT)
                        {
                        sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue());
                        }
                    }
                else
                    {
                    ObjectHandle[] ahVar = new ObjectHandle[chain.getTop().getMaxVars()];

                    iResult = hValue.getTemplate().invoke1(frame, chain, hValue, ahVar, Frame.RET_LOCAL);
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
            }
        else
            {
            Constant constValue = frame.getConstant(nValue);

            if (constValue instanceof StringConstant)
                {
                sb.append(((StringConstant) constValue).getValue());
                }
            else
                {
                sb.append(constValue.getValueString());
                }
            }

        Utils.log(frame, sb.toString());

        return iPC + 1;
        }

    private int m_nValue;
    }