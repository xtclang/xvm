/**
 * A Hasher for hashing String values in a case insensitive manner.
 */
static const CaseInsensitive
        implements Hasher<String> {
    @Override
    Int64 hashOf(String value) {
        @Unchecked Int64 hash = Int64:982_451_653.toUnchecked(); // start with a prime number
        for (Char char : value) {
            hash = hash * 31 + char.lowercase.toInt64();
        }
        return hash;
    }

    @Override
    Boolean areEqual(String value1, String value2) {
        if (value1.size != value2.size) {
            return False;
        }

        Iterator<Char> iter1 = value1.iterator();
        Iterator<Char> iter2 = value2.iterator();
        while (Char char1 := iter1.next(), Char char2 := iter2.next()) {
            if (char1 != char2 && char1.lowercase != char2.lowercase) {
                return False;
            }
        }

        return True;
    }

    /**
     * This is a [Type.Orderer] for [String] that uses case insensitive comparison.
     */
    static Ordered compare(String value1, String value2) {
        Int len1 = value1.size;
        Int len2 = value2.size;
        for (Int offset = 0, Int len = Int.minOf(len1, len2); offset < len; ++offset) {
            Char char1 = value1[offset];
            Char char2 = value2[offset];
            if (char1 != char2) {
                char1 = char1.lowercase;
                char2 = char2.lowercase;
                if (char1 != char2) {
                    return char1 <=> char2;
                }
            }
        }

        return len1 <=> len2;
    }

    /**
     * Test if a specified `String` begins with another specified `String`, but using
     * case-insensitive comparison.
     *
     * @param text    the full text
     * @param prefix  this is the snippet that the `text` may or may not start with
     *
     * @return True iff the case-insensitive `text` starts with the case-insensitive `prefix`
     */
    static Boolean stringStartsWith(String text, String prefix) {
        if (text.startsWith(prefix)) {
            return True;
        }

        Int length = prefix.size;
        if (text.size <= length) {
            return False;
        }

        for (Int offset = 0; offset < length; ++offset) {
            if (text[offset].lowercase != prefix[offset].lowercase) {
                return False;
            }
        }

        return True;
    }

    /**
     * Test if a specified `String` ends with another specified `String`, but using case-insensitive
     * comparison.
     *
     * @param text    the full text
     * @param suffix  this is the snippet that the `text` may or may not end with
     *
     * @return True iff the case-insensitive `text` ends with the case-insensitive `suffix`
     */
    static Boolean stringEndsWith(String text, String suffix) {
        if (text.endsWith(suffix)) {
            return True;
        }

        Int length     = suffix.size;
        Int textOffset = text.size - length;
        if (textOffset < 0) {
            return False;
        }

        for (Int offset = 0; offset < length; ++offset) {
            if (text[textOffset + offset].lowercase != suffix[offset].lowercase) {
                return False;
            }
        }

        return True;
    }
}