import json.Doc;
import json.JsonArray;
import json.JsonObject;
import json.JsonObjectBuilder;

class JsonBuilderTest {

    @Test
    void shouldMergeSimpleObjectIntoObject() {
        JsonObject target = Map:["a"="b"];
        JsonObject source = Map:["c"="d"];
        JsonObject result = new JsonObjectBuilder(target).merge(source).build();
        assert result == Map:["a"="b", "c"="d"];
    }

    @Test
    void shouldMergeComplexObjectIntoObject() {
        JsonObject target = Map:["a"="b"];
        JsonObject source = Map:["c"=Map<String, Doc>:["one"="two"]];
        JsonObject result = new JsonObjectBuilder(target).merge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=Map<String, Doc>:["one"="two"]];
    }

    @Test
    void shouldMergeArrayIntoObject() {
        JsonObject target = Map:["a"="b"];
        JsonArray  source = [1, 2, 3];
        JsonObject result = new JsonObjectBuilder(target).merge(source).build();
        assert result == Map<String, Doc>:["a"="b", "0"=1, "1"=2, "2"=3];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingObject() {
        JsonObject target = Map:["a"="b", "c"=Map<String, Doc>:["one"=1, "two"=2]];
        JsonObject source = Map:["c"=Map<String, Doc>:["two"=22, "three"=3]];
        JsonObject result = new JsonObjectBuilder(target).merge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=Map<String, Doc>:["one"=1, "two"=22, "three"=3]];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingArray() {
        JsonObject target = Map:["a"="b", "c"=Array<Doc>:["one", "two", "three"]];
        JsonObject source = Map:["c"=Map<String, Doc>:["0"="one-updated", "2"="three-updated"]];
        JsonObject result = new JsonObjectBuilder(target).merge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=Array<Doc>:["one-updated", "two", "three-updated"]];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingComplexArray() {
        JsonObject objOrig0     = Map<String, Doc>:["one"=1, "two"=2];
        JsonObject objOrig1     = Map<String, Doc>:["three"=3, "four"=4, "five"=5];
        JsonObject objOrig2     = Map<String, Doc>:["six"=6, "seven"=7];
        JsonObject target       = Map<String, Doc>:["a"="b", "c"=Array<Doc>:[objOrig0, objOrig1, objOrig2]];
        JsonObject objUpdate1   = Map<String, Doc>:["four"=44];
        JsonObject objExpected1 = Map<String, Doc>:["three"=3, "four"=44, "five"=5];
        JsonObject source       = Map<String, Doc>:["c"=Map<String, Doc>:["1"=objUpdate1]];
        JsonObject result       = new JsonObjectBuilder(target).merge(source).build();
        Doc        c            = result["c"];
        assert c.is(Array<Doc>);
        assert c == Array<Doc>:[objOrig0, objExpected1, objOrig2];
        //assert result == Map<String, Doc>:["a"="b", "c"=Array<Doc>:[objOrig0, objExpected1, objOrig2]];
    }

    @Test
    void shouldNotMergeObjectWithObjectWithNonIntKeysIntoObjectWithExistingArray() {
        JsonObject        target  = Map<String, Doc>:["a"="b", "c"=Array<Doc>:["one", "two", "three"]];
        JsonObject        source  = Map<String, Doc>:["c"=Map<String, Doc>:["0"="one-updated", "three"="three-updated"]];
        JsonObjectBuilder builder = new JsonObjectBuilder(target);
        try {
            builder.merge(source);
            assert as "Should have failed to merge JSON Object into JSON Array";
        } catch (IllegalState e) {
            // expected
        }
        // target should be unchanged
        assert target == Map<String, Doc>:["a"="b", "c"=Array<Doc>:["one", "two", "three"]];
    }

    @Test
    void shouldNotMergeObjectWithObjectWithOutOfRangeKeysIntoObjectWithExistingArray() {
        JsonObject        target  = Map<String, Doc>:["a"="b", "c"=Array<Doc>:["one", "two", "three"]];
        JsonObject        source  = Map<String, Doc>:["c"=Map<String, Doc>:["0"="one-updated", "3"="four"]];
        JsonObjectBuilder builder = new JsonObjectBuilder(target);
        try {
            builder.merge(source);
            assert as "Should have failed to merge JSON Object into JSON Array";
        } catch (IllegalState e) {
            // expected
        }
        // target should be unchanged
        assert target == Map<String, Doc>:["a"="b", "c"=Array<Doc>:["one", "two", "three"]];
    }

    @Test
    void shouldMergeObjectWithPrimitiveIntoObjectWithExistingPrimitive() {
        JsonObject target = Map:["a"="b", "c"="d"];
        JsonObject source = Map:["c"="updated"];
        JsonObject result = new JsonObjectBuilder(target).merge(source).build();
        assert result == Map:["a"="b", "c"="updated"];
    }
}