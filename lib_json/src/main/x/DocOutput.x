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
     * The JSON [Schema] that provides the genericized Ecstasy-to-JSON [Mappings].
     * A default Schema is used when no custom mappings are provided.
     */
    @RO Schema schema;

    /**
     * Metadata "poke ahead": If/when the next mutating operation opens an object, automatically
     * include the specified metadata as part of that object.
     *
     * @param attribute  the metadata attribute name
     * @param doc        the corresponding JSON document (for example, a String)
     */
    void prepareMetadata(String attribute, Doc doc);


    // ----- context -------------------------------------------------------------------------------

    /**
     * Represents the ability to write a value.
     *
     * For a single element, this is `True` before the element's value is written, and `False`
     * after.
     *
     * While writing elements in an array, this property is `True`.
     *
     * For name/value pairs, this property is `True`.
     *
     * In all cases, this property is `False` after the `DocOutput` is closed.
     */
    @RO Boolean canWrite;

    /**
     * The parent `DocOutput` representing the node that provided this `DocOutput`.
     */
    @RO ParentOutput parent;

    /**
     * Determine if this `DocOutput` represents a JSON element.
     *
     * @return True iff this `DocOutput` represents a single element, which corresponds to any JSON
     *         `Doc` value, including a single value, an array, or an object
     * @return (conditional) the [ElementOutput] that can be used to write the value of the JSON
     *         element
     */
    conditional ElementOutput<ParentOutput> insideElement();

    /**
     * Determine if this `DocOutput` represents a sequence of JSON elements in an array.
     *
     * @return True iff this `DocOutput` represents a sequence of elements in an array, each element
     *         of which corresponds to any JSON `Doc` value
     * @return (conditional) the [ElementOutput] that can be used to write each element of the JSON
     *         array
     */
    conditional ElementOutput<ParentOutput> insideArray();

    /**
     * Determine if this `DocOutput` represents a JSON object, which is a sequence of name/value
     * pairs.
     *
     * @return True iff this `DocOutput` represents a JSON object
     * @return (conditional) the [FieldOutput] that can be used to write the name/value pairs of the
     *         JSON object
     */
    conditional FieldOutput<ParentOutput> insideObject();


    // ----- pointers ------------------------------------------------------------------------------

    /**
     * The JSON pointer that corresponds to the JSON document node that this `DocOutput` currently
     * represents.
     *
     * A JSON pointer represents the path to this node from the root object.
     *
     * @see [RFC 6901 §5](https://tools.ietf.org/html/rfc6901#section-5)
     */
    @RO String pointer;

    /**
     * For an object that can be referenced via a JSON pointer, obtain the pointer if that object
     * has already been written out and registered with a pointer.
     *
     * @param object  the object to find a pointer for
     *
     * @return True iff the object was previously serialized and a pointer for the object was
     *         associated with the object
     * @return (conditional) the pointer for the object, if one was found
     */
    <Serializable> conditional String findPointer(Serializable object);


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
