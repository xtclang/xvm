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
            assert remainder == Null as $|Invalid pointer, "{pointer}" the array append path "-"\
                                         | can only be used as the final pointer element
                                         ;
        }
        if (key.indexOf('~')) {
            StringBuffer refToken = new StringBuffer();
            for (Int c = 0, Int last = key.size - 1; c <= last; c++) {
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
        this.pointer   = pointer;
        this.isEmpty   = key == "";
        this.isLeaf    = remainder == Null;
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
    Boolean isEmpty;

    /**
     * A flag that is `True` if this pointer is the last pointer
     * in the chain.
     */
    Boolean isLeaf;

    /**
     * If this pointer represents an array index, then return
     * the index value, otherwise return `Null`.
     *
     * To be a valid array index, the key must be a numeric string
     * representing a non-negative `Int` value.
     */
    @Lazy Int? index.calc() {
        if (key == AppendKey) {
            return Null;
        }
        try {
            IntLiteral lit = new IntLiteral(key);
            Int i = lit.toInt64();
            return i;
        } catch (Exception ignored) {}
        return Null;
    }

    /**
     * A leaf `JsonPointer` that represents the path value to indicate appending to
     * the end of a JSON array.
     */
    static JsonPointer Append = from("/-");

    /**
     * The key used to indicate a value should be appended to the array.
     */
    static String AppendKey = "-";

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
            return new JsonPointer(pointer, pointer[1 ..< index], JsonPointer.from(remainder));
        }
        return new JsonPointer(pointer, pointer[1 ..< pointer.size]);
    }

    /**
     * Determine whether this `JsonPointer` is equivalent to, or is a parent
     * of the specified `JsonPointer`.
     *
     * @param p  the `JsonPointer` that may be a child of this `JsonPointer`
     *
     * @returns `True` iff this `JsonPointer` is equivalent to, or is a  parent
     *          of the specified `JsonPointer`
     */
    Boolean isParent(JsonPointer p) {
        if (isEmpty) {
            return True;
        }
        if (this.key != p.key) {
            return False;
        }
        JsonPointer? remainderThis  = this.remainder;
        JsonPointer? remainderOther = p.remainder;
        return switch (remainderThis.is(_), remainderOther.is(_)) {
            case (Null, Null):               True;
            case (Null, JsonPointer):        True;
            case (JsonPointer, Null):        False;
            case (JsonPointer, JsonPointer): remainderThis.isParent(remainderOther);
            default: assert;
        };
    }

    /**
     * Obtain the value from the specified JSON `Doc` at the location
     * pointed to by this `JsonPointer`.
     *
     * @param doc                   the JSON `Doc` to obtain the value from
	 * @param allowNegativeIndices  support the non-standard use of negative indices for JSON arrays to mean indices
	 *                              starting at the end of an array. For example, -1 points to the last element in
	 *                              the array. Valid negative indices are -1 ..< -array.size The default is `False`
     *
     * @return `True` iff the doc contains a value at the location of this pointer
     * @return the JSON value in the doc at the location of this pointer
     */
    conditional Doc get(Doc doc, Boolean supportNegativeIndices = False) {
        JsonPointer? remainder = this.remainder;
        switch (doc.is(_)) {
            case JsonObject:
                if (Doc child := doc.get(this.key)) {
                    if (remainder.is(JsonPointer)) {
                        return remainder.get(child, supportNegativeIndices);
                    }
                    return True, child;
                }
                break;
            case JsonArray:
                if (Int index := ensurePositiveIndex(this.index, doc, supportNegativeIndices)) {
                    Doc child = doc[index];
                    if (remainder.is(JsonPointer)) {
                        return remainder.get(child, supportNegativeIndices);
                    }
                    return True, child;
                }
                break;
            case Primitive:
                if (remainder == Null) {
                    return True, doc;
                }
                break;
        }
        return False;
    }

    /**
     * Ensure the specified index is a positive index into the array
     *
     * @return False if the array is empty, or True if the index is zero or positive in the range 0 ..< array.size,
     *         or True if the index is negative in the range -array.size >.. -1
     * @return a valid index into the array
     */
    private conditional Int ensurePositiveIndex(Int? index, JsonArray array, Boolean supportNegativeIndices) {
        if (index.is(Int)) {
            if (index >= 0 && index < array.size) {
                return True, index;
            }
            if (supportNegativeIndices && index < 0 && index >= -array.size) {
                return True, index + array.size;
            }
        }
        return False;
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() = pointer.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = pointer.appendTo(buf);
}