package org.xvm.asm;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.RegisterInfo;

/**
 * An Op that operates on primitive types and can build optimized operations.
 */
public abstract class OpOptimized
        extends Op {
    /**
     * Generate the code to load the binary Op's target and argument onto the stack and perform the
     * binary operation optimized for a Java primitive type.
     * <p>
     * This method will also validate that the argument type matches the target type.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    protected TypeConstant buildOptimizedBinary(BuildContext bctx, CodeBuilder  code,
                                                RegisterInfo regTarget, int nArgValue) {
        RegisterInfo regLoaded = regTarget.load(code);
        RegisterInfo regArg    = bctx.loadArgument(code, nArgValue);
        ClassDesc    cdArg     = regArg.cd();
        if (!cdArg.isPrimitive()) {
            Builder.unbox(code, regArg);
            cdArg = JitTypeDesc.getJavaPrimitive(regArg.type());
        }
        if (!cdArg.equals(regLoaded.cd())) {
            throw new UnsupportedOperationException("Convert " +
                    regArg.type().getValueString() + " to " + regLoaded.type().getValueString());
        }
        buildOptimizedBinary(bctx, code, regLoaded, regArg);
        return regLoaded.type();
    }

    /**
     * Generate the bytecodes for the corresponding binary op optimized for a Java primitive type.
     * The values for the target and argument must already be on the top of the Java stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the {@link RegisterInfo} for the target of the operation
     * @param regArg     the {@link RegisterInfo} for the argument of the operation
     */
    protected void buildOptimizedBinary(BuildContext bctx, CodeBuilder code,
                                        RegisterInfo regTarget, RegisterInfo regArg) {
         throw new UnsupportedOperationException();
     }

    /**
     * Generate the bytecodes for the corresponding unary op optimized for a Java primitive type.
     * The primitive value for the target must already be on the top of the Java stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  that final result type
     */
    protected void buildOptimizedUnary(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        throw new UnsupportedOperationException();
    }

    /**
     * Generate the code to load the binary Op's target and argument onto the stack and perform the
     * binary operation optimized for an XVM primitive Number type.
     * <p>
     * This method will also validate that the argument type matches the target type.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx      the current build context
     * @param code      the code builder to add the op codes to
     * @param regTarget the register containing the target of the operation
     * @param nArgValue the id of the register containing the operation argument
     *
     * @return the type of the resulting value (on Java stack)
     */
    protected TypeConstant buildOptimizedNumber(BuildContext bctx, CodeBuilder code,
                                                RegisterInfo regTarget, int nArgValue) {
        RegisterInfo regLoaded = regTarget.load(code);
        RegisterInfo regArg    = bctx.loadArgument(code, nArgValue);
        if (!regArg.cd().equals(regLoaded.cd())) {
            throw new UnsupportedOperationException("Convert " +
                    regArg.type().getValueString() + " to " + regLoaded.type().getValueString());
        }
        buildOptimizedNumber(bctx, code, regLoaded, regArg);
        return regLoaded.type();
    }

    /**
     * Generate the bytecodes for the corresponding binary op optimized for an XVM primitive Number
     * type.
     * The values for the target and argument must already be on the top of the Java stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the {@link RegisterInfo} for the target of the operation
     * @param regArg     the {@link RegisterInfo} for the argument of the operation
     */
    protected void buildOptimizedNumber(BuildContext bctx, CodeBuilder code,
                                        RegisterInfo regTarget, RegisterInfo regArg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Generate the bytecodes for the corresponding unary op optimized for an XVM primitive Number
     * type.
     * The primitive value for the target must already be on the top of the Java stack.
     * <p>
     * The target register should not have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  that final result type
     */
    protected void buildOptimizedNumber(BuildContext bctx, CodeBuilder code,
                                        RegisterInfo regTarget) {
        throw new UnsupportedOperationException();
    }

    /**
     * Invoke an optimized method on an XVM primitive target.
     *
     * @param bctx         the current build context
     * @param code         the code builder to add the op codes to
     * @param regTarget    the target register
     * @param method       the method to invoke
     * @param anArgValues  the ids of the argument registers
     *
     * @return the JIT method descriptor
     */
    protected JitMethodDesc buildXvmOptimized(BuildContext bctx, CodeBuilder code,
                                              RegisterInfo regTarget, MethodInfo method,
                                              int[] anArgValues) {
        TypeConstant  typeTarget = regTarget.type();
        JitMethodDesc jmd        = method.getJitDesc(bctx.builder, typeTarget);
        assert typeTarget.isXvmPrimitive() && jmd.isOptimizedStatic;

        String sJitName = method.ensureJitMethodName(bctx.typeSystem) + Builder.OPT;

        regTarget = regTarget.load(code);
        if (!regTarget.flavor().isOptimized) {
            Builder.unbox(code, typeTarget);
        }
        bctx.loadCtx(code);
        bctx.loadCallArguments(code, jmd, anArgValues);
        code.invokestatic(bctx.builder.ensureClassDesc(typeTarget), sJitName, jmd.optimizedMD);
        return jmd;
    }
}
