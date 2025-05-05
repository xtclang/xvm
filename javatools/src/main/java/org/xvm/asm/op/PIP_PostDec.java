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
 * PIP_DECA PROPERTY, rvalue-target, lvalue ; same as IP_DECA for a register
 */
public class PIP_PostDec
        extends OpPropInPlace {
    /**
     * Construct a PIP_DECA op based on the passed arguments.
     *
     * @param idProp     the property id
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to move the result into (Register or local property)
     */
    public PIP_PostDec(PropertyConstant idProp, Argument argTarget, Argument argReturn) {
        super(idProp, argTarget, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_PostDec(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_PIP_DECA;
    }

    @Override
    protected int complete(Frame frame, ObjectHandle hTarget, PropertyConstant idProp) {
        return hTarget.getTemplate().invokePostDec(frame, hTarget, idProp, m_nRetValue);
    }
}