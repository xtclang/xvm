package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlaceAssign;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * PIP_SUB PROPERTY, rvalue-target, rvalue2 ; T -= T
 */
public class PIP_Sub
        extends OpPropInPlaceAssign
    {
    /**
     * Construct a PIP_SUB op based on the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     * @param argValue       the value Argument
     */
    public PIP_Sub(PropertyConstant constProperty, Argument argTarget, Argument argValue)
        {
        super(constProperty, argTarget, argValue);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_Sub(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_PIP_SUB;
        }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        return hTarget.getTemplate().invokePropertySub(frame, hTarget, sPropName, hValue);
        }
    }