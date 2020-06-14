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
    }
