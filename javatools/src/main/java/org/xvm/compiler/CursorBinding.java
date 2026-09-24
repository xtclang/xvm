package org.xvm.compiler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.xvm.asm.Register;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.IncompleteStatement;

/** Facts captured at an explicit cursor while its real validation context is alive. */
public record CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                            List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                            List<FunctionCandidate> functions) {
    public CursorBinding {
        variables = List.copyOf(variables);
        types = List.copyOf(types);
        candidates = List.copyOf(candidates);
        functions = List.copyOf(functions);
    }

    /** Retain callers that only consume method candidates. Record patterns must include functions. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected) {
        this(variables, thisType, instance, types, candidates, callsInspected, List.of());
    }

    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance) {
        this(variables, thisType, instance, List.of(), List.of(), false);
    }

    public CursorBinding withCandidates(List<Candidate> candidates) {
        return new CursorBinding(variables, thisType, instance, types, candidates, true, functions);
    }

    public CursorBinding withFunctions(List<FunctionCandidate> functions) {
        return new CursorBinding(variables, thisType, instance, types, candidates, true, functions);
    }

    public CursorBinding withTypes(List<NamedType> types) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected, functions);
    }

    public record NamedType(String name, IdentityConstant identity) {}

    /** Fits the written arguments; missing arguments cannot establish a selected overload. */
    public record Candidate(MethodConstant method, SignatureConstant signature,
                            List<InvocationBinding.Argument> arguments, boolean converting) {
        public Candidate {
            arguments = List.copyOf(arguments);
        }
    }

    /** A function type whose written arguments fit; there is no selected method or parameter names. */
    public record FunctionCandidate(TypeConstant type, List<InvocationBinding.Argument> arguments) {
        public FunctionCandidate {
            arguments = List.copyOf(arguments);
        }
    }

    /** A visible variable and its narrowed type; unreadable variables still shadow outer names. */
    public record Variable(String name, Register register, TypeConstant type, boolean readable) {}

    /** Attempt-owned scratch space; no context or collector is stored on a syntax node. */
    public static final class Collector {
        public Collector() {
            this(true);
        }

        private Collector(boolean enabled) {
            f_enabled = enabled;
        }

        public boolean isEnabled() {
            return f_enabled;
        }

        /** A validation retry must not retain facts from its earlier attempt. */
        public void begin(IncompleteStatement site) {
            if (f_enabled) {
                f_bindings.remove(site);
            }
        }

        public void record(IncompleteStatement site, CursorBinding binding) {
            if (f_enabled) {
                f_bindings.put(site, binding);
            }
        }

        /** Discard trial clones and release scratch entries when publishing surviving syntax. */
        public Map<IncompleteStatement, CursorBinding> finish(List<? extends AstNode> roots) {
            Map<IncompleteStatement, CursorBinding> result = new IdentityHashMap<>();
            roots.stream().flatMap(Collector::nodes)
                    .filter(IncompleteStatement.class::isInstance)
                    .map(IncompleteStatement.class::cast)
                    .filter(f_bindings::containsKey)
                    .forEach(site -> result.put(site, f_bindings.get(site)));
            f_bindings.clear();
            return Collections.unmodifiableMap(result);
        }

        private static Stream<AstNode> nodes(AstNode node) {
            return Stream.concat(Stream.of(node), StreamSupport.stream(node.children().spliterator(), false)
                    .flatMap(Collector::nodes));
        }

        public static final Collector NONE = new Collector(false);

        private final boolean f_enabled;

        private final Map<IncompleteStatement, CursorBinding> f_bindings = new IdentityHashMap<>();
    }
}
