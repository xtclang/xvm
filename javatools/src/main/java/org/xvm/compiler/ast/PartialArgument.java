package org.xvm.compiler.ast;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.partial.IncompleteExpression;
import org.xvm.compiler.ast.partial.IncompleteStatement;

/** A written argument cursor and its slot, derived from syntax rather than cached on an AST node. */
record PartialArgument(IncompleteStatement cursor, int index) {
    static Optional<PartialArgument> of(IncompleteStatement call) {
        // Multiple array dimensions are not positional arguments to an ordinary constructor.
        if (call.getOperator().getId() == Id.L_SQUARE) {
            return Optional.empty();
        }
        var arguments = call.getArguments();
        return IntStream.range(0, arguments.size()).mapToObj(index ->
                cursor(arguments.get(index)).map(site -> new PartialArgument(site, index)))
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<IncompleteStatement> cursor(Expression expression) {
        if (expression instanceof IncompleteExpression hole) {
            return hole.getSite().isCall() ? Optional.empty() : Optional.of(hole.getSite());
        }
        if (expression instanceof LambdaExpression || expression instanceof InvocationExpression
                || expression instanceof NewExpression) {
            return Optional.empty();
        }
        var sites = StreamSupport.stream(expression.children().spliterator(), false)
                .filter(Expression.class::isInstance).map(Expression.class::cast)
                .flatMap(child -> cursor(child).stream()).toList();
        return sites.size() == 1 ? Optional.of(sites.getFirst()) : Optional.empty();
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
        var arguments = call.getArguments();
        return IntStream.range(0, arguments.size())
                .mapToObj(i -> i == index ? value : arguments.get(i)).toList();
    }

    private static Expression substitute(Expression written, Expression value) {
        if (written instanceof IncompleteExpression) {
            return value;
        }
        var copy = (Expression) written.clone();
        var children = StreamSupport.stream(copy.children().spliterator(), false).toList();
        children.stream().filter(Expression.class::isInstance).map(Expression.class::cast)
                .filter(child -> cursor(child).isPresent())
                .forEach(child -> copy.replaceChild(child, substitute(child, value)));
        return copy;
    }
}
