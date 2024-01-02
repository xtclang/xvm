import json.JsonMergePatch;
import json.JsonObject;

class JsonMergePatchTest {

    @Test
    void shouldBeEmpty() {
        JsonObject     o     = json.newObject();
        JsonMergePatch patch = new JsonMergePatch(o);
        assert patch.empty;
    }

    
}