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
 * PIP_INCA PROPERTY, rvalue-target, lvalue ; same as IP_INCA for a register
 */
public class PIP_PostInc
        extends OpPropInPlace {
    /**
     * Construct a PIP_INCA op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to move the result into (Register or local property)
     */
    public PIP_PostInc(PropertyConstant idProp, Argument argTarget, Argument argReturn) {
        super(idProp, argTarget, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_PostInc(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_PIP_INCA;
    }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp) {
        return hTarget.getTemplate().invokePostInc(frame, hTarget, idProp, m_nRetValue);
    }
}