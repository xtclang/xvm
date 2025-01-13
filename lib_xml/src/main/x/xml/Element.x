import ecstasy.io.TextPosition;

/**
 * Represents a piece of an XML document.
 */
interface Element
        extends ValueHolder {
    /**
     * `Element`s can be nested. A nested `Element` will have a non-`Null` parent `Element`, or be a
     * child of the [Document] itself.
     */
    @Override
    @RO (Element|Document)? parent;

    /**
     * The textual form of the `Element`'s value, or `Null` if the `Element` is an empty `Element`,
     * which has no value.
     */
    @Override
    String? value;

    @Override
    <Value> Value? valueAs(Format<Value> format) {
        if (String text ?= value) {
            text = text.trim();
            if (!text.empty) {
                return format.decode(text);
            }
        }
        return Null;
    }

    @Override
    <Value> Value valueAs(Format<Value> format, Value defaultValue) {
        if (String text ?= value) {
            text = text.trim();
            if (!text.empty) {
                return format.decode(text);
            }
        }
        return defaultValue;
    }

    @Override
    <Value> String? format(Value? value, Format<Value> format) {
        if (value == Null) {
            this.value = Null;
            return Null;
        }

        String text = format.encode(value);
        this.value = text;
        return text;
    }

    /**
     * A representation of the [Attribute]s of this XML `Element`.
     */
    @RO Map<String, Attribute> attributes;

    /**
     * A representation of the child `Element`s of this XML `Element`.
     */
    @RO List<Element> elements;

// TODO Element ensureElement(String name);
}