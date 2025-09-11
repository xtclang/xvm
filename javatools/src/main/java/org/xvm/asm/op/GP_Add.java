package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

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

        if (!slotTarget.isSingle()) {
            throw new UnsupportedOperationException("'+' operation on multi-slot");
        }

        ClassDesc    cdTarget = slotTarget.cd();
        TypeConstant typeRet  = slotTarget.type();

        if (cdTarget.isPrimitive()) {
            Slot slotArg = bctx.loadArgument(code, m_nArgValue);

            if (!slotArg.cd().equals(cdTarget)) {
                throw new UnsupportedOperationException("Convert " +
                    slotArg.type().getValueString() + " to " + slotTarget.type().getValueString());
            }

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
        } else {
            // TODO: there could be multiple "add" methods; need to use the arg type
            TypeInfo      info     = slotTarget.type().ensureTypeInfo();
            MethodInfo    method   = info.findOpMethod("add", "+", 1);
            String        sJitName = method.getIdentity().ensureJitMethodName(bctx.typeSystem);
            JitMethodDesc jmd      = method.getJitDesc(bctx.typeSystem);

            MethodTypeDesc md;
            if (jmd.isOptimized) {
                md        = jmd.optimizedMD;
                sJitName += Builder.OPT;
            } else {
                md = jmd.standardMD;
            }

            bctx.loadCtx(code);
            bctx.loadArgument(code, m_nArgValue);
            code.invokevirtual(slotTarget.cd(), sJitName, md);
        }
        bctx.storeValue(code, bctx.ensureSlot(m_nRetValue, typeRet));
    }
}
