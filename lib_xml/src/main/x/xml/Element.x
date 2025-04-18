/**
 * Represents a piece of an XML document.
 */
interface Element
        extends ValueHolder {
    @Override
    @RO Element root.get() = parent.is(Element)?.root : this;

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
    <Value> String? encode(Value? value, Format<Value> format) {
        if (value == Null) {
            this.value = Null;
            return Null;
        }

        String text = format.encode(value);
        this.value = text;
        return text;
    }

    /**
     * A representation of the `Attribute`s of this XML `Element`.
     *
     * If the containing [Document] (or substructure thereof) is mutable, then `Attribute` objects
     * can be added and removed from this `List`, subject to any constraints imposed by the XML
     * specification. `Attribute` objects added to this `List` may be replaced (as part of the add
     * operation, or at any subsequent point) with different objects representing the same
     * information, so the caller must not assume that the reference added to the `List` is the same
     * reference that is held by the `List`.
     */
    @RO List<Attribute> attributes;

    /**
     * A representation of the [Attribute]s of this XML `Element`, keyed by `Attribute` name. The
     * insertion order of the `Map` is maintained.
     *
     * If the containing [Document] (or substructure thereof) is mutable, then `Attribute` objects
     * can be added and removed from this `Map`, subject to any constraints imposed by the XML
     * specification. `Attribute` objects added to this `Map` may be replaced (as part of the add
     * operation, or at any subsequent point) with different objects representing the same
     * information, so the caller must not assume that the reference added to the `Map` is the same
     * reference that is held by the `Map`.
     */
    @RO Map<String, Attribute> attributesByName;

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
    <Value> Attribute encodeAttribute(String name, Value value, Format<Value> format) {
        return setAttribute(name, format.encode(value));
    }

    /**
     * A representation of the child `Element`s of this XML `Element`.
     *
     * If the containing [Document] (or substructure thereof) is mutable, then `Element` objects can
     * be added and removed from this `List`, subject to any constraints imposed by the XML
     * specification. `Element` objects added to this `List` may be replaced (as part of the add
     * operation, or at any subsequent point) with different objects representing the same
     * information, so the caller must not assume that the reference added to the `List` is the same
     * reference that is held by the `List`.
     */
    @RO List<Element!> elements;

    /**
     * Find the first child `Element` with the specified name and return it.
     *
     * @param name  the name of the child `Element` to find
     *
     * @return `True` iff this `Element` contains a child `Element` with the specified name
     * @return (optional) the first child `Element` that has the specified name
     */
    conditional Element! find(String name) = elements.any(e -> e.name == name);

    /**
     * Add a new child `Element` with the specified name and optional value. The new child is
     * appended to the end of [the list of child Elements](elements).
     *
     * @param name   the name of the child `Element` to find
     * @param value  the `String` value for the new child `Element`, or `Null`
     *
     * @return the new child `Element`
     */
    Element! add(String name, String? value = Null);

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
    <Value> Element! add(String name, Value? value, Format<Value> format) {
        return add(name, format.encode(value?) : Null);
    }

    /**
     * Remove the child `Element` with the specified name, if any such child `Element` exists. If
     * more than one `Element` with the specified name exists, all are removed.
     *
     * @return this `Element`
     */
    Element! remove(String name) {
        elements.removeAll(e -> e.name == name);
        return this;
    }

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) {
        List<Part> parts = this.parts;
        if (parts.empty) {
            return indent + name.size + 3;
        }

        if (pretty) {
            Int size = (indent + name.size + 3) * 2;    // includes a newline
            for (Part part : parts) {
                // long story short: attributes add " " (first only) or ", ", so call that 2, and
                // elements add a new line, but content adds nothing, so just assume +2 for each
                size += 2 + part.estimateStringLength(pretty, indent + 2);
            }
            return size;
        }

        Int     size        = name.size + 2 * 2 + 5;
        Boolean passedAttrs = False;
        Each: for (Part part : parts) {
            if (!passedAttrs) {
                if (part.is(Attribute)) {
                    size += Each.first ? 1 : 2;
                } else {
                    passedAttrs = True;
                }
            }
            size += part.estimateStringLength();
        }
        return size;
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent = "") {
        List<Part> parts = this.parts;

        buf.add('<');
        name.appendTo(buf);
        if (parts.empty) {
            return "/>".appendTo(buf);
        }

        Boolean stag      = True;       // spec refers to opening element tag as "stag"
        String  newIndent = indent;     // will be grown to a new indent before it gets used
        Loop: for (Part part : parts) {
            if (part.is(Attribute)) {
                if (Loop.first) {
                    buf.add(' ');
                } else {
                    ", ".appendTo(buf);
                }
                part.appendTo(buf, pretty, indent);
            } else {
                if (stag) {
                    buf.add('>');
                    newIndent = pretty ? indent + "  " : indent;
                    stag      = False;
                }

                if (pretty) {
                    buf.add('\n');
                    newIndent.appendTo(buf);
                }
                part.appendTo(buf, pretty, newIndent);
            }
        }

        if (stag) {
            return "/>".appendTo(buf);
        }

        if (pretty) {
            buf.add('\n');
            indent.appendTo(buf);
        }
        "</".appendTo(buf);
        name.appendTo(buf);
        return buf.add('>');
    }

    @Override
    static <CompileType extends Element> Int64 hashCode(CompileType value) {
        // an element is mutable, so its hash code should only contain things that represent its
        // permanent state; unfortunately, we can assume that is its name meets that criteria, and
        // even its name can be modified
        return value.name.hashCode();
    }

    @Override
    static <CompileType extends Element> Boolean equals(CompileType value1, CompileType value2) {
        // two elements are considered identical if they have the same name and value, and are
        // composed of the same parts
        return value1.name  == value2.name
            && value1.value == value2.value
            && value1.parts == value2.parts;
    }
}