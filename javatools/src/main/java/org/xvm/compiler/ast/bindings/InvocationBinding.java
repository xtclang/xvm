package org.xvm.compiler.ast.bindings;

import java.util.ArrayList;
import java.util.Collections;
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
import org.xvm.compiler.ast.NewExpression;
import org.xvm.compiler.ast.PropertyDeclarationStatement;

import static java.util.Objects.requireNonNull;

/**
 * Immutable source provenance for a selected, instantiated method call. Created during real
 * validation, not overload probing. No Context or mutable argument expressions are retained.
 *
 * @param method     the compiler-selected method identity
 * @param signature  the instantiated signature fitted at this source site
 * @param arguments  written argument spans associated with visible parameter indices
 */
public record InvocationBinding(MethodConstant method, SignatureConstant signature,
                                List<Argument> arguments) {
    public InvocationBinding {
        arguments = List.copyOf(arguments);
    }

    /**
     * A validated function-valued invocation has a signature, but no statically selected method.
     *
     * @param type       the compiler-resolved value or function type
     * @param arguments  written argument spans associated with visible parameter indices
     */
    public record FunctionCall(TypeConstant type, List<Argument> arguments) {
        public FunctionCall {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * The two kinds of call facts remain separate so a function never claims a method target.
     * Maps are immutable identity snapshots: keys and values compare by reference.
     *
     * @param methods       validated method calls keyed by invocation identity
     * @param functions     validated function calls keyed by invocation identity
     * @param constructors  validated constructor calls keyed by construction identity
     * @param initializers  folded initializer facts keyed by source property identity
     */
    public record Facts(Map<InvocationExpression, InvocationBinding> methods,
                        Map<InvocationExpression, FunctionCall> functions,
                        Map<NewExpression, InvocationBinding> constructors,
                        Map<PropertyDeclarationStatement, InitializerBinding> initializers) {
        public Facts {
            methods      = identitySnapshot(methods);
            functions    = identitySnapshot(functions);
            constructors = identitySnapshot(constructors);
            initializers = identitySnapshot(initializers);
        }

        /**
         * Capture call facts, leaving omitted constructor or initializer facts empty.
         *
         * @param methods       validated method calls keyed by invocation identity
         * @param functions     validated function calls keyed by invocation identity
         * @param constructors  validated constructor calls keyed by construction identity
         */
        public Facts(Map<InvocationExpression, InvocationBinding> methods,
                     Map<InvocationExpression, FunctionCall> functions,
                     Map<NewExpression, InvocationBinding> constructors) {
            this(methods, functions, constructors, Map.of());
        }

        /**
         * Capture call facts, leaving omitted constructor or initializer facts empty.
         *
         * @param methods    validated method calls keyed by invocation identity
         * @param functions  validated function calls keyed by invocation identity
         */
        public Facts(Map<InvocationExpression, InvocationBinding> methods,
                     Map<InvocationExpression, FunctionCall> functions) {
            this(methods, functions, Map.of());
        }
    }

    /** Preserve node identity, reject null entries and detach from the collector. */
    private static <K, V> Map<K, V> identitySnapshot(Map<K, V> source) {
        Map<K, V> copy = new IdentityHashMap<>();
        source.forEach((key, value) -> copy.put(requireNonNull(key), requireNonNull(value)));
        return Collections.unmodifiableMap(copy);
    }

    /** A written label copied before argument rewriting, without retaining its mutable token. */
    public record Label(String name, long startPosition, long endPosition) {}

    /** A written argument's source range and visible parameter index; positional/legacy facts have no label. */
    public record Argument(long startPosition, long endPosition, int parameterIndex, boolean named,
                           Label label) {
        /**
         * Retain callers that recorded named status without the label's source span.
         *
         * @param startPosition   the written source range's inclusive start
         * @param endPosition     the written source range's exclusive end
         * @param parameterIndex  the zero-based visible parameter index
         * @param named           whether the argument was written with a parameter label
         */
        public Argument(long startPosition, long endPosition, int parameterIndex, boolean named) {
            this(startPosition, endPosition, parameterIndex, named, null);
        }

        /**
         * Retain the original positional-argument construction API.
         *
         * @param startPosition   the written source range's inclusive start
         * @param endPosition     the written source range's exclusive end
         * @param parameterIndex  the zero-based visible parameter index
         */
        public Argument(long startPosition, long endPosition, int parameterIndex) {
            this(startPosition, endPosition, parameterIndex, false);
        }
    }

    /**
     * Match the compiler's parameter-ordered expressions to their written argument spans before
     * validation inserts conversions. Synthetic defaults have no matching written expression.
     *
     * @param written  the arguments in source order, before validation rewrites
     * @param ordered  the same arguments arranged in parameter order, including synthetic defaults
     *
     * @return source-to-parameter associations, or empty if any written argument cannot be matched
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
        /** Ordinary compiler clients need not collect tooling facts. This instance never writes. */
        public static final Collector NONE = new Collector(false);

        private final boolean enabled;

        private final Map<InvocationExpression, InvocationBinding> bindings = new IdentityHashMap<>();
        private final Map<InvocationExpression, FunctionCall> functions = new IdentityHashMap<>();
        private final Map<NewExpression, InvocationBinding> constructors = new IdentityHashMap<>();
        private final Map<PropertyDeclarationStatement, InitializerBinding> initializers = new IdentityHashMap<>();

        /** Create an enabled call-fact collector for one compilation attempt. */
        public Collector() {
            this(true);
        }

        private Collector(boolean enabled) {
            this.enabled = enabled;
        }

        /** @return whether this attempt requests call facts */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Invalidate any earlier result before retrying validation of this invocation.
         *
         * @param invocation  the source invocation being validated
         */
        public void begin(InvocationExpression invocation) {
            if (enabled) {
                bindings.remove(invocation);
                functions.remove(invocation);
            }
        }

        /**
         * Record a completed validation; node identity keeps speculative clones separate.
         *
         * @param invocation  the source invocation being validated
         * @param binding     the successfully established facts for this source node
         */
        public void record(InvocationExpression invocation, InvocationBinding binding) {
            if (enabled) {
                bindings.put(invocation, binding);
            }
        }

        /**
         * Retain a successfully validated call's source associations.
         *
         * @param invocation  the source invocation being validated
         * @param binding     the successfully established facts for this source node
         */
        public void record(InvocationExpression invocation, FunctionCall binding) {
            if (enabled) {
                functions.put(invocation, binding);
            }
        }

        /**
         * Constructor arguments undergo the same named/default argument rewriting as methods.
         *
         * @param construction  the source construction being validated
         */
        public void begin(NewExpression construction) {
            if (enabled) {
                constructors.remove(construction);
            }
        }

        /**
         * Retain a successfully validated call's source associations.
         *
         * @param construction  the source construction being validated
         * @param binding       the successfully established facts for this source node
         */
        public void record(NewExpression construction, InvocationBinding binding) {
            if (enabled) {
                constructors.put(construction, binding);
            }
        }

        /**
         * A discarded synthetic initializer keeps detached facts anchored to its source property.
         *
         * @param property  the surviving property declaration that owns the initializer
         * @param binding   the successfully established facts for this source node
         */
        public void record(PropertyDeclarationStatement property, InitializerBinding binding) {
            if (enabled) {
                initializers.put(property, binding);
            }
        }

        /**
         * Finish the attempt, returning immutable facts for the surviving source trees.
         *
         * @param roots  the surviving source roots to publish facts for
         *
         * @return immutable method-call facts keyed by surviving invocation identity; scratch entries are cleared
         */
        public Map<InvocationExpression, InvocationBinding> finish(List<? extends AstNode> roots) {
            return finishFacts(roots).methods();
        }

        /**
         * Finish both kinds together, releasing discarded or speculative nodes in either map.
         *
         * @param roots  the surviving source roots to publish facts for
         *
         * @return immutable call and initializer facts for surviving syntax; scratch entries are cleared
         */
        public Facts finishFacts(List<? extends AstNode> roots) {
            Map<InvocationExpression, InvocationBinding> result = new IdentityHashMap<>();
            Map<InvocationExpression, FunctionCall> survivingFunctions = new IdentityHashMap<>();
            Map<NewExpression, InvocationBinding> survivingConstructors = new IdentityHashMap<>();
            Map<PropertyDeclarationStatement, InitializerBinding> survivingInitializers = new IdentityHashMap<>();
            List<AstNode> nodes = new ArrayList<>(roots);
            for (int i = 0; i < nodes.size(); ++i) {
                AstNode node = nodes.get(i);
                if (node instanceof InvocationExpression invocation &&
                        invocation.isValidated() && invocation.getTypeFit().isFit()) {
                    InvocationBinding binding = bindings.get(invocation);
                    if (binding != null) {
                        result.put(invocation, binding);
                    }
                    FunctionCall function = functions.get(invocation);
                    if (function != null) {
                        survivingFunctions.put(invocation, function);
                    }
                }
                if (node instanceof NewExpression construction && construction.isValidated() &&
                        construction.getTypeFit().isFit()) {
                    InvocationBinding binding = constructors.get(construction);
                    if (binding != null) {
                        survivingConstructors.put(construction, binding);
                    }
                }
                if (node instanceof PropertyDeclarationStatement property) {
                    InitializerBinding binding = initializers.get(property);
                    if (binding != null) {
                        survivingInitializers.put(property, binding);
                    }
                }
                node.children().forEachRemaining(nodes::add);
            }
            bindings.clear();
            functions.clear();
            constructors.clear();
            initializers.clear();
            return new Facts(result, survivingFunctions, survivingConstructors, survivingInitializers);
        }
    }
}
