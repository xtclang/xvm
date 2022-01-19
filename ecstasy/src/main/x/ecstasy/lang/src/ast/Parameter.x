import io.TextPosition;

import Lexer.Token;


/**
 * A `Parameter` represents an in (argument) or out (return value) parameter, including a type,
 * an optional name, and an optional default value.
 */
const Parameter(TypeExpression type,
                Token?         name  = Null,
                Expression?    value = Null)
        extends Node
    {
    assert()
        {
        assert value == Null || name != Null;   // must have a name to have a value
        }

    @Override
    TextPosition start.get()
        {
        return type.start;
        }

    @Override
    TextPosition end.get()
        {
        return value?.end : name?.end : type.end;
        }

    @Override
    String toString()
        {
        if (name != Null)
            {
            return value == Null
                    ? $"{type} {name}"
                    : $"{type} {name} = {value}";
            }

        return type.toString();
        }
    }
