/**
 * The interface for reading a sequence of name/value pairs from a JSON object.
 */
interface FieldInput<ParentInput extends (ElementInput | FieldInput)?>
        extends DocInput<ParentInput>
    {
    // ----- nesting values ------------------------------------------------------------------------

    /**
     * For a name/value pair in the current JSON object, obtain an [ElementInput] for the value.
     *
     * @param name  the name of the JSON property within the current JSON object
     *
     * @return the ElementInput that can read the contents of the specified field
     */
    ElementInput<FieldInput> openField(String name);

    /**
     * For a named JSON array under the current document, "enter" that array, returning the
     * `ElementInput` to use to read the array's contents.
     *
     * @param name  the name of the JSON property within the current JSON object that contains a
     *              JSON array
     *
     * @return the ElementInput that can read the elements of the JSON array, starting with the
     *         first element
     *
     * @throws IllegalJSON   if the specified name does not have a value which is a JSON array
     */
    ElementInput<FieldInput> openArray(String name);

    /**
     * For a named JSON object under the current document, "enter" that document, returning the
     * `FieldInput` to use to read the entered document's contents.
     *
     * Note that the resulting `FieldInput` does not copy the strict ordering designation of the
     * previous `FieldInput`.
     *
     * If the current element is a JSON object, then obtain a `FieldInput` for that object.
     *
     * @param name  the name of the JSON property within the current JSON object that contains a
     *              JSON object
     *
     * @return the FieldInput for the JSON object that is held in the current element
     *
     * @throws IllegalJSON   if the specified name does not have a value which is a JSON object
     */
    FieldInput!<FieldInput> openObject(String name);


    // ----- object fields -------------------------------------------------------------------------

    /**
     * Obtain the name of the next field to read from the document.
     *
     * @return True if any fields remain to read; `False` if [canRead] is `False`
     * @return (conditional) the name of the next field
     */
    conditional String nextName();

    /**
     * Test if the JSON object contains the specified name from an unread name/value pair, and if it
     * does, whether the corresponding value is not `null`.
     *
     * The behavior of this method depends largely on whether the [Schema.randomAccess] option is
     * enabled:
     * * If the [Schema.randomAccess] is `False`, then only the next name/value pair is evaluated.
     * * If the [Schema.randomAccess] is `True`, then potentially all unread name/value pairs are
     *   evaluated, starting with the current name/value pair, followed by any previously-skipped
     *   name/value pairs, followed by the sequence of yet-to-be-read name/value pairs, until the
     *   specified name is found (or the name/value pairs are exhausted).
     *
     * @param name  the name to test for
     *
     * @return True iff the name is present (as specified by the documentation of this method) and
     *         unread in the JSON object
     * @return (conditional) True iff the corresponding value is not `null`
     */
    conditional Boolean contains(String name);

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
     * Test for the existence of an unread name/value pair with the specified name without altering
     * the input position within the document (unless the [Schema.randomAccess] option is enabled).
     *
     * @param name  the name of the JSON property to check for absence or a `null` value
     *
     * @return True iff the element is `null` or does not exist
     */
    Boolean isNull(String name)
        {
        Boolean notNull = False;
        notNull := contains(name);
        return !notNull;
        }

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
    Boolean readBoolean(String name, Boolean? defaultValue = Null)
        {
        Doc doc = readDoc(name);
        if (doc.is(Boolean))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|Boolean value required for "{name}" at "{pointer}"\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    String readString(String name, String? defaultValue = Null)
        {
        Doc doc = readDoc(name);
        if (doc.is(String))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|String value required for "{name}" at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    IntLiteral readIntLiteral(String name, (IntLiteral|Number)? defaultValue = Null)
        {
        Doc doc = readDoc(name);
        if (doc.is(IntLiteral))
            {
            return doc;
            }

        if (doc == Null)
            {
            return ensureIntLiteral(defaultValue?);
            }

        throw new IllegalJSON($|IntLiteral value required for "{name}" at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    FPLiteral readFPLiteral(String name, (IntLiteral|FPLiteral|Number)? defaultValue = Null)
        {
        Doc doc = readDoc(name);
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

        throw new IllegalJSON($|FPLiteral value required for "{name}" at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    Int readInt(String name, Int? defaultValue = Null)
        {
        Doc doc = readDoc(name);
        if (doc.is(IntLiteral))
            {
            return doc;
            }

        if (doc == Null)
            {
            return defaultValue?;
            }

        throw new IllegalJSON($|Int value required for "{name}" at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

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
    Dec readDec(String name, Dec? defaultValue = Null)
        {
        Doc doc = readDoc(name);
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

        throw new IllegalJSON($|Dec value required for "{name}" at "{pointer}";\
                               | {doc == Null ? "no value" : &doc.actualType} found
                             );
        }

    /**
     * Read the specified named value, deserializing it using the available `Schema` information.
     *
     * @param Serializable  the type of the resulting value
     * @param name          the name of the JSON property to read
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON     if the value is 2b=ull and no default value is provided, or if the
     *                         value is not of the requested type
     * @throws MissingMapping  if no appropriate mapping can be located
     */
    <Serializable> Serializable readObject(String        name,
                                           Serializable? defaultValue = Null)
        {
        if (isNull(name))
            {
            return defaultValue?;

            if (Null.is(Serializable))
                {
                // TODO GG return Null;
                return Null.as(Serializable);
                }

            throw new IllegalJSON($|Value required at "{pointer}" of type "{Serializable}"\
                                   | for "{name}" at "{pointer}"; no value found
                                 );
            }

        using (val element = openField(name))
            {
            return element.readObject<Serializable>(defaultValue);
            }
        }

    /**
     * Read the specified named value using the specified deserialization function.
     *
     * @param Serializable  the type of the resulting value
     * @param name          the name of the JSON property to read
     * @param mapping       the Mapping to use to transforms the stream into an Ecstasy object
     * @param defaultValue  (optional) the value to use if the name is missing or has a null value
     *
     * @return a `Serializable` value
     *
     * @throws IllegalJSON  if the value is null or missing and no default value is provided, or if
     *                      the value is not of the requested type
     */
    <Serializable> Serializable readUsing(String                name,
                                          Mapping<Serializable> mapping,
                                          Serializable?         defaultValue = Null)
        {
        if (isNull(name))
            {
            return defaultValue?;

            if (Null.is(Serializable))
                {
                // TODO GG return Null;
                return Null.as(Serializable);
                }

            throw new IllegalJSON($|Value required at "{pointer}" of type "{Serializable}"\
                                   | for "{name}" at "{pointer}"; no value found
                                 );
            }

        using (val element = openField(name))
            {
            return element.readUsing<Serializable>(mapping, defaultValue);
            }
        }


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
    Doc[] readDocArray(String name, Doc[] defaultValue = [])
        {
        if (isNull(name))
            {
            return defaultValue;
            }

        Doc value = readDoc(name);
        return value.is(Doc[])
                ? value
                : throw new IllegalJSON($|Doc[] value required for "{name}" at "{pointer}";\
                                         | {&value.actualType} found
                                       );
        }

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
    Boolean[] readBooleanArray(String name, Boolean[] defaultValue = [])
        {
        if (isNull(name))
            {
            return defaultValue;
            }

        Boolean[] values = new Boolean[];
        using (val elements = openArray(name))
            {
            while (elements.canRead)
                {
                values.add(elements.readBoolean());
                }
            }
        return values;
        }

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
    String[] readStringArray(String name, String[] defaultValue = [])
        {
        if (isNull(name))
            {
            return defaultValue;
            }

        String[] values = new String[];
        using (val elements = openArray(name))
            {
            while (elements.canRead)
                {
                values.add(elements.readString());
                }
            }
        return values;
        }

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
    Int[] readIntArray(String name, Int[] defaultValue = [])
        {
        if (isNull(name))
            {
            return defaultValue;
            }

        Int[] values = new Int[];
        using (val elements = openArray(name))
            {
            while (elements.canRead)
                {
                values.add(elements.readInt());
                }
            }
        return values;
        }

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
    Dec[] readDecArray(String name, Dec[] defaultValue = [])
        {
        if (isNull(name))
            {
            return defaultValue;
            }

        Dec[] values = new Dec[];
        using (val elements = openArray(name))
            {
            while (elements.canRead)
                {
                values.add(elements.readDec());
                }
            }
        return values;
        }

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
        if (isNull(name))
            {
            return defaultValue?;
            throw new IllegalJSON($|Array required of type "{Serializable}"\
                                   | for "{name}" at "{pointer}"; no value found
                                 );
            }

        Serializable[] values = new Serializable[];
        using (val elements = openArray(name))
            {
            while (elements.canRead)
                {
                values.add(elements.readObject<Serializable>());
                }
            }
        return values;
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
    <Serializable> Serializable[] readArrayUsing(String name,
                                                 Mapping<Serializable> mapping,
                                                 Serializable[]?       defaultValue = Null)
        {
        if (isNull(name))
            {
            return defaultValue?;
            throw new IllegalJSON($|Array required of type "{Serializable}"\
                                   | for "{name}" at "{pointer}"; no value found
                                 );
            }

        Serializable[] values = new Serializable[];
        using (val elements = openArray(name))
            {
            while (elements.canRead)
                {
                values.add(elements.readUsing<Serializable>(mapping));
                }
            }
        return values;
        }
    }