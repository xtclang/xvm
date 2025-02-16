/**
 * An implementation of the [Element] interface using the [Node] framework. For a given XML
 * document, there are likely to be a huge number of [Element] instances, so this implementation is
 * optimized for space, while still attempting to provide high performance for expected uses.
 */
class ElementNode
        extends ValueHolderNode
        implements xml.Element {

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
        construct ValueHolderNode(that);
        this.counts_ = that.counts_;
    }

    /**
     * Construct a new `ElementNode`, copying the content of the passed `Element`.
     *
     * @param that  the `Element` to copy
     */
    construct(Element that) {
        construct ValueHolderNode(that);
        this.counts_ = MaxValue; // forces a lazy re-count
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
    @RO Int size.get() = attributeCount + contentCount + elementCount;  // TODO handle contentCount == 0 and value != Null

    @Override
    @RO Boolean empty.get() = child_ == Null && value == Null;

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

    @Override
    protected conditional Node allowsChild(Part part) {
        return part.is(Element) || part.is(Attribute) || part.is(Content)
                ? super(part)
                : False;
    }

    private UInt32 counts_;

    @Override
    protected Int contentCount {
        static Int    Bits  = 7;
        static UInt32 Ones  = (1 << Bits) - 1;
        static Int    Shift = 0;
        static UInt32 Mask  = Ones << Shift;

        @Override
        Int get() {
            Int count = counts_ & Mask;
            if (count == Ones) {
                count = parts.filter(n -> n.is(ContentNode)).size;
                if (count < Ones) {
                    set(count);
                }
            }
            return count;
        }

        @Override
        void set(Int newValue) {
            assert newValue >= 0;
            counts_ = counts_ & ~Mask | newValue.notGreaterThan(Ones).toUInt32();
        }
    }

    @Override
    protected (Node? prev, Node? node) firstContent() {
        TODO
    }

    /**
     * The number of [Attribute] child [Node]s this `ElementNode` contains.
     */
    protected Int attributeCount {
        static Int    Bits  = 7;
        static UInt32 Ones  = (1 << Bits) - 1;
        static Int    Shift = contentCount.Bits;
        static UInt32 Mask  = Ones << Shift;

        @Override
        Int get() {
            Int count = counts_ & Mask >>> Shift;
            if (count == Ones) {
                count = parts.filter(n -> n.is(AttributeNode)).size;
                if (count < Ones) {
                    set(count);
                }
            }
            return count;
        }

        @Override
        void set(Int newValue) {
            assert newValue >= 0;
            counts_ = counts_ & ~Mask | (newValue.notGreaterThan(Ones).toUInt32() << Shift);
        }
    }

    /**
     * The number of [Element] child [Node]s this `ElementNode` contains.
     */
    protected Int elementCount {
        static Int    Bits  = 32 - contentCount.Bits - attributeCount.Bits;
        static UInt32 Ones  = (1 << Bits) - 1;
        static Int    Shift = attributeCount.Shift + attributeCount.Bits;
        static UInt32 Mask  = Ones << Shift;

        @Override
        Int get() {
            Int count = counts_ & Mask >>> Shift;
            if (count == Ones) {
                count = parts.filter(n -> n.is(ElementNode)).size;
                if (count < Ones) {
                    set(count);
                }
            }
            return count;
        }

        @Override
        void set(Int newValue) {
            assert newValue >= 0;
            counts_ = counts_ & ~Mask | (newValue.notGreaterThan(Ones).toUInt32() << Shift);
        }
    }

    /**
     * Determine if any [AttributeNode] of the specified name exists within this `ElementNode`.
     *
     * @param name  the [Attribute] name to search for
     *
     * @return `True` iff an [Attribute] of the specified name was found
     * @return (conditional) the [AttributeNode] with the specified name
     */
    /* TODO CP protected */ conditional AttributeNode attributeByName(String name) {
        // attributes precede all other child nodes, so just go until the node is not an attribute
        Node? node = child_;
        while (node.is(AttributeNode)) {
            if (node.name == name) {
                return True, node;
            }
            node = node.as(Node).next_;     // TODO CP get rid of .as(Node)
        }
        return False;
    }
}