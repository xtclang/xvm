package org.xvm.compiler.ast.partial;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.InvocationExpression;
import org.xvm.compiler.ast.LambdaExpression;
import org.xvm.compiler.ast.NewExpression;

/**
 * Read-only queries over retained partial syntax, shared by parsing and compiler validation.
 * These operations neither resolve names nor attach trial children. Results refer to the caller's
 * current tree; they are not detached semantic snapshots and must not outlive that tree's ownership.
 */
public final class PartialSyntax {
    private PartialSyntax() {}

    /** Include deferred anonymous source bodies without changing compiler child ownership. */
    public static Stream<AstNode> children(AstNode node) {
        var children = StreamSupport.stream(node.childNodes().spliterator(), false);
        return node instanceof NewExpression creation
                ? Stream.concat(children, creation.getUnregisteredBody().stream()) : children;
    }

    /** A value cursor and its written argument slot; no inferred type or candidate is retained. */
    public record ArgumentCursor(IncompleteStatement cursor, int index) {}

    /** Whether this node or any descendant is retained incomplete syntax; null has no syntax. */
    public static boolean contains(AstNode node) {
        return contains(node, site -> true);
    }

    /** Whether a retained site ends at exactly this source cursor, including beneath nested calls. */
    public static boolean containsAt(AstNode node, long cursor) {
        return contains(node, site -> site.getEndPosition() == cursor);
    }

    private static boolean contains(AstNode node, Predicate<IncompleteStatement> matches) {
        return node != null && (node instanceof IncompleteStatement site && matches.test(site)
                || children(node)
                        .anyMatch(child -> contains(child, matches)));
    }

    /** Find a value cursor in a written argument, excluding array-dimension slots. */
    public static Optional<ArgumentCursor> argument(IncompleteStatement call) {
        // Multiple array dimensions are not positional arguments to an ordinary constructor.
        if (call.getOperator().getId() == Id.L_SQUARE) {
            return Optional.empty();
        }
        var arguments = call.getArguments();
        return IntStream.range(0, arguments.size()).mapToObj(index ->
                valueCursor(arguments.get(index)).map(site -> new ArgumentCursor(site, index)))
                .flatMap(Optional::stream).findFirst();
    }

    /**
     * Find the single value cursor inside labels, groups or compound expressions. A nested call,
     * construction or lambda owns its own arguments/scope and is never searched through here.
     */
    public static Optional<IncompleteStatement> valueCursor(Expression expression) {
        if (expression instanceof IncompleteExpression hole) {
            return hole.getSite().isCall() ? Optional.empty() : Optional.of(hole.getSite());
        }
        if (isArgumentBoundary(expression)) {
            return Optional.empty();
        }
        var sites = StreamSupport.stream(expression.childNodes().spliterator(), false)
                .filter(Expression.class::isInstance).map(Expression.class::cast)
                .flatMap(child -> valueCursor(child).stream()).toList();
        return sites.size() == 1 ? Optional.of(sites.getFirst()) : Optional.empty();
    }

    /** Find the real call containing a direct, labeled, grouped or compound argument cursor. */
    public static Optional<IncompleteStatement> argumentCall(IncompleteStatement site) {
        AstNode parent = site.getParent();
        while (parent instanceof Expression expression && !isArgumentBoundary(expression)) {
            parent = parent.getParent();
        }
        return parent instanceof IncompleteStatement call && call.isCall()
                && argument(call).map(argument -> argument.cursor() == site).orElse(false)
                ? Optional.of(call) : Optional.empty();
    }

    private static boolean isArgumentBoundary(Expression expression) {
        return expression instanceof LambdaExpression || expression instanceof InvocationExpression
                || expression instanceof NewExpression;
    }
}
