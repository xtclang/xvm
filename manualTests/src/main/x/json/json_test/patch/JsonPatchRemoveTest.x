import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchRemoveTest {

    @Test
    void shouldCreatePatchWithSingleRemoveOp() {
        JsonPatch patch = JsonPatch.builder()
            .remove("/one/two")
            .build();
        assert patch.size == 1;
        assert patch[0] == new JsonPatch.Operation(Action.Remove, JsonPointer.from("/one/two"));
    }

    @Test
    void shouldCreatePatchWithMultipleRemoveOps() {
        JsonPatch patch = JsonPatch.builder()
            .remove("/one/two")
            .remove("/one/three")
            .remove("/four")
            .build();
        assert patch.size == 3;
        assert patch[0] == new JsonPatch.Operation(Action.Remove, JsonPointer.from("/one/two"));
        assert patch[1] == new JsonPatch.Operation(Action.Remove, JsonPointer.from("/one/three"));
        assert patch[2] == new JsonPatch.Operation(Action.Remove, JsonPointer.from("/four"));
    }

    @Test
    void shouldApplyRemoveToPrimitive() {
        Doc       doc    = "foo";
        JsonPatch patch  = JsonPatch.builder().remove("/").build();
        Doc       result = patch.apply(doc);
        assert result == Null;
    }

    @Test
    void shouldNotApplyRemoveWithNonEmptyPathToPrimitive() {
        Doc             doc    = "foo";
        JsonPatch       patch  = JsonPatch.builder().remove("/one").build();
        IllegalArgument ex     = assertThrows(() -> patch.apply(doc));
    }

    @Test
    void shouldApplyRemoveToKeyInObject() {
        JsonObject obj    = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonPatch  patch  = JsonPatch.builder().remove("/one").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result == json.objectBuilder().add("two", 2).build();
    }

    @Test
    void shouldApplyRemoveToKeyInChildObjectInObject() {
        JsonObject child  = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonObject obj    = json.objectBuilder().add("A", child).build();
        JsonPatch  patch  = JsonPatch.builder().remove("/A/one").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result == json.objectBuilder().add("A", json.objectBuilder().add("two", 2)).build();
    }

    @Test
    void shouldNotApplyRemoveToNonExistentKeyInObject() {
        JsonObject      obj   = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonPatch       patch = JsonPatch.builder().remove("/three").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(obj));
    }

    @Test
    void shouldApplyRemoveToIndexInArray() {
        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch patch  = JsonPatch.builder().remove("/1").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result == json.arrayBuilder().addAll(["one", "three"]).build();
    }

    @Test
    void shouldApplyRemoveToIndexInChildArrayOfArray() {
        JsonArray child  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonArray array  = json.arrayBuilder().addAll(["A", child]).build();
        JsonPatch patch  = JsonPatch.builder().remove("/1/1").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 2;
        assert result[0] == "A";
        assert result[1].is(JsonArray);
        assert result [1].as(JsonArray) == json.arrayBuilder().addAll(["one", "three"]).build();
    }

    @Test
    void shouldNotApplyRemoveForNonExistentIndexInArray() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = JsonPatch.builder().remove("/3").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldParseRemoveOperation() {
        String jsonOp = $|\{
                         |"op": "remove",
                         |"path": "/one/two"
                         |}
                         ;

        JsonObject          value    = Map:["a"="b"];
        JsonPatch.Operation expected = new JsonPatch.Operation(Remove, JsonPointer.from("/one/two"));
        assertOperation(jsonOp, expected);
    }
}