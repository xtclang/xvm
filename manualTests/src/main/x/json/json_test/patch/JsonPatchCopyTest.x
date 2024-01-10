import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;
import json.Primitive;

class JsonPatchCopyTest {

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

    @Test
    void shouldNotCopyPrimitive() {
        Doc             target = "Foo";
        JsonPatch       patch  = JsonPatch.builder().copy("/one", "/two").build();
        IllegalArgument error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotCopyPrimitiveFromRootToRoot() {
        Doc          target = "Foo";
        JsonPatch    patch  = JsonPatch.builder().copy("/", "/").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotCopyNonexistentKeyInObject() {
        JsonObject   target = json.objectBuilder().add("foo", 1234).build();
        JsonPatch    patch  = JsonPatch.builder().copy("/one", "/two").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldCopyChildObjectInObject() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonObject target = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 2;
        Doc one = result["one"];
        assert one.is(JsonObject);
        assert one == child;
        Doc two = result["two"];
        assert two.is(JsonObject);
        assert two == child;
    }

    @Test
    void shouldCopyChildObjectInObjectOverwritingExistingDestination() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonObject target = json.objectBuilder().add("one", child).add("two", "foo").build();
        JsonPatch  patch  = JsonPatch.builder().copy("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 2;
        Doc one = result["one"];
        assert one.is(JsonObject);
        assert one == child;
        Doc two = result["two"];
        assert two.is(JsonObject);
        assert two == child;
    }

    @Test
    void shouldCopyChildArrayInObject() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonObject target = json.objectBuilder().add("one", child).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 2;
        Doc one = result["one"];
        assert one.is(JsonArray);
        assert one == child;
        Doc two = result["two"];
        assert two.is(JsonArray);
        assert two == child;
    }

    @Test
    void shouldCopyChildArrayInObjectOverwritingExistingDestination() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonObject target = json.objectBuilder().add("one", child).add("two", "foo").build();
        JsonPatch  patch  = JsonPatch.builder().copy("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 2;
        Doc one = result["one"];
        assert one.is(JsonArray);
        assert one == child;
        Doc two = result["two"];
        assert two.is(JsonArray);
        assert two == child;
    }

    @Test
    void shouldCopyChildPrimitiveInObject() {
        JsonObject target = json.objectBuilder().add("one", 1234).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 2;
        Doc one = result["one"];
        assert one.is(Primitive);
        assert one == 1234;
        Doc two = result["two"];
        assert two.is(Primitive);
        assert two == 1234;
    }

    @Test
    void shouldCopyChildPrimitiveInObjectOverwritingExistingDestination() {
        JsonObject target = json.objectBuilder().add("one", 1234).add("two", 9876).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/one", "/two").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonObject);
        assert result.size == 2;
        Doc one = result["one"];
        assert one.is(Primitive);
        assert one == 1234;
        Doc two = result["two"];
        assert two.is(Primitive);
        assert two == 1234;
    }

    @Test
    void shouldNotCopyNonexistentKeyInArray() {
        JsonArray    target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch    patch  = JsonPatch.builder().copy("/2", "/3").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotCopyAppendKeyInArray() {
        JsonArray    target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch    patch  = JsonPatch.builder().copy("/-", "/3").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldNotCopyNegativeKeyInArray() {
        JsonArray    target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch    patch  = JsonPatch.builder().copy("/-1", "/3").build();
        IllegalState error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldCopyChildObjectInArrayPastEndOfArray() {
        JsonArray       target = json.arrayBuilder().addAll([1, 2]).build();
        JsonPatch       patch  = JsonPatch.builder().copy("/1", "/5").build();
        IllegalArgument error  = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldCopyChildObjectInArrayToEndOfArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, child, 2, 3, child];
    }

    @Test
    void shouldCopyChildObjectInArrayToNegativeIndexInArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/-1").build();
        Doc        result = patch.apply(target, new JsonPatch.Options(supportNegativeIndices = True));
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, child, 2, child, 3];
    }

    @Test
    void shouldCopyChildObjectUsingNegativeIndexToNegativeIndexInArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([child, 1, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/-4", "/-1").build();
        Doc        result = patch.apply(target, new JsonPatch.Options(supportNegativeIndices = True));
        assert result.is(JsonArray);
        assert result == Array<Doc>:[child, 1, 2, child, 3];
    }

    @Test
    void shouldCopyChildObjectInArrayToAppendPointer() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, child, 2, 3, child];
    }

    @Test
    void shouldCopyChildObjectInArray() {
        JsonObject child  = json.objectBuilder().add("foo", 1234).build();
        JsonArray  target = json.arrayBuilder().addAll(["one", child, "two", "three"]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/3").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:["one", child, "two", child, "three"];
    }

    @Test
    void shouldCopyChildArrayInArrayToEndOfArray() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonArray  target = json.arrayBuilder().add(child).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/0", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result.size == 2;
        Doc one = result[0];
        assert one.is(JsonArray);
        assert one == child;
        Doc two = result[1];
        assert two.is(JsonArray);
        assert two == child;
    }

    @Test
    void shouldCopyChildArrayToIndexInArray() {
        JsonArray  child  = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonArray  target = json.arrayBuilder().addAll([1, child, 2, 3]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/3").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, child, 2, child, 3];
    }

    @Test
    void shouldCopyChildPrimitiveInArrayToEndOfArray() {
        JsonArray  target = json.arrayBuilder().addAll([1, 2, 3, 4]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/-").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, 3, 4, 2];
    }

    @Test
    void shouldCopyChildPrimitiveToIndexInArray() {
        JsonArray  target = json.arrayBuilder().addAll([1, 2, 3, 4, 5]).build();
        JsonPatch  patch  = JsonPatch.builder().copy("/1", "/3").build();
        Doc        result = patch.apply(target);
        assert result.is(JsonArray);
        assert result == Array<Doc>:[1, 2, 3, 2, 4, 5];
    }
}