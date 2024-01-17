import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;
import json.Primitive;

class JsonPatchMoveTest {

    @Test
    void shouldNotMovePrimitive() {
        Doc             target = "Foo";
        JsonPatch       patch  = JsonPatch.builder().move("/one", "/two").build();
        IllegalArgument error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotMovePrimitiveFromRootToRoot() {
        Doc             target = "Foo";
        JsonPatch       patch  = JsonPatch.builder().move("/", "/").build();
        IllegalArgument error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotMoveNonexistentKeyInObject() {
        JsonObject   target = json.objectBuilder().add("foo", 1234).build();
        JsonPatch    patch  = JsonPatch.builder().move("/one", "/two").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldMoveChildObjectInObject() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonObject target = json.objectBuilder().add("one", child).add("three", 33).build();
        JsonPatch  patch  = JsonPatch.builder().move("/one", "/two").build();
        Doc        result = Map<String, Doc>:["two"=child, "three"=33];
    }

    @Test
    void shouldMoveChildObjectInObjectOverwritingExistingDestination() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonObject target = json.objectBuilder().add("one", child).add("two", "foo").add("three", 33).build();
        JsonPatch  patch  = JsonPatch.builder().move("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result == Map<String, Doc>:["two"=child, "three"=33];
    }

    @Test
    void shouldMoveChildArrayInObject() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonObject target = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().move("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 1;
        Doc two = result["two"];
        assert two.is(JsonArray);
        assert two == child;
    }

    @Test
    void shouldMoveChildArrayInObjectOverwritingExistingDestination() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonObject target = json.objectBuilder().add("one", child).add("two", "foo").build();
        JsonPatch  patch  = JsonPatch.builder().move("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 1;
        Doc two = result["two"];
        assert two.is(JsonArray);
        assert two == child;
    }

    @Test
    void shouldMoveChildPrimitiveInObject() {
        JsonObject target = json.objectBuilder().add("one", 1234).build();
        JsonPatch  patch  = JsonPatch.builder().move("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 1;
        Doc two = result["two"];
        assert two.is(Primitive);
        assert two == 1234;
    }

    @Test
    void shouldMoveChildPrimitiveInObjectOverwritingExistingDestination() {
        JsonObject target = json.objectBuilder().add("one", 1234).add("two", 9876).build();
        JsonPatch  patch  = JsonPatch.builder().move("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 1;
        Doc two = result["two"];
        assert two.is(Primitive);
        assert two == 1234;
    }

    @Test
    void shouldNotMoveNonexistentKeyInArray() {
        JsonArray    target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch    patch  = JsonPatch.builder().move("/2", "/3").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotMoveAppendKeyInArray() {
        JsonArray    target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch    patch  = JsonPatch.builder().move("/-", "/3").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotMoveNegativeKeyInArray() {
        JsonArray    target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch    patch  = JsonPatch.builder().move("/-1", "/3").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldMoveChildObjectInArrayPastEndOfArray() {
        JsonArray       target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch       patch  = JsonPatch.builder().move("/1", "/5").build();
        IllegalArgument error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldMoveChildObjectInArrayToEndOfArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, 3, child];
    }

    @Test
    void shouldMoveChildObjectInArrayToNegativeIndexInArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/-1").build();
        Doc        result = patch.apply(target, new JsonPatch.Options(supportNegativeIndices = True));
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, child, 3];
    }

    @Test
    void shouldMoveChildObjectUsingNegativeIndexToNegativeIndexInArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([child, 1, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/-4", "/-1").build();
        Doc        result = patch.apply(target, new JsonPatch.Options(supportNegativeIndices = True));
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, child, 3];
    }

    @Test
    void shouldMoveChildObjectInArrayToAppendPointer() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, 3, child];
    }

    @Test
    void shouldMoveChildObjectInArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll(["one", child, "two", "three"]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/2").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:["one", "two", child, "three"];
    }

    @Test
    void shouldMoveChildArrayToIndexInArray() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/2").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, child, 3];
    }

    @Test
    void shouldMoveChildPrimitiveInArrayToEndOfArray() {
        JsonArray  target = json.arrayBuilder().addAll([1, 2, 3, 4]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 3, 4, 2];
    }

    @Test
    void shouldMoveChildPrimitiveToIndexInArray() {
        JsonArray  target = json.arrayBuilder().addAll([1, 2, 3, 4, 5]).build();
        JsonPatch  patch  = JsonPatch.builder().move("/1", "/3").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 3, 4, 2, 5];
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
}