package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_COMPL rvalue, lvalue   ; ~T -> T
 */
public class GP_Compl
        extends OpGeneral {
    /**
     * Construct a GP_COMPL op for the passed arguments.
     *
     * @param argValue   the Argument to calculate the complement of
     * @param argResult  the Argument to store the result in
     */
    public GP_Compl(Argument argValue, Argument argResult) {
        super(argValue, argResult);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Compl(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_COMPL;
    }

    @Override
    protected boolean isBinaryOp() {
        return false;
    }

    @Override
    protected int completeUnary(Frame frame, ObjectHandle hTarget) {
        return hTarget.getOpSupport().invokeCompl(frame, hTarget, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        Slot slotTarget = bctx.loadArgument(code, m_nTarget);

        if (!slotTarget.isSingle()) {
            throw new UnsupportedOperationException("'~' operation on multi-slot");
        }

        ClassDesc    cdTarget = slotTarget.cd();
        TypeConstant typeRet  = slotTarget.type();
        if (cdTarget.isPrimitive()) {
            switch (cdTarget.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    code.iconst_m1()
                        .ixor();
                    break;
                case "J":
                    code.ldc(-1L)
                        .lxor();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else {
            throw new UnsupportedOperationException("TODO:  ~" + slotTarget.type().getValueString());
        }
        bctx.storeValue(code, bctx.ensureSlot(m_nRetValue, typeRet));
    }
}
