package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

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
    protected TypeConstant buildOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                                RegisterInfo regTarget, int nArgValue) {
        return buildRange(bctx, code, regTarget, nArgValue);
    }

    @Override
    protected TypeConstant buildXvmOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                                   RegisterInfo regTarget, int nArgValue) {
        return buildRange(bctx, code, regTarget, nArgValue);
    }

    private TypeConstant buildRange(BuildContext bctx, CodeBuilder code,
                                    RegisterInfo regTarget, int nArgValue) {
        TypeConstant        typeEl    = regTarget.type();
        TypeConstant        typeRange = bctx.pool().ensureRangeType(typeEl);
        Set<MethodConstant> setCtors  = typeRange.ensureTypeInfo().
                    findMethods("construct", 4, MethodKind.Constructor);
        assert setCtors.size() == 1;

        MethodConstant idCtor = setCtors.iterator().next();
        assert typeRange.ensureTypeInfo().getMethodById(idCtor) != null;

        bctx.buildNew(code, typeRange, idCtor, jmdNew -> {
            assert jmdNew.isOptimized;

            regTarget.load(code);
            RegisterInfo regArg = bctx.loadArgument(code, nArgValue);
            if (!regArg.cd().equals(regTarget.cd())) {
                throw new UnsupportedOperationException("Convert " +
                        regArg.type().getValueString() + " to " + typeEl.getValueString());
            }
            addRangeAttributes(code);
        });

        return typeRange;
    }
}
