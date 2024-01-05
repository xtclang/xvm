import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchTestTest {

    @Test
    void shouldParseTestOperation() {
        String jsonOp = $|\{
                         |"op": "test",
                         |"path": "/one/two",
                         |"value": \{
                         |    "a": "b"
                         |    }
                         |}
                         ;

        JsonObject          value    = Map:["a"="b"];
        JsonPatch.Operation expected = new JsonPatch.Operation(Test, JsonPointer.from("/one/two"), value);
        assertOperation(jsonOp, expected);
    }


}