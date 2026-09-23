package org.xvm.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.InvocationExpression;
import org.xvm.compiler.ast.LabeledExpression;

/**
 * Immutable source provenance for a selected, instantiated method call. Created during real
 * validation, not overload probing. No Context or mutable argument expressions are retained.
 */
public record InvocationBinding(MethodConstant method, SignatureConstant signature,
                                List<Argument> arguments) {
    public InvocationBinding {
        arguments = List.copyOf(arguments);
    }

    /** A written argument's source range, visible parameter index and explicit-label status. */
    public record Argument(long startPosition, long endPosition, int parameterIndex, boolean named) {
        /** Retain the original positional-argument construction API. */
        public Argument(long startPosition, long endPosition, int parameterIndex) {
            this(startPosition, endPosition, parameterIndex, false);
        }
    }

    /**
     * Match the compiler's parameter-ordered expressions to their written argument spans before
     * validation inserts conversions. Synthetic defaults have no matching written expression.
     */
    public static Optional<List<Argument>> arguments(List<Expression> written, List<Expression> ordered) {
        List<Argument> bindings = new ArrayList<>();
        for (Expression expression : written) {
            Expression value = expression instanceof LabeledExpression labeled
                    ? labeled.getUnderlyingExpression() : expression;
            int parameter = -1;
            for (int i = 0; i < ordered.size(); ++i) {
                Expression candidate = ordered.get(i);
                if (candidate == expression || candidate == value ||
                        candidate.getStartPosition() == value.getStartPosition() &&
                        candidate.getEndPosition() == value.getEndPosition()) {
                    parameter = i;
                    break;
                }
            }
            if (parameter < 0) {
                // A compiler rewrite changed the source association. Omit tooling facts rather
                // than guessing a parameter or changing whether the program compiles.
                return Optional.empty();
            }
            bindings.add(new Argument(expression.getStartPosition(), expression.getEndPosition(),
                    parameter, expression instanceof LabeledExpression));
        }
        return Optional.of(List.copyOf(bindings));
    }

    /**
     * Compiler-worker scratch space owned by one compilation attempt, never by an AST node.
     * Validation may replace or invalidate entries. Publication retains only successfully
     * validated invocations still present in the final tree, excluding speculative clones and
     * discarded rewrites. Finishing clears scratch entries, including discarded clones, even if
     * a compiler context survives with the returned tree. The result is detached from the collector.
     */
    public static final class Collector {
        public Collector() {
            this(true);
        }

        private Collector(boolean enabled) {
            f_enabled = enabled;
        }

        /** @return whether this attempt requests call facts */
        public boolean isEnabled() {
            return f_enabled;
        }

        /** Invalidate any earlier result before retrying validation of this invocation. */
        public void begin(InvocationExpression invocation) {
            if (f_enabled) {
                f_bindings.remove(invocation);
            }
        }

        /** Record a completed validation; node identity keeps speculative clones separate. */
        public void record(InvocationExpression invocation, InvocationBinding binding) {
            if (f_enabled) {
                f_bindings.put(invocation, binding);
            }
        }

        /** Finish the attempt, returning immutable facts for the surviving source trees. */
        public Map<InvocationExpression, InvocationBinding> finish(List<? extends AstNode> roots) {
            Map<InvocationExpression, InvocationBinding> result = new IdentityHashMap<>();
            List<AstNode> nodes = new ArrayList<>(roots);
            for (int i = 0; i < nodes.size(); ++i) {
                AstNode node = nodes.get(i);
                if (node instanceof InvocationExpression invocation &&
                        invocation.isValidated() && invocation.getTypeFit().isFit()) {
                    InvocationBinding binding = f_bindings.get(invocation);
                    if (binding != null) {
                        result.put(invocation, binding);
                    }
                }
                node.children().forEachRemaining(nodes::add);
            }
            f_bindings.clear();
            return Collections.unmodifiableMap(result);
        }

        /** Ordinary compiler clients need not collect tooling facts. This instance never writes. */
        public static final Collector NONE = new Collector(false);

        private final boolean f_enabled;

        private final Map<InvocationExpression, InvocationBinding> f_bindings = new IdentityHashMap<>();
    }
}
