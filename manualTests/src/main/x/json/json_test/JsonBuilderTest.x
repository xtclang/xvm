import json.Doc;
import json.JsonArray;
import json.JsonBuilder;
import json.JsonObject;
import json.JsonObjectBuilder;
import json.Primitive;

class JsonBuilderTest {

    @Test
    void shouldCopyPrimitive() {
        Primitive value  = 1234;
        Primitive result = JsonBuilder.deepCopy(value);
        assert result == value;
    }

    @Test
    void shouldCopyEmptyObject() {
        JsonObject object = json.newObject();
        JsonObject result = JsonBuilder.deepCopy(object);
        assertDeepCopy(object, result);
    }

    @Test
    void shouldCopySimpleObject() {
        JsonObject object = Map<String, Doc>:["one"=11, "two"=22, "three"=33];
        JsonObject result = JsonBuilder.deepCopy(object);
        assertDeepCopy(object, result);
    }

    @Test
    void shouldCopySimpleArray() {
        JsonArray array  = Array<Doc>:[1, 2, 3];
        JsonArray result = JsonBuilder.deepCopy(array);
        assertDeepCopy(array, result);
    }

    @Test
    void shouldCopyComplexObject() {
        JsonObject child  = Map<String, Doc>:["one"=11, "two"=22, "three"=33];
        JsonArray  array  = Array<Doc>:[1, 2, 3];
        JsonObject object = Map<String, Doc>:["one"=child, "two"=array, "three"=33];
        JsonObject result = JsonBuilder.deepCopy(object);
        assertDeepCopy(object, result);
    }

    @Test
    void shouldCopyComplexArray() {
        JsonArray  child  = Array<Doc>:[1, 2, 3];
        JsonObject object = Map<String, Doc>:["one"=11, "two"=22, "three"=33];
        JsonArray  array  = Array<Doc>:[child, object];
        JsonArray  result = JsonBuilder.deepCopy(array);
        assertDeepCopy(array, result);
    }

    void assertDeepCopy(Doc source, Doc copy) {
        switch (source.is(_), copy.is(_)) {
            case (JsonObject, JsonObject):
                assertDeepCopyObject(source, copy);
                break;
            case (JsonArray, JsonArray):
                assertDeepCopyArray(source, copy);
                break;
            case (Primitive, Primitive):
                assert copy == source;
                break;
            default:
                assert as $"source and copy are different types source={&source.actualType} copy={&copy.actualType}";
        }
    }

    void assertDeepCopyObject(JsonObject source, JsonObject copy) {
        assert &copy != &source as "source and copy should be different object references";
        assert copy.inPlace == True as "copy should be mutable";
        for (Map<String, Doc>.Entry entry : source.entries) {
            assert Doc copyValue := copy.get(entry.key);
            assertDeepCopy(entry.value, copyValue);
        }
    }

    void assertDeepCopyArray(JsonArray source, JsonArray copy) {
        assert &copy != &source as "source and copy should be different object references";
        assert copy.inPlace == True as "copy should be mutable";
        assert copy.size == source.size;
        for (Int i : 0 ..< source.size) {
            assertDeepCopy(source[i], copy[i]);
        }
    }


    @Test
    void shouldMergeSimpleObjectIntoObject() {
        JsonObject target = Map:["a"="b"];
        JsonObject source = Map:["c"="d"];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map:["a"="b", "c"="d"];
    }

    @Test
    void shouldMergeComplexObjectIntoObject() {
        JsonObject target = Map:["a"="b"];
        JsonObject source = Map:["c"=Map<String, Doc>:["one"="two"]];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=Map<String, Doc>:["one"="two"]];
    }

    @Test
    void shouldMergeArrayIntoObject() {
        JsonObject target = Map:["a"="b"];
        JsonArray  source = [1, 2, 3];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "0"=1, "1"=2, "2"=3];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingObject() {
        JsonObject target = Map:["a"="b", "c"=Map<String, Doc>:["one"=1, "two"=2]];
        JsonObject source = Map:["c"=Map<String, Doc>:["two"=22, "three"=3]];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=Map<String, Doc>:["one"=1, "two"=22, "three"=3]];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingArray() {
        JsonObject target = Map:["a"="b", "c"=Array<Doc>:["one", "two", "three"]];
        JsonObject source = Map:["c"=Map<String, Doc>:["0"="one-updated", "2"="three-updated"]];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
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
        JsonObject result       = new JsonObjectBuilder(target).deepMerge(source).build();
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
            builder.deepMerge(source);
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
            builder.deepMerge(source);
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
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map:["a"="b", "c"="updated"];
    }
}