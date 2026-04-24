package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nObj;
import static org.xvm.javajit.Builder.CD_nType;
import static org.xvm.javajit.Builder.DataType;
import static org.xvm.javajit.Builder.MD_TypeIsA;
import static org.xvm.javajit.Builder.MD_xvmType;


/**
 * JMP_ISA rvalue, #:(CONST, addr), addr-default ; if value "isA" a constant, jump to address, otherwise default
 * <p/>
 * Note: No support for wild-cards or ranges.
 */
public class JumpIsA
        extends JumpVal {
    /**
     * Construct a JMP_ISA op.
     *
     * @param argCond     a value Argument (the "condition")
     * @param aConstCase  an array of "case" values (constants)
     * @param aOpCase     an array of Ops to jump to
     * @param opDefault   an Op to jump to in the "default" case
     */
    public JumpIsA(Argument argCond, Constant[] aConstCase, Op[] aOpCase, Op opDefault) {
        super(argCond, aConstCase, aOpCase, opDefault);
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public JumpIsA(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);
    }

    @Override
    public int getOpCode() {
        return OP_JMP_ISA;
    }

    @Override
    protected int complete(Frame frame, int iPC, ObjectHandle hValue) {
        ObjectHandle[] ahCase  = m_ahCase;
        TypeConstant   typeVal = hValue.getUnsafeType();
        for (int i = 0, c = ahCase.length; i < c; ++i) {
            if (typeVal.isA(((TypeHandle) ahCase[i]).getDataType())) {
                return iPC + m_aofCase[i];
            }
        }
        return iPC + m_ofDefault;
    }

    // ----- JIT support ---------------------------------------------------------------------------

    public int build(BuildContext bctx, CodeBuilder code) {
        int   nThis     = getAddress();
        Label labelDflt = bctx.ensureLabel(code, nThis + m_ofDefault);
        int[] aofCase   = m_aofCase;
        int   cRows     = aofCase.length;

        // load the argument and get its type
        RegisterInfo reg = bctx.loadArgument(code, m_nArgCond);
        if (reg.type().isJitInterface()) {
            code.checkcast(CD_nObj);
        }
        bctx.loadCtx(code);
        code.invokevirtual(CD_nObj, "$xvmType", MD_xvmType);
        // argument TypeConstant is on the stack, store in a slot so we can reload for
        // each case test
        int slot = bctx.storeTempValue(code, CD_TypeConstant);

        for (int iRow = 0; iRow < cRows; iRow++) {
            Label label = bctx.ensureLabel(code, nThis + aofCase[iRow]);

            if (m_anConstCase[iRow] <= CONSTANT_OFFSET) {
                TypeConstant typeTest   = bctx.getArgumentType(m_anConstCase[iRow]);
                assert typeTest.isTypeOfType();
                typeTest = typeTest.getParamType(0).getParamType(0);

                TypeConstant type = reg.type();
                if (type.isJavaPrimitive()) {
                    // we can statically compute the result
                    if (getOpCode() == OP_IS_TYPE) {
                        if (type.isA(typeTest)) {
                            code.ifne(label);
                        }
                        continue;
                    }
                } else {
                    Builder.load(code, CD_TypeConstant, slot);
                    bctx.loadTypeConstant(code, typeTest);
                }
            } else {
                RegisterInfo regType = bctx.loadArgument(code, m_anConstCase[iRow]);
                assert regType.type().isTypeOfType();
                code.getfield(CD_nType, DataType, CD_TypeConstant);
            }

            code.invokevirtual(CD_TypeConstant, "isA", MD_TypeIsA);
            code.ifne(label);
        }
        code.goto_(labelDflt);
        return -1;
    }
}
