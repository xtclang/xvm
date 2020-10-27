/**
 * An interface for building a JSON document. TODO
 */
interface ElementOutput<ParentOutput extends (ElementOutput | FieldOutput)?>
        extends DocOutput<ParentOutput>
    {
    // ----- nesting values ------------------------------------------------------------------------

    /**
     * Create a JSON object under the current document, and then "enter" that document so that it
     * becomes the new current document.
     *
     * @return the ElementOutput for the new JSON array
     *
     * @throws IllegalJSON   if this element is not an array and already has a value
     * @throws IllegalState  if this element has already been closed
     */
    ElementOutput!<ElementOutput> openArray();

    /**
     * Create a JSON object under the current document, and then "enter" that object, so that it
     * becomes the active node of the document being built.
     *
     * @return the FieldOutput for the new JSON object
     *
     * @throws IllegalJSON   if this element is not an array and already has a value
     * @throws IllegalState  if this element has already been closed
     */
    FieldOutput<ElementOutput> openObject();


    // ----- single values -------------------------------------------------------------------------

    /**
     * Store the specified JSON `Doc` value in this JSON element.
     *
     * @param value  the JSON `Doc` value
     *
     * @return this ElementOutput
     *
     * @throws IllegalJSON   if this element is not an array and already has a value
     * @throws IllegalState  if this element has already been closed
     */
    ElementOutput add(Doc value);

    /**
     * Store the specified integer value in this JSON element.
     *
     * @param value  the integer value for the JSON property
     *
     * @return this ElementOutput
     */
    ElementOutput add(IntNumber value)
        {
        return add(value.toIntLiteral());
        }

    /**
     * Store the specified floating point value in this JSON element.
     *
     * @param value  the floating-point number value for the JSON property
     *
     * @return this ElementOutput
     */
    ElementOutput add(FPNumber value)
        {
        return add(value.toFPLiteral());
        }

    /**
     * Store the serialized form of the specified value in this JSON element.
     *
     * @param value  the object to serialize into a nested sub-object
     *
     * @return this ElementOutput
     *
     * @throws MissingMapping  if no appropriate mapping can be located
     */
    <Serializable> ElementOutput addObject(Serializable value)
        {
        val type = &value.actualType;
        if (schema.enableMetadata)
            {
            prepareMetadata(schema.typeKey, schema.nameForType(type));
            }

        return addUsing(schema.ensureMapping(type), value);
        }

    /**
     * Store the serialized form of the specified value in this JSON element.
     *
     * @param mapping  the [Mapping] instance to use to serialize the object
     * @param value    the object to serialize into a nested sub-object
     *
     * @return this ElementOutput
     */
    <Serializable> ElementOutput addUsing(Mapping<Serializable> mapping, Serializable value)
        {
        if (value == Null)
            {
            return add(Null);
            }

        mapping.write(this, value);
        return this;
        }


    // ----- array values --------------------------------------------------------------------------

    /**
     * Store an array of the specified values in this JSON element.
     *
     * @param values  a sequence of JSON values for the JSON property
     *
     * @return this ElementOutput
     */
    ElementOutput addArray(Iterable<Doc> values)
        {
        return add(values.toArray());
        }

    /**
     * Store an array of the specified values in this JSON element.
     *
     * @param values  a sequence of integer values for the JSON property
     *
     * @return this ElementOutput
     */
    ElementOutput addArray(Iterable<IntNumber> values)
        {
        val iter = values.iterator();
        return add(new Array<IntLiteral>(values.size, (_) ->
                {
                assert val num := iter.next();
                return num.toIntLiteral();
                }));
        }

    /**
     * Store an array of the specified values in this JSON element.
     *
     * @param values  a sequence of floating-point number values for the JSON property
     *
     * @return this ElementOutput
     */
    ElementOutput addArray(Iterable<FPNumber> values)
        {
        val iter = values.iterator();
        return add(new Array<FPLiteral>(values.size, (_) ->
                {
                assert val num := iter.next();
                return num.toFPLiteral();
                }));
        }

    /**
     * Store an array of the specified values in this JSON element, with each value being serialized
     * by the [Mapping] for the type of the `Serializable` values.
     *
     * @param value  the objects to serialize into the array of nested sub-objects
     *
     * @return this ElementOutput
     */
    <Serializable> ElementOutput addObjectArray(Iterable<Serializable> values)
        {
        val array = openArray();
        for (Serializable value : values)
            {
            array.addObject(value);
            }
        array.close();
        return this;
        }
    }