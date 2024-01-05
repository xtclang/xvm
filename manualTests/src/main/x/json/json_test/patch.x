/**
 * The `patch` package contains tests for `JsonPatch`.
 */
package patch {

    import ecstasy.io.CharArrayReader;
    import ecstasy.io.Reader;
    import json.JsonPatch;
    import json.ObjectInputStream;
    import json.Schema;

    static void assertOperation(String jsonOp, JsonPatch.Operation expected) {
        Schema              schema = Schema.DEFAULT;
        Reader              reader = new CharArrayReader(jsonOp);
        ObjectInputStream   o_in   = new ObjectInputStream(schema, reader);
        JsonPatch.Operation result = o_in.read();
        assert result == expected;
    }

}