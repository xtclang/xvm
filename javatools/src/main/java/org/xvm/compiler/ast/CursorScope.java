package org.xvm.compiler.ast;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;
import org.xvm.asm.TypedefStructure;
import org.xvm.asm.constants.ChildInfo;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeParameterConstant;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.partial.IncompleteStatement;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/** Capture cursor scope facts and resolve type-name candidates through normal contextual lookup. */
final class CursorScope {
    /** Copy scope facts now; callers must not retain this validation context. */
    static CursorBinding capture(Context ctx) {
        // Parameter registers are initialized lazily, including their assignment state.
        Stream.iterate(ctx, Objects::nonNull, Context::getOuterContext).forEach(Context::getNameMap);
        Set<String> names = new HashSet<>();
        ctx.collectVariables(names);
        var variables = names.stream().sorted()
                .filter(name -> !ctx.isReservedName(name))
                .map(name -> ctx.getVar(name) instanceof Register register
                        ? new CursorBinding.Variable(name, register, register.getType(), ctx.isVarReadable(name))
                        : null)
                .filter(Objects::nonNull)
                .toList();
        return new CursorBinding(variables, ctx.getThisType(), !ctx.isFunction());
    }

    static List<CursorBinding.NamedType> types(IncompleteStatement site, Context ctx, ErrorListener errs) {
        String prefix = site.getCompletionPrefix();
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        return names(site).stream().filter(name -> name.startsWith(prefix)).sorted()
                .takeWhile(name -> !errs.isAbortDesired())
                .map(name -> {
                    var token = new Token(site.getEndPosition(), site.getEndPosition(), Id.IDENTIFIER, name);
                    var target = ctx.resolveName(token, probe);
                    return namedType(name, target);
                }).filter(Objects::nonNull).toList();
    }

    /** Header lookup uses the real enclosing declaration; no method Context is invented. */
    static List<CursorBinding.NamedType> declarationTypes(IncompleteStatement site, AstNode scope,
            List<Parameter> writtenFormals, ErrorListener errs) {
        if (site.getTarget() instanceof NamedTypeExpression type) {
            if (writtenFormals.stream().anyMatch(formal -> formal.getName().equals(firstName(type)))) {
                if (type.left != null || type.getNames().length < 2) {
                    return List.of();
                }
                var parameters = writtenFormals.stream()
                        .collect(Collectors.toMap(Parameter::getName, Function.identity(), (first, second) -> first));
                var qualifier = formalConstraint(parameters.get(firstName(type)), scope, parameters, Set.of(), errs);
                if (qualifier == null) {
                    return List.of();
                }
                // NameResolver's FORMAL_TYPE mode permits virtual child classes, not typedefs
                // or static nested types. An upper bound must not erase that distinction.
                var candidates = parameterizedTypes(new NamedTypeExpression(
                        qualifier, type.names.subList(1, type.names.size()), type.paramTypes, type.getEndPosition()),
                        scope, site.getCompletionPrefix(), errs);
                if (type.names.size() == 2) {
                    return candidates.stream().filter(candidate ->
                            candidate.identity().getComponent() instanceof ClassStructure child && child.isVirtualChild()).toList();
                }
                var bound = formalBound(parameters.get(firstName(type)), scope, parameters, Set.of(), errs);
                var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
                var first = bound == null ? null : bound.ensureTypeInfo(scope.getComponent().getIdentityConstant(), probe)
                        .getChildInfosByName().get(type.names.get(1).getValueText());
                return first != null && first.getIdentity().getComponent() instanceof ClassStructure child
                        && child.isVirtualChild() && !probe.hasSeriousErrors() && !probe.isAbortDesired() ? candidates : List.of();
            }
            if (type.left != null) {
                return parameterizedTypes(type, scope, site.getCompletionPrefix(), errs);
            }
            if (type.getNames().length > 1) {
                return qualifiedTypes(type, scope, site.getCompletionPrefix(), errs);
            }
        }
        String prefix = site.getCompletionPrefix();
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        return names(site, writtenFormals).stream().filter(name -> name.startsWith(prefix)).sorted()
                .takeWhile(name -> !errs.isAbortDesired())
                .map(name -> namedType(name, new NameResolver(scope, name).forceResolve(probe)))
                .filter(Objects::nonNull).toList();
    }

    private static String firstName(NamedTypeExpression type) {
        return type.left instanceof NamedTypeExpression left ? firstName(left) : type.getNames()[0];
    }

