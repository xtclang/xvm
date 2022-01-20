import io.TextPosition;

import Lexer.Token;


/**
 * Represents the type of a function, including the parameter types and the return types.
 */
const FunctionTypeExpression(Token             func,
                             Parameter[]       returns,
                             TypeExpression[]  params,
                             TextPosition      end)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return func.start;
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer();

        "function ".appendTo(buf);

        switch (returns.size)
            {
            case 0:
                "void".appendTo(buf);
                break;

            case 1:
                Parameter ret = returns[0];
                if (ret.name != Null)
                    {
                    $"({ret})".appendTo(buf);
                    }
                else
                    {
                    ret.appendTo(buf);
                    }
                break;

            default:
                buf.add('(');
                Loop: for (Parameter ret : returns)
                    {
                    if (!Loop.first)
                        {
                        buf.add(',').add(' ');
                        }
                    ret.appendTo(buf);
                    }
                buf.add(')');
                break;
            }

        buf.add(' ').add('(');
        Loop: for (TypeExpression type : params)
            {
            if (!Loop.first)
                {
                buf.add(',').add(' ');
                }
            type.appendTo(buf);
            }
        buf.add(')');

        return buf.toString();
        }
    }
