package org.xvm.asm.op;

import java.io.DataInput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpGeneral;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.NumberSupport;
import org.xvm.javajit.RegisterInfo;
import org.xvm.javajit.TextSupport;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * GP_ADD rvalue1, rvalue2, lvalue ; T + T -> T
 */
public class GP_Add
        extends OpGeneral
        implements NumberSupport, TextSupport {
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
    protected TypeConstant buildOptimizedBinary(BuildContext bctx,
                                                CodeBuilder  code,
                                                RegisterInfo regTarget,
                                                int          nArgValue) {
        TypeConstant type = regTarget.type();
        if (type.getValueString().equals("Char")) {
            return buildAddToChar(bctx, code, regTarget, nArgValue);
        }
        return super.buildOptimizedBinary(bctx, code, regTarget, nArgValue);
    }

    @Override
    protected void buildOptimizedBinary(BuildContext bctx,
                                        CodeBuilder  code,
                                        RegisterInfo regTarget,
                                        RegisterInfo regArg) {
        buildPrimitiveAdd(bctx, code, regTarget);
    }

    @Override
    protected RegisterInfo buildXvmOptimizedBinary(BuildContext bctx,
                                                   CodeBuilder  code,
                                                   RegisterInfo regTarget,
                                                   int          nArgValue) {
        buildXvmPrimitiveAdd(bctx, code, regTarget, nArgValue);
        return regTarget;
    }
}
