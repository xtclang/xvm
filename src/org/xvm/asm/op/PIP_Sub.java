package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlaceAssign;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xRef.RefHandle;


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
    protected int completeRegular(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        ClassTemplate template = hTarget.getTemplate();

        return new InPlace(template, hTarget, hValue, sPropName, template::invokeSub).doNext(frame);
        }

    @Override
    protected int completeRef(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        return hTarget.getOpSupport().invokeSub(frame, hTarget, hValue, Frame.RET_UNUSED);
        }
    }