import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchOperationTest {

    @Test
    void shouldBeEqualOperationsWithJsonObjectValue() {
        JsonPatch.Operation op1 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                Map:["a"="b"], JsonPointer.from("/three/four"));
        JsonPatch.Operation op2 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                Map:["a"="b"], JsonPointer.from("/three/four"));
        assert op1 == op2;
    }

    @Test
    void shouldBeEqualOperationsWithJsonArrayValue() {
        JsonPatch.Operation op1 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                ["a","b","c"], JsonPointer.from("/three/four"));
        JsonPatch.Operation op2 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                ["a","b","c"], JsonPointer.from("/three/four"));
        assert op1 == op2;
    }

    @Test
    void shouldBeEqualOperationsWithPrimitiveValue() {
        JsonPatch.Operation op1 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                1234, JsonPointer.from("/three/four"));
        JsonPatch.Operation op2 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                1234, JsonPointer.from("/three/four"));
        assert op1 == op2;
    }

    @Test
    void shouldBeEqualOperationsWithNullValue() {
        JsonPatch.Operation op1 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                Null, JsonPointer.from("/three/four"));
        JsonPatch.Operation op2 = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"),
                Null, JsonPointer.from("/three/four"));
        assert op1 == op2;
    }
}