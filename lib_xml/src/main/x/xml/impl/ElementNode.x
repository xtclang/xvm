class ElementNode
        implements xml.Element
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * TODO
     */
    construct(String name) {
        assert:arg isValidName(name);
        this.name = name;
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

    /**
     * Construct a new `ElementNode`, copying the content of the passed `Element`.
     *
     * @param that  the `Element` to copy
     */
    construct(Element that) {
        Node? prev = Null;
        EachPart: for (Part part : that.parts) {
            Node node = makeNode(part);
            if (node.is(Content)) {
                // TODO should we build up the element value here, or lazily do it when requested
            }
            if (prev == Null) {
                child_ = node;
            } else {
                prev.next_ = node;
            }
            prev = node;
        }
    } finally {
        // finish the adoption
        for (Node? node = child_; node != Null; node = node.next_) {
            node.parent_ = this;
        }
    }

    // ----- Element API --------------------------------------------------------------------------

    @Override
    @RO (xml.Element|Document)? parent.get() = parent_.as((xml.Element|Document)?);

    @Override
    String name.set(String newName) {
        String oldName = name;
        if (newName != oldName) {
            assert:arg isValidName(newName);
            mod();
            super(newName);
        }
    }

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
            mod();
            super(value);
        }
    }

    @Override
    @RO List<Content> contents.get() = TODO();

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