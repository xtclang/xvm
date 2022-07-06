package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * JMP_ISA rvalue, #:(CONST, addr), addr-default ; if value "isA" a constant, jump to address, otherwise default
 * <p/>
 * Note: No support for wild-cards or ranges.
 */
public class JumpIsA
        extends JumpVal
    {
    /**
     * Construct a JMP_ISA op.
     *
     * @param argCond     a value Argument (the "condition")
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpIsA(Argument argCond, Constant[] aConstCase, Op[] aOpCase, Op opDefault)
        {
        super(argCond, aConstCase, aOpCase, opDefault);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpIsA(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_JMP_ISA;
        }

    @Override
    protected int complete(Frame frame, int iPC, ObjectHandle hValue)
        {
        ObjectHandle[] ahCase  = m_ahCase;
        TypeConstant   typeVal = hValue.getType();
        for (int i = 0, c = ahCase.length; i < c; ++i)
            {
            if (typeVal.isA(((TypeHandle) ahCase[i]).getDataType()))
                {
                return iPC + m_aofCase[i];
                }
            }
        return iPC + m_ofDefault;
        }
    }