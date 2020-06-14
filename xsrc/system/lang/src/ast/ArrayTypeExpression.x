import io.TextPosition;


/**
 * Represents an annotated type, for example:
 *
 *     Int[]
 */
const ArrayTypeExpression(TypeExpression type,
                          Int            dims,
                          TextPosition   end)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return type.start;
        }

    @Override
    String toString()
        {
        String       type = this.type.toString();
        Boolean      rel  = this.type.is(RelationalTypeExpression);
        StringBuffer buf  = new StringBuffer(type.size + 2*dims + (rel ? 3 : 1));

        if (rel)
            {
            buf.add('(');
            }

        type.appendTo(buf);

        if (rel)
            {
            buf.add(')');
            }

        buf.add('[');

        if (dims >= 1)
            {
            Loop: for (Int i : 1..dims)
                {
                if (!Loop.first)
                    {
                    buf.add(',');
                    }
                buf.add('?');
                }
            }

        buf.add(']');

        return buf.toString();
        }
    }
