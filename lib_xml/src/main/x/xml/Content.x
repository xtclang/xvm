/**
 * Content data including [CharData](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CharData),
 * [CDATA](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect), and
 * [EntityRef](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-EntityRef) within an XML [Element] or
 * [Attribute]. (Note that CDATA can only occur within an Element.)
 */
@Abstract class Content
        implements Part {
    @Override
    public/protected (Element|Attribute)? parent.set((Element|Attribute)? parent) {
        (Element|Attribute)? oldParent = this.parent;
        assert oldParent == Null || &parent == &oldParent || !oldParent.parts.contains(this)
                as "The parent of this Content cannot be modified";
        super(parent);
    }

    /**
     * The text content of the `Data`.
     */
    @RO String text;
}