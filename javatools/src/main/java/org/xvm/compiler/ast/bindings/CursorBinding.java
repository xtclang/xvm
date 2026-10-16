package org.xvm.compiler.ast.bindings;

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

import org.xvm.compiler.Token;
import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.partial.IncompleteStatement;

/**
 * Facts captured at an explicit cursor while its real validation context is alive.
 *
 * @param variables             visible variables with their narrowed types and read permissions
 * @param thisType              the enclosing receiver type, or null when no receiver is available
 * @param instance              whether the current scope permits instance access
 * @param types                 compiler-resolved type names visible at the cursor
 * @param candidates            methods whose signatures fit the written arguments
 * @param callsInspected        whether call fitting ran, distinguishing rejection from an uninspected call
 * @param functions             function signatures whose written arguments fit
 * @param argumentValues        readable variables whose insertion fits a call candidate
 * @param argumentProperties    property reads whose insertion fits a call candidate
 * @param formals               written formal names and constraints, without invented declaration identities
 * @param argumentLiterals      literal spellings validated as candidate argument insertions
 * @param argumentExpressions   enclosing-instance expressions validated as candidate argument insertions
 * @param enclosingExpressions  enclosing-instance expressions available in the current value context
 * @param argumentTemplates     compiler-fitted source templates for the argument slot
 */
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

    /**
     * Retain ordinary-expression consumers predating compiler-fitted value templates.
     *
     * @param variables             visible variables with their narrowed types and read permissions
     * @param thisType              the enclosing receiver type, or null when no receiver is available
     * @param instance              whether the current scope permits instance access
     * @param types                 compiler-resolved type names visible at the cursor
     * @param candidates            methods whose signatures fit the written arguments
     * @param callsInspected        whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions             function signatures whose written arguments fit
     * @param argumentValues        readable variables whose insertion fits a call candidate
     * @param argumentProperties    property reads whose insertion fits a call candidate
     * @param formals               written formal names and constraints, without invented declaration identities
     * @param argumentLiterals      literal spellings validated as candidate argument insertions
     * @param argumentExpressions   enclosing-instance expressions validated as candidate argument insertions
     * @param enclosingExpressions  enclosing-instance expressions available in the current value context
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals,
                         List<String> argumentLiterals, List<String> argumentExpressions, List<String> enclosingExpressions) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions, argumentValues,
                argumentProperties, formals, argumentLiterals, argumentExpressions, enclosingExpressions, List.of());
    }

    /**
     * Replace argument templates while preserving scope and other candidate facts.
     *
     * @param templates  replacement compiler-fitted argument templates
     *
     * @return a new binding containing the replacement templates
     */
    public CursorBinding withArgumentTemplates(List<String> templates) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected,
                functions, argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions,
                enclosingExpressions, templates);
    }

    /**
     * Preserve the previous constructor while adding ordinary-expression proposals.
     *
     * @param variables            visible variables with their narrowed types and read permissions
     * @param thisType             the enclosing receiver type, or null when no receiver is available
     * @param instance             whether the current scope permits instance access
     * @param types                compiler-resolved type names visible at the cursor
     * @param candidates           methods whose signatures fit the written arguments
     * @param callsInspected       whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions            function signatures whose written arguments fit
     * @param argumentValues       readable variables whose insertion fits a call candidate
     * @param argumentProperties   property reads whose insertion fits a call candidate
     * @param formals              written formal names and constraints, without invented declaration identities
     * @param argumentLiterals     literal spellings validated as candidate argument insertions
     * @param argumentExpressions  enclosing-instance expressions validated as candidate argument insertions
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals,
                         List<String> argumentLiterals, List<String> argumentExpressions) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions, List.of());
    }

    /**
     * Replace enclosing-instance value proposals while preserving call facts.
     *
     * @param expressions  replacement compiler-validated enclosing-instance expressions
     *
     * @return a new binding containing the replacement enclosing-instance expressions
     */
    public CursorBinding withEnclosingExpressions(List<String> expressions) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected,
                functions, argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions, expressions, argumentTemplates);
    }

    /**
     * Retain callers predating enclosing-instance insertion proposals.
     *
     * @param variables           visible variables with their narrowed types and read permissions
     * @param thisType            the enclosing receiver type, or null when no receiver is available
     * @param instance            whether the current scope permits instance access
     * @param types               compiler-resolved type names visible at the cursor
     * @param candidates          methods whose signatures fit the written arguments
     * @param callsInspected      whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions           function signatures whose written arguments fit
     * @param argumentValues      readable variables whose insertion fits a call candidate
     * @param argumentProperties  property reads whose insertion fits a call candidate
     * @param formals             written formal names and constraints, without invented declaration identities
     * @param argumentLiterals    literal spellings validated as candidate argument insertions
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals, List<String> argumentLiterals) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, formals, argumentLiterals, List.of());
    }

    /**
     * Retain callers predating literal insertion proposals.
     *
     * @param variables           visible variables with their narrowed types and read permissions
     * @param thisType            the enclosing receiver type, or null when no receiver is available
     * @param instance            whether the current scope permits instance access
     * @param types               compiler-resolved type names visible at the cursor
     * @param candidates          methods whose signatures fit the written arguments
     * @param callsInspected      whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions           function signatures whose written arguments fit
     * @param argumentValues      readable variables whose insertion fits a call candidate
     * @param argumentProperties  property reads whose insertion fits a call candidate
     * @param formals             written formal names and constraints, without invented declaration identities
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<Formal> formals) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions,
                argumentValues, argumentProperties, formals, List.of());
    }

    /**
     * Retain the original complete constructor for existing embedding consumers.
     *
     * @param variables           visible variables with their narrowed types and read permissions
     * @param thisType            the enclosing receiver type, or null when no receiver is available
     * @param instance            whether the current scope permits instance access
     * @param types               compiler-resolved type names visible at the cursor
     * @param candidates          methods whose signatures fit the written arguments
     * @param callsInspected      whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions           function signatures whose written arguments fit
     * @param argumentValues      readable variables whose insertion fits a call candidate
     * @param argumentProperties  property reads whose insertion fits a call candidate
     */
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
     *
     * @param name               the written formal name token
     * @param constraint         the resolved constraint, or null when only its recursive source spelling is
     *                           available
     * @param writtenConstraint  the recursive source spelling, or null when the constraint is resolved
     */
    public record Formal(Token name, TypeConstant constraint, String writtenConstraint) {
        /**
         * Capture a written formal with a resolved constraint.
         *
         * @param name        the written formal name token
         * @param constraint  the resolved formal constraint
         */
        public Formal(Token name, TypeConstant constraint) {
            this(name, constraint, null);
        }

        public Formal {
            if ((constraint == null) == (writtenConstraint == null)) {
                throw new IllegalArgumentException("A formal needs either a resolved or a written constraint");
            }
        }
    }

    /**
     * Replace written formals while preserving scope and call facts.
     *
     * @param formals  written formal names and constraints, without invented declaration identities
     *
     * @return a new binding containing the replacement written formals
     */
    public CursorBinding withFormals(List<Formal> formals) {
        return new CursorBinding(variables, thisType, instance, types, candidates, callsInspected,
                functions, argumentValues, argumentProperties, formals, argumentLiterals, argumentExpressions, enclosingExpressions, argumentTemplates);
    }

    /**
     * Retain variable-completion callers. Record patterns must also include argument properties.
     *
     * @param variables       visible variables with their narrowed types and read permissions
     * @param thisType        the enclosing receiver type, or null when no receiver is available
     * @param instance        whether the current scope permits instance access
     * @param types           compiler-resolved type names visible at the cursor
     * @param candidates      methods whose signatures fit the written arguments
     * @param callsInspected  whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions       function signatures whose written arguments fit
     * @param argumentValues  readable variables whose insertion fits a call candidate
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions, argumentValues, List.of());
    }

    /**
     * Retain signature-only callers.
     *
     * @param variables       visible variables with their narrowed types and read permissions
     * @param thisType        the enclosing receiver type, or null when no receiver is available
     * @param instance        whether the current scope permits instance access
     * @param types           compiler-resolved type names visible at the cursor
     * @param candidates      methods whose signatures fit the written arguments
     * @param callsInspected  whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions       function signatures whose written arguments fit
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected,
                         List<FunctionCandidate> functions) {
        this(variables, thisType, instance, types, candidates, callsInspected, functions, List.of());
    }

    /**
     * Retain callers that only consume method candidates.
     *
     * @param variables       visible variables with their narrowed types and read permissions
     * @param thisType        the enclosing receiver type, or null when no receiver is available
     * @param instance        whether the current scope permits instance access
     * @param types           compiler-resolved type names visible at the cursor
     * @param candidates      methods whose signatures fit the written arguments
     * @param callsInspected  whether call fitting ran, distinguishing rejection from an uninspected call
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, List<Candidate> candidates, boolean callsInspected) {
        this(variables, thisType, instance, types, candidates, callsInspected, List.of());
    }

    /**
     * Capture visible variables and receiver context without inspecting calls.
     *
     * @param variables  visible variables with their narrowed types and read permissions
     * @param thisType   the enclosing receiver type, or null when no receiver is available
     * @param instance   whether the current scope permits instance access
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance) {
        this(variables, thisType, instance, List.of(), List.of(), false);
    }

    /**
     * Group call-specific facts while retaining the existing constructors and record components.
     *
     * @param variables  visible variables with their narrowed types and read permissions
     * @param thisType   the enclosing receiver type, or null when no receiver is available
     * @param instance   whether the current scope permits instance access
     * @param types      compiler-resolved type names visible at the cursor
     * @param calls      the inspected call candidates and insertion facts
     */
    public CursorBinding(List<Variable> variables, TypeConstant thisType, boolean instance,
                         List<NamedType> types, CallFacts calls) {
        this(variables, thisType, instance, types, calls.candidates(), calls.inspected(),
                calls.functions(), calls.argumentValues(), calls.argumentProperties(), List.of(), calls.argumentLiterals(), calls.argumentExpressions());
    }

    /**
     * A grouped immutable view; no context, mutable operation or AST child is introduced.
     *
     * @return an immutable grouped view of the current call candidates and insertion facts
     */
    public CallFacts callFacts() {
        return new CallFacts(candidates, callsInspected, functions, argumentValues, argumentProperties, argumentLiterals, argumentExpressions);
    }

    private CursorBinding withCallFacts(CallFacts calls) {
        return new CursorBinding(variables, thisType, instance, types, calls).withFormals(formals).withEnclosingExpressions(enclosingExpressions).withArgumentTemplates(argumentTemplates);
    }

    /**
     * Replace method candidates and mark the call as inspected.
     *
     * @param candidates  methods whose signatures fit the written arguments
     *
     * @return a new binding with the replacement methods and call inspection recorded
     */
    public CursorBinding withCandidates(List<Candidate> candidates) {
        return withCallFacts(callFacts().withCandidates(candidates));
    }

    /**
     * Replace function candidates and mark the call as inspected.
     *
     * @param functions  function signatures whose written arguments fit
     *
     * @return a new binding with the replacement functions and call inspection recorded
     */
    public CursorBinding withFunctions(List<FunctionCandidate> functions) {
        return withCallFacts(callFacts().withFunctions(functions));
    }

    /**
     * Replace visible type names while preserving scope and call facts.
     *
     * @param types  compiler-resolved type names visible at the cursor
     *
     * @return a new binding containing the replacement visible type names
     */
    public CursorBinding withTypes(List<NamedType> types) {
        return new CursorBinding(variables, thisType, instance, types, callFacts()).withFormals(formals).withEnclosingExpressions(enclosingExpressions).withArgumentTemplates(argumentTemplates);
    }

    /**
     * Readable source variables whose proposed insertion fits at least one incomplete-call candidate.
     *
     * @param values  replacement readable variable insertions that fit a call candidate
     *
     * @return a new binding containing the replacement variable insertions
     */
    public CursorBinding withArgumentValues(List<Variable> values) {
        return withCallFacts(callFacts().withArgumentValues(values));
    }

    /**
     * Implicit property/constant reads whose insertion fits at least one incomplete-call candidate.
     *
     * @param properties  replacement property insertions that fit a call candidate
     *
     * @return a new binding containing the replacement property insertions
     */
    public CursorBinding withArgumentProperties(List<Property> properties) {
        return withCallFacts(callFacts().withArgumentProperties(properties));
    }

    /**
     * Literal source spellings whose insertion fits and validates with the other written arguments.
     *
     * @param literals  replacement validated literal source spellings
     *
     * @return a new binding containing the replacement literal insertions
     */
    public CursorBinding withArgumentLiterals(List<String> literals) {
        return withCallFacts(new CallFacts(candidates, callsInspected, functions, argumentValues, argumentProperties, literals, argumentExpressions));
    }

    /**
     * Compiler-validated enclosing-instance source expressions, distinct from literals.
     *
     * @param expressions  replacement compiler-validated enclosing-instance expressions
     *
     * @return a new binding containing the replacement expression insertions
     */
    public CursorBinding withArgumentExpressions(List<String> expressions) {
        return withCallFacts(new CallFacts(candidates, callsInspected, functions, argumentValues,
                argumentProperties, argumentLiterals, expressions));
    }

    /**
     * Call inspection and insertion facts, separate from visible scope and syntax selection.
     * An inspected empty candidate list means rejection, not absence of inspection. Updating
     * argument values, properties or literals preserves that distinction. Previous constructors
     * remain available; record patterns must include the literal-proposal component.
     *
     * @param candidates           methods whose signatures fit the written arguments
     * @param inspected            whether call fitting ran, distinguishing rejection from an uninspected call
     * @param functions            function signatures whose written arguments fit
     * @param argumentValues       readable variables whose insertion fits a call candidate
     * @param argumentProperties   property reads whose insertion fits a call candidate
     * @param argumentLiterals     literal spellings validated as candidate argument insertions
     * @param argumentExpressions  enclosing-instance expressions validated as candidate argument insertions
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

        /**
         * Capture call candidates and insertion facts; omitted proposal kinds remain empty.
         *
         * @param candidates          methods whose signatures fit the written arguments
         * @param inspected           whether call fitting ran, distinguishing rejection from an uninspected call
         * @param functions           function signatures whose written arguments fit
         * @param argumentValues      readable variables whose insertion fits a call candidate
         * @param argumentProperties  property reads whose insertion fits a call candidate
         * @param argumentLiterals    literal spellings validated as candidate argument insertions
         */
        public CallFacts(List<Candidate> candidates, boolean inspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties, List<String> argumentLiterals) {
            this(candidates, inspected, functions, argumentValues, argumentProperties, argumentLiterals, List.of());
        }

        /**
         * Capture call candidates and insertion facts; omitted proposal kinds remain empty.
         *
         * @param candidates          methods whose signatures fit the written arguments
         * @param inspected           whether call fitting ran, distinguishing rejection from an uninspected call
         * @param functions           function signatures whose written arguments fit
         * @param argumentValues      readable variables whose insertion fits a call candidate
         * @param argumentProperties  property reads whose insertion fits a call candidate
         */
        public CallFacts(List<Candidate> candidates, boolean inspected,
                         List<FunctionCandidate> functions, List<Variable> argumentValues,
                         List<Property> argumentProperties) {
            this(candidates, inspected, functions, argumentValues, argumentProperties, List.of());
        }

        /**
         * Replace method candidates and mark the call as inspected.
         *
         * @param candidates  methods whose signatures fit the written arguments
         *
         * @return new call facts with the replacement methods and inspection recorded
         */
        public CallFacts withCandidates(List<Candidate> candidates) {
            return new CallFacts(candidates, true, functions, argumentValues, argumentProperties, argumentLiterals, argumentExpressions);
        }

        /**
         * Replace function candidates and mark the call as inspected.
         *
         * @param functions  function signatures whose written arguments fit
         *
         * @return new call facts with the replacement functions and inspection recorded
         */
        public CallFacts withFunctions(List<FunctionCandidate> functions) {
            return new CallFacts(candidates, true, functions, argumentValues, argumentProperties, argumentLiterals, argumentExpressions);
        }

        /**
         * Replace variable insertions without changing whether the call was inspected.
         *
         * @param values  replacement readable variable insertions that fit a call candidate
         *
         * @return new call facts containing the replacement variable insertions
         */
        public CallFacts withArgumentValues(List<Variable> values) {
            return new CallFacts(candidates, inspected, functions, values, argumentProperties, argumentLiterals, argumentExpressions);
        }

        /**
         * Replace property insertions without changing whether the call was inspected.
         *
         * @param properties  replacement property insertions that fit a call candidate
         *
         * @return new call facts containing the replacement property insertions
         */
        public CallFacts withArgumentProperties(List<Property> properties) {
            return new CallFacts(candidates, inspected, functions, argumentValues, properties, argumentLiterals, argumentExpressions);
        }
    }

    /** A compiler-resolved type name; type preserves parameterized qualifier substitution. */
    public record NamedType(String name, IdentityConstant identity, TypeConstant type) {
        /**
         * Capture a type name using its declaration's own type without qualifier substitution.
         *
         * @param name      the source-visible type name
         * @param identity  the compiler-resolved declaration identity
         */
        public NamedType(String name, IdentityConstant identity) {
            this(name, identity, identity.getType());
        }
    }

    /**
     * Fits the written arguments; missing arguments cannot establish a selected overload.
     *
     * @param method            the compiler-selected method identity
     * @param signature         the instantiated signature fitted at this source site
     * @param arguments         written argument spans associated with visible parameter indices
     * @param converting        whether fitting requires argument conversion
     * @param receiverArgument  whether the explicit receiver occupies an argument slot
     */
    public record Candidate(MethodConstant method, SignatureConstant signature,
                            List<InvocationBinding.Argument> arguments, boolean converting, boolean receiverArgument) {
        public Candidate {
            arguments = List.copyOf(arguments);
        }

        /**
         * Capture a fitted method candidate without a receiver argument.
         *
         * @param method      the compiler-selected method identity
         * @param signature   the instantiated signature fitted at this source site
         * @param arguments   source-to-parameter associations for the written arguments
         * @param converting  whether fitting requires argument conversion
         */
        public Candidate(MethodConstant method, SignatureConstant signature,
                         List<InvocationBinding.Argument> arguments, boolean converting) {
            this(method, signature, arguments, converting, false);
        }

        /**
         * Mark the receiver as occupying a call argument slot.
         *
         * @return a new candidate whose receiver occupies an argument slot
         */
        public Candidate withReceiverArgument() {
            return new Candidate(method, signature, arguments, converting, true);
        }
    }

    /**
     * A function type whose written arguments fit; there is no selected method or parameter names.
     *
     * @param type       the compiler-resolved value or function type
     * @param arguments  written argument spans associated with visible parameter indices
     */
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
        /** Create an enabled cursor-fact collector for one compilation attempt. */
        public Collector() {
            this(true);
        }

        private Collector(boolean enabled) {
            f_enabled = enabled;
        }

        /**
         * Determine whether this attempt requests cursor facts.
         *
         * @return whether this collector records cursor facts
         */
        public boolean isEnabled() {
            return f_enabled;
        }

        /**
         * A validation retry must not retain facts from its earlier attempt.
         *
         * @param site  the retained incomplete source site
         */
        public void begin(IncompleteStatement site) {
            if (f_enabled) {
                f_bindings.remove(site);
            }
        }

        /**
         * Retain the facts captured for this incomplete source site.
         *
         * @param site     the retained incomplete source site
         * @param binding  the successfully established facts for this source node
         */
        public void record(IncompleteStatement site, CursorBinding binding) {
            if (f_enabled) {
                f_bindings.put(site, binding);
            }
        }

        /**
         * Discard trial clones and release scratch entries when publishing surviving syntax.
         *
         * @param roots  the surviving source roots to publish facts for
         *
         * @return immutable facts keyed by surviving site identity; scratch entries are cleared
         */
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
