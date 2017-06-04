package org.xvm.proto.op;

import org.xvm.proto.Frame;
import org.xvm.proto.Op;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xBoolean.BooleanHandle;

/**
 * JMP_FALSE rvalue-bool, rel-addr ; jump if value is false
 *
 * @author gg 2017.03.08
 */
public class JumpFalse extends Op
    {
    private final int f_nValue;
    private final int f_nRelAddr;

    public JumpFalse(int nValue, int nRelAddr)
        {
        f_nValue = nValue;
        f_nRelAddr = nRelAddr;
        }


    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            BooleanHandle hTest = (BooleanHandle) frame.getArgument(f_nValue);
            if (hTest == null)
                {
                return R_REPEAT;
                }

            return hTest.get() ? iPC + 1 : iPC + f_nRelAddr;
            }
        catch (ExceptionHandle.WrapperException e)
            {
            frame.m_hException = e.getExceptionHandle();
            return R_EXCEPTION;
            }
        }
    }
