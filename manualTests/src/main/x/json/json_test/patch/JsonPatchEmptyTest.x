import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonPatch;
import json.JsonPatch.Action;
import json.JsonPatch.Operation;
import json.JsonPointer;

class JsonPatchEmptyTest {

    @Test
    void shouldCreateEmptyPatch() {
        JsonPatch patch = JsonPatch.builder().build();
        assert patch.empty == True;
    }

    @Test
    void shouldPatchObjectWithEmptyPatch() {
        Doc       target = json.objectBuilder().add("foo", "bar").build();
        JsonPatch patch  = JsonPatch.builder().build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldPatchArrayWithEmptyPatch() {
        Doc       target = json.arrayBuilder().addAll(["foo", "bar"]).build();
        JsonPatch patch  = JsonPatch.builder().build();
        Doc       result = patch.apply(target);
        assert result == target;
    }

    @Test
    void shouldPatchPrimitiveWithEmptyPatch() {
        Doc       target = "foo";
        JsonPatch patch  = JsonPatch.builder().build();
        Doc       result = patch.apply(target);
        assert result == target;
    }
}