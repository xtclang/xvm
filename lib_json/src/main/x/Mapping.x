/**
 * A mapping represents the ability to read and/or write objects of a certain serializable type
 * from/to a JSON document format.
 */
interface Mapping<Serializable>
        extends immutable Const
    {
    /**
     * The name of the type for the mapping. This name helps to identify a Mapping from JSON
     * metadata, or allows the Mapping to be identified in the metadata related to objects
     * emitted by this Mapping.
     */
    @RO String typeName;

    /**
     * Read a value of type `Serializable` from the provided `ElementInput`.
     *
     * @param in  the `ElementInput` to read from
     *
     * @return a `Serializable` object
     */
    Serializable read(ElementInput in);

    /**
     * Write a value of type `Serializable` to the provided `ElementOutput`.
     *
     * @param out    the `ElementOutput` to write to
     * @param value  the `Serializable` value to write
     */
    void write(ElementOutput out, Serializable value);
    }
