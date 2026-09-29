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

/** Enumerate type-name candidates, then let normal contextual lookup establish their meaning. */
final class CursorScope {
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
                && (named.left != null || named.getNames().length > 1) ? List.of() : resolveFormals(scope, writtenFormals, errs);
    }

    private static List<CursorBinding.Formal> resolveFormals(AstNode scope, List<Parameter> parameters,
            ErrorListener errs) {
        var byName = parameters.stream().collect(Collectors.toMap(Parameter::getName, Function.identity(),
                (first, second) -> first));
        return parameters.stream().takeWhile(parameter -> !errs.isAbortDesired()).map(parameter -> {
            TypeConstant bound = formalBound(parameter, scope, byName, Set.of(), errs);
            return bound == null ? null : new CursorBinding.Formal(parameter.getNameToken(), bound);
        }).filter(Objects::nonNull).toList();
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
