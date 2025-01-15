/**
 * Content data including [CharData](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CharData) and
 * [CDATA](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect) within an XML Element, but not
 * including [EntityRef](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-EntityRef).
 */
class Data(String data, Boolean cdsect = False)
        implements Part {
    /**
     * This constructor allows a new orphan Content `Data` to be created, which can then be added to
     * an [Element].
     *
     * @param text    the textual content of the `Data` `Part`
     * @param cdsect  `True` iff the data is a [CDSect](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect)
     */
    construct(String text, Boolean cdsect = False) {
        if (cdsect) {
            assert:arg isValidCData(text) as $"Invalid CData text: {text.quoted()}";
        } else {
            assert:arg isValidCharData(text) as $"Invalid CharData: {text.quoted()}";
        }
        this.text   = text;
        this.cdsect = cdsect;
    }

    @Override
    public/protected Element? parent;

    /**
     * The text content of the `Data`.
     */
    String text.set(String s) {
        if (cdsect) {
            assert:arg isValidCData(s) as $"Invalid CData text: {s.quoted()}";
        } else {
            assert:arg isValidCharData(s) as $"Invalid CharData: {s.quoted()}";
        }
        super(s);
    }

    /**
     * `True` iff the text content is [CDATA](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect)
     */
    public/protected Boolean cdsect;

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) {
        // TODO
        return 0;
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") {
        // TODO
        return buf;
    }
}