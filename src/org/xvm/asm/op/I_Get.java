package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.OpIndex;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.IndexSupport;


/**
 * I_GET rvalue-target, rvalue-ix, lvalue ; T = T[ix]
 */
public class I_Get
        extends OpIndex
    {
    /**
     * Construct an I_GET op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argReturn  the Argument to store the result into
     */
    public I_Get(Argument argTarget, Argument argIndex, Argument argReturn)
        {
        super(argTarget, argIndex, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Get(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_GET;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hIndex)
        {
        ClassTemplate template = hTarget.getTemplate();
        if (template instanceof IndexSupport)
            {
            long lIndex = ((JavaLong) hIndex).getValue();

            if (frame.isNextRegister(m_nRetValue))
                {
                frame.introduceElementVar(m_nTarget, (int) lIndex);
                }
            return ((IndexSupport) template).extractArrayValue(frame, hTarget, lIndex, m_nRetValue);
            }

        CallChain chain = getOpChain(hTarget.getType());
        if (chain == null)
            {
            chain = template.findOpChain(hTarget, "[]", hIndex);
            if (chain == null)
                {
                return frame.raiseException("Invalid op: \"[]\"");
                }
            saveOpChain(hTarget.getType(), chain);
            }

        MethodStructure method = chain.getTop();

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introduceResolvedVar(m_nTarget, method.getReturnTypes()[0]);
            }

        return chain.invoke(frame, hTarget, hIndex, m_nRetValue);
        }
    }
