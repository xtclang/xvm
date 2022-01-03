module json.xtclang.org
        delegates ecstasy.io.TextFormat(Schema.DEFAULT)
    {
    import ecstasy.io.IOException;

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
    typedef (Nullable | Boolean | IntLiteral | FPLiteral | String) as Primitive;

    /**
     * JSON types include primitive types, array types, and map types.
     */
    typedef (Primitive | Map<String, Doc> | Array<Doc>) as Doc;
    }