package org.xvm.compiler.ast.bindings;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.BindingAst;
import org.xvm.compiler.ast.LambdaExpression;
import org.xvm.compiler.ast.LambdaExpression.LambdaContext;

/**
 * Source associations for one generated lambda method, owned by its compilation context.
 * A new validation context starts with new bindings; cloning an AST neither copies nor clears
 * these maps. Register allocation and runtime register identity are unaffected.
 * Only the compiler-owned collector records facts; readers receive immutable collection snapshots.
 */
public final class LambdaBindings {
    private final Map<Register, Register>        captureOrigins = new IdentityHashMap<>();
    private final Map<Integer, ParameterBinding> parameters     = new LinkedHashMap<>();

    private LambdaBindings() {}

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

    /**
     * Compiler-only writer owned by one lambda validation context. Its method identity guards
     * against exposing the original context's facts through a copied lambda. The returned facts
     * belong to the same compiler worker; the collector is not a host-side editing API.
     */
    public static final class Collector {
        private       MethodStructure method;
        private final LambdaBindings  bindings = new LambdaBindings();

        /** Create the source-binding collector for a fresh lambda validation context. */
        public Collector() {}

        /**
         * Read facts only when they belong to the requested generated method.
         *
         * @param method  the lambda's current generated method, or null before validation
         * @return the collected bindings, or null if this context does not own that method
         */
        public LambdaBindings getBindings(MethodStructure method) {
            return method != null && this.method == method ? bindings : null;
        }

        /**
         * Retain a source association after ordinary validation allocates a generated parameter.
         *
         * @param lambda    the source lambda being validated
         * @param context   its active capture/validation context
         * @param index     the generated parameter index, including capture parameters
         * @param register  the existing generated parameter register
         */
        public void bind(LambdaExpression lambda, LambdaContext context, int index, Register register) {
            method = lambda.getLambda();
            int captures = method.getParamCount() - lambda.getParamCount();
            if (index < captures) {
                String   name   = method.getParam(index).getName();
                Argument origin = context.ensureRegisterMap().get(name);
                if (origin == null) {
                    origin = context.getFormalMap().get(name);
                }
                if (origin instanceof Register source) {
                    bindings.captureOrigins.put(register.getOriginalRegister(), source.getOriginalRegister());
                }
            } else {
                int   sourceIndex = index - captures;
                Token name        = BindingAst.bindLambdaParameter(lambda, sourceIndex, register);
                if (name != null) {
                    bindings.parameters.put(sourceIndex, new ParameterBinding(name, register));
                }
            }
        }
    }
}
