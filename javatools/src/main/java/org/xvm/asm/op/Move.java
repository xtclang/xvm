package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;
import org.xvm.asm.OpMove;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;


/**
 * MOV rvalue-src, lvalue-dest
 */
public class Move
        extends OpMove {
    /**
     * Construct a MOV op for the passed arguments.
     *
     * @param argFrom  the Argument to move from
     * @param argTo    the Argument to move to
     */
    public Move(Argument argFrom, Argument argTo) {
        super(argFrom, argTo);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Move(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_MOV;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            int nFrom = m_nFromValue;
            int nTo   = m_nToValue;

            ObjectHandle hValue = frame.getArgument(nFrom);

            if (frame.isNextRegister(nTo)) {
                frame.introduceVarCopy(nTo, nFrom);
            }

            return frame.assignDeferredValue(nTo, hValue);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    @Override
    public boolean checkRedundant(Op[] aop) {
        if (m_argFrom instanceof Register && m_argFrom.equals(m_argTo)) {
            markRedundant();
            return true;
        }
        return false;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regFrom  = bctx.loadArgument(code, m_nFromValue);
        RegisterInfo regTo    = bctx.ensureRegInfo(m_nToValue, regFrom.type());
        TypeConstant typeFrom = regFrom.type();
        TypeConstant typeTo   = regTo.type();
        ClassDesc    cdFrom   = regFrom.cd();
        ClassDesc    cdTo     = regTo.cd();

        if (typeFrom.isA(typeTo)) {
            buildMove(bctx, code, regFrom, regTo);
        } else {
            if (typeFrom.isA(regTo.original().type())) {
                buildMove(bctx, code, regFrom, regTo);
            } else {
                if (cdFrom.isPrimitive()) {
                    if (cdTo.isPrimitive()) {
                        throw new IllegalStateException("Unreconcilable types " +
                            typeFrom.getValueString() + " -> " + typeTo.getValueString());
                    }
                    Builder.box(code, typeFrom, cdFrom);
                }
                // TODO: generateCheckCast()
                code.checkcast(typeTo.ensureClassDesc(bctx.typeSystem));
                if (cdTo.isPrimitive()) {
                    Builder.unbox(code, typeTo, cdTo);
                }
                bctx.storeValue(code, regTo, typeFrom);
            }
        }
    }

    public void buildMove(BuildContext bctx, CodeBuilder code,
                          RegisterInfo regFrom, RegisterInfo regTo) {
        if (regFrom.cd().isPrimitive() ^ regTo.cd().isPrimitive()) {
            if (regFrom.cd().isPrimitive()) {
                Builder.box(code, regFrom);
            } else {
                Builder.unbox(code, regTo);
            }
        }
        bctx.storeValue(code, regTo, regFrom.type());
    }
}