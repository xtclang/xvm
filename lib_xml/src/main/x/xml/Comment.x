/**
 * An [XML Comment](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Comment).
 */
class Comment(String text)
        implements Part {
    /**
     * This constructor allows a new orphan `Comment` to be created, which can then be added to a
     * [Document] or [Element].
     */
    construct(String text) {
        assert:arg isValidComment(text) as $"Invalid Comment text: {text.quoted()}";
        this.text = text;
    }

    @Override
    public/protected (Document|Element)? parent.set((Document|Element)? parent) {
        (Document|Element)? oldParent = this.parent;
        assert oldParent == Null || &parent == &oldParent || !oldParent.parts.contains(this)
                as "The parent of this Comment cannot be modified";
        super(parent);
    }

    /**
     * The text content of the `Comment`.
     */
    String text.set(String s) {
        assert:arg isValidComment(s) as $"Invalid Comment text: {s.quoted()}";
        super(s);
    }

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) {
        return 7 + (text.empty ? 0 : text.size + 1);
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") {
        "<!--".appendTo(buf);
        if (!text.empty) {
            if (!text[0].isWhitespace()) {
                buf.add(' ');
            }
            text.appendTo(buf);
            if (!text[text.size-1].isWhitespace()) {
                buf.add(' ');
            }
        }
        return "-->".appendTo(buf);
    }
}