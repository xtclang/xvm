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
        Expression[]? args = this.args;
        return switch (args?.size)
            {
            case  0: $"@{name}()";
            case  1: $"@{name}({args[0]})";
            default:
                {
                StringBuffer buf = new StringBuffer();
                buf.add('@');
                name.appendTo(buf);
                buf.add('(');
                Loop: for (Expression arg : args)
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
