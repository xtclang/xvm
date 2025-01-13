/**
 * Content data including [CharData](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CharData),
 * [CDATA](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect), and
 * [EntityRef](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-EntityRef) within an XML [Element] or
 * [Attribute]. (Note that CDATA can only occur within an Element.)
 */
@Abstract class Content(String text)
        implements Part {
    @Override // TODO GG this compiled without an @Override
    @RO (Element|Attribute)? parent.get() = Null;

    @Override
    construct(Content that) {
        this.text = that.text;
    }
}