/**
 * Represents internal details about a [Part] of an XML [Document].
 */
mixin ContentNode(Node? parent_ = Null)
        into Content
        extends Node {
    // TODO GG whether or not this redundant declaration is made here, the following error occurs:
    // VERIFY-67: Property information for "xml:Content.parent" contains conflicting types "xml:Element | xml:Attribute?" and "xml:Part?".
    @Override
    public/protected (Element|Attribute)? parent;

    @Override
    String text.set(String newText) {
        String oldText = get();
        if (newText != oldText) {
            super(newText);
            mod();
        }
    }

    @Override
    protected UInt32 mod() {
        super();
        parent_?.mod();
    }

    @Override
    protected conditional Node allowsChild(Part part) {
        return False;
    }
}