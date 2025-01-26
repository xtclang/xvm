class ElementNode
        implements xml.Element
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * TODO
     */
    construct(String name) {
        TODO
    }

    /**
     * Construct a new mutable `ElementNode`, copying the content of the passed `ElementNode`.
     *
     * @param that  the `ElementNode` to copy
     */
    @Override
    construct(ElementNode that) {
        TODO
    }

    // ----- Element API --------------------------------------------------------------------------

    @Override
    @RO (xml.Element|Document)? parent.get() = parent_.as((xml.Element|Document)?);

    @Override
    String? value.set(String? newValue) {
        String? oldValue = this.value;
        if (newValue != oldValue) {
            if (oldValue != Null) {
                // TODO remove all previous parts (or if the first one is a data part, rewrite it)
            }
            if (newValue != Null) {
                // TODO add one data part
            }
            super(value);
        }
    }

    @Override
    @RO List<Attribute> attributes.get() = TODO();

    @Override
    @RO Map<String, Attribute> attributesByName.get() = TODO();

    @Override
    Attribute setAttribute(String name, String value) {
        TODO
    }

    @Override
    @RO List<xml.Element> elements.get() = TODO();

    @Override
    Element add(String name, String? value = Null) = TODO();

    // ----- internal ------------------------------------------------------------------------------

}