    /** Resolve bounds on disposable syntax only; no unfinished class/method or formal is registered. */
    static List<CursorBinding.Formal> declarationFormals(IncompleteStatement site, AstNode scope,
            List<Parameter> writtenFormals, ErrorListener errs) {
        return site.getTarget() instanceof NamedTypeExpression named
                && (named.left != null || named.getNames().length > 1) ? List.of() : resolveFormals(site, scope, writtenFormals, errs);
    }

    private static List<CursorBinding.Formal> resolveFormals(IncompleteStatement site, AstNode scope, List<Parameter> parameters,
            ErrorListener errs) {
        var byName = parameters.stream().collect(Collectors.toMap(Parameter::getName, Function.identity(),
                (first, second) -> first));
        return parameters.stream().takeWhile(parameter -> !errs.isAbortDesired()).map(parameter -> {
            if (parameter.getType() instanceof BadTypeExpression bad && bad.nonType instanceof TypeExpression written
                    && site.getTarget() instanceof NamedTypeExpression selected
                    && parameter.getName().startsWith(site.getCompletionPrefix())) {
                var proposal = completeBound(written, selected.getNameToken(), parameter.getNameToken());
                // Only the candidate's own guarded cycle may complete an unfinished bound. Keep
                // its written text; this probe creates no formal identity or registered signature.
                return writtenBound(proposal, scope, byName, Map.of(parameter.getName(), 0), 0, errs) == BoundSyntax.RECURSIVE
                        ? new CursorBinding.Formal(parameter.getNameToken(), null, written + " (incomplete)") : null;
            }
            TypeConstant bound = formalBound(parameter, scope, byName, Set.of(), errs);
            if (bound != null) {
                return new CursorBinding.Formal(parameter.getNameToken(), bound);
            }
            return writtenBound(parameter.getType(), scope, byName, Map.of(parameter.getName(), 0), 0, errs)
                    == BoundSyntax.RECURSIVE
                    ? new CursorBinding.Formal(parameter.getNameToken(), null, parameter.getType().toString()) : null;
        }).filter(Objects::nonNull).toList();
    }

    /** Replace just the selected leaf on disposable syntax, retaining every other bound operand. */
    private static TypeExpression completeBound(TypeExpression written, Token selected, Token name) {
        if (written instanceof NamedTypeExpression named && named.left == null && named.paramTypes == null
                && named.getNames().length == 1
                && named.getNameToken().getStartPosition() == selected.getStartPosition()) {
            return new NamedTypeExpression(null, List.of(name), null, null, null, named.getEndPosition());
        }
        var copy = (TypeExpression) written.clone();
        StreamSupport.stream(copy.children().spliterator(), false).filter(TypeExpression.class::isInstance)
                .map(TypeExpression.class::cast).toList()
                .forEach(child -> copy.replaceChild(child, completeBound(child, selected, name)));
        return copy;
    }

    /**
     * Recognize guarded written recursion without assigning it a type. A cycle must cross a real
     * class's type-argument boundary; aliases and direct/sibling cycles cannot establish one.
     * Unknown or inaccessible names still reject the constraint. Qualified formal members need a
     * resolved bound and deliberately do not use this syntax-only fallback.
     */
    private static BoundSyntax writtenBound(TypeExpression type, AstNode scope,
            Map<String, Parameter> parameters, Map<String, Integer> resolving, int depth, ErrorListener errs) {
        if (errs.isAbortDesired()) {
            return BoundSyntax.INVALID;
        }
        // These operators preserve the written recursion guard; none creates a class boundary.
        // Resolve every operand so an unknown name or an unguarded sibling cycle still rejects it.
        if (type instanceof NullableTypeExpression || type instanceof DecoratedTypeExpression
                || type instanceof BiTypeExpression) {
            return StreamSupport.stream(type.children().spliterator(), false)
                    .filter(TypeExpression.class::isInstance).map(TypeExpression.class::cast)
                    .map(child -> writtenBound(child, scope, parameters, resolving, depth, errs))
                    .reduce(BoundSyntax.COMPLETE, BoundSyntax::combine);
        }
        if (!(type instanceof NamedTypeExpression named) || named.left != null) {
            return BoundSyntax.INVALID;
        }
        String name = named.getNames()[0];
        if (parameters.containsKey(name)) {
            if (named.getNames().length != 1 || named.paramTypes != null) {
                return BoundSyntax.INVALID;
            }
            if (resolving.containsKey(name)) {
                return depth > resolving.get(name) ? BoundSyntax.RECURSIVE : BoundSyntax.INVALID;
            }
            var parameter = parameters.get(name);
            if (parameter.getType() == null) {
                return BoundSyntax.COMPLETE;
            }
            var active = Stream.concat(resolving.entrySet().stream(), Stream.of(Map.entry(name, depth)))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            return writtenBound(parameter.getType(), scope, parameters, active, depth, errs);
        }
        var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var reference = new NamedTypeExpression(null, named.names, named.access, null, null, named.getEndPosition());
        reference.setParent(scope);
        var target = new NameResolver(reference, Arrays.asList(named.getNames()).iterator()).forceResolve(probe);
        if (!(target instanceof IdentityConstant identity) || !isTypeIdentity(identity)
                || !visible(identity, scope.getComponent().getIdentityConstant())
                || probe.hasSeriousErrors() || probe.isAbortDesired()) {
            return BoundSyntax.INVALID;
        }
        int nested = depth + (identity.getComponent() instanceof ClassStructure ? 1 : 0);
        return named.paramTypes == null ? BoundSyntax.COMPLETE : named.paramTypes.stream()
                .map(argument -> writtenBound(argument, scope, parameters, resolving, nested, errs))
                .reduce(BoundSyntax.COMPLETE, BoundSyntax::combine);
    }

