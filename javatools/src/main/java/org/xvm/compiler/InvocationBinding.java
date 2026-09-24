package org.xvm.compiler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

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

    /** A validated function-valued invocation has a signature, but no statically selected method. */
    public record FunctionCall(TypeConstant type, List<Argument> arguments) {
        public FunctionCall {
            arguments = List.copyOf(arguments);
        }
    }

    /** The two kinds of call facts remain separate so a function never claims a method target. */
    public record Facts(Map<InvocationExpression, InvocationBinding> methods,
                        Map<InvocationExpression, FunctionCall> functions) {
        public Facts {
            methods   = Map.copyOf(methods);
            functions = Map.copyOf(functions);
        }
    }

    /** A written label copied before argument rewriting, without retaining its mutable token. */
    public record Label(String name, long startPosition, long endPosition) {}

    /** A written argument's source range and visible parameter index; positional/legacy facts have no label. */
    public record Argument(long startPosition, long endPosition, int parameterIndex, boolean named,
                           Label label) {
        /** Retain callers that recorded named status without the label's source span. */
        public Argument(long startPosition, long endPosition, int parameterIndex, boolean named) {
            this(startPosition, endPosition, parameterIndex, named, null);
        }

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
            Label label = expression instanceof LabeledExpression labeled
                    ? new Label(labeled.getName(), labeled.getNameToken().getStartPosition(),
                            labeled.getNameToken().getEndPosition())
                    : null;
            bindings.add(new Argument(expression.getStartPosition(), expression.getEndPosition(),
                    parameter, label != null, label));
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
                f_functions.remove(invocation);
            }
        }

        /** Record a completed validation; node identity keeps speculative clones separate. */
        public void record(InvocationExpression invocation, InvocationBinding binding) {
            if (f_enabled) {
                f_bindings.put(invocation, binding);
            }
        }

        public void record(InvocationExpression invocation, FunctionCall binding) {
            if (f_enabled) {
                f_functions.put(invocation, binding);
            }
        }

        /** Finish the attempt, returning immutable facts for the surviving source trees. */
        public Map<InvocationExpression, InvocationBinding> finish(List<? extends AstNode> roots) {
            return finishFacts(roots).methods();
        }

        /** Finish both kinds together, releasing discarded or speculative nodes in either map. */
        public Facts finishFacts(List<? extends AstNode> roots) {
            Map<InvocationExpression, InvocationBinding> result = new IdentityHashMap<>();
            Map<InvocationExpression, FunctionCall> functions = new IdentityHashMap<>();
            List<AstNode> nodes = new ArrayList<>(roots);
            for (int i = 0; i < nodes.size(); ++i) {
                AstNode node = nodes.get(i);
                if (node instanceof InvocationExpression invocation &&
                        invocation.isValidated() && invocation.getTypeFit().isFit()) {
                    InvocationBinding binding = f_bindings.get(invocation);
                    if (binding != null) {
                        result.put(invocation, binding);
                    }
                    FunctionCall function = f_functions.get(invocation);
                    if (function != null) {
                        functions.put(invocation, function);
                    }
                }
                node.children().forEachRemaining(nodes::add);
            }
            f_bindings.clear();
            f_functions.clear();
            return new Facts(result, functions);
        }

        /** Ordinary compiler clients need not collect tooling facts. This instance never writes. */
        public static final Collector NONE = new Collector(false);

        private final boolean f_enabled;

        private final Map<InvocationExpression, InvocationBinding> f_bindings = new IdentityHashMap<>();
        private final Map<InvocationExpression, FunctionCall> f_functions = new IdentityHashMap<>();
    }
}
