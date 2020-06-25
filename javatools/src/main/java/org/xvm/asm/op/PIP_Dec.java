package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlace;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * PIP_DEC PROPERTY, rvalue-target ; in-place decrement; no result
 */
public class PIP_Dec
        extends OpPropInPlace
    {
    /**
     * Construct a PIP_DEC op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     */
    public PIP_Dec(PropertyConstant idProp, Argument argTarget)
        {
        super(idProp, argTarget);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_Dec(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_PIP_DEC;
        }

    @Override
    protected boolean isAssignOp()
        {
        return false;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp)
        {
        return hTarget.getTemplate().invokePreDec(frame, hTarget, idProp, A_IGNORE);
        }
    }