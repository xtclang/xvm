/**
 * An Annotation represents the information about annotation class, optionally with constant value
 * constructor arguments.
 */
const Annotation(Class annoClass, Argument[] arguments = []) {
    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return 1 + annoClass.displayName.size + (arguments.size == 0 ? 0 : 2 * arguments.size)
                 + arguments.map(a -> a.estimateStringLength())
                            .reduce(0, (n1, n2) -> n1 + n2);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        buf.add('@')
           .addAll(annoClass.displayName);

        if (arguments.size > 0) {
            buf.add('(');
            Args: for (Argument arg : arguments) {
                if (!Args.first) {
                    buf.addAll(", ");
                }
                arg.appendTo(buf);
            }
            buf.add(')');
        }
        return buf;
    }
}