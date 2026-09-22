package org.xvm.compiler.ast;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.LambdaExpression.LambdaContext;

/**
 * Source associations for one generated lambda method, owned by its compilation context.
 * A new validation context starts with new bindings; cloning an AST neither copies nor clears
 * these maps. Register allocation and runtime register identity are unaffected.
 */
public final class LambdaBindings {
    /** A source parameter and its validated binding, excluding synthetic capture parameters. */
    public record ParameterBinding(Token name, Register register) {}

    /**
     * @return the source parameter bindings retained so far
     */
    public List<ParameterBinding> getParameters() {
        return List.copyOf(parameters.values());
    }

    /**
     * @return capture registers mapped to enclosing source registers, by object identity
     */
    public Map<Register, Register> getCaptureOrigins() {
        return Collections.unmodifiableMap(new IdentityHashMap<>(captureOrigins));
    }

    boolean isFor(MethodStructure method) {
        return method != null && this.method == method;
    }

    void bind(LambdaExpression lambda, LambdaContext context, int index, Register register) {
        method = lambda.getLambda();
        int captures = method.getParamCount() - lambda.getParamCount();
        if (index < captures) {
            String   name   = method.getParam(index).getName();
            Argument origin = context.ensureRegisterMap().get(name);
            if (origin == null) {
                origin = context.getFormalMap().get(name);
            }
            if (origin instanceof Register source) {
                captureOrigins.put(register.getOriginalRegister(), source.getOriginalRegister());
            }
        } else {
            int   sourceIndex = index - captures;
            Token name;
            if (lambda.hasOnlyParamNames()) {
                name = lambda.paramNames.get(sourceIndex) instanceof NameExpression expression
                        ? expression.getNameToken() : null;
            } else {
                Parameter parameter = lambda.params.get(sourceIndex);
                parameter.setResolvedTarget(register);
                name = parameter.getNameToken();
            }
            if (name != null) {
                parameters.put(sourceIndex, new ParameterBinding(name, register));
            }
        }
    }

    private       MethodStructure                method;
    private final Map<Register, Register>        captureOrigins = new IdentityHashMap<>();
    private final Map<Integer, ParameterBinding> parameters = new LinkedHashMap<>();
}
