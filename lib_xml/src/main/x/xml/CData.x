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
        this.text   = text;
    }

    @Override
    public/protected Element? parent;

    /**
     * The text content of the `CData` section.
     */
    @Override
    String text.set(String s) {
        assert:arg isValidCData(s) as $"Invalid CData text: {s.quoted()}";
        super(s);
    }

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) = text.size + 12; // "<![CDATA[" ... "]]>"

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") = writeCData(buf, text);
}