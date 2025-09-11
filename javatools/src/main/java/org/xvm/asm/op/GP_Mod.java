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
 * GP_MOD rvalue1, rvalue2, lvalue ; T % T -> T
 */
public class GP_Mod
        extends OpGeneral {
    /**
     * Construct a GP_MOD op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    public GP_Mod(Argument argTarget, Argument argValue, Argument argReturn) {
        super(argTarget, argValue, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Mod(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_MOD;
    }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return hTarget.getOpSupport().invokeMod(frame, hTarget, hArg, m_nRetValue);
    }
    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        Slot slotTarget = bctx.loadArgument(code, m_nTarget);
        Slot slotArg    = bctx.loadArgument(code, m_nArgValue);

        if (!slotTarget.isSingle() || !slotArg.isSingle()) {
            throw new UnsupportedOperationException("'%' operation on multi-slot");
        }

        ClassDesc    cdTarget = slotTarget.cd();
        TypeConstant typeRet  = slotTarget.type();

        if (cdTarget.isPrimitive()) {
            switch (cdTarget.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    code.irem();
                    break;
                case "J":
                    code.lrem();
                    break;
                default:
                    throw new IllegalStateException();
            }
            // TODO: for signed types we need to do this:
//            if (f_fSigned && lMod != 0 && (lMod < 0) != (l2 < 0)) {
//                lMod += l2;
//                assert (lMod < 0) == (l2 < 0);
//            }

        } else {
            throw new UnsupportedOperationException("TODO: " +
                slotTarget.type().getValueString() + " % " + slotArg.type().getValueString());
        }
        bctx.storeValue(code, bctx.ensureSlot(m_nRetValue, typeRet));
    }

}
