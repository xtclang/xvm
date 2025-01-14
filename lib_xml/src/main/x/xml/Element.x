import ecstasy.io.TextPosition;

/**
 * Represents a piece of an XML document.
 */
interface Element
        extends ValueHolder {
    @Override
    @RO Element root.get() = parent.is(Element)?.root : this;

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
     * Add or modify an [Attribute] on this `Element` of the specified name.
     *
     * @param name   the name of the [Attribute] to add or modify
     * @param value  the `String` value to assign to the [Attribute]
     *
     * @return the added or modified [Attribute] of the specified name
     */
    Attribute setAttribute(String name, String value);

    /**
     * Add or modify an [Attribute] on this `Element` of the specified name.
     *
     * @param name    the name of the [Attribute] to add or modify
     * @param value   a `Value` that is compatible with the specified [Format]
     * @param format  a [Format] that can convert the specified `Value` to a `String`
     *
     * @return the added or modified [Attribute] of the specified name
     */
    <Value> Attribute formatAttribute(String name, Value value, Format<Value> format) {
        return setAttribute(name, format.encode(value));
    }

    /**
     * A representation of the child `Element`s of this XML `Element`.
     */
    @RO List<Element> elements;

    /**
     * Find the first child `Element` with the specified name and return it.
     *
     * @param name  the name of the child `Element` to find
     *
     * @return `True` iff this `Element` contains a child `Element` with the specified name
     * @return (optional) the first child `Element` that has the specified name
     */
    conditional Element find(String name) = elements.any(e -> e.name == name);

    /**
     * Add a new child `Element` with the specified name and optional value. The new child is
     * appended to the end of [the list of child Elements](elements).
     *
     * @param name   the name of the child `Element` to find
     * @param value  the `String` value for the new child `Element`, or `Null`
     *
     * @return the new child `Element`
     */
    Element add(String name, String? value = Null);

    /**
     * Add a new child `Element` with the specified name and optional value. The new child is
     * appended to the end of [the list of child Elements](elements).
     *
     * @param name    the name of the child `Element` to find
     * @param value   a `Value` that is compatible with the specified [Format]
     * @param format  a [Format] that can convert the specified `Value` to a `String`
     *
     * @return the new child `Element`
     */
    <Value> Element add(String name, Value? value, Format<Value> format) {
        String? text = Null;
        if (value != Null) {
            text = format.encode(value);
        }
        return add(name, text);
    }

    /**
     * Remove the child `Element` with the specified name, if any such child `Element` exists. If
     * more than one `Element` with the specified name exists, all are removed.
     *
     * @return this `Element`
     */
    Element remove(String name) {
        elements.removeAll(e -> e.name == name);
        return this;
    }
}