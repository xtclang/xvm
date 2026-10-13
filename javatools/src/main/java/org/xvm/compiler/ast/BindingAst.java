package org.xvm.compiler.ast;

import org.xvm.asm.Register;

import org.xvm.compiler.Token;

/**
 * Internal bridge for source bindings that need access to ordinary AST parameter slots.
 * Java subpackages do not share package access; this preserves the existing visibility of
 * written parameter lists and their binding setter while fact collection lives in ast.bindings.
 * Hosts read the published bindings instead of calling this compiler-worker operation.
 */
public final class BindingAst {
    private BindingAst() {}

    /**
     * Associate an existing lambda parameter register with its written declaration.
     * This neither allocates a register nor changes capture discovery or parameter ordering.
     *
     * @param lambda       the source lambda being validated
     * @param sourceIndex  the written parameter index, excluding generated capture parameters
     * @param register     the register already allocated by ordinary validation
     *
     * @return the written name token, or null when the parameter has no name expression
     */
    public static Token bindLambdaParameter(LambdaExpression lambda, int sourceIndex, Register register) {
        if (lambda.hasOnlyParamNames()) {
            return lambda.paramNames.get(sourceIndex) instanceof NameExpression expression
                    ? expression.getNameToken() : null;
        }

        Parameter parameter = lambda.params.get(sourceIndex);
        parameter.setResolvedTarget(register);
        return parameter.getNameToken();
    }
}
