package org.xvm.asm;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

/**
 * An Op that operates on primitive types and can build optimized operations.
 */
public abstract class OpOptimized
        extends Op {
    /**
     * Generate the code to load the binary Op's target and argument onto the stack and perform the
     * optimized binary operation.
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
    protected TypeConstant buildOptimizedBinary(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        regTarget.load(code);
        RegisterInfo regArg = bctx.loadArgument(code, nArgValue);
        if (!regArg.cd().equals(regTarget.cd())) {
            throw new UnsupportedOperationException("Convert " +
                    regArg.type().getValueString() + " to " + regTarget.type().getValueString());
        }
        buildOptimizedBinary(bctx, code, regTarget, regArg);
        return regTarget.type();
    }

    /**
     * Generate the bytecodes for the corresponding op. The values for the target and argument
     * must already be on the top of the Java stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the {@link RegisterInfo} for the target of the operation
     * @param regArg     the {@link RegisterInfo} for the argument of the operation
     */
    protected void buildOptimizedBinary(BuildContext bctx,
                                      CodeBuilder  code,
                                      RegisterInfo regTarget,
                                      RegisterInfo regArg) {
         throw new UnsupportedOperationException();
     }

    /**
     * Generate the bytecodes for the corresponding op. The primitive value for the target must
     * already be on the top of the Java stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  that final result type
     */
    protected void buildOptimizedUnary(BuildContext bctx, CodeBuilder code, RegisterInfo regTarget) {
        throw new UnsupportedOperationException();
    }
}
