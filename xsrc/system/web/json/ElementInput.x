/**
 * The interface for reading a single JSON value, or for reading JSON values from within an array of
 * values.
 */
interface ElementInput<ParentInput extends (ElementInput | FieldInput)?>
        extends DocInput<ParentInput>
    {
    // ----- nesting values ------------------------------------------------------------------------

    /**
     * If the current element is a JSON object, then obtain a [FieldInput] for that object.
     *
     * @return the `FieldInput` for the JSON object that is held in the current element
     *
     * @throws IllegalJSON  if this `ElementInput` does not contain a value which is a JSON object
     */
    FieldInput<ElementInput> openObject();

    /**
     * If the current element is a JSON array, then obtain a `ElementInput` for reading the
     * individual elements of the array.
     *
     * @return the `ElementInput` that can read the elements of the JSON array, starting with the
     *         first element
     *
     * @throws IllegalJSON  if this `ElementInput` does not contain a value which is a JSON array
     */
    ElementInput!<ElementInput> openArray();


    // ----- single values -------------------------------------------------------------------------

    /**
     * Read the element value as a JSON `Doc` object.
     *
     * @return a JSON `Doc` object (which may be `Null`)
     */
    Doc readDoc();

    /**
     * Read the element value as a `Boolean`.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Boolean` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    Boolean readBoolean(Boolean? defaultValue = Null);

    /**
     * Read the element value as a `String`.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Boolean` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    String readString(String? defaultValue = Null);

    /**
     * Read the element value as an `IntLiteral`.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an `IntLiteral` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    IntLiteral readIntLiteral((IntLiteral|FPLiteral|Number)? defaultValue = Null);

    /**
     * Read the element value as an `FPLiteral`.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an `FPLiteral` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    FPLiteral readFPLiteral((IntLiteral|FPLiteral|Number)? defaultValue = Null);

    /**
     * Read the element value as an `Int`.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an `Int` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    Int readInt(Int? defaultValue = Null);

    /**
     * Read the element value as a `Dec`.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Dec` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    Dec readDec(Dec? defaultValue = Null);

    /**
     * Read the element value, deserializing it using the available [Schema] information.
     *
     * @param Serializable  the type of the resulting value
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    <Serializable> Serializable read<Serializable>(Serializable? defaultValue = Null)
        {
        if (Mapping<Serializable> mapping := schema.getMapping(Serializable))
            {
            TODO return read<Serializable>(mapping.read<Serializable>(_), defaultValue);
            }

        throw new MissingMapping(type = Type<Serializable>);
        }

    /**
     * Read the element value using the specified deserialization function.
     *
     * @param Serializable  the type of the resulting value
     * @param deserialize   a function that takes in a JSON `Doc` and transforms it to an Ecstasy
     *                      object of the `Serializable` type
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    <Serializable> Serializable read<Serializable>(function Serializable(DocInput<>) deserialize,
                                                   Serializable? defaultValue = Null);


    // ----- array values --------------------------------------------------------------------------

    /**
     * Read the element array value as an array of JSON `Doc` objects.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of JSON `Doc` objects.
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Doc[] readDocArray(Doc[] defaultValue = []);

    /**
     * Read the element array value as an array of `Boolean` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `Boolean` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Boolean[] readBooleanArray(Boolean[] defaultValue = []);

    /**
     * Read the element array value as an array of `String` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `String` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    String[] readStringArray(String[] defaultValue = []);

    /**
     * Read the element array value as an array of `Int` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `Int` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Int[] readIntArray(Int[] defaultValue = []);

    /**
     * Read the element array value as an array of `Dec` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `Dec` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Dec[] readDecArray(Dec[] defaultValue = []);

    /**
     * Read the element array value as an array of values, deserialized using the available
     * `Schema` information.
     *
     * @param Serializable  the type of the elements in the resulting array value
     * @param deserialize   a function that takes in a JSON `Doc` for each element of the resulting
     *                      array, and transforms it to an Ecstasy object of the `Serializable` type
     * @param defaultValue  (optional) the array value to use if this element has a null value
     *
     * @return an array of `Serializable` values
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    <Serializable> Serializable[] readArray(Serializable[]? defaultValue = Null)
        {
        if (Mapping<Serializable> mapping := schema.getMapping(Serializable))
            {
            TODO return readArray(mapping.read<Serializable>(_), defaultValue);
            }

        throw new MissingMapping(type = Type<Serializable>);
        }

    /**
     * Read the element array value as an array of values resulting from the specified
     * deserialization function.
     *
     * @param Serializable  the type of the elements in the resulting array value
     * @param deserialize   a function that takes in a JSON `Doc` for each element of the resulting
     *                      array, and transforms it to an Ecstasy object of the `Serializable` type
     * @param defaultValue  (optional) the array value to use if this element has a null value
     *
     * @return an array of `Serializable` values
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    <Serializable> Serializable[] readArray(function Serializable(DocInput<>) deserialize,
                                            Serializable[]? defaultValue = Null);
    }