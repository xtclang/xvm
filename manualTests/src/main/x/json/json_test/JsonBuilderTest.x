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
        JsonObject object = json.newObject(["one"=11, "two"=22, "three"=33]);
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
        JsonObject child  = json.newObject(["one"=11, "two"=22, "three"=33]);
        JsonArray  array  = [1, 2, 3];
        JsonObject object = json.newObject(["one"=child, "two"=array, "three"=33]);
        JsonObject result = JsonBuilder.deepCopy(object);
        assertDeepCopy(object, result);
    }

    @Test
    void shouldCopyComplexArray() {
        JsonArray  child  = [1, 2, 3];
        JsonObject object = json.newObject(["one"=11, "two"=22, "three"=33]);
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
                assert as $"source and copy are different types source={&source.actualType} copy={&copy.actualType}";
        }
    }

    // hide "immutable" compile-time type inference for the specified object
    static JsonObject hideCompileType(JsonObject o) = o;
    static JsonArray  hideCompileType(JsonArray  a) = a;

    void assertDeepCopyObject(JsonObject source, JsonObject copy) {
        assert &copy != &source as "source and copy should be different object references";
        assert copy.inPlace == True as "copy should be mutable";
        for (Map.Entry<String, Doc> entry : source.entries) {
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
        JsonObject target = json.newObject(["a"="b"]);
        JsonObject source = json.newObject(["c"="d"]);
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == json.newObject(["a"="b", "c"="d"]);
    }

    @Test
    void shouldMergeComplexObjectIntoObject() {
        JsonObject target = json.newObject(["a"="b"]);
        JsonObject source = json.newObject(["c"=["one"="two"]]);
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == json.newObject(["a"="b", "c"=["one"="two"]]);
    }

    @Test
    void shouldMergeArrayIntoObject() {
        JsonObject target = json.newObject(["a"="b"]);
        JsonArray  source = [1, 2, 3];
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == json.newObject(["a"="b", "0"=1, "1"=2, "2"=3]);
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingObject() {
        JsonObject target = json.newObject(["a"="b", "c"=["one"=1, "two"=2]]);
        JsonObject source = json.newObject(["c"=["two"=22, "three"=3]]);
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == json.newObject(["a"="b", "c"=["one"=1, "two"=22, "three"=3]]);
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingArray() {
        JsonObject target = json.newObject(["a"="b", "c"=["one", "two", "three"]]);
        JsonObject source = json.newObject(["c"=["0"="one-updated", "2"="three-updated"]]);
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == json.newObject(["a"="b", "c"=["one-updated", "two", "three-updated"]]);
    }

    @Test
    void shouldMergeObjectWithObjectIntoObjectWithExistingComplexArray() {
        JsonObject objOrig0     = json.newObject(["one"=1, "two"=2]);
        JsonObject objOrig1     = json.newObject(["three"=3, "four"=4, "five"=5]);
        JsonObject objOrig2     = json.newObject(["six"=6, "seven"=7]);
        JsonObject target       = json.newObject(["a"="b", "c"=[objOrig0, objOrig1, objOrig2]]);
        JsonObject objUpdate1   = json.newObject(["four"=44]);
        JsonObject objExpected1 = json.newObject(["three"=3, "four"=44, "five"=5]);
        JsonObject source       = json.newObject(["c"=["1"=objUpdate1]]);
        JsonObject result       = new JsonObjectBuilder(target).deepMerge(source).build();
        Doc        c            = result["c"];
        assert c.is(Doc[]);
        assert c == Doc[]:[objOrig0, objExpected1, objOrig2];
        //assert result == json.newObject(["a"="b", "c"=Doc[]:[objOrig0, objExpected1, objOrig2]]);
    }

    @Test
    void shouldNotMergeObjectWithObjectWithNonIntKeysIntoObjectWithExistingArray() {
        JsonObject        target  = json.newObject(["a"="b", "c"=["one", "two", "three"]]);
        JsonObject        source  = json.newObject(["c"=["0"="one-updated", "three"="three-updated"]]);
        JsonObjectBuilder builder = new JsonObjectBuilder(target);
        try {
            builder.deepMerge(source);
            assert as "Should have failed to merge JSON Object into JSON Array";
        } catch (IllegalState e) {
            // expected
        }
        // target should be unchanged
        assert target == json.newObject(["a"="b", "c"=["one", "two", "three"]]);
    }

    @Test
    void shouldNotMergeObjectWithObjectWithOutOfRangeKeysIntoObjectWithExistingArray() {
        JsonObject        target  = json.newObject(["a"="b", "c"=["one", "two", "three"]]);
        JsonObject        source  = json.newObject(["c"=["0"="one-updated", "3"="four"]]);
        JsonObjectBuilder builder = new JsonObjectBuilder(target);
        try {
            builder.deepMerge(source);
            assert as "Should have failed to merge JSON Object into JSON Array";
        } catch (IllegalState e) {
            // expected
        }
        // target should be unchanged
        assert target == json.newObject(["a"="b", "c"=["one", "two", "three"]]);
    }

    @Test
    void shouldMergeObjectWithPrimitiveIntoObjectWithExistingPrimitive() {
        JsonObject target = json.newObject(["a"="b", "c"="d"]);
        JsonObject source = json.newObject(["c"="updated"]);
        JsonObject result = new JsonObjectBuilder(target).deepMerge(source).build();
        assert result == json.newObject(["a"="b", "c"="updated"]);
    }
}