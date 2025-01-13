/**
 * Represents [CDATA](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect) within an XML
 * [Element].
 */
class CData(String text)
        extends Content {
    /**
     * This constructor allows a new orphan [Content] `CData` section to be created, which can then
     * be added to an [Element].
     *
     * @param text  the textual content of the `CData` section
     */
    construct(String text) {
        assert:arg isValidCData(text) as $"Invalid CData text: {text.quoted()}";
        this.text = text;
    }

    @Override
    construct(CData that) {
        construct CData(that.text);
    }

    /**
     * The text content of the `CData` section.
     */
    @Override
    String text.set(String newValue) {
        String oldValue = get();
        if (newValue != oldValue) {
            assert:arg isValidCData(newValue) as $"Invalid CData text: {newValue.quoted()}";
            super(newValue);
        }
    }

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) = text.size + 12; // "<![CDATA[" ... "]]>"

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") = writeCData(buf, text);
}