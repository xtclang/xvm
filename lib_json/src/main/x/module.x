module json.xtclang.org
        implements ecstasy.io.TextFormat
    {
    import ecstasy.io.IOException;
    import ecstasy.io.ObjectInput;
    import ecstasy.io.ObjectOutput;
    import ecstasy.io.Reader;
    import ecstasy.io.Writer;

    /**
     * An IllegalJSON exception is raised when a JSON format error is detected.
     */
    const IllegalJSON(String? text = Null, Exception? cause = Null)
            extends IOException(text, cause);

    /**
     * A MissingMapping exception is raised when a [Mapping] is required for a particular Ecstasy
     * type or JSON document format, and no corresponding Mapping is available.
     */
    const MissingMapping(String? text = Null, Exception? cause = Null, Type? type = Null)
            extends IllegalJSON(text, cause);

    /**
     * JSON primitive types are all JSON values except for arrays and objects.
     */
    typedef (Nullable | Boolean | IntLiteral | FPLiteral | String) Primitive;

    /**
     * JSON types include primitive types, array types, and map types.
     */
    typedef (Primitive | Map<String, Doc> | Array<Doc>) Doc;


    // ----- TextFormat interface ------------------------------------------------------------------

    @Override
    @RO String name.get()
        {
        return Schema.DEFAULT.name;
        }

    @Override
    ObjectInput createObjectInput(Reader reader)
        {
        // use the default JSON schema to deserialize objects
        return Schema.DEFAULT.createObjectInput(reader);
        }

    @Override
    ObjectOutput createObjectOutput(Writer writer)
        {
        // use the default JSON schema to serialize objects
        return Schema.DEFAULT.createObjectOutput(writer);
        }
    }