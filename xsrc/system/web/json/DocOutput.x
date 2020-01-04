/**
 * An interface for building a JSON document. There are three possible contexts within a JSON
 * document:
 *
 * * Within a JSON value;
 * * Within a JSON array, which is a sequence of comma-delimited values surrounded by `[` and `]`;
 * * Within a JSON object, which is a sequence of comma-delimited name/value pairs surrounded by
 *   `{` and `}`.
 *
 * The first and second contexts are supported by the [ElementOutput] interface, and the third is
 * supported by the [FieldOutput] interface.
 */
interface DocOutput<ParentOutput extends (ElementOutput | FieldOutput)?>
        extends Closeable
    {
    /**
     * The JSON [Schema] that provides the genericized Ecstasy-to-JSON [Mappings](Schema.Mapping).
     * A default Schema is used when no custom mappings are provided.
     */
    @RO Schema schema;


    // ----- Closeable -----------------------------------------------------------------------------

    /**
     * Finish the document by closing any nested JSON arrays and JSON objects, which adds any
     * necessary closing `]` and `}` terminators as necessary.
     *
     * @return the root `ElementOutput` node
     *
     * @throws IllegalState if the DocOutput cannot be closed from its current point
     */
    @Override
    ParentOutput close();
    }
