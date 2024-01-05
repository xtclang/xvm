import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchReplaceTest {

    @Test
    void shouldApplyReplaceToPrimitive() {
        Doc       doc    = "foo";
        JsonPatch patch  = JsonPatch.builder().replace("/", "bar").build();
        Doc       result = patch.apply(doc);
        assert result == "bar";
    }

    @Test
    void shouldNotApplyReplaceWithNonEmptyPathToPrimitive() {
        Doc       doc    = "foo";
        JsonPatch patch  = JsonPatch.builder().replace("/one", "bar").build();
        try {
            patch.apply(doc);
            assert as "should have thrown exception";
        } catch (IllegalArgument e) {
            // expected
        }
    }

    @Test
    void shouldApplyReplaceAsAppendToArray() {
        JsonArray       array = json.arrayBuilder().add("one").build();
        JsonPatch       patch = JsonPatch.builder().replace(JsonPointer.Append, "two").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyReplaceToEndOfArray() {
        JsonArray       array = json.arrayBuilder().add("one").build();
        JsonPatch       patch = JsonPatch.builder().replace($"/{array.size}", "two").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyReplaceToIndexInArray() {
        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch patch  = JsonPatch.builder().replace("/1", "TWO").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 3;
        assert result[0] == "one";
        assert result[1] == "TWO";
        assert result[2] == "three";
    }

    @Test
    void shouldApplyReplaceToObjectAtIndexInArray() {
        JsonObject obj      = json.objectBuilder().add("one", 11).add("two", 22).build();
        JsonObject expected = json.objectBuilder().add("one", 1234).add("two", 22).build();
        JsonArray  array    = json.arrayBuilder().addAll(["A", obj, "B"]).build();
        JsonPatch  patch    = JsonPatch.builder().replace("/1/one", 1234).build();
        Doc        result   = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 3;
        assert result[0] == "A";
        assert result[2] == "B";
        assert result[1].is(JsonObject);
        assert result[1].as(JsonObject) == expected;
    }

    @Test
    void shouldApplyReplaceToArrayAtIndexInArray() {
        JsonArray child    = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonArray expected = json.arrayBuilder().addAll(["one", "TWO", "three"]).build();
        JsonArray array    = json.arrayBuilder().addAll(["A", child, "B"]).build();
        JsonPatch patch    = JsonPatch.builder().replace("/1/1", "TWO").build();
        Doc       result   = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 3;
        assert result[0] == "A";
        assert result[2] == "B";
        assert result[1].is(JsonArray);
        assert result[1].as(JsonArray) == expected;
    }

    @Test
    void shouldApplyReplaceWithIndexPastEndOfArray() {
        JsonArray       array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch  = JsonPatch.builder().replace("/100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyReplaceWithNegativeIndex() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = JsonPatch.builder().replace("/-100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyReplaceToNonIntIndex() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = JsonPatch.builder().replace("/-100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyReplaceToObject() {
        JsonObject obj    = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonPatch  patch  = JsonPatch.builder().replace("two", 22).build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 2;
        assert result["one"] == 1;
        assert result["two"] == 22;
    }

    @Test
    void shouldApplyReplaceToChildObjectOfObject() {
        JsonObject child  = json.objectBuilder().add("two", 22).build();
        JsonObject obj    = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().replace("/one/two", 1234).build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 1;
        assert result["one"].is(JsonObject);
        assert result["one"].as(JsonObject) == json.objectBuilder().add("two", 1234).build();
    }

    @Test
    void shouldApplyReplaceToChildArrayOfObject() {
        JsonArray  child  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonObject obj    = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().replace("/one/1", "TWO").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 1;
        assert result["one"].is(JsonArray);
        assert result["one"].as(JsonArray) == json.arrayBuilder().addAll(["one", "TWO", "three"]).build();
    }

    @Test
    void shouldParseReplaceOperation() {
        String jsonOp = $|\{
                         |"op": "replace",
                         |"path": "/one/two",
                         |"value": \{
                         |    "a": "b"
                         |    }
                         |}
                         ;

        JsonObject          value    = Map:["a"="b"];
        JsonPatch.Operation expected = new JsonPatch.Operation(Replace, JsonPointer.from("/one/two"), value);
        assertOperation(jsonOp, expected);
    }

}