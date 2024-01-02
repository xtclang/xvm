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

    mixin JsonStructWithValue(Primitive value)
            into JsonStruct;

    /**
     * @return a new instance of a mutable `JsonObject`.
     */
    JsonObject newObject() {
        return new ListMap<String, Doc>();
    }

    /**
     * @return a builder that can produce immutable JSON object instances.
     */
    JsonObjectBuilder objectBuilder() {
        return new JsonObjectBuilder();
    }

    /**
     * @return a new instance of a mutable `JsonArray`.
     */
    JsonArray newArray() {
        return new Array<Doc>();
    }

    /**
     * @return a builder that can produce immutable JSON array instances.
     */
    JsonArrayBuilder arrayBuilder() {
        return new JsonArrayBuilder();
    }

    /**
     * @return a builder that can produce immutable JSON patch instances.
     */
    JsonPatch.Builder patchBuilder() {
        return JsonPatch.builder();
    }
}