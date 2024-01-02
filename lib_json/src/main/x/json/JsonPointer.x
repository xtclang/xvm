/**
 * This const represents an immutable implementation of a JSON Pointer
 * as defined by http://tools.ietf.org/html/rfc6901
 *
 * A JSON Pointer, when applied to a target JSON `Doc`, defines a reference location in the target `Doc`.
 * An empty JSON Pointer defines a reference to the target itself.
 */
const JsonPointer {
    /**
     * Private `JsonPointer` constructor.
     * Pointer instances are created via the static `from` method, which
     * will properly validate the pointer string and create the correct
     * pointer chain.
     */
    private construct (String pointer, String key = "", JsonPointer? remainder = Null) {
        if (key == "-") {
            assert remainder == Null as $"Invalid pointer, '{pointer}' the array append path '-' can only be used as the final pointer element";
        }
        if (key.indexOf('~')) {
            StringBuffer refToken = new StringBuffer();
            Int last = key.size - 1;
            for (Int c = 0; c < key.size; c++) {
                Char ch = key[c];
                if (c != last && ch == '~') {
                    Char next = key[c + 1];
                    if (next == '0') {
                        ch = '~';
                        c++;
                    } else if (next == '1') {
                        ch = '/';
                        c++;
                    }
                }
                refToken.append(ch);
            }
            this.key = refToken.toString();
        } else {
            this.key = key;
        }
        this.remainder = remainder;
        this.pointer = pointer;
    }

    /**
     * The key for this pointer.
     */
    String key;

    /**
     * The remainder of the pointer chain.
     */
    JsonPointer? remainder;

    /**
     * The string representation of this pointer.
     */
    String pointer;

    /**
     * A flag that is `True` if this pointer is the last pointer
     * in the chain.
     */
    @Lazy Boolean isEmpty.calc() {
        return key == "";
    }

    /**
     * A flag that is `True` if this pointer is the last pointer
     * in the chain.
     */
    @Lazy Boolean isLeaf.calc() {
        return remainder == Null;
    }

    /**
     * If this pointer represents an array index, then return
     * the index value, otherwise return `Null`.
     *
     * To be a valid array index, the key must be a numeric string
     * representing a non-negative `Int` value.
     */
    @Lazy Int? index.calc() {
        if (key == "-") {
            return AppendIndex;
        }
        try {
            IntLiteral lit = new IntLiteral(key);
            Int i = lit.toInt64();
            if (i >= 0) {
                return i;
            }
        } catch (Exception ignored) {
        }
        return Null;
    }

    /**
     * A leaf `JsonPointer` that represents the path value to indicate appending to
     * the end of a JSON array.
     */
    static JsonPointer Append = from("/-");

    /**
     * The index value used to indicate a value should be appended to the array.
     */
    static Int AppendIndex = -1;

    /**
     * Create a `JsonPointer` from a `String` representation of a JSON Pointer as defined
     * by by http://tools.ietf.org/html/rfc6901
     *
     * If the JSON Pointer string is non-empty, it must be a sequence of '/' prefixed tokens,
     * and the target must either be a JSON Array, or a JSON Object.
     * If the target is a JSON Array, the pointer defines a reference to an array element,
     * and the last token specifies the index.
     * If the target is a JSON Object, the pointer defines a reference to a name/value pair,
     * and the last token specifies the name.
     */
    static JsonPointer from(String pointer) {
        // The JSON pointer spec requires all paths to start with a "/".
        // Rather than throw an exception, we just ensure that there is always
        // a leading "/"
        if (pointer.size == 0 || pointer[0] != '/') {
            pointer = "/" + pointer;
        }
        if (pointer.size == 1) {
            return new JsonPointer(pointer);
        }
        assert pointer[1] != '/';
        if (Int index := pointer.indexOf('/', 1)) {
            String remainder = pointer[index ..< pointer.size];
            if (remainder.size == 1) {
                return new JsonPointer(pointer[0 ..< pointer.size - 1], pointer[1 ..< index]);
            }
            return new JsonPointer(pointer, pointer[1 ..< index], JsonPointer.from(pointer[index ..< pointer.size]));
        }
        return new JsonPointer(pointer, pointer[1 ..< pointer.size]);
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return pointer.size;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        return pointer.appendTo(buf);
    }
}