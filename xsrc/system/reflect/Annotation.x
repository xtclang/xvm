/**
 * An Annotation represents the information about a mixin class, optionally with constant value
 * constructor arguments, that is used to augment another class via annotation.
 */
const Annotation(Class mixinClass, Argument[] arguments = [])
    {
    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1 + mixinClass.displayName.size + (arguments.size == 0 ? 0 : 2 * arguments.size)
                 + arguments.iterator().map(a -> a.estimateStringLength())
                                       .reduce(0, (n1, n2) -> n1 + n2);
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add('@')
                .add(mixinClass.displayName);

        if (arguments.size > 0)
            {
            appender.add('(');
            Args: for (Argument arg : arguments)
                {
                if (!Args.first)
                    {
                    appender.add(", ");
                    }
                arg.appendTo(appender);
                }
            appender.add(')');
            }
        }
    }
