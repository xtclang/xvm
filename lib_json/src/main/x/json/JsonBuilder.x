/**
 * A base class for JSON value builders.
 *
 * @param JsonType  the type of the JSON value the builder builds
 * @param Id        the type of the identifier used to identify entries in the builder
 */
@Abstract
class JsonBuilder<JsonType extends JsonStruct, Id extends Int | String> {

    /**
     * Build a JSON value.
     */
    @Abstract JsonType build();

    /**
     * Return an `Id` for an entry in the builder from a given `JsonPointer`.
     *
     * @param path  the `JsonPointer` to convert to an `Id`
     *
     * @return the `Id` created from the `JsonPointer`
     */
    @Abstract protected Id id(JsonPointer path);

    /**
     * Merge the specified `Doc` into the entry in this builder
     * with the specified `Id`.
     *
     * The exact behaviour and merge rules will differ depending on the
     * type of JSON value the builder builds.
     */
    @Abstract protected void merge(Id id, Doc doc);

    /**
     * Re-map the entry at the specified `Id` with a new value.
     *
     * @param id   the `Id` of the entry to update
     * @param doc  the new `Doc` value to map to the entry
     */
    @Abstract protected void update(Id id, Doc doc);

    /**
     * Return the `Doc` value the builder contains for the specified `Id`.
     *
     * As a `Doc` type can be `Null`, if there is no entry contained in the
     * builder for the specified `Id` then a `Null` value will be returned,
     * which is a valid `Doc` value.
     */
    @Abstract protected Doc get(Id id);

    /**
     * Perform a deep merge of the specified JSON structure with
     * the JSON structure this builder is building.
     *
     * How the merge is performed will differ depending on the type of
     * structure passed in and the structure being built.
     *
     * @param  the `JsonStruct` to merge
     *
     * @return this `JsonBuilder`
     */
    JsonBuilder deepMerge(JsonStruct s) {
        switch (s.is(_)) {
        case JsonObject:
            mergeObject(s);
            break;
        case JsonArray:
            mergeArray(s);
            break;
        default:
            assert;
        }
        return this;
    }

    /**
     * Deeply merge a JSON value into this builder.
     *
     * @param path  the path to the location to merge the value into
     * @param doc   the JSON value to merge at the specified location
     *
     * @return this `JsonBuilder`
     */
    JsonBuilder deepMerge(JsonPointer path, Doc doc) {
        Id id = id(path);
        if (path.isLeaf) {
            merge(id, doc);
        } else {
            Doc existing = get(id);
            switch (existing.is(_)) {
            case JsonObject:
                mergeObjectMember(existing, path, doc, id);
                break;
            case JsonArray:
                mergeArrayMember(existing, path, doc, id);
                break;
            case Primitive:
                mergePrimitiveMember(existing, path, doc, id);
                break;
            default:
                assert;
            }
        }
        return this;
    }

    /**
     * Deeply merge the entries in a `JsonObject` into the JSON value being
     * produced by this builder.
     *
     * @param o  the `JsonObject` to merge
     */
    protected void mergeObject(JsonObject o) {
        for (Map.Entry<String, Doc> entry : o.entries) {
            deepMerge(JsonPointer.from(entry.key), entry.value);
        }
    }

    /**
     * Deeply merge a `JsonObject` value into this builder.
     *
     * @param p     the `JsonObject` to merge
     * @param path  the path to the location the object value should be merged into
     * @param doc   the value to merge the object into
     * @param id    the id of the entry being merged into
     */
    protected void mergeObjectMember(JsonObject o, JsonPointer path, Doc doc, Id id) {
        JsonPointer remainder = path.remainder ?: assert;
        JsonObject  updated   = new JsonObjectBuilder(o).deepMerge(remainder, doc).build();
        update(id, updated);
    }

    /**
     * Deeply merge the entries in a `JsonArray` into the JSON value being
     * produced by this builder.
     *
     * @param a  the `JsonArray` to merge
     */
    protected void mergeArray(JsonArray a) {
        for (Int i : 0 ..< a.size) {
            deepMerge(JsonPointer.from(i.toString()), a[i]);
        }
    }

    /**
     * Deeply merge a `JsonArray` value into this builder.
     *
     * @param a     the `JsonArray` to merge
     * @param path  the path to the location the array value should be merged into
     * @param doc   the value to merge the array into
     * @param id    the id of the entry being merged into
     */
    protected void mergeArrayMember(JsonArray a, JsonPointer path, Doc doc, Id id) {
        JsonPointer remainder = path.remainder ?: assert;
        JsonArray   updated   = new JsonArrayBuilder(a).deepMerge(remainder, doc).build();
        update(id, updated);
    }

    /**
     * Deeply merge a `Primitive` value into this builder.
     *
     * @param p     the `Primitive` to merge
     * @param path  the path to the location the primitive value should be merged into
     * @param doc   the value to merge the primitive into
     * @param id    the id of the entry being merged into
     */
    protected void mergePrimitiveMember(Primitive p, JsonPointer path, Doc doc, Id id) {
        JsonPointer remainder = path.remainder ?: assert;
        JsonObject  updated   = new JsonObjectBuilder().deepMerge(remainder, doc).build();
        update(id, updated);
    }

    /**
     * Perform a deep copy of the specified JSON document.
     *
     * @param doc  the JSON document to copy
     *
     * @return a mutable copy of the JSON document unless the document is
     *         a primitive, in which case the same primitive is returned
     */
    static <JsonDoc extends Doc> JsonDoc deepCopy(JsonDoc doc) {
        return switch (doc.is(_)) {
            case JsonObject: deepCopyObject(doc).as(JsonDoc);
            case JsonArray:  deepCopyArray(doc).as(JsonDoc);
            case Primitive:  doc;
            default:
                assert;
        };
    }

    /**
     * Perform a deep copy of the specified JSON object.
     *
     * @param o  the JSON object to copy
     *
     * @return a mutable copy of the JSON object
     */
    static JsonObject deepCopyObject(JsonObject o) {
        JsonObject copy = json.newObject();
        for (Map.Entry<String, Doc> entry : o.entries) {
            copy.put(entry.key, deepCopy(entry.value));
        }
        return copy;
    }

    /**
     * Perform a deep copy of the specified JSON array.
     *
     * @param o  the JSON array to copy
     *
     * @return a mutable copy of the JSON array
     */
    static JsonArray deepCopyArray(JsonArray a) {
        JsonArray copy = json.newArray();
        for (Doc doc : a) {
            copy.add(deepCopy(doc));
        }
        return copy;
    }
}