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

    /**
     * Test if this `Mapping` can provide a more specific `Mapping` that will handle the specified
     * `Serializable` sub-type. For example, a Mapping for a container type `C` might exist, and the
     * `Schema` may query it to test if it can provide a more specific mapping for `type` of `C<T>`.
     * This allows a Schema to be configured without providing a `Mapping` in advance for each
     * potential generic type combination, yet with all of the advantages of having provided all
     * such possible `Mapping` instances.
     *
     * @param schema  the `Schema` within which this is occurring
     * @param type    the `Type` to obtain a more specific `Mapping` instance for
     *
     * @return True iff a more specific Mapping is available for the specified type
     * @return (optional) a more specific Mapping for the specified type
     */
    <SubType extends Serializable> conditional Mapping<SubType> narrow(Schema schema, Type<SubType> type)
        {
        return False;
        }
    }
