/**
 * Represents internal details about a [Part] of an XML [Document].
 */
annotation ContentNode(Node? parent_ = Null)
        into Content
        incorporates Node {
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
        parent_?.mod();
        return super();
    }

    @Override
    protected conditional Node allowsChild(Part part) {
        return False;
    }
}