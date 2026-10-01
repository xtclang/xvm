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
import org.xvm.compiler.ast.partial.IncompleteStatement;
/** Facts captured at an explicit cursor while its real validation context is alive. */
public record CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                            List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                            List<FunctionCandidate> functions, List<Variable> argumentValues,
                            List<Property> argumentProperties, List<Formal> formals,
                            List<String> argumentLiterals, List<String> argumentExpressions, List<String> enclosingExpressions,
                            List<String> argumentTemplates) {
    public CursorBinding {
        variables = List.copyOf(variables);
        types = List.copyOf(types);
        candidates = List.copyOf(candidates);
        functions = List.copyOf(functions);
        argumentValues = List.copyOf(argumentValues);
        argumentProperties = List.copyOf(argumentProperties);
        formals = List.copyOf(formals);
        argumentLiterals = List.copyOf(argumentLiterals);
        argumentExpressions = List.copyOf(argumentExpressions);
        enclosingExpressions = List.copyOf(enclosingExpressions);
        argumentTemplates = List.copyOf(argumentTemplates);
    }

    /** Retain ordinary-expression consumers predating compiler-fitted value templates. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals,
                         List<String> argumentLiterals, List<String> argumentExpressions, List<String> enclosingExpressions) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions, argumentValues,
                argumentProperties, formals, argumentLiterals, argumentExpressions, enclosingExpressions, List.of());
    }

    public CursorBinding withArgumentTemplates(List<String> templates) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected,
                functions, argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions,
                enclosingExpressions, templates);
    }

    /** Preserve the previous constructor while adding ordinary-expression proposals. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals,
                         List<String> argumentLiterals, List<String> argumentExpressions) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions, List.of());
    }

    public CursorBinding withEnclosingExpressions(List<String> expressions) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected,
                functions, argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions, expressions, argumentTemplates);
    }

    /** Retain callers predating enclosing-instance insertion proposals. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals, List<String> argumentLiterals) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, formals, argumentLiterals, List.of());
    }

    /** Retain callers predating literal insertion proposals. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, formals, List.of());
    }

    /** Retain the original complete constructor for existing embedding consumers. */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, List.of());
    }

    /**
     * A written formal, not an invented declaration identity. A recursive written constraint has
     * no resolved type until its declaration exists; only that case supplies text instead.
     */
    public record Formal(Token name, TypeConstant constraint, String writtenConstraint) {
        public Formal(Token name, TypeConstant constraint) {
            this(name, constraint, null);
        }

        public Formal {
            if ((constraint == null) == (writtenConstraint == null)) {
                throw new IllegalArgumentException("A formal needs either a resolved or a written constraint");
            }
        }
    }

    public CursorBinding withFormals(List<Formal> formals) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected,
                functions, argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions, enclosingExpressions, argumentTemplates);
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
                calls.functions(), calls.argumentValues(), calls.argumentProperties(), List.of(), calls.argumentLiterals(), calls.argumentExpressions());
    }

    /** A grouped immutable view; no context, mutable operation or AST child is introduced. */
    public CallFacts callFacts() {
        return new CallFacts(candidates, callsInspected, functions, argumentValues, argumentProperties, argumentLiterals, argumentExpressions);
    }

    private CursorBinding withCallFacts(CallFacts calls) {
        return new CursorBinding(variables, thisType, instance, types, calls).withFormals(formals).withEnclosingExpressions(enclosingExpressions).withArgumentTemplates(argumentTemplates);
    }

    public CursorBinding withCandidates(List<Candidate> candidates) {
        return withCallFacts(callFacts().withCandidates(candidates));
    }

    public CursorBinding withFunctions(List<FunctionCandidate> functions) {
        return withCallFacts(callFacts().withFunctions(functions));
    }

    public CursorBinding withTypes(List<NamedType> types) {
        return new CursorBinding(variables, thisType, instance, types, callFacts()).withFormals(formals).withEnclosingExpressions(enclosingExpressions).withArgumentTemplates(argumentTemplates);
    }

    /** Readable source variables whose proposed insertion fits at least one incomplete-call candidate. */
    public CursorBinding withArgumentValues(List<Variable> values) {
        return withCallFacts(callFacts().withArgumentValues(values));
    }

    /** Implicit property/constant reads whose insertion fits at least one incomplete-call candidate. */
    public CursorBinding withArgumentProperties(List<Property> properties) {
        return withCallFacts(callFacts().withArgumentProperties(properties));
    }

    /** Literal source spellings whose insertion fits and validates with the other written arguments. */
    public CursorBinding withArgumentLiterals(List<String> literals) {
        return withCallFacts(new CallFacts(candidates, callsInspected, functions, argumentValues, argumentProperties, literals, argumentExpressions));
    }

    /** Compiler-validated enclosing-instance source expressions, distinct from literals. */
    public CursorBinding withArgumentExpressions(List<String> expressions) {
        return withCallFacts(new CallFacts(candidates, callsInspected, functions, argumentValues,
                argumentProperties, argumentLiterals, expressions));
    }

    /**
     * Call inspection and insertion facts, separate from visible scope and syntax selection.
     * An inspected empty candidate list means rejection, not absence of inspection. Updating
     * argument values, properties or literals preserves that distinction. Previous constructors
     * remain available; record patterns must include the literal-proposal component.
     */
    public record CallFacts(List<Candidate> candidates, boolean inspected,
                            List<FunctionCandidate> functions, List<Variable> argumentValues,
                            List<Property> argumentProperties, List<String> argumentLiterals, List<String> argumentExpressions) {
        public CallFacts {
            candidates = List.copyOf(candidates);
            functions = List.copyOf(functions);
            argumentValues = List.copyOf(argumentValues);
            argumentProperties = List.copyOf(argumentProperties);
            argumentLiterals = List.copyOf(argumentLiterals);
            argumentExpressions = List.copyOf(argumentExpressions);
        }

        public CallFacts(List<Candidate> candidates, boolean inspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<String> argumentLiterals) {
            this(candidates, inspected, functions, argumentValues, argumentProperties, argumentLiterals, List.of());
        }

        public CallFacts(List<Candidate> candidates, boolean inspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties) {
            this(candidates, inspected, functions, argumentValues, argumentProperties, List.of());
        }

        public CallFacts withCandidates(List<Candidate> candidates) {
            return new CallFacts(candidates, true, functions, argumentValues, argumentProperties, argumentLiterals, argumentExpressions);
        }

        public CallFacts withFunctions(List<FunctionCandidate> functions) {
            return new CallFacts(candidates, true, functions, argumentValues, argumentProperties, argumentLiterals, argumentExpressions);
        }

        public CallFacts withArgumentValues(List<Variable> values) {
            return new CallFacts(candidates, inspected, functions, values, argumentProperties, argumentLiterals, argumentExpressions);
        }

        public CallFacts withArgumentProperties(List<Property> properties) {
            return new CallFacts(candidates, inspected, functions, argumentValues, properties, argumentLiterals, argumentExpressions);
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
                            List<InvocationBinding.Argument> arguments, boolean converting, boolean receiverArgument) {
        public Candidate {
            arguments = List.copyOf(arguments);
        }

        public Candidate(MethodConstant method, SignatureConstant signature,
                         List<InvocationBinding.Argument> arguments, boolean converting) {
            this(method, signature, arguments, converting, false);
        }

        public Candidate withReceiverArgument() {
            return new Candidate(method, signature, arguments, converting, true);
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
