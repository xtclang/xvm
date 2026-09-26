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
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.IncompleteStatement;

/** Facts captured at an explicit cursor while its real validation context is alive. */
public record CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                            List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                            List<FunctionCandidate> functions, List<Variable> argumentValues,
                            List<Property> argumentProperties) {
    public CursorBinding {
        variables = List.copyOf(variables);
        types = List.copyOf(types);
        candidates = List.copyOf(candidates);
        functions = List.copyOf(functions);
        argumentValues = List.copyOf(argumentValues);
        argumentProperties = List.copyOf(argumentProperties);
    }

    /** Retain variable-completion callers. Record patterns must also include argument properties. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions, argumentValues, List.of());
    }

    /** Retain signature-only callers. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions, List.of());
    }

    /** Retain callers that only consume method candidates. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected) {
        this(variables, thisType, instance, types, candidates, callsInspected, List.of());
    }

    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance) {
        this(variables, thisType, instance, List.of(), List.of(), false);
    }

    /** Group call-specific facts while retaining the existing constructors and record components. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, CallFacts calls) {
        this(variables, thisType, instance, types, calls.candidates(), calls.inspected(),
                calls.functions(), calls.argumentValues(), calls.argumentProperties());
    }

    /** A grouped immutable view; no context, mutable operation or AST child is introduced. */
    public CallFacts callFacts() {
        return new CallFacts(candidates, callsInspected, functions, argumentValues, argumentProperties);
    }

    private CursorBinding withCallFacts(CallFacts calls) {
        return new CursorBinding(variables, thisType, instance, types, calls);
    }

    public CursorBinding withCandidates(List<Candidate> candidates) {
        return withCallFacts(callFacts().withCandidates(candidates));
    }

    public CursorBinding withFunctions(List<FunctionCandidate> functions) {
        return withCallFacts(callFacts().withFunctions(functions));
    }

    public CursorBinding withTypes(List<NamedType> types) {
        return new CursorBinding(variables, thisType, instance, types, callFacts());
    }

    /** Readable source variables whose proposed insertion fits at least one incomplete-call candidate. */
    public CursorBinding withArgumentValues(List<Variable> values) {
        return withCallFacts(callFacts().withArgumentValues(values));
    }

    /** Implicit property/constant reads whose insertion fits at least one incomplete-call candidate. */
    public CursorBinding withArgumentProperties(List<Property> properties) {
        return withCallFacts(callFacts().withArgumentProperties(properties));
    }

    /**
     * Call inspection and insertion facts, separate from visible scope and syntax selection.
     * An inspected empty candidate list means rejection, not absence of inspection. Updating
     * argument values or properties preserves that distinction. The outer record retains its
     * component list so existing record patterns, accessors and construction APIs keep working.
     */
    public record CallFacts(List<Candidate> candidates, boolean inspected,
                            List<FunctionCandidate> functions, List<Variable> argumentValues,
                            List<Property> argumentProperties) {
        public CallFacts {
            candidates = List.copyOf(candidates);
            functions = List.copyOf(functions);
            argumentValues = List.copyOf(argumentValues);
            argumentProperties = List.copyOf(argumentProperties);
        }

        public CallFacts withCandidates(List<Candidate> candidates) {
            return new CallFacts(candidates, true, functions, argumentValues, argumentProperties);
        }

        public CallFacts withFunctions(List<FunctionCandidate> functions) {
            return new CallFacts(candidates, true, functions, argumentValues, argumentProperties);
        }

        public CallFacts withArgumentValues(List<Variable> values) {
            return new CallFacts(candidates, inspected, functions, values, argumentProperties);
        }

        public CallFacts withArgumentProperties(List<Property> properties) {
            return new CallFacts(candidates, inspected, functions, argumentValues, properties);
        }
    }

    /** A compiler-resolved type name; type preserves parameterized qualifier substitution. */
    public record NamedType(String name, IdentityConstant identity, TypeConstant type) {
        public NamedType(String name, IdentityConstant identity) {
            this(name, identity, identity.getType());
        }
    }

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

    /** A property read, resolved in the cursor's context, and its validated value type. */
    public record Property(String name, PropertyConstant identity, TypeConstant type) {}

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
