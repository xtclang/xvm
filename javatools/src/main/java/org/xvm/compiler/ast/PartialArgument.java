package org.xvm.compiler.ast;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.xvm.compiler.ast.partial.IncompleteExpression;
import org.xvm.compiler.ast.partial.IncompleteStatement;
import org.xvm.compiler.ast.partial.PartialSyntax;
import org.xvm.compiler.ast.partial.PartialSyntax.ArgumentCursor;

/** Trial argument construction; lexical parenting stays inside the ordinary compiler package. */
final class PartialArgument {
    private PartialArgument() {}

    /** Candidate enumeration uses the compiler's ordinary unbound-parameter marker, never a type. */
    static List<Expression> unbound(IncompleteStatement call, ArgumentCursor argument) {
        var written = call.getArguments().get(argument.index());
        Expression hole = new NonBindingExpression(written.getStartPosition(), written.getEndPosition(), null);
        if (written instanceof LabeledExpression label) {
            hole = new LabeledExpression(label.getNameToken(), hole);
        }
        return replace(call, argument.index(), hole);
    }

    static List<Expression> proposed(IncompleteStatement call, ArgumentCursor argument, Expression value) {
        return replace(call, argument.index(), substitute(call.getArguments().get(argument.index()), value));
    }

    private static List<Expression> replace(IncompleteStatement call, int index, Expression value) {
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
        var copy = written.copyTree();
        var children = StreamSupport.stream(copy.childNodes().spliterator(), false).toList();
        children.stream().filter(Expression.class::isInstance).map(Expression.class::cast)
                .filter(child -> PartialSyntax.valueCursor(child).isPresent())
                .forEach(child -> copy.replaceChild(child, substitute(child, value)));
        return copy;
    }
}
