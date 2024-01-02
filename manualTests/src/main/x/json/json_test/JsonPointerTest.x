import json.JsonPointer;

class JsonPointerTest {

    @Test
    void shouldCreatePointerFromEmptyString() {
        JsonPointer pointer = JsonPointer.from("");
        assert pointer.key == "";
        assert pointer.remainder == Null;
        assert pointer.isEmpty;
        assert pointer.isLeaf;
        assert pointer.pointer == "/";
    }

    @Test
    void shouldCreatePointerFromSingleSlash() {
        JsonPointer pointer = JsonPointer.from("/");
        assert pointer.key == "";
        assert pointer.remainder == Null;
        assert pointer.isEmpty;
        assert pointer.isLeaf;
        assert pointer.pointer == "/";
    }

    @Test
    void shouldCreateLeafPointer() {
        JsonPointer pointer = JsonPointer.from("/foo");
        assert pointer.key == "foo";
        assert pointer.remainder == Null;
        assert pointer.isEmpty == False;
        assert pointer.isLeaf;
        assert pointer.pointer == "/foo";
    }

    @Test
    void shouldCreatePointerChain() {
        JsonPointer pointer = JsonPointer.from("/one/two/three");
        assert pointer.key == "one";
        assert pointer.remainder == JsonPointer.from("/two/three");
        assert pointer.isEmpty == False;
        assert pointer.isLeaf == False;
        assert pointer.pointer == "/one/two/three";
    }

    @Test
    void shouldIgnoreTrailingSlash() {
        JsonPointer pointer = JsonPointer.from("/foo/");
        assert pointer.key == "foo";
        assert pointer.remainder == Null;
        assert pointer.isEmpty == False;
        assert pointer.isLeaf == True;
        assert pointer.pointer == "/foo";
    }

    @Test
    void shouldUnescapeTilde() {
        JsonPointer pointer = JsonPointer.from("/foo~0bar");
        assert pointer.key == "foo~bar";
        assert pointer.remainder == Null;
        assert pointer.isEmpty == False;
        assert pointer.isLeaf == True;
        assert pointer.pointer == "/foo~0bar";
    }

    @Test
    void shouldUnescapeSlash() {
        JsonPointer pointer = JsonPointer.from("/foo~1bar");
        assert pointer.key == "foo/bar";
        assert pointer.remainder == Null;
        assert pointer.isEmpty == False;
        assert pointer.isLeaf == True;
        assert pointer.pointer == "/foo~1bar";
    }

    @Test
    void shouldNotUnescapeTrailingTilde() {
        JsonPointer pointer = JsonPointer.from("/foo~");
        assert pointer.key == "foo~";
        assert pointer.remainder == Null;
        assert pointer.isEmpty == False;
        assert pointer.isLeaf == True;
        assert pointer.pointer == "/foo~";
    }

    @Test
    void shouldBeAnIndex() {
        JsonPointer pointer = JsonPointer.from("/19");
        assert pointer.key == "19";
        assert pointer.remainder == Null;
        assert pointer.isEmpty == False;
        assert pointer.isLeaf == True;
        assert pointer.pointer == "/19";
        Int? index = pointer.index;
        assert index.is(Int);
        assert index == 19;
    }

    @Test
    void shouldNotBeAnIndex() {
        JsonPointer pointer = JsonPointer.from("/foo");
        Int? index = pointer.index;
        assert index == Null;
    }
}