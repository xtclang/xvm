package org.xvm.compiler.ast;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** A written argument cursor and its slot, derived from syntax rather than cached on an AST node. */
record PartialArgument(IncompleteStatement cursor, int index) {
    static Optional<PartialArgument> of(IncompleteStatement call) {
        return IntStream.range(0, call.getArguments().size()).mapToObj(index ->
                cursor(call.getArguments().get(index)).map(site -> new PartialArgument(site, index)))
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<IncompleteStatement> cursor(Expression expression) {
        return switch (expression) {
            case LabeledExpression label -> cursor(label.getUnderlyingExpression());
            case ParenthesizedExpression group -> cursor(group.getUnderlyingExpression());
            case IncompleteExpression hole when !hole.getSite().isCall() -> Optional.of(hole.getSite());
            default -> Optional.empty();
        };
    }

    /** Candidate enumeration uses the compiler's ordinary unbound-parameter marker, never a type. */
    List<Expression> unbound(IncompleteStatement call) {
        var written = call.getArguments().get(index);
        Expression hole = new NonBindingExpression(written.getStartPosition(), written.getEndPosition(), null);
        if (written instanceof LabeledExpression label) {
            hole = new LabeledExpression(label.getNameToken(), hole);
        }
        return replace(call, hole);
    }

    List<Expression> proposed(IncompleteStatement call, Expression value) {
        return replace(call, substitute(call.getArguments().get(index), value));
    }

    private List<Expression> replace(IncompleteStatement call, Expression value) {
        value.setParent(call);
        value.introduceParentage();
        return IntStream.range(0, call.getArguments().size())
                .mapToObj(i -> i == index ? value : call.getArguments().get(i)).toList();
    }

    private static Expression substitute(Expression written, Expression value) {
        return switch (written) {
            case LabeledExpression label -> new LabeledExpression(label.getNameToken(),
                    substitute(label.getUnderlyingExpression(), value));
            case ParenthesizedExpression group -> new ParenthesizedExpression(
                    substitute(group.getUnderlyingExpression(), value), group.getStartPosition(), group.getEndPosition());
            default -> value;
        };
    }
}
