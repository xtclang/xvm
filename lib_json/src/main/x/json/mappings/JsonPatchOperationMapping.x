/**
 * A mapping for a `JsonPatch.Operation`.
 */
const JsonPatchOperationMapping
        implements Mapping<JsonPatch.Operation> {

    /**
     * Construct the JsonPatchOperationMapping.
     */
    construct() {
        typeName  = "JsonPatch.Operation";
        actionMap = new HashMap();
        for (JsonPatch.Action action : JsonPatch.Action.values) {
            actionMap.put(action.jsonName, action);
        }
    }

    /**
     * A map of operation name to `JsonPatch.Action` enum.
     */
    private Map<String, JsonPatch.Action> actionMap;

    @Override
    Serializable read(ElementInput in) {
        using (val obj = in.openObject()) {
            JsonPatch.Action op    = actionMap[obj.readString("op")] ?: assert;
            JsonPointer      path  = JsonPointer.from(obj.readString("path"));
            Doc              value = obj.isNull("value") ? Null : obj.readDoc("value").makeImmutable();
            JsonPointer?     from  = obj.isNull("from") ? Null : JsonPointer.from(obj.readString("from"));
            return new JsonPatch.Operation(op, path, value, from);
        }
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        using (val obj = out.openObject()) {
            obj.add("op", value.op.jsonName);
            obj.add("path", value.path.toString());
            if (value.value != Null) {
                obj.add("value", value.value);
            }
            if (value.from.is(JsonPointer)) {
                obj.add("from", value.from.toString());
            }
        }
    }
}
