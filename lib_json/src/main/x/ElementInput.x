/**
 * The interface for reading a single JSON value, or for reading JSON values from within an array of
 * values.
 */
interface ElementInput<ParentInput extends (ElementInput | FieldInput)?>
        extends DocInput<ParentInput>
    {
    // ----- nesting values ------------------------------------------------------------------------

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

    /**
     * If the current element is a JSON object, then obtain a [FieldInput] for that object.
     *
     * @return the `FieldInput` for the JSON object that is held in the current element
     *
     * @throws IllegalJSON  if this `ElementInput` does not contain a value which is a JSON object
     */
    FieldInput<ElementInput> openObject();


    // ----- single values -------------------------------------------------------------------------

    /**
     * Test the element for existence without altering the input position within the document.
     *
     * @return True iff the element is `null` or does not exist
     */
    Boolean isNull();

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
     * @throws IllegalJSON  if the value is `null` and no default value is provided, or if the value
     *                      is not of the requested type
     */
    Boolean readBoolean(Boolean? defaultValue = Null)
        {
        Doc doc = readDoc();
        if (doc.is(Boolean))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|Boolean value required at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    String readString(String? defaultValue = Null)
        {
        Doc doc = readDoc();
        if (doc.is(String))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|String value required at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    IntLiteral readIntLiteral((IntLiteral|Number)? defaultValue = Null)
        {
        Doc doc = readDoc();
        if (doc.is(IntLiteral))
            {
            return doc;
            }

        if (doc == Null)
            {
            return ensureIntLiteral(defaultValue?);
            }

        throw new IllegalJSON($|IntLiteral value required at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    FPLiteral readFPLiteral((IntLiteral|FPLiteral|Number)? defaultValue = Null)
        {
        Doc doc = readDoc();
        if (doc.is(FPLiteral))
            {
            return doc;
            }

        if (doc.is(IntLiteral))
            {
            return doc.toFPLiteral();
            }

        if (doc == Null)
            {
            return ensureFPLiteral(defaultValue?);
            }

        throw new IllegalJSON($|FPLiteral value required at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    Int readInt(Int? defaultValue = Null)
        {
        Doc doc = readDoc();
        if (doc.is(IntLiteral))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|Int value required at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    Dec readDec(Dec? defaultValue = Null)
        {
        Doc doc = readDoc();
        if (doc.is(FPLiteral))
            {
            return doc;
            }

        if (doc.is(IntLiteral))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|Dec value required at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

    /**
     * Read the element value, deserializing it using the available [Schema] information.
     *
     * @param Serializable  the type of the resulting value
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON     if the value is null and no default value is provided, or if the
     *                         value is not of the requested type
     * @throws MissingMapping  if no appropriate mapping can be located
     */
    <Serializable> Serializable readObject(Serializable? defaultValue = Null)
        {
        if (isNull())
            {
            return defaultValue?;

            if (Null.is(Serializable))
                {
                // TODO GG: return Null;
                return Null.as(Serializable);
                }

            throw new IllegalJSON($|Value required at "{pointer}" of type "{Serializable}";\
                                   | no value found
                                 );
            }

        Type<Serializable> type   = Serializable;
        Schema             schema = this.schema;
        if (schema.enableMetadata, Doc typeName ?= peekMetadata(schema.typeKey), typeName.is(String))
            {
            type = schema.typeForName(typeName).as(Type<Serializable>);
            }

        return readUsing(schema.ensureMapping(type), defaultValue);
        }

    /**
     * Read the element value using the specified deserialization function.
     *
     * @param Serializable  the constraint type for the resulting value
     * @param mapping       the Mapping to use to transforms the stream into an Ecstasy object
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON  if the value is null and no default value is provided, or if the value
     *                      is not of the requested type
     */
    <Serializable> Serializable readUsing(Mapping<Serializable> mapping,
                                          Serializable?         defaultValue = Null)
        {
        if (isNull())
            {
            return defaultValue?;

            if (Null.is(Serializable))
                {
                // TODO GG: return Null;
                return Null.as(Serializable);
                }

            throw new IllegalJSON($|Value required at "{pointer}" of type "{Serializable}";\
                                   | no value found
                                 );
            }

        return mapping.read(this);
        }


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
    Doc[] readDocArray(Doc[] defaultValue = [])
        {
        if (isNull())
            {
            return defaultValue;
            }

        Doc value = readDoc();
        return value.is(Doc[])
                ? value
                : throw new IllegalJSON($|Doc[] value required at "{pointer}";\
                                         | {&value.actualType} found
                                       );
        }

    /**
     * Read the element array value as an array of `Boolean` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `Boolean` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Boolean[] readBooleanArray(Boolean[] defaultValue = [])
        {
        if (isNull())
            {
            return defaultValue;
            }

        Boolean[] values = new Boolean[];
        using (ElementInput<ElementInput> elements = openArray())
            {
            while (elements.canRead)
                {
                values.add(elements.readBoolean());
                }
            }
        return values;
        }

    /**
     * Read the element array value as an array of `String` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `String` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    String[] readStringArray(String[] defaultValue = [])
        {
        if (isNull())
            {
            return defaultValue;
            }

        String[] values = new String[];
        using (ElementInput<ElementInput> elements = openArray())
            {
            while (elements.canRead)
                {
                values.add(elements.readString());
                }
            }
        return values;
        }

    /**
     * Read the element array value as an array of `Int` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `Int` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Int[] readIntArray(Int[] defaultValue = [])
        {
        if (isNull())
            {
            return defaultValue;
            }

        Int[] values = new Int[];
        using (ElementInput<ElementInput> elements = openArray())
            {
            while (elements.canRead)
                {
                values.add(elements.readInt());
                }
            }
        return values;
        }

    /**
     * Read the element array value as an array of `Dec` values.
     *
     * @param defaultValue  (optional) the value to use if the value is `null`
     *
     * @return an array of `Dec` values
     *
     * @throws IllegalJSON  if the value is not of the requested type
     */
    Dec[] readDecArray(Dec[] defaultValue = [])
        {
        if (isNull())
            {
            return defaultValue;
            }

        Dec[] values = new Dec[];
        using (ElementInput<ElementInput> elements = openArray())
            {
            while (elements.canRead)
                {
                values.add(elements.readDec());
                }
            }
        return values;
        }

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
        if (isNull())
            {
            return defaultValue ?: [];
            }

        Serializable[] values = new Serializable[];
        using (ElementInput<ElementInput> elements = openArray())
            {
            while (elements.canRead)
                {
                values.add(elements.readObject<Serializable>());
                }
            }
        return values;
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
    <Serializable> Serializable[] readArrayUsing(Mapping<Serializable> mapping,
                                                 Serializable[]?       defaultValue = Null)
        {
        if (isNull())
            {
            return defaultValue ?: [];
            }

        Serializable[] values = new Serializable[];
        using (ElementInput<ElementInput> elements = openArray())
            {
            while (elements.canRead)
                {
                values.add(elements.readUsing(mapping));
                }
            }
        return values;
        }
    }