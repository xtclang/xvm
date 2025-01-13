/**
 * Represents [CharData](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CharData) within an XML
 * [Element] or [Attribute].
 */
class EntityRef(String text)
        extends Content {
    /**
     * This constructor allows a new orphan Content `Data` to be created, which can then be added to
     * an [Element].
     *
     * @param text  the textual content of the `Data` `Part`
     */
    construct(String text) {
        assert:arg isValidEntityRef(text) as $"Invalid EntityRef: {text.quoted()}";
        this.text = text;
    }

    @Override
    construct(EntityRef that) {
        construct EntityRef(that.text);
    }

    /**
     * The text content of the `EntityRef`.
     */
    @Override
    String text.set(String newValue) {
        String oldValue = get();
        if (newValue != oldValue) {
            assert:arg isValidEntityRef(newValue) as $"Invalid EntityRef: {newValue.quoted()}";
            super(newValue);
        }
    }

    // TODO need a property or helper that looks up the reference and returns the corresponding value

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) = text.size;

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") = text.appendTo(buf);
}