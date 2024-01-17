import json.Doc;
import json.JsonArray;
import json.JsonObject;

class JsonObjectBuilderTest {

    @Test
    void shouldBuildEmptyJsonObject() {
        JsonObject o = json.objectBuilder().build();
        assert o.empty == True;
    }

    @Test
    void shouldBeDoc() {
        Doc doc = json.objectBuilder().build();
        assert doc.is(JsonObject);
    }

    @Test
    void shouldBuildSingleEntryJsonObject() {
        JsonObject o = json.objectBuilder()
            .add("foo", "bar")
            .build();

        assert o.size == 1;
        assert o["foo"] == "bar";
    }

    @Test
    void shouldBuildMultiEntryJsonObject() {
        JsonObject o = json.objectBuilder()
            .add("one", 1)
            .add("two", 2)
            .add("three", 3)
            .build();

        assert o.size == 3;
        assert o["one"] == 1;
        assert o["two"] == 2;
        assert o["three"] == 3;
    }

    @Test
    void shouldAddBuilder() {
        JsonObject o = json.objectBuilder()
            .add("one", json.objectBuilder().add("childOne", 100))
            .add("two", json.arrayBuilder().addAll(["childTwo", "childThree"]))
            .build();

        assert o.size == 2;
        assert Doc one := o.get("one");
        assert one.is(JsonObject);
        assert one.size == 1;
        assert one["childOne"] == 100;
        assert Doc two := o.get("two");
        assert two.is(JsonArray);
        assert two.size == 2;
        assert two[0] == "childTwo";
        assert two[1] == "childThree";
    }
}