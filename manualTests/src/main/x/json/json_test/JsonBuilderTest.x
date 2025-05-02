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
        JsonObject object = hideCompileType(["one"=11, "two"=22, "three"=33]);
        JsonObject result = JsonBuilder.deepCopy(object);
        assertDeepCopy(object, result);
    }

    @Test
    void shouldCopySimpleArray() {
        JsonArray array  = hideCompileType([1, 2, 3]);
        JsonArray result = JsonBuilder.deepCopy(array);
        assertDeepCopy(array, result);
    }

    @Test
    void shouldCopyComplexObject() {
        JsonObject child  = ["one"=11, "two"=22, "three"=33];
        JsonArray  array  = [1, 2, 3];
        JsonObject object = ["one"=child, "two"=array, "three"=33];
        JsonObject result = JsonBuilder.deepCopy(object);
        assertDeepCopy(object, result);
    }

    @Test
    void shouldCopyComplexArray() {
        JsonArray  child  = [1, 2, 3];
        JsonObject object = ["one"=11, "two"=22, "three"=33];
        JsonArray  array  = [child, object];
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
                assert as $"source and copy are different types source={&source.type} copy={&copy.type}";
        }
    }

    // hide "immutable" compile-time type inference for the specified object
    static JsonObject hideCompileType(JsonObject o) = o;
    static JsonArray  hideCompileType(JsonArray  a) = a;

    void assertDeepCopyObject(JsonObject source, JsonObject copy) {
        assert &copy != &source as "source and copy should be different object references";
        assert copy.inPlace == True as "copy should be mutable";
        for ((String key, Doc value) : source) {
            assert Doc copyValue := copy.get(key);
            assertDeepCopy(value, copyValue);
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
        JsonObject target = ["a"="b"];
        JsonObject source = ["c"="d"];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == ["a"="b", "c"="d"];
    }

    @Test
    void shouldMergeComplexObjectIntoObject() {
        JsonObject target = ["a"="b"];
        JsonObject source = ["c"=["one"="two"]];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=["one"="two"]];
    }

    @Test
    void shouldMergeArrayIntoObject() {
        JsonObject target = ["a"="b"];
        JsonArray  source = [1, 2, 3];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "0"=1, "1"=2, "2"=3];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingObject() {
        JsonObject target = ["a"="b", "c"=["one"=1, "two"=2]];
        JsonObject source = ["c"=["two"=22, "three"=3]];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=["one"=1, "two"=22, "three"=3]];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingArray() {
        JsonObject target = ["a"="b", "c"=["one", "two", "three"]];
        JsonObject source = ["c"=["0"="one-updated", "2"="three-updated"]];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map<String, Doc>:["a"="b", "c"=["one-updated", "two", "three-updated"]];
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingComplexArray() {
        JsonObject objOrig0     = ["one"=1, "two"=2];
        JsonObject objOrig1     = ["three"=3, "four"=4, "five"=5];
        JsonObject objOrig2     = ["six"=6, "seven"=7];
        JsonObject target       = ["a"="b", "c"=[objOrig0, objOrig1, objOrig2]];
        JsonObject objUpdate1   = ["four"=44];
        JsonObject objExpected1 = ["three"=3, "four"=44, "five"=5];
        JsonObject source       = ["c"=["1"=objUpdate1]];
        JsonObject result       = new JsonObjectBuilder(target).deepMerge(source).build();
        Doc        a            = result["a"];
        Doc        c            = result["c"];
        assert a.is(String);
        assert a == "b";
        assert c.is(Array<Doc>);
        assert c == Doc[]:[objOrig0, objExpected1, objOrig2];
    }

    @Test
    void shouldNotMergeObjectWithObjectWithNonIntKeysIntoObjectWithExistingArray() {
        JsonObject        target  = ["a"="b", "c"=["one", "two", "three"]];
        JsonObject        source  = ["c"=["0"="one-updated", "three"="three-updated"]];
        JsonObjectBuilder builder = new JsonObjectBuilder(target);
        try {
            builder.deepMerge(source);
            assert as "Should have failed to merge JSON Object into JSON Array";
        } catch (IllegalState e) {
            // expected
        }
        // target should be unchanged
        assert target == Map<String, Doc>:["a"="b", "c"=["one", "two", "three"]];
    }

    @Test
    void shouldNotMergeObjectWithObjectWithOutOfRangeKeysIntoObjectWithExistingArray() {
        JsonObject        target  = ["a"="b", "c"=["one", "two", "three"]];
        JsonObject        source  = ["c"=["0"="one-updated", "3"="four"]];
        JsonObjectBuilder builder = new JsonObjectBuilder(target);
        try {
            builder.deepMerge(source);
            assert as "Should have failed to merge JSON Object into JSON Array";
        } catch (IllegalState e) {
            // expected
        }
        // target should be unchanged
        assert target == Map<String, Doc>:["a"="b", "c"=["one", "two", "three"]];
    }

    // ---- Merge into primitive fields ------------------------------------------------------------

    @Test
    void shouldMergePrimitiveIntoObjectWithExistingPrimitiveField() {
        JsonObject target = ["a"="b", "c"="d"];
        JsonObject source = ["c"="updated"];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == ["a"="b", "c"="updated"];
    }

    @Test
    void shouldMergePrimitiveIntoObjectWithoutExistingPrimitiveField() {
        JsonObject target = Map:["a"="b"];
        JsonObject source = Map:["c"="d"];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == Map:["a"="b", "c"="d"];
    }

    @Test
    void shouldMergeObjectIntoObjectWithExistingPrimitiveField() {
        JsonObject child  = Map:["child-one"="value-one", "child-two"="value-two"];
        JsonObject target = Map:["a"="b", "c"="d"];
        JsonObject source = Map:["c"=child];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result["a"] == "b";
        Doc c = result["c"];
        assert c.is(JsonObject);
        assert c == child;
    }

    @Test
    void shouldMergeArrayIntoObjectWithExistingPrimitiveField() {
        JsonArray  child  = Array<Doc>:[1, 2, 3];
        JsonObject target = Map:["a"="b", "c"="d"];
        JsonObject source = Map:["c"=child];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result["a"] == "b";
        Doc c = result["c"];
        assert c.is(JsonArray);
        assert c == child;
    }
}