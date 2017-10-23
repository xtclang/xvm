package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpCallable;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.Function.FunctionHandle;

import static org.xvm.util.Handy.readPackedInt;


/**
 * CALL_00 rvalue-function.
 */
public class Call_00
        extends OpCallable
    {
    /**
     * Construct a CALL_00 op.
     *
     * @param nFunction  the r-value indicating the function to call
     *
     * @deprecated
     */
    public Call_00(int nFunction)
        {
        super((Argument) null);

        m_nFunctionId = nFunction;
        }

    /**
     * Construct a CALL_00 op based on the passed arguments.
     *
     * @param argFunction the function Argument
     */
    public Call_00(Argument argFunction)
        {
        super(argFunction);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Call_00(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_CALL_00;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        if (m_nFunctionId == A_SUPER)
            {
            CallChain chain = frame.m_chain;
            if (chain == null)
                {
                throw new IllegalStateException();
                }

            return chain.callSuperNN(frame, Utils.OBJECTS_NONE, Utils.ARGS_NONE);
            }

        if (m_nFunctionId < 0)
            {
            MethodStructure function = getMethodStructure(frame);

            ObjectHandle[] ahVar = new ObjectHandle[function.getMaxVars()];

            return frame.call1(function, null, ahVar, Frame.RET_UNUSED);
            }

        try
            {
            FunctionHandle hFunction = (FunctionHandle) frame.getArgument(m_nFunctionId);
            if (hFunction == null)
                {
                return R_REPEAT;
                }

            return hFunction.call1(frame, null, Utils.OBJECTS_NONE, Frame.RET_UNUSED);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }
    }
