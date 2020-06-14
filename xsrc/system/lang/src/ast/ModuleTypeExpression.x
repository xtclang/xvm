import io.TextPosition;

import Lexer.Token;


/**
 * Represents a named type, including optional access, non-narrowing designation, and
 * parameters. For example:
 *
 *     ecstasy.collections.HashMap!<String?, IntLiteral>
 */
const ModuleTypeExpression(Token[]        names,
                           TypeExpression type)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return names[0].start;
        }

    @Override
    TextPosition end.get()
        {
        return type.end;
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer();

        Loop: for (Token token : names)
            {
            if (!Loop.first)
                {
                buf.add('.');
                }
            token.appendTo(buf);
            }

        buf.add(':');

        type.appendTo(buf);

        return buf.toString();
        }
    }
