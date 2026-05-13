package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Set;

import java.util.function.Consumer;
import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.RegisterInfo;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nRangeInt64;
import static org.xvm.javajit.Builder.CD_nRangeInt8;

/**
 * The base class for OP_GP_%RANGE% ops
 */
public abstract class OpRange
        extends OpGeneral {

    protected OpRange(Argument argTarget, Argument argValue, Argument argReturn) {
        super(argTarget, argValue, argReturn);
    }

    protected OpRange(DataInput in, Constant[] aconst) throws IOException {
        super(in, aconst);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regTarget = bctx.ensureRegister(code, m_nTarget);

        ClassDesc    cdTarget   = regTarget.cd();
        TypeConstant typeTarget = regTarget.type();

        if (!regTarget.isSingle()) {
            System.err.println("TODO JK/GG: NPE for range operation on " +regTarget.flavor());
            code.aconst_null();
            bctx.storeValue(code, bctx.ensureRegister(m_nRetValue, typeTarget));
            return -1;
        }

        if (cdTarget.isPrimitive()) {
            RegisterInfo regArg = bctx.ensureRegister(code, m_nArgValue);

            if (!regArg.cd().equals(cdTarget)) {
                throw new UnsupportedOperationException("Convert " +
                    regArg.type().getValueString() + " to " + typeTarget.getValueString());
            }

            switch (cdTarget.descriptorString()) {
            case "J": {
                ClassDesc cdRange = CD_nRangeInt64;
                bctx.loadCtx(code);
                bctx.loadTypeConstant(code, typeTarget);
                regTarget.load(code);
                regArg.load(code);
                addRangeAttributes(code);

                code.invokestatic(cdRange, "$new$p", MethodTypeDesc.of(cdRange, CD_Ctx, CD_TypeConstant,
                    CD_long, CD_long, CD_boolean, CD_boolean, CD_boolean, CD_boolean));
                break;
            }

            case "I": {
                ClassDesc cdRange = CD_nRangeInt8;
                bctx.loadCtx(code);
                bctx.loadTypeConstant(code, typeTarget);
                regTarget.load(code);
                regArg.load(code);
                addRangeAttributes(code);

                code.invokestatic(cdRange, "$new$p", MethodTypeDesc.of(cdRange, CD_Ctx, CD_TypeConstant,
                    CD_int, CD_int, CD_boolean, CD_boolean, CD_boolean, CD_boolean));
                break;
            }

            case "S", "B", "Z":
            default:
                throw new IllegalStateException("Not implemented: Range< " +
                    typeTarget.getValueString() + ">");
            }

            bctx.storeValue(code, bctx.ensureRegister(m_nRetValue, typeTarget));
        } else {
            super.build(bctx, code);
        }
        return -1;
    }

    protected void addRangeAttributes(CodeBuilder code) {
        switch (getOpCode()) {
            case OP_GP_IRANGEI -> code.iconst_1().iconst_0().iconst_1().iconst_0();
            case OP_GP_ERANGEI -> code.iconst_0().iconst_0().iconst_1().iconst_0();
            case OP_GP_IRANGEE -> code.iconst_1().iconst_0().iconst_0().iconst_0();
            case OP_GP_ERANGEE -> code.iconst_0().iconst_0().iconst_0().iconst_0();
            default            -> throw new IllegalStateException();
        }
    }

    @Override
    protected TypeConstant buildXvmOptimizedBinary(BuildContext bctx, CodeBuilder  code,
                                                   RegisterInfo regTarget, int nArgValue) {
        TypeConstant typeEl = regTarget.type();
        assert typeEl.equals(bctx.getArgumentType(nArgValue));

        TypeConstant        typeRange = bctx.pool().ensureRangeType(regTarget.type());
        Set<MethodConstant> setCtors  = typeRange.ensureTypeInfo().
                    findMethods("construct", 4, MethodKind.Constructor);
        assert setCtors.size() == 1;

        MethodConstant idCtor = setCtors.iterator().next();
        assert typeRange.ensureTypeInfo().getMethodById(idCtor) != null;

        Consumer<JitMethodDesc> argsLoader = anArg -> {
            regTarget.load(code);
            bctx.loadArgument(code, nArgValue);
            addRangeAttributes(code);
            code.iconst_0();
        };

        bctx.buildNew(code, typeRange, idCtor, argsLoader);
        return typeRange;
    }
}