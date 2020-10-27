/**
 * An interface for building a JSON object. A JSON object is a sequence of name/value pairs, in
 * which each value can be a primitive (`null`, `true`, `false`, number, string), array, or object
 * value.
 *
 * This interface represents a context that is within a JSON object. A JSON object can be thought of
 * as a sequence of comma-delimited name/value pairs within curly braces, so when this interface is
 * obtained for an empty object, the context can be imagined as this:
 *
 *     {
 *          <--- you are here
 *     }
 *
 * If the client then calls `add("@class", "Point")`, the resulting object would look like this:
 *
 *     {
 *     "@class": "Point"
 *     }
 *
 * A few more calls to add, and the JSON object is a fully populated JSON object:
 *
 *     {
 *     "@class": "Point",
 *     "x": 0,
 *     "y": 0
 *     }
 *
 * A call to `openObject("Color")` would add a sub-object, and changes the context:
 *
 *     {
 *     "@class": "Point",
 *     "x": 0,
 *     "y": 0,
 *     "Color":
 *       {
 *            <--- you are now here
 *       }
 *     }
 *
 * And a few calls later, like `add("R", 255)` and similar, and the resulting object looks like:
 *
 *     {
 *     "@class": "Point",
 *     "x": 0,
 *     "y": 0,
 *     "Color":
 *       {
 *       "R": 255,
 *       "G": 0,
 *       "B": 255
 *       }
 *     }
 *
 * A corresponding call to `close()` returns the context from the sub-object for _Color_, back to
 * the original object for _Point_.
 *
 * If the Schema in use had a mapping for the Color object, then all of those steps from
 * `openObject()` to `close()` could be replaced with a single call to `addObject("Color", color)`,
 * which would delegate the nested object name/value population to the [Mapping] for the `Color`
 * type.
 */
interface FieldOutput<ParentOutput extends (ElementOutput | FieldOutput)?>
        extends DocOutput<ParentOutput>
    {
    // ----- nesting values ------------------------------------------------------------------------

    /**
     * Add a JSON array under the current document, and return an ElementOutput that will write to
     * it.
     *
     * @param name  the name for the JSON property that will contain the new JSON array
     *
     * @return a ElementOutput that will write to the nested JSON array
     */
    ElementOutput<FieldOutput> openField(String name);

    /**
     * Add a JSON array under the current document, and return an ElementOutput that will write to
     * it.
     *
     * @param name  the name for the JSON property that will contain the new JSON array
     *
     * @return a ElementOutput that will write to the nested JSON array
     */
    ElementOutput<FieldOutput> openArray(String name);

    /**
     * Add a JSON object under the current document, and return a FieldOutput that will write to it.
     *
     * @param name  the name for the JSON property that will contain the new JSON object
     *
     * @return a FieldOutput that will write to the nested JSON object
     */
    FieldOutput!<FieldOutput> openObject(String name);


    // ----- single values -------------------------------------------------------------------------

    /**
     * Add the name/value pair to the current JSON object.
     *
     * @param name   the name for the JSON property
     * @param value  the JSON Doc value for the JSON property
     *
     * @return this FieldOutput
     */
    FieldOutput add(String name, Doc value)
        {
        using (val field = openField(name))
            {
            field.add(value);
            }
        return this;
        }

    /**
     * Add the name/value pair to the current JSON object.
     *
     * @param name   the name for the JSON property
     * @param value  the integer value for the JSON property
     *
     * @return this FieldOutput
     */
    FieldOutput add(String name, IntNumber value)
        {
        return add(name, value.toIntLiteral());
        }

    /**
     * Add the name/value pair to the current JSON object.
     *
     * @param name   the name for the JSON property
     * @param value  the floating-point number value for the JSON property
     *
     * @return this FieldOutput
     */
    FieldOutput add(String name, FPNumber value)
        {
        return add(name, value.toFPLiteral());
        }

    /**
     * Add a name/value pair to the current JSON object, using the provided name and emitting a
     * JSON value using the [Mapping] obtained from the [Schema] based on the type of `value`.
     * Additionally, if [Schema.enableMetadata] is `True`, then it is expected that the Ecstasy type
     * will also be encoded into the resulting JSON value, providing some future deserialization
     * operation with sufficient type information to instantiate a sufficiently-identical object
     * from the JSON value.
     *
     * @param name   the name for the JSON property
     * @param value  the object to serialize into the named value (typically a nested sub-object)
     *
     * @return this FieldOutput
     */
    <Serializable> FieldOutput addObject(String name, Serializable value)
        {
        if (value == Null)
            {
            return add(name, Null);
            }

        using (val nested = openField(name))
            {
            nested.addObject(value);
            }

        return this;
        }

    /**
     * Add a name/value pair to the current JSON object, using the provided name and emitting a
     * JSON value using the provided [Mapping].
     *
     * @param mapping  the [Mapping] instance to use to serialize the object
     * @param name     the name for the JSON property
     * @param value    the object to serialize into the named value (typically a nested sub-object)
     *
     * @return this FieldOutput
     */
    <Serializable> FieldOutput addUsing(Mapping<Serializable> mapping, String name, Serializable value)
        {
        if (value == Null)
            {
            return add(name, value);
            }

        using (val nested = openField(name))
            {
            nested.addUsing(mapping, value);
            }

        return this;
        }


    // ----- array values --------------------------------------------------------------------------

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of JSON values for the JSON property
     *
     * @return this FieldOutput
     */
    FieldOutput addArray(String name, Iterable<Doc> values)
        {
        return add(name, values.toArray());
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of integer values for the JSON property
     *
     * @return this FieldOutput
     */
    FieldOutput addArray(String name, Iterable<IntNumber> values)
        {
        val iter = values.iterator();
        return add(name, new Array<IntLiteral>(values.size, (_) ->
                {
                assert val num := iter.next();
                return num.toIntLiteral();
                }));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of floating-point number values for the JSON property
     *
     * @return this FieldOutput
     */
    FieldOutput addArray(String name, Iterable<FPNumber> values)
        {
        val iter = values.iterator();
        return add(name, new Array<FPLiteral>(values.size, (_) ->
                {
                assert val num := iter.next();
                return num.toFPLiteral();
                }));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being a JSON array with each
     * element populated by the [Mapping] for the type of the `Serializable` values.
     *
     * @param name   the name for the JSON property
     * @param value  the objects to serialize into the array of nested sub-objects
     *
     * @return this FieldOutput
     */
    <Serializable> FieldOutput addObjectArray(String name, Iterable<Serializable> values)
        {
        using (val array = openArray(name))
            {
            for (Serializable value : values)
                {
                array.addObject(value);
                }
            }
        return this;
        }
    }