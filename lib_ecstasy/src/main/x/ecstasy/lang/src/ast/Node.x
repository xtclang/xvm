import src.Lexer.Token;

import io.TextPosition;


/**
 * This is the abstract representation for every node in an Ecstasy "abstract syntax tree".
 */
@Abstract const Node
    {
    /**
     * The position of the node in the source code.
     */
    @RO TextPosition start;

    /**
     * The ending position (exclusive) of the node in the source code.
     */
    @RO TextPosition end;

    /**
     * Create a dot-delimited string from an array of tokens.
     *
     * @param tokens  the `Token` objects
     *
     * @return the dot-delimited String formed from the `valueText` of each token
     */
    static String toDotDelimString(Token[] tokens)
        {
        return switch (tokens.size)
            {
            case 0: "";

            case 1: tokens[0].valueText;

            default:
                {
                Int len = tokens.size;
                for (Token token : tokens)
                    {
                    len += token.valueText.size;
                    }
                StringBuffer buf = new StringBuffer(len);
                for (Token token : tokens)
                    {
                    token.valueText.appendTo(buf);
                    buf.add('.');
                    }
                return buf.truncate(-1).toString();
                };
            };
        }
    }
