/**
 * Represents [CharData](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CharData) within an XML
 * [Element] or [Attribute].
 */
class Data(String text)
        extends Content {
    /**
     * This constructor allows a new orphan Content `Data` to be created, which can then be added to
     * an [Element].
     *
     * @param text  the textual content of the `Data` `Part`
     */
    construct(String text) {
        assert:arg isValidCharData(text) as $"Invalid CharData: {text.quoted()}";
        this.text = text;
    }

    @Override
    construct(Data that) {
        construct Data(that.text);
    }

    /**
     * The text content of the `Data`.
     */
    @Override
    String text.set(String newValue) {
        String oldValue = get();
        if (newValue != oldValue) {
            assert:arg isValidCharData(newValue) as $"Invalid CharData: {newValue.quoted()}";
            super(newValue);
        }
    }

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) {
        String text = this.text;
        Int    of   = 0;
        Int    len  = text.size;
        while (of < len && text[of].isWhitespace()) {
            ++of;
        }
        while (len > of && text[len-1].isWhitespace()) {
            --len;
        }
        Int total = len - of;
        for ( ; of < len; ++of) {
            switch (Char ch = text[of]) {
            case '<':
                total += 3;
                break;
            case '&':
                total += 4;
                break;
            case '>':
                if (of >= 2 && text[of-1] == ']' && text[of-2] == ']') {
                    total += 3;
                }
                break;
            }
        }
        return total;
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") = writeData(buf, text);
}