import io.TextPosition;

import Lexer.Token;


/**
 * Represents a child of a NamedTypeExpression, for example:
 *
 *     ecstasy.collections.HashMap<String?, IntLiteral>.Entry
 */
const ChildTypeExpression(TypeExpression          parent,
                          AnnotationExpression[]? annotations,
                          Token[]                 names,
                          TypeExpression[]?       params,
                          TextPosition            end)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return parent.start;
        }

    @Override
    String toString()
        {
        StringBuffer buf = new StringBuffer();

        parent.appendTo(buf);
        buf.add('.');

        Loop: for (AnnotationExpression anno : annotations?)
            {
            anno.appendTo(buf);
            buf.add(' ');
            }

        Loop: for (Token token : names)
            {
            if (!Loop.first)
                {
                buf.add('.');
                }
            token.appendTo(buf);
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
