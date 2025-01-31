/**
 * An implementation of the [Element] interface using the [Node] framework. For a given XML
 * document, there are likely to be a huge number of [Element] instances, so this implementation is
 * optimized for space, while still attempting to provide high performance for expected uses.
 *
 * TODO
 * no value vs. cached-only vs. contents-only vs. both
 * no attr vs. #attrs 20
 * no sub elements vs. #elements 30
 */
class ElementNode
        implements xml.Element
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an [ElementNode] with the specified name and optional value.
     *
     * @param parent  the [Element]'s parent [Node], or `Null`
     * @param name    the [Element]'s name
     * @param value   (optional) the [Element]'s value
     */
    construct((DocumentNode|ElementNode)? parent, String name, String? value) {
        assert:arg isValidName(name);
        this.parent_ = parent;
        this.name    = name;
        this.value   = value;
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
    @RO (DocumentNode|ElementNode)? parent.get() = parent_.as((DocumentNode|ElementNode)?);

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

    /**
     * TODO
     */
    conditional AttributeNode attributeByName(String name) {
        TODO
    }
}