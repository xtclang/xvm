/**
 * A mapping for a `JsonPatch`.
 */
const JsonPatchMapping
        implements Mapping<JsonPatch> {

    /**
     * Construct the JsonPatchOperationMapping.
     */
    construct() {
        typeName = "JsonPatch";
    }

    /**
     * The mapper for the array of operations.
     */
    private ArrayMapping<JsonPatch.Operation> opMapping = new ArrayMapping(new JsonPatchOperationMapping());

    @Override
    Serializable read(ElementInput in) {
        Array<JsonPatch.Operation> ops = opMapping.read(in);
        return JsonPatch.create(ops);
    }

    @Override
    void write(ElementOutput out, Serializable value) {
        opMapping.write(out, value);
    }
}
