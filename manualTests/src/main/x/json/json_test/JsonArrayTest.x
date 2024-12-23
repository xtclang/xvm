import json.Doc;
import json.JsonArray;

class JsonArrayTest {

    @Test
    void shouldBeEmpty() {
        JsonArray array = json.arrayBuilder().build();
        assert array.empty == True;
    }

    @Test
    void shouldCreateSingleElementArray() {
        JsonArray array = json.arrayBuilder()
            .add("foo")
            .build();
        assert array == ["foo"];
    }

    @Test
    void shouldCreateMultiElementArray() {
        JsonArray array = json.arrayBuilder()
            .add("one")
            .add("two")
            .add("three")
            .build();
        assert array == ["one", "two", "three"];
    }

    @Test
    void shouldCreateMultiElementArrayUsingAddAll() {
        JsonArray array = json.arrayBuilder()
            .addAll(["one", "two", "three"])
            .build();
        assert array == ["one", "two", "three"];
    }

    @Test
    void shouldCreateMComplexArray() {
        JsonArray array = json.arrayBuilder()
            .add(json.objectBuilder().add("one", 1).add("two", 2).add("three", 3).add("four", 4))
            .add(json.objectBuilder().add("five", 5).add("six", 6))
            .build();
        assert array == Doc[]:[
                ["one"=1, "two"=2, "three"=3, "four"=4],
                ["five"=5, "six"=6]
        assert array == [
                ["one"=1, "two"=2, "three"=3, "four"=4],
                ["five"=5, "six"=6]
                ];
    }

    @Test
    void shouldSetElementInArray() {
        JsonArray array = json.arrayBuilder()
            .add("one")
            .add("two")
            .add("three")
            .set(1, "TWO")
            .build();
        assert array == ["one", "TWO", "three"];
    }
}