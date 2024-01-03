import ecstasy.io.CharArrayReader;
import ecstasy.io.Reader;
import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;
import json.ObjectInputStream;
import json.Schema;

class JsonPatchTest {

    @Test
    void shouldCreateEmptyPatch() {
        JsonPatch patch = json.patchBuilder().build();
        assert patch.empty == True;
    }

    @Test
    void shouldCreatePatchWithSingleAddOp() {
        JsonPatch patch = json.patchBuilder()
            .add("/one/two", "value-two")
            .build();
        assert patch.size == 1;
        assert patch[0] == new JsonPatch.Operation(Action.Add, JsonPointer.from("/one/two"), "value-two");
    }

    @Test
    void shouldCreatePatchWithMultipleAddOps() {
        JsonPatch patch = json.patchBuilder()
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
        JsonPatch patch  = json.patchBuilder().add("/", "bar").build();
        Doc       result = patch.apply(doc);
        assert result == "bar";
    }

    @Test
    void shouldNotApplyAddWithNonEmptyPathToPrimitive() {
        Doc             doc    = "foo";
        JsonPatch       patch  = json.patchBuilder().add("/one", "bar").build();
        IllegalArgument ex     = assertThrows(() -> patch.apply(doc));
    }

    @Test
    void shouldApplyAddAsAppendToArray() {
        JsonArray array  = json.arrayBuilder().add("one").build();
        JsonPatch patch  = json.patchBuilder().add(JsonPointer.Append, "two").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 2;
        assert result[0] == "one";
        assert result[1] == "two";
    }

    @Test
    void shouldApplyAddToEndOfArray() {
        JsonArray array  = json.arrayBuilder().add("one").build();
        JsonPatch patch  = json.patchBuilder().add($"/{array.size}", "two").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 2;
        assert result[0] == "one";
        assert result[1] == "two";
    }

    @Test
    void shouldApplyAddToIndexInArray() {
        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch patch  = json.patchBuilder().add("/1", "TWO").build();
        Doc       result = patch.apply(array);
        assert result.is(JsonArray);
        assert result.size == 3;
        assert result[0] == "one";
        assert result[1] == "TWO";
        assert result[2] == "three";
    }

    @Test
    void shouldApplyAddToObjectAtIndexInArray() {
        JsonObject obj      = json.objectBuilder().add("one", 11).add("two", 22).build();
        JsonObject expected = json.objectBuilder().add("one", 1234).add("two", 22).build();
        JsonArray  array    = json.arrayBuilder().addAll(["A", obj, "B"]).build();
        JsonPatch  patch    = json.patchBuilder().add("/1/one", 1234).build();
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
        JsonArray expected = json.arrayBuilder().addAll(["one", "TWO", "three"]).build();
        JsonArray array    = json.arrayBuilder().addAll(["A", child, "B"]).build();
        JsonPatch patch    = json.patchBuilder().add("/1/1", "TWO").build();
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
        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch patch  = json.patchBuilder().add("/100", "bad").build();
        patch.apply(array);
    }

    @Test
    void shouldApplyAddWithNegativeIndex() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = json.patchBuilder().add("/-100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyAddToNonIntIndex() {
        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
        JsonPatch       patch = json.patchBuilder().add("/-100", "bad").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
    }

