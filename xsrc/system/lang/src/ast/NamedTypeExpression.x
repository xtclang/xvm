import io.TextPosition;

import Lexer.Token;


/**
 * Represents a named type, including optional access, non-narrowing designation, and
 * parameters. For example:
 *
 *     ecstasy.collections.HashMap!<String?, IntLiteral>
 */
const NamedTypeExpression(Token[]           names,
                          Token?            access,
                          Token?            noNarrow,
                          TypeExpression[]? params,
                          TextPosition      end)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return names[0].start;
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

        if (access != Null)
            {
            buf.add(':');
            access.id.text.appendTo(buf);
            }

        if (noNarrow != Null)
            {
            noNarrow.id.text.appendTo(buf);
            }

        if (params != Null)
            {
            buf.add('<');
            Loop: for (TypeExpression param : params)
                {
                if (!Loop.first)
                    {
                    buf.add(',').add(' ');
                    }
                param.appendTo(buf);
                }
            buf.add('>');
            }

        return buf.toString();
        }
    }
