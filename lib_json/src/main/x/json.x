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

    /**
     * JSON types include primitive types, array types, and map types.
     */
    typedef (Primitive | Map<String, Doc> | Array<Doc>) as Doc;

    /**
     * A type representing a JSON Object.
     */
    typedef Map<String, Doc> as JsonObject;

    /**
     * A type representing a JSON Array.
     */
    typedef Array<Doc> as JsonArray;

    /**
     * A type representing a non-primitive JSON structure.
     */
    typedef JsonObject | JsonArray as JsonStruct;

    /**
     * A JSON struct with a value.
     * ToDo JK: I'm thinking to remove this, it is something really related to use of JSON in config and
     * the more I think about it the more I think we don;t want it as it add needless complications.
     */
    mixin JsonStructWithValue(Primitive value)
            into JsonStruct;

    /**
     * @return a new instance of a mutable `JsonObject`.
     */
    JsonObject newObject() = new ListMap<String, Doc>();

    /**
     * @return a builder that can produce immutable JSON object instances.
     */
    JsonObjectBuilder objectBuilder() = new JsonObjectBuilder();

    /**
     * @return a new instance of a mutable `JsonArray`.
     */
    JsonArray newArray() = new Array<Doc>();

    /**
     * @return a builder that can produce immutable JSON array instances.
     */
    JsonArrayBuilder arrayBuilder() = new JsonArrayBuilder();

    /**
     * @return a builder that can produce immutable JSON patch instances.
     */
    JsonPatch.Builder patchBuilder() = JsonPatch.builder();
}