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
    @Abstract @RO TextPosition start;

    /**
     * The ending position (exclusive) of the node in the source code.
     */
    @Abstract @RO TextPosition end;

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
                Int len = tokens.size - 1;
                for (Token token : tokens)
                    {
                    len += token.valueText.size;
                    }
                StringBuffer buf = new StringBuffer(len);
                Loop: for (Token token : tokens)
                    {
                    if (!Loop.first)
                        {
                        buf.add('.');
                        }
                    token.valueText.appendTo(buf);
                    }
                return buf.toString();
                };
            };
        }
    }
