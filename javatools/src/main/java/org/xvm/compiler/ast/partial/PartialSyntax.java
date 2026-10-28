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
    private PartialSyntax() {
        // empty
    }

    /**
     * Include deferred anonymous source bodies without changing compiler child ownership.
     *
     * @param node  the syntax node to inspect, or null for the containment queries
     *
     * @return the direct children, including a retained anonymous source body when present
     */
    public static Stream<AstNode> children(AstNode node) {
        var children = StreamSupport.stream(node.childNodes().spliterator(), false);
        return node instanceof NewExpression creation ? Stream.concat(children, creation.getUnregisteredBody().stream()) : children;
    }

    /** A value cursor and its written argument slot; no inferred type or candidate is retained. */
    public record ArgumentCursor(IncompleteStatement cursor, int index) {}

    /**
     * Whether this node or any descendant is retained incomplete syntax; null has no syntax.
     *
     * @param node  the syntax node to inspect, or null for the containment queries
     *
     * @return whether this node or a descendant retains an incomplete site; false for null
     */
    public static boolean contains(AstNode node) {
        return contains(node, site -> true);
    }

    /**
     * Whether a retained site ends at exactly this source cursor, including beneath nested calls.
     *
     * @param node    the syntax node to inspect, or null for the containment queries
     * @param cursor  the selected position in the owning Source snapshot
     *
     * @return whether a retained site ends at exactly the supplied source position
     */
    public static boolean containsAt(AstNode node, long cursor) {
        return contains(node, site -> site.getEndPosition() == cursor);
    }

    private static boolean contains(AstNode node, Predicate<IncompleteStatement> matches) {
        return node != null && (node instanceof IncompleteStatement site && matches.test(site)
                || children(node).anyMatch(child -> contains(child, matches)));
    }

    /**
     * Find a value cursor in a written argument, excluding array-dimension slots.
     *
     * @param call  the retained incomplete call whose arguments are inspected
     *
     * @return the first supported written argument containing a value cursor, or empty if none exists
     */
    public static Optional<ArgumentCursor> argument(IncompleteStatement call) {
        // Multiple array dimensions are not positional arguments to an ordinary constructor.
        if (call.getOperator().getId() == Id.L_SQUARE) {
            return Optional.empty();
        }
        var arguments = call.getArguments();
        return IntStream.range(0, arguments.size()).mapToObj(index ->
                valueCursor(arguments.get(index))
                        .map(site -> new ArgumentCursor(site, index)))
                .flatMap(Optional::stream).findFirst();
    }

    /**
     * Find the single value cursor inside labels, groups or compound expressions. A nested call,
     * construction or lambda owns its own arguments/scope and is never searched through here.
     *
     * @param expression  the written value expression to inspect
     *
     * @return the unique value cursor in the expression, or empty when absent, ambiguous or behind a call/scope
     *         boundary
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

    /**
     * Find the real call containing a direct, labeled, grouped or compound argument cursor.
     *
     * @param site  the retained incomplete source site
     *
     * @return the real containing incomplete call, or empty outside a supported argument context
     */
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
        return expression instanceof LambdaExpression || expression instanceof InvocationExpression || expression instanceof NewExpression;
    }
}
