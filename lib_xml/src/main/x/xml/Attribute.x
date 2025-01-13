/**
 * An [XML Attribute](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Attribute).
 */
interface Attribute
        extends ValueHolder {
    /**
     * `Attribute`s can only be nested within an `Element`.
     */
    @Override
    @RO Element? parent;

    @Override
    String value;

    @Override
    <Value> Value valueAs(Format<Value> format) = format.decode(value);

    @Override
    <Value> Value valueAs(Format<Value> format, Value defaultValue) {
        String text = value;
        return text.empty ? defaultValue : format.decode(text);
    }

    @Override
    <Value> String encode(Value? value, Format<Value> format) {
        assert:arg value != Null as $"Attribute values must not be Null ({name=})";
        String text = format.encode(value);
        this.value = text;
        return text;
    }

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) {
        Int total = name.size + value.size + 3;     // '=' and 2 quote chars
        Int squotes = 0;
        Int dquotes = 0;
        for (Char ch : value) {
            switch (ch) {
            case '\'':
                ++squotes;
                break;
            case '\"':
                ++dquotes;
                break;
            case '<':
                total += 3;         // "&lt;"
                break;
            case '&':
                total += 4;         // "&amp;"
                break;
            }
        }
        // we'll never escape single quotes because we'll always use double quotes by default or if
        // there are any single quotes; we may need to escape double quotes, though, under two
        // conditions: (1) the value is > 64 chars long (we always use double quotes), or (2) there
        // are both single and double quotes (in which case we also always use double quotes)
        if (dquotes > 0 && (value.size > 64 || squotes > 0)) {
            total += 5 * dquotes;   // "&quot;"
        }
        return total;
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") {
        return writeAttribute(buf, name, value);
    }
}
