import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchTestTest {

    @Test
    void shouldCreatePatchWithSingleTestOp() {
        JsonPatch patch = JsonPatch.builder()
            .test("/one/two", "value-two")
            .build();
        assert patch.size == 1;
        assert patch[0] == new JsonPatch.Operation(Action.Test, JsonPointer.from("/one/two"), "value-two");
    }

    @Test
    void shouldSucceedTestingPrimitiveAtRoot() {
        Doc       target = "foo";
        JsonPatch patch = JsonPatch.builder().test("/", "foo").build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldFailTestingPrimitiveAtRoot() {
        Doc          target = "foo";
        JsonPatch    patch = JsonPatch.builder().test("/", "bar").build();
        IllegalState error = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldSucceedTestingNullPrimitiveAtRoot() {
        Doc       target = Null;
        JsonPatch patch = JsonPatch.builder().test("/", Null).build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldFailTestingNullPrimitiveAtRoot() {
        Doc          target = Null;
        JsonPatch    patch = JsonPatch.builder().test("/", "bar").build();
        IllegalState error = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldSucceedTestingObject() {
        Doc       target = Map<String, Doc>:["one"=1, "two"=2, "three"=3];
        JsonPatch patch = JsonPatch.builder().test("/two", 2).build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldFailTestingObject() {
        Doc          target = Map<String, Doc>:["one"=1, "two"=2, "three"=3];
        JsonPatch    patch = JsonPatch.builder().test("/two", 200).build();
        IllegalState error = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldSucceedTestingChildObject() {
        Doc       child  = Map<String, Doc>:["one"=1, "two"=2, "three"=3];
        Doc       target = Map<String, Doc>:["foo"=child];
        JsonPatch patch = JsonPatch.builder().test("/foo/two", 2).build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldFailTestingChildObject() {
        Doc       child  = Map<String, Doc>:["one"=1, "two"=2, "three"=3];
        Doc       target = Map<String, Doc>:["foo"=child];
        JsonPatch patch = JsonPatch.builder().test("/foo/two", 200).build();
        IllegalState error = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldFailTestingMissingChildObject() {
        Doc       child  = Map<String, Doc>:["one"=1, "two"=2, "three"=3];
        Doc       target = Map<String, Doc>:["foo"=child];
        JsonPatch patch = JsonPatch.builder().test("/foo/four", 4).build();
        IllegalState error = assertThrows(() -> patch.apply(target));
    }

    @Test
    void shouldSucceedTestingNullChildObject() {
        Doc       child  = Map<String, Doc>:["one"=1, "two"=Null, "three"=3];
        Doc       target = Map<String, Doc>:["foo"=child];
        JsonPatch patch = JsonPatch.builder().test("/foo/two", Null).build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldSucceedTestingNullMissingChildObject() {
        Doc       child  = Map<String, Doc>:["one"=1, "two"=2, "three"=3];
        Doc       target = Map<String, Doc>:["foo"=child];
        JsonPatch patch = JsonPatch.builder().test("/foo/four", Null).build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

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