    private enum BoundSyntax {
        COMPLETE, RECURSIVE, INVALID;

        BoundSyntax combine(BoundSyntax other) {
            return switch (this) {
                case INVALID -> INVALID;
                case RECURSIVE -> other == INVALID ? INVALID : RECURSIVE;
                case COMPLETE -> other;
            };
        }
    }

    private static TypeConstant formalBound(Parameter parameter, AstNode scope, Map<String, Parameter> parameters,
                                           Set<String> resolving, ErrorListener errs) {
        var copy = formalConstraint(parameter, scope, parameters, resolving, errs);
        if (copy == null) {
            return null;
        }
        copy.setParent(scope);
        var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        TypeConstant bound = resolveType(copy, probe);
        return bound != null && visibleNames(copy, scope.getComponent().getIdentityConstant()) ? bound : null;
    }

    /** Keep real bound syntax: NameResolver must never interpret a TypeConstant's display string. */
    private static TypeExpression formalConstraint(Parameter parameter, AstNode scope,
            Map<String, Parameter> parameters, Set<String> resolving, ErrorListener errs) {
        if (resolving.contains(parameter.getName()) || errs.isAbortDesired()) {
            return null;
        }
        if (parameter.getType() == null) {
            return new NamedTypeExpression(new NameExpression(parameter.getNameToken()), scope.pool().typeObject());
        }
        var active = Stream.concat(resolving.stream(), Stream.of(parameter.getName()))
                .collect(Collectors.toUnmodifiableSet());
        return substituteFormals((TypeExpression) parameter.getType().clone(), scope, parameters, active, errs);
    }

    /** Written references to sibling formals resolve through their bounds, never an outer homonym. */
    private static TypeExpression substituteFormals(TypeExpression type, AstNode scope,
            Map<String, Parameter> parameters, Set<String> resolving, ErrorListener errs) {
        if (type instanceof BadTypeExpression) {
            return null;
        }
        if (type instanceof NamedTypeExpression named && named.left == null
                && parameters.containsKey(named.getNames()[0])) {
            TypeExpression replacement = formalConstraint(parameters.get(named.getNames()[0]), scope, parameters, resolving, errs);
            if (replacement == null || named.paramTypes != null) {
                return null;
            }
            return named.getNames().length == 1 ? replacement
                    : new NamedTypeExpression(replacement, named.names.subList(1, named.names.size()),
                            null, named.getEndPosition());
        }
        var children = StreamSupport.stream(type.children().spliterator(), false).toList();
        for (AstNode child : children) {
            if (child instanceof TypeExpression childType) {
                TypeExpression replacement = substituteFormals(childType, scope, parameters, resolving, errs);
                if (replacement == null) {
                    return null;
                }
                type.replaceChild(child, replacement);
            }
        }
        return type;
    }

