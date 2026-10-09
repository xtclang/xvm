package org.xvm.compiler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.NameExpression;
import org.xvm.compiler.ast.NamedTypeExpression;

/**
 * Successful constant-initializer facts copied before its temporary method is discarded.
 * Contains source spans and compiler constants, never clone nodes, contexts, registers or tokens.
 * Constants remain owned by the compilation worker, like {@link InvocationBinding} constants.
 */
public record InitializerBinding(List<Reference> references, List<TypedExpression> expressions,
                                 List<Call> calls) {
    public InitializerBinding {
        references  = List.copyOf(references);
        expressions = List.copyOf(expressions);
        calls       = List.copyOf(calls);
    }

    public record Span(long startPosition, long endPosition) {
        public static Span of(AstNode node) {
            return new Span(node.getStartPosition(), node.getEndPosition());
        }
    }

    public record Reference(String name, Span span, Constant target, TypeConstant type) {}

    public record TypedExpression(Span span, TypeConstant type) {}

    public record Call(Span span, Span callee, InvocationBinding binding, boolean construction) {}

    /** Capture the written nodes before validation can replace constant subexpressions. */
    public static List<AstNode> writtenNodes(AstNode root) {
        List<AstNode> nodes = new ArrayList<>(List.of(root));
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).children().forEachRemaining(nodes::add);
        }
        return List.copyOf(nodes);
    }

    /** Called only after successful validation/emission and the decision to discard the method. */
    public static InitializerBinding capture(List<AstNode> written, InvocationBinding.Facts facts) {
        List<Reference> references = new ArrayList<>();
        List<TypedExpression> expressions = new ArrayList<>();
        List<Call> calls = new ArrayList<>();
        Map<Expression, Constant> callees = new IdentityHashMap<>();
        facts.methods().forEach((node, binding) -> {
            callees.put(node.getInvokedExpression(), binding.method());
            calls.add(new Call(Span.of(node), Span.of(node.getInvokedExpression()), binding, false));
        });
        facts.constructors().forEach((node, binding) ->
                calls.add(new Call(Span.of(node), Span.of(node), binding, true)));
        written.forEach(node -> {
            TypeConstant type = node instanceof Expression expression && expression.isValidated() &&
                    expression.getTypeFit().isFit() ? expression.getType() : null;
            if (type != null) {
                expressions.add(new TypedExpression(Span.of(node), type));
            }
            if (node instanceof NameExpression name) {
                var target = callees.containsKey(name) ? callees.get(name) : name.getResolvedTarget();
                if (target instanceof Constant constant) {
                    references.add(reference(name.getNameToken(), constant, type));
                }
            } else if (node instanceof NamedTypeExpression name) {
                name.getNameBindings().stream().filter(binding -> binding.target() != null)
                        .forEach(binding -> references.add(reference(binding.name(), binding.target(), type)));
            }
        });
        return new InitializerBinding(references, expressions, calls);
    }

    private static Reference reference(Token token, Constant target, TypeConstant type) {
        return new Reference(token.getValueText(),
                new Span(token.getStartPosition(), token.getEndPosition()), target, type);
    }
}
