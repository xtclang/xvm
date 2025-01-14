/**
 * An [XML Attribute](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Attribute).
 */
interface Attribute
        extends ValueHolder {
    /**
     * `Attribute`s can only be nested within an `Element`.
     */
    @Override
    @RO Element parent;

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

    // TODO Stringable
}
