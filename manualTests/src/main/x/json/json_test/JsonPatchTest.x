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
    void shouldCreatePatchWithSingleAddOps() {
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
        Doc       doc    = "foo";
        JsonPatch patch  = json.patchBuilder().add("/one", "bar").build();
        try {
            patch.apply(doc);
            assert as "should have thrown exception";
        } catch (IllegalState e) {
            // expected
        }
    }

    @Test
    void shouldApplyAddToEndOfArray() {
        JsonArray array  = json.arrayBuilder().add("one").build();
        JsonPatch patch  = json.patchBuilder().add(JsonPointer.Append, "two").build();
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