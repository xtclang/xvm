/**
 * A fluent API to build instances of `JsonObject`.
 */
class JsonObjectBuilder
        extends JsonBuilder<JsonObject, String> {

    /**
     * A type that is a function that can create a new instance of a mutable `JsonObject`.
     */
    typedef function JsonObject () as Factory;

    /**
     * Create a JSON object builder.
     *
     * @param template  an optional `Map<String, Doc>` to use to populate
     *                  the builder with an initial set of values
     * @param factory   a factory to create a new mutable `JsonArray`
     */
    construct (JsonObject? template = Null, Factory factory = () -> json.newObject()) {
        this.factory = factory;
        values  = json.newObject();
        if (template.is(JsonObject)) {
            values.putAll(template);
        }
    }

    /**
     * The factory to create a new mutable `JsonObject`.
     */
    private Factory factory;

    /**
     * The map of values to be used to create a JSON object.
     */
    private Map<String, Doc> values;

    /**
     * @return the number of values that have been added to the builder.
     */
    Int size.get() = values.size;

    /**
     * Add a value to the `JsonObject`.
     *
     * @param key    the key representing the path in the `JsonObject` to add the value
     * @param value  the `Doc` value to add
     *
     * @return this `JsonObjectBuilder`
     */
    JsonObjectBuilder add(String key, Doc value) {
        values.put(key, value);
        return this;
    }

    /**
     * Add the JSON value created by a `JsonBuilder` to the `JsonObject`.
     *
     * @param key      the `JsonPointer` representing the path in the `JsonObject` to add the value
     * @param builder  the `JsonBuilder` that will build the `Doc` value to add
     *
     * @return this `JsonBuilder`
     */
    JsonObjectBuilder add(String key, JsonBuilder builder) = add(key, builder.build());

    /**
     * Add all the values contained in the `Map`
     *
     * @param map  the map of values to add
     *
     * @return this `JsonBuilder`
     */
    JsonObjectBuilder addAll(Map<String, Doc> map) {
        values.putAll(map);
        return this;
    }

    /**
     * Add all the values contained in the `JsonObject`
     *
     * @param map  the map of values to add
     *
     * @return this `JsonBuilder`
     */
    JsonObjectBuilder addAll(JsonObject o) {
        values.putAll(o);
        return this;
    }

    /**
     * Build an immutable `JsonObject` from the values added to this builder.
     */
    @Override
    JsonObject build() {
        JsonObject o = factory();
        o.putAll(values);
        return o.makeImmutable();
    }

    @Override
    protected String id(JsonPointer path) = path.key;

    @Override
    protected Doc get(String key) = values[key];

    @Override
    protected void update(String key, Doc doc) = add(key, doc);

    @Override
    protected void merge(String key, Doc value) {
        Doc existing = values[key];
        switch (existing.is(_)) {
        case JsonObject:
            if (value.is(JsonStruct)) {
                JsonObject o = new JsonObjectBuilder(existing).deepMerge(value).build();
                add(key, o);
            } else if (value.is(Primitive)) {
                add(key, value);
            } else {
                assert;
            }
            break;
        case JsonArray:
            if (value.is(JsonStruct)) {
                JsonArray a = new JsonArrayBuilder(existing).deepMerge(value).build();
                add(key, a);
            } else if (value.is(Primitive)) {
                add(key, value);
            } else {
                assert;
            }
            break;
        case Primitive:
            add(key, value);
            break;
        default:
            assert;
        }
    }
}