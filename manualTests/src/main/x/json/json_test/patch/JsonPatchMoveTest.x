import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchMoveTest {

    @Test
    void shouldParseMoveOperation() {
        String jsonOp = $|\{
                         |"op": "move",
                         |"path": "/one/two",
                         |"from": "/three/four"
                         |}
                         ;

        JsonPatch.Operation expected = new JsonPatch.Operation(Move, JsonPointer.from("/one/two"),
                Null, JsonPointer.from("/three/four"));

        assertOperation(jsonOp, expected);
    }
}