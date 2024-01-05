import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchAddTest {

    @Test
    void shouldCreatePatchWithSingleAddOp() {
        JsonPatch patch = JsonPatch.builder()
            .add("/one/two", "value-two")
            .build();
        assert patch.size == 1;
        assert patch[0] == new JsonPatch.Operation(Action.Add, JsonPointer.from("/one/two"), "value-two");
    }

    @Test
    void shouldCreatePatchWithMultipleAddOps() {
        JsonPatch patch = JsonPatch.builder()
            .add("/one/two", "value-two")
            .add("/one/three", "value-three")
            .add("/four", "value-four")
            .build();
        assert patch.size == 3;
        assert patch[0] == new JsonPatch.Operation(Action.Add, JsonPointer.from("/one/two"), "value-two");
        assert patch[1] == new JsonPatch.Operation(Action.Add, JsonPointer.from("/one/three"), "value-three");
        assert patch[2] == new JsonPatch.Operation(Action.Add, JsonPointer.from("/four"), "value-four");
    }

    @Test
    void shouldApplyAddToPrimitive() {
        Doc       doc    = "foo";
        JsonPatch patch  = JsonPatch.builder().add("/", "bar").build();
        Doc       result = patch.apply(doc);
        assert result == "bar";
    }

    @Test
    void shouldNotApplyAddWithNonEmptyPathToPrimitive() {
        Doc             doc   = "foo";
        JsonPatch       patch = JsonPatch.builder().add("/one", "bar").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(doc));
    }

    @Test
    void shouldApplyAddAsAppendToArray() {
        JsonArray array  = json.arrayBuilder().add("one").build();
        JsonPatch patch  = JsonPatch.builder().add(JsonPointer.Append, "two").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 2;
        assert result[0] == "one";
        assert result[1] == "two";
    }

    @Test
    void shouldApplyAddToIndexInArray() {
        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch patch  = JsonPatch.builder().add("/1", "added").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 4;
        assert result[0] == "one";
        assert result[1] == "added";
        assert result[2] == "two";
        assert result[3] == "three";
    }

    @Test
    void shouldApplyAddToObjectAtIndexInArray() {
        JsonObject obj      = json.objectBuilder().add("one", 11).add("two", 22).build();
        JsonObject expected = json.objectBuilder().add("one", 1234).add("two", 22).build();
        JsonArray  array    = json.arrayBuilder().addAll(["A", obj, "B"]).build();
        JsonPatch  patch    = JsonPatch.builder().add("/1/one", 1234).build();
        Doc        result   = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 3;
        assert result[0] == "A";
        assert result[2] == "B";
        assert result[1].is(JsonObject);
        assert result[1].as(JsonObject) == expected;
    }

    @Test
    void shouldApplyAddToArrayAtIndexInArray() {
        JsonArray child    = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonArray expected = json.arrayBuilder().addAll(["one", "added", "two", "three"]).build();
        JsonArray array    = json.arrayBuilder().addAll(["A", child, "B"]).build();
        JsonPatch patch    = JsonPatch.builder().add("/1/1", "added").build();
        Doc       result   = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 3;
        assert result[0] == "A";
        assert result[2] == "B";
        assert result[1].is(JsonArray);
        assert result[1].as(JsonArray) == expected;
    }

    @Test
    void shouldApplyAddWithIndexPastEndOfArray() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = JsonPatch.builder().add("/100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyAddWithNegativeIndex() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = JsonPatch.builder().add("/-100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyAddWithAllowedNegativeIndex() {
        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch patch  = JsonPatch.builder().add("/-2", "new").build();
        Doc       result = patch.apply(array, new JsonPatch.Options(supportNegativeIndices = True));
        assert result.is(JsonArray);
        assert result == Array<Doc>:["one", "new", "two", "three"];
    }

    @Test
    void shouldApplyAddToNonIntIndex() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = JsonPatch.builder().add("/-100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyAddToObject() {
        JsonObject obj    = json.objectBuilder().add("one", 11).build();
        JsonPatch  patch  = JsonPatch.builder().add("two", 22).build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 2;
        assert result["one"] == 11;
        assert result["two"] == 22;
    }

    @Test
    void shouldApplyAddToChildObjectOfObject() {
        JsonObject child  = json.objectBuilder().add("two", 22).build();
        JsonObject obj    = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().add("/one/two", 1234).build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 1;
        assert result["one"].is(JsonObject);
        assert result["one"].as(JsonObject) == json.objectBuilder().add("two", 1234).build();
    }

    @Test
    void shouldApplyAddToChildArrayOfObject() {
        JsonArray  child  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonObject obj    = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().add("/one/1", "added").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 1;
        assert result["one"].is(JsonArray);
        assert result["one"].as(JsonArray) == Array<Doc>:["one", "added", "two", "three"];
    }

    @Test
    void shouldApplyAddToChildArrayOfObjectUsingNegativeIndex() {
        JsonArray  child  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonObject obj    = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().add("/one/-1", "added").build();
        Doc        result = patch.apply(obj, new JsonPatch.Options(supportNegativeIndices = True));
        assert result.is(JsonObject);
        assert result.size == 1;
        assert result["one"].is(JsonArray);
        assert result["one"].as(JsonArray) == Array<Doc>:["one", "two", "added", "three"];
    }

    @Test
    void shouldParseAddOperation() {
        String jsonOp = $|\{
                         |"op": "add",
                         |"path": "/one/two",
                         |"value": \{
                         |    "a": "b"
                         |    }
                         |}
                         ;

        JsonObject          value    = Map:["a"="b"];
        JsonPatch.Operation expected = new JsonPatch.Operation(Add, JsonPointer.from("/one/two"), value);
        assertOperation(jsonOp, expected);
    }
}