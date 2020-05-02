package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpIndexInPlace;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.IndexSupport;


/**
 * I_SET rvalue-target, rvalue-ix, rvalue ; T[ix] = T
 */
public class I_Set
        extends OpIndexInPlace
    {
    /**
     * Construct an I_SET op for the passed target.
     *
     * @param argTarget  the target Argument
     * @param argIndex   the index Argument
     * @param argValue   the value Argument
     */
    public I_Set(Argument argTarget, Argument argIndex, Argument argValue)
        {
        super(argTarget, argIndex, argValue);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public I_Set(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_I_SET;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, JavaLong hIndex, ObjectHandle hValue)
        {
        ClassTemplate template = hTarget.getTemplate();
        if (template instanceof IndexSupport)
            {
            return ((IndexSupport) template).
                assignArrayValue(frame, hTarget, hIndex.getValue(), hValue);
            }

        CallChain chain = getOpChain(frame, hTarget.getType());
        if (chain == null)
            {
            chain = template.findOpChain(hTarget, "[]=", new ObjectHandle[] {hIndex, hValue});
            if (chain == null)
                {
                return frame.raiseException("Invalid op: \"[]=\"");
                }
            saveOpChain(frame, hTarget.getType(), chain);
            }

        ObjectHandle[] ahVar = new ObjectHandle[Math.max(chain.getMaxVars(), 2)];
        ahVar[0] = hIndex;
        ahVar[1] = hValue;

        return chain.invoke(frame, hTarget, ahVar, Op.A_IGNORE);
        }
    }