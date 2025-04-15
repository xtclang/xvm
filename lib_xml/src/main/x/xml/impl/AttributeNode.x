/**
 * An implementation of the [Attribute] interface using the [Node] framework. `Attribute`s occur
 * inside of [Element]s; each Element can have 0 or more named attributes, with the names being
 * unique **and case-sensitive**. Since there are likely to be a great number of `Attribute`
 * instances, the design optimizes for space.
 *
 * When there are no child nodes to hold the data, the `Attribute` maintains its value in a field,
 * but it can lazily instantiate a [Part] to represent the value if and when a caller requests the
 * child `Part`s.
 */
class AttributeNode
        extends ValueHolderNode
        implements Attribute {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an [AttributeNode] with the specified name and optional value.
     *
     * @param parent  the [Attribute]'s parent [Node], or `Null`
     * @param name    the [Attribute]'s name
     * @param value   the [Attribute]'s value
     */
    construct(ElementNode? parent, String name, String value) {
        construct ValueHolderNode(parent, name, value);
    }

    /**
     * Construct a new mutable `AttributeNode`, copying the content of the passed `AttributeNode`.
     *
     * @param that  the `AttributeNode` to copy
     */
    @Override
    construct(AttributeNode that) {
        construct ValueHolderNode(that);
    }

    /**
     * Construct a new `AttributeNode`, copying the content of the passed `Attribute`.
     *
     * @param that  the `Attribute` to copy
     */
    construct(Attribute that) {
        construct ValueHolderNode(that);
    }

    /**
     * Create an `AttributeNode` that contains the children provided in the passed linked list of
     * parsed nodes.
     *
     * @param firstNode  the first [Parsed] [Node] in the linked list of nodes
     */
    construct(String name, Parsed? firstNode) {
        this.name = name;
    } finally {
        Int contentCount = 0;
        for (Node? node = firstNode; node != Null; node = node.next_) {
            ++contentCount;
            node.parent_ = this;
        }
        this.contentCount = contentCount;   // note: not retained by this class, so this is a no-op
        this.child_       = firstNode;
    }

    // ----- Attribute API --------------------------------------------------------------------------

    @Override
    @RO ElementNode? parent.get() = parent_.as(ElementNode?);

    @Override
    String name.set(String newName) {
        String oldName = name;
        if (newName != oldName) {
            assert:arg isValidName(newName);
            // make sure no other sibling attribute has the same name
            assert !parent?.attributeByName(newName) as $"An Attribute with the name {newName.quoted()} already exists";
            mod();
            super(newName);
        }
    }

    @Override
    String value;

    @Override
    @RO List<Content> contents.get() = new ContentList(parts.as(AttributeNode));

    @Override
    conditional Int knownSize() = (child_?.next_ : Null) != Null ? False : (True, 1);

    @Override
    @RO Int size.get() = contentCount.notLessThan(1);   // always has at least one Content child

    @Override
    @RO Boolean empty.get() = False;                    // always has at least one Content child

    @Override
    Node clear() {
        assert as "An Attribute cannot clear its contents; doing so would delete its Data";
    }

    // ----- Content List implementation -----------------------------------------------------------

    protected static class ContentList(AttributeNode partList)
            extends ValueHolderNode.ContentList(partList) {
        @Override
        conditional Int knownSize() = partList.child_ == Null ? (True, 1) : False;

        @Override
        @RO Int size.get() = partList.contentCount.notLessThan(1);

        @Override
        @RO Boolean empty.get() = False; // always at least one Content on an Attribute
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected Int contentCount {
        @Override
        Int get() {
            // all child nodes are content nodes
            Int count = 0;
            Node? node = child_;
            while (node != Null) {
                ++count;
                node = node.next_;
            }
            return count;
        }

        @Override
        void set(Int newValue) {
            // value is not cached
        }
    }

    @Override
    protected (Node? prev, ContentNode? node) firstContent() {
        return Null, child_.as(ContentNode?);
    }

    @Override
    protected conditional Data oneDataChild() {
        if (Data node := child_.is(Data), node.next_ == Null) {
            return True, node;
        }
        return False;
    }

    @Override
    protected conditional Node allowsChild(Part part) = part.is(Content) ? super(part) : False;
}