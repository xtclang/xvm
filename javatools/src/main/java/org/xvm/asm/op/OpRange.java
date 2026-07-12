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
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nRangeBoolean;
import static org.xvm.javajit.Builder.CD_nRangeFloat32;
import static org.xvm.javajit.Builder.CD_nRangeFloat64;
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
            ClassDesc    cdRange;
            ClassDesc    cdPrim;

            if (!regArg.cd().equals(cdTarget)) {
                throw new UnsupportedOperationException("Convert " +
                    regArg.type().getValueString() + " to " + typeTarget.getValueString());
            }

            switch (cdTarget.descriptorString()) {
            case "J": {
                cdRange = CD_nRangeInt64;
                cdPrim  = CD_long;
                break;
            }

            case "I": {
                cdRange = CD_nRangeInt8;
                cdPrim  = CD_int;
                break;
            }

            case "F": {
                cdRange = CD_nRangeFloat32;
                cdPrim  = CD_float;
                break;
            }

            case "D": {
                cdRange = CD_nRangeFloat64;
                cdPrim  = CD_double;
                break;
            }

            case "Z": {
                cdRange = CD_nRangeBoolean;
                cdPrim  = CD_int;
                break;
            }

            case "S", "B":
            default:
                throw new IllegalStateException("Not implemented: Range<" +
                    typeTarget.getValueString() + ">");
            }

            bctx.loadCtx(code);
            bctx.loadTypeConstant(code, typeTarget);
            regTarget.load(code);
            regArg.load(code);
            addRangeAttributes(code);

            code.invokestatic(cdRange, "$new$p", MethodTypeDesc.of(cdRange, CD_Ctx,
                    CD_TypeConstant, cdPrim, cdPrim, CD_boolean, CD_boolean, CD_boolean, CD_boolean));

            bctx.storeValue(code, bctx.ensureRegister(m_nRetValue, typeTarget));
        } else {
            super.build(bctx, code);
        }
        return -1;
    }

    protected void addRangeAttributes(CodeBuilder code) {
        switch (getOpCode()) {
            case OP_GP_IRANGEI -> code.iconst_0().iconst_0().iconst_0().iconst_0();
            case OP_GP_ERANGEI -> code.iconst_1().iconst_0().iconst_0().iconst_0();
            case OP_GP_IRANGEE -> code.iconst_0().iconst_0().iconst_1().iconst_0();
            case OP_GP_ERANGEE -> code.iconst_1().iconst_0().iconst_1().iconst_0();
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
        };

        bctx.buildNew(code, typeRange, idCtor, argsLoader);
        return typeRange;
    }
}
