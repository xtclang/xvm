package org.xvm.compiler.ast;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.TypedefStructure;
import org.xvm.asm.constants.ChildInfo;
import org.xvm.asm.constants.IdentityConstant;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/** Enumerate type-name candidates, then let normal contextual lookup establish their meaning. */
final class CursorScope {
    static List<CursorBinding.NamedType> types(IncompleteStatement site, Context ctx, ErrorListener errs) {
        String prefix = site.getMemberName().map(Token::getValueText).orElse("");
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
    static List<CursorBinding.NamedType> declarationTypes(IncompleteStatement site, ErrorListener errs) {
        AstNode scope = declarationScope(site);
        if (site.getTarget() instanceof NamedTypeExpression type && type.getNames().length > 1) {
            return qualifiedTypes(type, scope, errs);
        }
        String prefix = site.getMemberName().map(Token::getValueText).orElse("");
        var probe = ErrorListener.cancellable(silent(PROBE), errs::isAbortDesired);
        return names(site).stream().filter(name -> name.startsWith(prefix)).sorted()
                .takeWhile(name -> !errs.isAbortDesired())
                .map(name -> namedType(name, new NameResolver(scope, name).forceResolve(probe)))
                .filter(Objects::nonNull).toList();
    }

    /** A syntax-only type has no component; its parent retains the real enclosing/import scope. */
    static AstNode declarationScope(IncompleteStatement site) {
        return site.getParent() instanceof IncompleteTypeCompositionStatement declaration
                ? declaration.getParent() : site;
    }

    /** Use contextual TypeInfo to enumerate children, then normal type-name resolution to bind them. */
    private static List<CursorBinding.NamedType> qualifiedTypes(NamedTypeExpression type, AstNode scope, ErrorListener errs) {
        var names = Arrays.asList(type.getNames());
        var qualifier = names.subList(0, names.size() - 1);
        String prefix = names.getLast();
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
        return target instanceof IdentityConstant identity && isTypeIdentity(identity)
                ? new CursorBinding.NamedType(name, identity) : null;
    }

    private static boolean isTypeIdentity(IdentityConstant identity) {
        return identity.getComponent() instanceof ClassStructure || identity.getComponent() instanceof TypedefStructure;
    }

    private static Set<String> names(IncompleteStatement site) {
        Set<String> names = new HashSet<>(ConstantPool.getImplicitImportNames());
        Stream.iterate(site.getParent(), Objects::nonNull, AstNode::getParent).forEach(node -> {
            if (node.isComponentNode() && node.getComponent() != null) {
                names.addAll(node.getComponent().getChildByNameMap().keySet());
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
        return names;
    }
}