    /** Use contextual TypeInfo to enumerate children, then normal type-name resolution to bind them. */
    private static List<CursorBinding.NamedType> qualifiedTypes(NamedTypeExpression type, AstNode scope,
                                                               String prefix, ErrorListener errs) {
        var names = Arrays.asList(type.getNames());
        var qualifier = names.subList(0, names.size() - 1);
        var owner = scope.getComponent().getIdentityConstant();
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var resolver = new NameResolver(type, qualifier.iterator());
        var target = resolver.forceResolve(lookup);
        if (!(target instanceof IdentityConstant identity) || !isTypeIdentity(identity)
                || lookup.hasSeriousErrors() || lookup.isAbortDesired() || !visible(identity, owner)) {
            return List.of();
        }
        var info = identity.getType().ensureTypeInfo(owner, lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return List.of();
        }
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        return info.getChildInfosByName().values().stream()
                .filter(child -> child.getName().startsWith(prefix))
                .filter(child -> info.getType().getAccess().canSee(child.getAccess())
                        || child.getIdentity().getClassIdentity().isNestMateOf(owner))
                .sorted(Comparator.comparing(ChildInfo::getName))
                .takeWhile(child -> !errs.isAbortDesired())
                .map(child -> {
                    String name = child.getName();
                    var resolved = new NameResolver(type, Stream.concat(qualifier.stream(), Stream.of(name)).iterator())
                            .forceResolve(probe);
                    return resolved instanceof IdentityConstant id && visible(id, owner) ? namedType(name, id) : null;
                }).filter(Objects::nonNull).toList();
    }

    /** Resolve only a disposable copy of the complete written qualifier, including its arguments. */
    private static List<CursorBinding.NamedType> parameterizedTypes(NamedTypeExpression type, AstNode scope,
                                                                   String prefix, ErrorListener errs) {
        var left = (TypeExpression) type.left.clone();
        TypeExpression qualifier = type.names.size() == 1 ? left
                : new NamedTypeExpression(left, type.names.subList(0, type.names.size() - 1), null,
                        type.names.get(type.names.size() - 2).getEndPosition());
        qualifier.setParent(scope);
        var lookup = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
        var owner = scope.getComponent().getIdentityConstant();
        TypeConstant resolved = resolveType(qualifier, lookup);
        if (resolved == null || !visibleNames(qualifier, owner)) {
            return List.of();
        }
        var info = resolved.ensureTypeInfo(owner, lookup);
        if (lookup.hasSeriousErrors() || lookup.isAbortDesired()) {
            return List.of();
        }
        return info.getChildInfosByName().values().stream()
                .filter(child -> child.getName().startsWith(prefix))
                .filter(child -> info.getType().getAccess().canSee(child.getAccess())
                        || child.getIdentity().getClassIdentity().isNestMateOf(owner))
                .sorted(Comparator.comparing(ChildInfo::getName))
                .takeWhile(child -> !errs.isAbortDesired())
                .map(child -> {
                    Token written = type.getNameToken();
                    var name = new Token(written.getStartPosition(), written.getEndPosition(), Id.IDENTIFIER, child.getName());
                    var candidate = new NamedTypeExpression((TypeExpression) qualifier.clone(), List.of(name), null,
                            written.getEndPosition());
                    candidate.setParent(scope);
                    var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
                    TypeConstant result = resolveType(candidate, probe);
                    var target = candidate.getNameBindings().getLast().target();
                    return result != null && target instanceof IdentityConstant identity
                            && isTypeIdentity(identity) && visible(identity, owner)
                            ? new CursorBinding.NamedType(child.getName(), identity, result) : null;
                }).filter(Objects::nonNull).toList();
    }

    private static TypeConstant resolveType(TypeExpression type, ErrorListener errs) {
        if (!new StageMgr(type, Stage.Resolved, errs).fastForward(20)
                || errs.hasSeriousErrors() || errs.isAbortDesired()) {
            return null;
        }
        TypeConstant result = type.ensureTypeConstant(null, errs);
        return result.containsUnresolved() || errs.hasSeriousErrors() || errs.isAbortDesired() ? null : result;
    }

    private static boolean visibleNames(AstNode node, IdentityConstant owner) {
        return (!(node instanceof NamedTypeExpression named)
                || named.getNameBindings().stream().allMatch(binding ->
                        binding.target() instanceof IdentityConstant identity && visible(identity, owner)
                        || binding.target() instanceof TypeConstant && named.alreadyReached(Stage.Validated)))
                && StreamSupport.stream(node.children().spliterator(), false).allMatch(child -> visibleNames(child, owner));
    }

