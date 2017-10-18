package org.xvm.asm.op;


import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.StringConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xString;

/**
 * Debugging only.
 */
public class X_Print
        extends OpInvocable
    {
    public X_Print(int nValue)
        {
        super(nValue, 0);

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

                switch (info.getStyle())
                    {
                    case Frame.VAR_DYNAMIC_REF:
                        sb.append("<dynamic> ");
                        break;

                    case Frame.VAR_WAITING:
                        // must not happen
                        throw new IllegalStateException();
                    }

                sb.append(info.getName()).append("=");

                ObjectHandle hValue = frame.getArgument(nValue);
                if (hValue == null)
                    {
                    sb.append("<waiting>...");
                    Utils.log(sb.toString());
                    return R_REPEAT;
                    }

                // call the "to<String>()" method for the object to get the value
                m_nMethodId = frame.f_adapter.getMethodConstId("Object", "to",
                    ClassTemplate.VOID, ClassTemplate.STRING);
                TypeComposition clz = hValue.f_clazz;
                CallChain chain = getCallChain(frame, clz);

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
            Constant constValue = frame.f_context.f_pool.getConstant(Op.CONSTANT_OFFSET - nValue);

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

    private int m_nValue;
    }