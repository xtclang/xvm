package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.CD_xObj;

/**
 * GP_ADD rvalue1, rvalue2, lvalue ; T + T -> T
 */
public class GP_Add
        extends OpGeneral {
    /**
     * Construct a GP_ADD op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argValue   the second value Argument
     * @param argReturn  the Argument to store the result into
     */
    public GP_Add(Argument argTarget, Argument argValue, Argument argReturn) {
        super(argTarget, argValue, argReturn);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public GP_Add(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_GP_ADD;
    }

    protected int completeBinary(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return hTarget.getOpSupport().invokeAdd(frame, hTarget, hArg, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        Slot slotTarget = bctx.loadArgument(code, m_nTarget);
        Slot slotArg    = bctx.loadArgument(code, m_nArgValue);

        if (!slotTarget.isSingle() || !slotArg.isSingle()) {
            throw new UnsupportedOperationException("Add operation on multi-slot");
        }

        ClassDesc    cdTarget = slotTarget.cd();
        ClassDesc    cdArg    = slotArg.cd();
        TypeConstant typeRet  = slotTarget.type();

        if (cdTarget.isPrimitive()) {
            assert cdArg.equals(cdTarget);
            switch (cdTarget.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    code.iadd();
                    break;
                case "J":
                    code.ladd();
                    break;
                case "F":
                    code.fadd();
                    break;
                case "D":
                    code.dadd();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else if (cdTarget.equals(CD_String)) {
            MethodTypeDesc mdAdd = MethodTypeDesc.of(CD_String, CD_String, CD_xObj);
            code.invokevirtual(CD_String, "add", mdAdd);
        } else {
            throw new UnsupportedOperationException("TODO: " + cdTarget.descriptorString());
        }
        bctx.storeValue(code, bctx.ensureSlot(m_nRetValue, typeRet));
    }
}
