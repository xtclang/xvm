import Schema.Mapping;
import Schema.MissingMapping;


/**
 * The interface for reading a sequence of name/value pairs from a JSON object.
 */
interface FieldInput<ParentInput extends (ElementInput | FieldInput)?>
        extends DocInput<ParentInput>
    {
    /**
     * Obtain the name of the next field to read from the document.
     *
     * @return True if any fields remain to read; `False` if [canRead] is `False`
     * @return (conditional) the name of the next field
     */
    conditional String nextName();

    /**
     * Test if the JSON object contains the specified name.
     *
     * (If strict ordering is enforced, this method is only guaranteed to test the immediately next
     * name.)
     *
     * @param name  the name to test for
     *
     * @return True iff the name is present in the JSON object
     */
    Boolean contains(String name);

    /**
     * For a name/value pair in the current JSON object, obtain an [ElementInput] for the value.
     *
     * @param name  the name of the JSON property within the current JSON object
     *
     * @return True iff the name was present
     * @return (conditional) the FieldInput for the value
     */
    conditional ElementInput<FieldInput> openField(String name);

    /**
     * For a named JSON object under the current document, "enter" that document, returning the
     * `FieldInput` to use to read the entered document's contents.
     *
     * Note that the resulting `FieldInput` does not copy the strict ordering designation of the
     * previous `FieldInput`.
     *
     * If the current element is a JSON object, then obtain a `FieldInput` for that object.
     *
     * @return the FieldInput for the JSON object that is held in the current element
     *
     * @throws IllegalJSON   if the specified name does not have a value which is a JSON object
     */
    conditional FieldInput!<FieldInput> openObject(String name);

    /**
     * If the current element is a JSON array, then obtain a `ElementInput` for the elements of the
     * array.
     *
     * @return the ElementInput that can read the elements of the JSON array, starting with the
     *         first element
     *
     * @throws IllegalJSON   if the specified name does not have a value which is a JSON array
     */
    conditional ElementInput<FieldInput> openArray(String name);

    /**
     * If any fields of the current JSON object have _not_ been read, combine those fields into a
     * JSON `Doc` object and return it.
     *
     * @return a `Map` containing any unread (both skipped over and yet-to-be-read) fields, or
     *         `Null` if all fields were read
     */
    Map<String, Doc>? takeRemainder();


    // ----- single values -------------------------------------------------------------------------

    /**
     * Read the specified array value as a JSON `Doc` object.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing
     *
     * @return the 'Doc' value for the specified name, which may be `Null` if the value is `null`
     *         or missing
     */
    Doc readDoc(String name, Doc defaultValue = Null);

    /**
     * Read the specified named value as a `Boolean`.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Boolean` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    Boolean readBoolean(String name, Boolean? defaultValue = Null);

    /**
     * Read the specified named value as a `String`.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Boolean` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    String readString(String name, String? defaultValue = Null);

    /**
     * Read the specified named value as an `IntLiteral`.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an `IntLiteral` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    IntLiteral readIntLiteral(String name, (IntLiteral|FPLiteral|Number)? defaultValue = Null);

    /**
     * Read the specified named value as an `FPLiteral`.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an `FPLiteral` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    FPLiteral readFPLiteral(String name, (IntLiteral|FPLiteral|Number)? defaultValue = Null);

    /**
     * Read the specified named value as an `Int`.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an `Int` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    Int readInt(String name, Int? defaultValue = Null);

    /**
     * Read the specified named value as a `Dec`.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Dec` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    Dec readDec(String name, Dec? defaultValue = Null);

    /**
     * Read the specified named value, deserializing it using the available `Schema` information.
     *
     * @param Serializable  the type of the resulting value
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    <Serializable> Serializable readObject(String name, Serializable? defaultValue = Null)
        {
        if (Mapping<Serializable> mapping := schema.mappingFor(Serializable))
            {
            TODO return read<Serializable>(name, mapping.read<Serializable>(_), defaultValue);
            }

        throw new MissingMapping(type = Type<Serializable>);
        }

    /**
     * Read the specified named value using the specified deserialization function.
     *
     * @param Serializable  the type of the resulting value
     * @param name          the name of the JSON property to read
     * @param deserialize   a function that takes in a JSON `Doc` and transforms it to an Ecstasy
     *                      object of the `Serializable` type
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    <Serializable> Serializable read<Serializable>(String name,
                                                   function Serializable(DocInput<>) deserialize,
                                                   Serializable? defaultValue = Null);


    // ----- array values --------------------------------------------------------------------------

    /**
     * Read the specified named array value as an array of JSON `Doc` objects.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an array of JSON `Doc` objects.
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Doc[] readDocArray(String name, Doc[] defaultValue = []);

    /**
     * Read the specified named array value as an array of `Boolean` values.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an array of `Boolean` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Boolean[] readBooleanArray(String name, Boolean[] defaultValue = []);

    /**
     * Read the specified named array value as an array of `String` values.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an array of `String` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    String[] readStringArray(String name, String[] defaultValue = []);

    /**
     * Read the specified named array value as an array of `Int` values.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an array of `Int` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Int[] readIntArray(String name, Int[] defaultValue = []);

    /**
     * Read the specified named array value as an array of `Dec` values.
     *
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return an array of `Dec` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Dec[] readDecArray(String name, Dec[] defaultValue = []);

    /**
     * Read the specified named array value as an array of values, deserializing each value using
     * the available `Schema` information.
     *
     * @param Serializable  the type of the elements in the resulting array value
     * @param name          the name of the JSON property to read
     * @param deserialize   a function that takes in a JSON `Doc` for each element of the resulting
     *                      array, and transforms it to an Ecstasy object of the `Serializable` type
     * @param defaultValue  (optional) the array value to use if the name is missing or has a null
     *                      value
     *
     * @return an array of `Serializable` values
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    <Serializable> Serializable[] readArray(String name, Serializable[]? defaultValue = Null)
        {
        if (Mapping<Serializable> mapping := schema.mappingFor(Serializable))
            {
            TODO return readObjectArray(name, mapping.read(_), defaultValue);
            }

        throw new MissingMapping(type = Type<Serializable>);
        }

    /**
     * Read the specified named array value as an array of values resulting from the specified
     * deserialization function.
     *
     * @param Serializable  the type of the elements in the resulting array value
     * @param name          the name of the JSON property to read
     * @param deserialize   a function that takes in a JSON `Doc` for each element of the resulting
     *                      array, and transforms it to an Ecstasy object of the `Serializable` type
     * @param defaultValue  (optional) the array value to use if the name is missing or has a null
     *                      value
     *
     * @return an array of `Serializable` values
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    <Serializable> Serializable[] readArray(String name,
                                            function Serializable(FieldInput) deserialize,
                                            Serializable[]? defaultValue = Null);
    }