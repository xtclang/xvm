/**
 * The base interface for reading the contents of a JSON document. There are three contexts from
 * which JSON document contents are accessed:
 *
 * * Any JSON element, which could be a JSON `null`, `false`, `true`, number, string, object, or
 *   array;
 * * A sequence of name/value pairs within a JSON object;
 * * A sequence of values within a JSON array.
 *
 * The sequence of name/value pairs are represented by the [FieldInput] interface, while the other
 * cases are represented by the [ElementInput] interface. (It is possible to work with the value in
 * a name/value pair as an [ElementInput] as well.)
 */
interface DocInput<ParentInput extends (ElementInput | FieldInput)?>
        extends Closeable
    {
    // ----- schema --------------------------------------------------------------------------------

    /**
     * The JSON [Schema] that provides the genericized JSON-to-Ecstasy [Mappings].
     * A default Schema is used when no custom mappings are provided.
     */
    @RO Schema schema;

    /**
     * The version of the data being read from the FieldInput, if the version is known. The version
     * information may be inherited from an outer JSON object, or it may be gleaned from the current
     * document. Be cautious when modifying the `version`, at it may impact the parsing of any
     * nested JSON objects.
     */
    Version? version;

    /**
     * Search from the current `DocInput` to its parent and so on, until a JSON object (such as
     * would be represented by a [FieldInput]) is encountered, and obtain the specified metadata
     * property from that object.
     *
     * Metadata properties are automatically collected from the beginning of the closest enclosing
     * JSON object if the [Schema's collectMetadata property](Schema.collectMetadata) is `True`.
     * These properties are included in the "remainder", and are available during the parsing of the
     * JSON object via this method.
     *
     * All leading properties that begin with an "@", a "$", or an "_" character are assumed to be
     * metadata properties.
     *
     * @param attribute  the metadata attribute name
     *
     * @return the value of the metadata attribute, or `Null`
     */
    Doc metadataFor(String attribute);

    /**
     * Search for the specified metadata property only within the confines of the current
     * `DocInput`, peeking ahead in the input as necessary.
     *
     * @param attribute  the metadata attribute name
     *
     * @return the value of the metadata attribute, or `Null`
     */
    Doc peekMetadata(String attribute);


    // ----- context -------------------------------------------------------------------------------

    /**
     * Represents the ability to read a value.
     *
     * For a single element, this is `True` before the element's value is read, and `False` after.
     *
     * While reading through the elements of an array, this property is `True` until after the last
     * element of the array has been read; in all other cases, it is `False`. Note that an array
     * may contain zero elements, so even a newly created `ElementInput` for an array may return
     * `False` for this property.
     *
     * For name/value pairs, this is `True` until the last name/value pair has been read, and
     * `False` after. The meaning of this value may be irrelevant when a schema enables the
     * `randomAccess` option, because the entire set of name/value pairs may be read in advance to
     * enable random access to the contents of a document.
     */
    @RO Boolean canRead;

    /**
     * The parent `DocInput` representing the node that provided this `DocInput`.
     */
    @RO ParentInput parent;

    /**
     * Determine if this `DocInput` represents a JSON element.
     *
     * @return True iff this `DocInput` represents a single element, which corresponds to any JSON
     *         `Doc` value, including a single value, an array, or an object
     * @return (conditional) the [ElementInput] that can be used to read the value of the JSON
     *         element
     */
    conditional ElementInput<ParentInput> insideElement();

    /**
     * Determine if this `DocInput` represents a sequence of JSON elements in an array.
     *
     * @return True iff this `DocInput` represents a sequence of elements in an array, each element
     *         of which corresponds to any JSON `Doc` value
     * @return (conditional) the [ElementInput] that can be used to read each element of the JSON
     *         array
     */
    conditional ElementInput<ParentInput> insideArray();

    /**
     * Determine if this `DocInput` represents a JSON object, which is a sequence of name/value
     * pairs.
     *
     * @return True iff this `DocInput` represents a JSON object
     * @return (conditional) the [FieldInput] that can be used to read the name/value pairs of the
     *         JSON object
     */
    conditional FieldInput<ParentInput> insideObject();


    // ----- pointers ------------------------------------------------------------------------------

    /**
     * The pointer that corresponds to the JSON document node that this `DocInput` currently
     * represents.
     */
    @RO String pointer;

    /**
     * For a JSON pointer, obtain the object from that location that was previously deserialized.
     *
     * @param pointer  the location in the JSON document at which the desired object's data is
     *                 located
     *
     * @return the object that was previously deserialized from the data at the location specified
     *         by the pointer
     *
     * @throws IllegalJSON  if the specified pointer does not have an already-deserialized object
     *                      associated with it, or if there is a type mismatch
     */
    <Serializable> Serializable dereference(String pointer);


    // ----- Closeable methods ---------------------------------------------------------------------

    /**
     * Discard this `DocInput`, and return the outer `DocInput`.
     *
     * @return the parent `DocInput` representing the node that provided this `DocInput`
     */
    @Override
    ParentInput close();


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert a number or literal to an IntLiteral.
     *
     * @param a number or literal
     *
     * @return an IntLiteral
     */
    static IntLiteral ensureIntLiteral(IntLiteral | Number value)
        {
        return value.is(IntLiteral)
                ? value
                : value.toIntLiteral();
        }

    /**
     * Convert a number or literal to an FPLiteral.
     *
     * @param a number or literal
     *
     * @return an FPLiteral
     */
    static FPLiteral ensureFPLiteral(FPLiteral | IntLiteral | Number value)
        {
        if (value.is(FPLiteral))
            {
            return value;
            }

        return value.is(Number)
            ? value.toFPLiteral()
            : value.toFPLiteral();
        }
    }