    /** Every containing type must be visible, including ancestors hidden by an imported alias. */
    private static boolean visible(IdentityConstant target, IdentityConstant owner) {
        return Stream.iterate(target, Objects::nonNull, IdentityConstant::getParentConstant).allMatch(identity -> {
            var component = identity.getComponent();
            var parent = identity.getParentConstant();
            return component != null && (component.getAccess() == Access.PUBLIC || identity.isNestMateOf(owner)
                    || parent != null && parent.getComponent() instanceof ClassStructure enclosing
                        && enclosing.getFormalType().adjustAccess(owner).getAccess().canSee(component.getAccess()));
        });
    }

    private static CursorBinding.NamedType namedType(String name, Argument target) {
        if (target instanceof StatementBlock.TargetInfo info) {
            target = info.getId();
        } else if (target instanceof Register register && register.getType().isTypeOfType()) {
            TypeConstant formal = register.getType().getParamType(0);
            target = !formal.containsUnresolved() && formal.isSingleDefiningConstant()
                    && formal.getDefiningConstant() instanceof TypeParameterConstant parameter
                    && parameter.getRegister() == register.getIndex() ? parameter : null;
        }
        return target instanceof IdentityConstant identity && isTypeIdentity(identity)
                ? new CursorBinding.NamedType(name, identity,
                        identity instanceof PropertyConstant property ? property.getFormalType() : identity.getType()) : null;
    }

    private static boolean isTypeIdentity(IdentityConstant identity) {
        return identity instanceof PropertyConstant property ? property.isFormalType()
                : identity instanceof TypeParameterConstant
                    || identity.getComponent() instanceof ClassStructure || identity.getComponent() instanceof TypedefStructure;
    }

    /** Candidate names only; ordinary read validation still proves accessibility and ownership. */
    static Set<String> valueNames(IncompleteStatement site) {
        return names(site);
    }

    /** Validate disposable proposals while the real lexical context and required type are alive. */
    static List<String> enclosingValues(IncompleteStatement site, Context ctx, TypeConstant required,
                                       ErrorListener errs) {
        if (site.isCall() || site.isTypeCompletion() || site.getDeclarationType().isPresent()) {
            return List.of();
        }
        return PartialCallResolver.instanceSpellings(site).takeWhile(text -> !errs.isAbortDesired())
                .filter(text -> {
                    var probe = ErrorListener.cancellable(ErrorListener.collecting(silent(PROBE)::log), errs::isAbortDesired);
                    var trial = ctx.enter();
                    try {
                        var value = PartialCallResolver.proposedInstance(site, text).validate(trial, required, probe);
                        return value != null && value.getTypeFit().isFit()
                                && !probe.hasSeriousErrors() && !probe.isAbortDesired();
                    } finally {
                        trial.discard();
                    }
                }).toList();
    }

    /** Lexical spellings only; normal expression validation proves that an outer instance exists. */
    static List<String> enclosingInstances(IncompleteStatement site) {
        return Stream.iterate(site.getParent(), Objects::nonNull, AstNode::getParent)
                .filter(TypeCompositionStatement.class::isInstance).map(TypeCompositionStatement.class::cast)
                .map(TypeCompositionStatement::getNameToken).filter(Objects::nonNull)
                .map(Token::getValueText).distinct().toList();
    }

    private static Set<String> names(IncompleteStatement site) {
        return names(site, List.of());
    }

    private static Set<String> names(IncompleteStatement site, List<Parameter> writtenFormals) {
        Set<String> names = new HashSet<>(ConstantPool.getImplicitImportNames());
        Stream.iterate(site.getParent(), Objects::nonNull, AstNode::getParent).forEach(node -> {
            if (node.isComponentNode() && node.getComponent() != null) {
                names.addAll(node.getComponent().getChildByNameMap().keySet());
                if (node.getComponent() instanceof MethodStructure method) {
                    method.getParams().stream().filter(parameter -> parameter.isTypeParameter())
                            .map(parameter -> parameter.getName()).forEach(names::add);
                }
            }
            if (node instanceof StatementBlock block) {
                if (block.imports != null) {
                    names.addAll(block.imports.keySet());
                }
                if (block.importsWild != null) {
                    block.importsWild.forEach(statement -> {
                        if (statement.getNameResolver().getConstant() instanceof IdentityConstant identity
                                && identity.getComponent() != null) {
                            names.addAll(identity.getComponent().getChildByNameMap().keySet());
                        }
                    });
                }
            }
        });
        writtenFormals.stream().map(Parameter::getName).forEach(names::remove);
        return names;
    }
}
