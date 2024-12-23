module json.xtclang.org
        delegates ecstasy.io.TextFormat(Schema.DEFAULT) {

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

    // or maybe:

//    typedef (Nullable | Boolean | IntLiteral | IntNumber | FPLiteral | FPNumber | String) as Primitive;

    // or even:
//    typedef (Nullable | Boolean | ecstasy.numbers.IntConvertible | ecstasy.numbers.FPConvertible | String) as Primitive;

    /**
     * JSON types include primitive types, array types, and map types.
     */
    typedef (Primitive | Map<String, Doc> | List<Doc>) as Doc;

    /**
     * A type representing a JSON Array.
     */
    typedef Doc[] as JsonArray;

    /**
     * A type representing a non-primitive JSON structure.
     */
    typedef Map<String, Doc> | JsonArray as JsonStruct;

    /**
     * @return a new instance of a mutable [JsonObject].
     */
    JsonObject newObject(Map<String, Doc> map = []) = new JsonObject(map);

    /**
     * @return a builder that can produce immutable JSON object instances
     */
    JsonObjectBuilder objectBuilder() = new JsonObjectBuilder();

    /**
     * @return a new instance of a mutable `JsonArray`
     */
    JsonArray newArray() = new Doc[];

    /**
     * @return a builder that can produce immutable JSON array instances
     */
    JsonArrayBuilder arrayBuilder() = new JsonArrayBuilder();
}