    @Test
    void shouldApplyAddToObject() {
        JsonObject obj    = json.objectBuilder().add("one", 11).build();
        JsonPatch  patch  = json.patchBuilder().add("two", 22).build();
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
        JsonPatch  patch  = json.patchBuilder().add("/one/two", 1234).build();
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
        JsonPatch  patch  = json.patchBuilder().add("/one/1", "TWO").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result.size == 1;
        assert result["one"].is(JsonArray);
        assert result["one"].as(JsonArray) == json.arrayBuilder().addAll(["one", "TWO", "three"]).build();
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

    @Test
    void shouldCreatePatchWithSingleRemoveOp() {
        JsonPatch patch = json.patchBuilder()
            .remove("/one/two")
            .build();
        assert patch.size == 1;
        assert patch[0] == new JsonPatch.Operation(Action.Remove, JsonPointer.from("/one/two"));
    }

    @Test
    void shouldCreatePatchWithMultipleRemoveOps() {
        JsonPatch patch = json.patchBuilder()
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
        JsonPatch patch  = json.patchBuilder().remove("/").build();
        Doc       result = patch.apply(doc);
        assert result == Null;
    }

    @Test
    void shouldNotApplyRemoveWithNonEmptyPathToPrimitive() {
        Doc             doc    = "foo";
        JsonPatch       patch  = json.patchBuilder().remove("/one").build();
        IllegalArgument ex     = assertThrows(() -> patch.apply(doc));
    }

    @Test
    void shouldApplyRemoveToKeyInObject() {
        JsonObject obj    = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonPatch  patch  = json.patchBuilder().remove("/one").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result == json.objectBuilder().add("two", 2).build();
    }

    @Test
    void shouldApplyRemoveToKeyInChildObjectInObject() {
        JsonObject child  = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonObject obj    = json.objectBuilder().add("A", child).build();
        JsonPatch  patch  = json.patchBuilder().remove("/A/one").build();
        Doc        result = patch.apply(obj);
        assert result.is(JsonObject);
        assert result == json.objectBuilder().add("A", json.objectBuilder().add("two", 2)).build();
    }

    @Test
    void shouldNotApplyRemoveToNonExistentKeyInObject() {
        JsonObject      obj   = json.objectBuilder().add("one", 1).add("two", 2).build();
        JsonPatch       patch = json.patchBuilder().remove("/three").build();
        IllegalArgument ex    = assertThrows(() -> patch.apply(obj));
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

//    @Test
//    void shouldApplyReplaceToPrimitive() {
//        Doc       doc    = "foo";
//        JsonPatch patch  = json.patchBuilder().replace("/", "bar").build();
//        Doc       result = patch.apply(doc);
//        assert result == "bar";
//    }
//
//    @Test
//    void shouldNotApplyReplaceWithNonEmptyPathToPrimitive() {
//        Doc       doc    = "foo";
//        JsonPatch patch  = json.patchBuilder().replace("/one", "bar").build();
//        try {
//            patch.apply(doc);
//            assert as "should have thrown exception";
//        } catch (IllegalArgument e) {
//            // expected
//        }
//    }
//
//    @Test
//    void shouldApplyReplaceAsAppendToArray() {
//        JsonArray array  = json.arrayBuilder().add("one").build();
//        JsonPatch patch  = json.patchBuilder().replace(JsonPointer.Append, "two").build();
//        Doc       result = patch.apply(array);
//        assert result.is(JsonArray);
//        assert result.size == 2;
//        assert result[0] == "one";
//        assert result[1] == "two";
//    }
//
//    @Test
//    void shouldApplyReplaceToEndOfArray() {
//        JsonArray array  = json.arrayBuilder().add("one").build();
//        JsonPatch patch  = json.patchBuilder().replace($"/{array.size}", "two").build();
//        Doc       result = patch.apply(array);
//        assert result.is(JsonArray);
//        assert result.size == 2;
//        assert result[0] == "one";
//        assert result[1] == "two";
//    }
//
//    @Test
//    void shouldApplyReplaceToIndexInArray() {
//        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
//        JsonPatch patch  = json.patchBuilder().replace("/1", "TWO").build();
//        Doc       result = patch.apply(array);
//        assert result.is(JsonArray);
//        assert result.size == 3;
//        assert result[0] == "one";
//        assert result[1] == "TWO";
//        assert result[2] == "three";
//    }
//
//    @Test
//    void shouldApplyReplaceToObjectAtIndexInArray() {
//        JsonObject obj      = json.objectBuilder().add("one", 11).add("two", 22).build();
//        JsonObject expected = json.objectBuilder().add("one", 1234).add("two", 22).build();
//        JsonArray  array    = json.arrayBuilder().addAll(["A", obj, "B"]).build();
//        JsonPatch  patch    = json.patchBuilder().replace("/1/one", 1234).build();
//        Doc        result   = patch.apply(array);
//        assert result.is(JsonArray);
//        assert result.size == 3;
//        assert result[0] == "A";
//        assert result[2] == "B";
//        assert result[1].is(JsonObject);
//        assert result[1].as(JsonObject) == expected;
//    }
//
//    @Test
//    void shouldApplyReplaceToArrayAtIndexInArray() {
//        JsonArray child    = json.arrayBuilder().addAll(["one", "two", "three"]).build();
//        JsonArray expected = json.arrayBuilder().addAll(["one", "TWO", "three"]).build();
//        JsonArray array    = json.arrayBuilder().addAll(["A", child, "B"]).build();
//        JsonPatch patch    = json.patchBuilder().replace("/1/1", "TWO").build();
//        Doc       result   = patch.apply(array);
//        assert result.is(JsonArray);
//        assert result.size == 3;
//        assert result[0] == "A";
//        assert result[2] == "B";
//        assert result[1].is(JsonArray);
//        assert result[1].as(JsonArray) == expected;
//    }
//
//    @Test
//    void shouldApplyReplaceWithIndexPastEndOfArray() {
//        JsonArray array  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
//        JsonPatch patch  = json.patchBuilder().replace("/100", "bad").build();
//        patch.apply(array);
//    }
//
//    @Test
//    void shouldApplyReplaceWithNegativeIndex() {
//        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
//        JsonPatch       patch = json.patchBuilder().replace("/-100", "bad").build();
//        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
//    }
//
//    @Test
//    void shouldApplyReplaceToNonIntIndex() {
//        JsonArray       array = json.arrayBuilder().addAll(["one", "two", "three"]).build();
//        JsonPatch       patch = json.patchBuilder().replace("/-100", "bad").build();
//        IllegalArgument ex    = assertThrows(() -> patch.apply(array));
//    }
//
//    @Test
//    void shouldApplyReplaceToObject() {
//        JsonObject obj    = json.objectBuilder().add("one", 11).build();
//        JsonPatch  patch  = json.patchBuilder().replace("two", 22).build();
//        Doc        result = patch.apply(obj);
//        assert result.is(JsonObject);
//        assert result.size == 2;
//        assert result["one"] == 11;
//        assert result["two"] == 22;
//    }
//
//    @Test
//    void shouldApplyReplaceToChildObjectOfObject() {
//        JsonObject child  = json.objectBuilder().add("two", 22).build();
//        JsonObject obj    = json.objectBuilder().add("one", child).build();
//        JsonPatch  patch  = json.patchBuilder().replace("/one/two", 1234).build();
//        Doc        result = patch.apply(obj);
//        assert result.is(JsonObject);
//        assert result.size == 1;
//        assert result["one"].is(JsonObject);
//        assert result["one"].as(JsonObject) == json.objectBuilder().add("two", 1234).build();
//    }
//
//    @Test
//    void shouldApplyReplaceToChildArrayOfObject() {
//        JsonArray  child  = json.arrayBuilder().addAll(["one", "two", "three"]).build();
//        JsonObject obj    = json.objectBuilder().add("one", child).build();
//        JsonPatch  patch  = json.patchBuilder().add("/one/1", "TWO").build();
//        Doc        result = patch.apply(obj);
//        assert result.is(JsonObject);
//        assert result.size == 1;
//        assert result["one"].is(JsonArray);
//        assert result["one"].as(JsonArray) == json.arrayBuilder().addAll(["one", "TWO", "three"]).build();
//    }

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

    @Test
    void shouldParseCopyOperation() {
        String jsonOp = $|\{
                         |"op": "copy",
                         |"path": "/one/two",
                         |"from": "/three/four"
                         |}
                         ;

        JsonPatch.Operation expected = new JsonPatch.Operation(Copy, JsonPointer.from("/one/two"),
                Null, JsonPointer.from("/three/four"));

        assertOperation(jsonOp, expected);
    }

    void assertOperation(String jsonOp, JsonPatch.Operation expected) {
        Schema              schema = Schema.DEFAULT;
        Reader              reader = new CharArrayReader(jsonOp);
        ObjectInputStream   o_in   = new ObjectInputStream(schema, reader);
        JsonPatch.Operation result = o_in.read();
        assert result == expected;
    }

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