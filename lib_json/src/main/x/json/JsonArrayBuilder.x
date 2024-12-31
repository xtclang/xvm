/**
 * A fluent API to build instances of `JsonArray`.
 */
class JsonArrayBuilder
        extends JsonBuilder<JsonArray, Int> {

    /**
     * A type that is a function that can create a new instance of a mutable `JsonArray`.
     */
    typedef function JsonArray () as Factory;

    /**
     * Create a JSON array builder.
     *
     * @param template  (optional) an optional `JsonArray` to use to populate the builder with an
     *                  initial set of values
     * @param factory   (optional) a factory to create a new mutable `JsonArray`
     */
    construct(JsonArray? template = Null, Factory factory = () -> json.newArray()) {
        this.factory = factory;
        this.values  = new Array();
        if (template.is(JsonArray)) {
            values.addAll(template);
        }
    }

    /**
     * The factory to create a new mutable `JsonObject`.
     */
    private Factory factory;

    /**
     * The array of values to be used to create a JSON array.
     */
    private Array<Doc> values;

    /**
     * @return the number of values that have been added to the builder.
     */
    Int size.get() = values.size;

    /**
     * Add a value to the `JsonArray`.
     *
     * @param value  the `Doc` value to add
     *
     * @return this `JsonArrayBuilder`
     */
    JsonArrayBuilder add(Doc value) {
        values.add(value);
        return this;
    }

    /**
     * Add the JSON value created by a `JsonBuilder` to the `JsonArray`.
     *
     * @param builder  the `JsonBuilder` that will build the `Doc` value to add
     *
     * @return this `JsonArrayBuilder`
     */
    JsonArrayBuilder add(JsonBuilder builder) = add(builder.build());

    /**
     * Add all the values to the `JsonArray`.
     *
     * @param docs  an array of JSON `Doc` to add
     *
     * @return this `JsonArrayBuilder`
     */
    JsonArrayBuilder addAll(Doc[] docs) {
        values.addAll(docs);
        return this;
    }

    /**
     * Set the value at the specified index in the array.
     *
     * @param index  the index to add the value at
     * @param value  the value to set at the specified index
     */
    JsonArrayBuilder set(Int index, Doc value) {
        values.replace(index, value);
        return this;
    }

    /**
     * Set the value produced by a `JsonBuilder` at the specified index in the array.
     *
     * @param builder  the `JsonBuilder` that will build the `Doc` value to set
     * @param value    the value to set at the specified index
     */
    JsonArrayBuilder set(Int index, JsonBuilder builder) = set(index, builder.build());

    /**
     * Build an immutable `JsonArray` from the values added to this builder.
     */
    @Override
    JsonArray build() {
        JsonArray array = factory();
        array.addAll(values);
        return array.makeImmutable();
    }

    @Override
    protected Int id(JsonPointer path) = path.index ?: assert;

    @Override
    protected Doc get(Int index) = values[index];

    @Override
    protected void update(Int index, Doc doc) = set(index, doc);

    @Override
    protected void merge(Int index, Doc value) {
        if (index < 0) {
            add(value);
        } else {
            Doc existing = values[index];
            switch (existing.is(_), value.is(_)) {
            case (JsonObject, JsonStruct):
                set(index, new JsonObjectBuilder(existing).deepMerge(value).build());
                break;
            case (JsonArray, JsonStruct):
                set(index, new JsonArrayBuilder(existing).deepMerge(value).build());
                break;
            case (Doc, Primitive):
            case (Primitive, Doc):
                set(index, value);
                break;
            default:
                assert as $"Cannot merge a {&value.actualType} into a {&existing.actualType}";
            }
        }
    }

    /**
     * Merge a `JsonObject` into the array.
     *
     * All of the `JsonObject` keys must be strings that are integer literals in the range between
     * zero and the current size of the array being built.
     */
    @Override
    protected void mergeObject(JsonObject o) {
        Map<JsonPointer, Doc> map = new ListMap();
        for (Map.Entry<String, Doc> entry : o.entries) {
            JsonPointer pointer = JsonPointer.from(entry.key);
            Int?        index   = pointer.index;
            assert index != Null as "Cannot merge JSON Object with non-Int keys into a JSON array";
            assert 0 <= index < values.size as
                    $|Cannot merge JSON Object into JSON array - key "{entry.key}" does not match \
                     |an existing array entry in the range 0..<{values.size}
                     ;
            map.put(pointer, entry.value);
        }

        for (Map.Entry<JsonPointer, Doc> entry : map.entries) {
            deepMerge(entry.key, entry.value);
        }
    }
}