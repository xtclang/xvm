/**
 * A Label represents a source code label used to identify a "do", "while", or "for" statement.
 */
interface Label<KeyType, ValueType>
    {
    /**
     * True on the first iteration of the loop (do, while, or for).
     */
    @RO Boolean first;

    /**
     * True on the last iteration of the loop. This value is only available on a label for a "for"
     * loop of the for-each variety iterating over a Range or Sequence.
     */
    @RO Boolean last;

    /**
     * The number of loop iterations that have completed.
     */
    @RO Int count;

    /**
     * The current Entry. This value is only available on a label for a "for" loop of the for-each
     * variety iterating over a Map.
     */
    @RO Entry<KeyType, ValueType> entry;
    }
