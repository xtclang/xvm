import io.TextPosition;


/**
 * Represents an annotation, including its arguments if any.
 */
const AnnotationExpression(TypeExpression name,
                           Expression[]?  args,
                           TextPosition   start,
                           TextPosition   end)
        extends Expression
    {
    @Override
    String toString()
        {
        return switch (args?.size)
            {
            case  0: $"@{name}()";
            // TODO GG: case  1: $"@{name}({args[0]})";
            case  1: $"@{name}({args.as(Expression[])[0]})";
            default:
                {
                StringBuffer buf = new StringBuffer();
                buf.add('@');
                name.appendTo(buf);
                buf.add('(');
                // TODO GG: Loop: for (Expression arg : args)
                Loop: for (Expression arg : args.as(Expression[]))
                    {
                    if (!Loop.first)
                        {
                        buf.add(',').add(' ');
                        }
                    buf.add(arg.toString());
                    }
                buf.add(')');
                return buf.toString();
                };
            };

        return $"@{name}";
        }
    }
