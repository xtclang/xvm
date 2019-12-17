/**
 * A "builder" interface for a JSON object.
 */
interface Builder
        extends Closeable
    {
    // ----- builder -------------------------------------------------------------------------------

    /**
     * Add the name/value pair to the current JSON object. A JSON Builder represents any number of
     * nested JSON objects; the current JSON object is the last one to have been "entered" but not
     * "exited".
     *
     * @param name   the name for the JSON property
     * @param value  the JSON Doc value for the JSON property
     *
     * @return this Builder
     */
    Builder add(String name, Doc value);

    /**
     * Add the name/value pair to the current JSON object. A JSON Builder represents any number of
     * nested JSON objects; the current JSON object is the last one to have been "entered" but not
     * "exited".
     *
     * @param name   the name for the JSON property
     * @param value  the integer value for the JSON property
     *
     * @return this Builder
     */
    Builder add(String name, IntNumber value)
        {
        return add(name, value.toIntLiteral());
        }

    /**
     * Add the name/value pair to the current JSON object. A JSON Builder represents any number of
     * nested JSON objects; the current JSON object is the last one to have been "entered" but not
     * "exited".
     *
     * @param name   the name for the JSON property
     * @param value  the floating-point number value for the JSON property
     *
     * @return this Builder
     */
    Builder add(String name, FPNumber value)
        {
        return add(name, value.toFPLiteral());
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of JSON values for the JSON property
     *
     * @return this Builder
     */
    Builder addArray(String name, Doc... values)
        {
        return add(name, values.toArray());
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of integer values for the JSON property
     *
     * @return this Builder
     */
    Builder addArray(String name, IntNumber... values)
        {
        return add(name, new Array<IntLiteral>(values.size, (i) -> values[i].toIntLiteral()));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param values  the sequence of floating-point number values for the JSON property
     *
     * @return this Builder
     */
    Builder addArray(String name, FPNumber... values)
        {
        return add(name, new Array<FPLiteral>(values.size, (i) -> values[i].toFPLiteral()));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array of values.
     *
     * @param name    the name for the JSON property
     * @param size    the number of values to place in the array
     * @param supply  the function that will provide the value for each element of the array
     *
     * @return this Builder
     */
    Builder addArray(String name, Int size, function Doc|IntNumber|FPNumber (Int) supply)
        {
        function Doc (Int) transform = supply.is(function Doc (Int))
                ? supply
                : (i) ->
                        {
                        val v = supply(i);
                        return switch()
                            {
                            case v.is(IntNumber): v.as(IntNumber).toIntLiteral();     // TODO GG as() redundant?
                            case v.is(FPNumber) : v.as(FPNumber) .toFPLiteral();
                            default             : v.as(Doc);
                            };
                        };

        return add(name, new Array<Doc>(size, transform));
        }

    /**
     * Add a name/value pair to the current JSON object, with the value being an array composed of
     * JSON objects added to this Builder via the specified function.
     *
     * @param name   the name for the JSON property
     * @param size   the number of values to place in the array
     * @param build  the function that will build JSON objects into each element of the array
     *
     * @return this Builder
     */
    Builder addArray(String name, Int size, function void (Int, Builder) build);

    /**
     * Create a JSON object under the current document, and then "enter" that document so that it
     * becomes the new current document.
     *
     * @param name  the name for the JSON property that will contain the new JSON object
     *
     * @return this Builder
     */
    Builder enter(String name);

    /**
     * Undo a corresponding previous call to [enter()].
     *
     * @return this Builder
     */
    Builder exit();

    /**
     * Finish the document, adding any necessary closing `]` and `}` terminators.
     *
     * @return this Builder
     *
     * @throws IllegalState if the builder cannot be closed from its current point
     */
    @Override
    Builder close()
        {
        return this;
        }
    }