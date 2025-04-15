/**
 * An implementation of the [Element] interface using the [Node] framework. For a given XML
 * document, there are likely to be a huge number of [Element] instances, so this implementation is
 * optimized for space, while still attempting to provide high performance for expected uses.
 *
 * In addition to the state managed by the [Node] and [ValueHolderNode] base classes, the
 * `ElementNode` adds two fields for caching data: A 32-bit value for holding child counts, and a
 * reference to an append point (the last [AttributeNode], or the last `ElementNode`/[ContentNode])
 * within its linked list of children. This defrays the big-O cost for dealing with large XML
 * structures, such as performing append operations in a loop. While large XML structures are
 * relatively uncommon, many business applications exchange documents that have a handful of
 * "unbounded" XML elements which for example can grow to thousands (or even millions) of child
 * elements -- a serious big-O problem for a singly-linked-list-based data structure accessed only
 * via its head.
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

    /**
     * Create an `ElementNode` that contains the children provided in the passed linked list of
     * parsed nodes.
     *
     * @param firstNode  the first [Parsed] [Node] in the linked list of nodes
     */
    construct(String name, Parsed? firstNode) {
        this.name = name;
    } finally {
        Int attributeCount = 0;
        Int elementCount   = 0;
        Int contentCount   = 0;
        for (Node? node = firstNode; node != Null; node = node.next_) {
            node.parent_ = this;
            if (node.is(AttributeNode)) {
                assert elementCount == contentCount == 0;
                ++attributeCount;
            } else if (node.is(ElementNode)) {
                ++elementCount;
            } else if (node.is(ContentNode)) {
                ++contentCount;
            }
        }
        this.child_         = firstNode;
        this.attributeCount = attributeCount;
        this.elementCount   = elementCount;
        this.contentCount   = contentCount;
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
    String? value {
        @Override
        void set(String? newValue, Boolean cacheOnly = False) {
            if (cacheOnly) {
                super(newValue, cacheOnly);
            } else {
                String? oldValue = this.value;
                if (newValue != oldValue) {
                    if (oldValue != Null) {
                        Int contentCount = this.contentCount;
                        if (contentCount > 0) {
                            Node? prev = Null;
                            Node? node = child_;
                            // fast-skip attributes
                            Int attributeCount = this.attributeCount;
                            for (Int i : 0..<attributeCount) {
                                assert node.is(AttributeNode);
                                prev = node;
                                node = node.next_;
                            }
                            // remove all previous content parts
                            Boolean replaced  = False;
                            Int     remaining = contentCount;
                            while (remaining > 0) {
                                if (node.is(ContentNode)) {
                                    // if the first ContentNode is a Data part, then rewrite it
                                    // instead of removing it
                                    if (!replaced && newValue != Null && remaining == contentCount
                                            && node.is(Data)) {
                                        node.text = newValue;
                                        replaced  = True;
                                        prev      = node;
                                        node      = node.next_;
                                    } else {
                                        Node? next = node.next_;
                                        unlink_(prev, node);
                                        node = next;
                                    }
                                    --remaining;
                                } else {
                                    prev = node;
                                    node = node?.next_ : assert;
                                }
                            }
                            this.contentCount = replaced ? 1 : 0;
                        }
                    }
                    mod();
                    super(newValue, False);
                }
            }
        }
    }

    @Override
    @RO Int size.get() {
        Int contentCount = this.contentCount;
        Int totalCount   = attributeCount + contentCount + elementCount;
        if (contentCount == 0 && value != Null) {
            ++totalCount;
        }
        return totalCount;
    }

    @Override
    @RO Boolean empty.get() = child_ == Null && value == Null;

    @Override
    @RO List<Content> contents.get() = new ContentList(this);

    @Override
    @RO List<Attribute> attributes.get() = new AttributeList(this);

    @Override
    @RO Map<String, Attribute> attributesByName.get() = TODO new AttributeMap(this);

    @Override
    Attribute setAttribute(String name, String value) {
        Int   count = attributeCount;
        Node? prev  = Null;
        if (count > 0) {
            if (AttributeNode node := attributeByName(name)) {
                node.value = value;
                return node;
            }
            prev = trailing_;
        }
        AttributeNode node = new AttributeNode(this, name, value);
        link_(prev, node);
        attributeCount = count + 1;
        return node;
    }

    @Override
    @RO List<xml.Element> elements.get() = TODO new ElementList(this);

    @Override
    Element add(String name, String? value = Null) {
        // TODO
        TODO
    }

    // ----- Content List implementation -----------------------------------------------------------

    protected static class ContentList(ElementNode partList)
            extends ValueHolderNode.ContentList(partList) {
        @Override
        conditional Int knownSize() = (True, size);

        @Override
        @RO Int size.get() {
            Int count = partList.contentCount;
            if (count == 0 && partList.value != Null) {
                count = 1;
            }
            return count;
        }

        @Override
        @RO Boolean empty.get() = partList.contentCount == 0 && partList.value == Null;
    }

    // ----- Attribute List implementation ---------------------------------------------------------

    /**
     * An implementation of `List<Attribute>` that represents the attributes in an `ElementNode`.
     *
     * This class must be a static child class, because the `ElementNode` has a type parameter named
     * `Element` and this child class also has a different and conflicting type parameter named
     * `Element`.
     */
    protected static class AttributeList(ElementNode partList)
            implements List<Attribute> {
        @Override
        @RO Boolean indexed.get() = False;

        @Override
        @RO Int size.get() = partList.attributeCount;

        @Override
        conditional Attribute first() {
            Node? node = partList.child_;
            return node.is(AttributeNode);
        }

        @Override
        conditional Attribute last() {
            if (AttributeNode node := partList.trailing_.is(AttributeNode)) {
                return True, node;
            }

            Node? node = partList.child_;
            if (!node.is(AttributeNode)) {
                return False;
            }

            Node? next = node.as(private Node).next_;
            while (next.is(AttributeNode)) {
                node = next;
                next = node.as(protected Node).next_;
            }
            partList.trailing_ = node;
            return True, node;
        }

        @Override
        Iterator<Attribute> iterator() {
            return new Iterator() {
                private Node? node = partList.child_;

                @Override
                conditional Attribute next() {
                    if (Node prev := node.is(AttributeNode)) {
                        node = prev.next_;
                        return True, prev;
                    }
                    return False;
                }
            };
        }

        @Override
        Cursor cursor(Int index = 0) {
            TODO
// TODO
//            CursorImpl cursor = new CursorImpl();
//            if (index != 0) {
//                cursor.index = index;
//            }
//            return cursor;
        }

        @Override
        @Op("[]") Attribute getElement(Index index) {
            assert:bounds index >= 0;
            Node? cur = partList.child_;
            for (Int i = 0; i < index && cur != Null; ++i) {
                cur = cur.next_;
            }
            return cur.is(AttributeNode) ?: assert:bounds as $"Index {index} is out of range";
        }

        @Override
        @Op("[]=") void setElement(Index index, Attribute value) {
            (Boolean found, Int bounds, Node? prev, Node? node) = findNode(index);
            assert:bounds found as $"Index {index} out of bounds ({bounds})";
            partList.replaceNode(index, prev, node, value);
        }

        @Override
        Boolean contains(Attribute value) = findNode(value);

        @Override
        conditional Int indexOf(Attribute value, Int startAt = 0) = findNode(value, startAt);

        @Override
        AttributeList! add(Attribute attribute) {
            (_, Int index, Node? prev, Node? node) = findNode(Int.MaxValue);
            partList.insertNode(index, prev, node, attribute);
            return this;
        }

        @Override
        AttributeList! insert(Int index, Attribute attribute) {
            (Boolean found, Int bounds, Node? prev, Node? node) = findNode(index);
            assert:bounds index <= bounds as $"Index {index} out of bounds ({bounds})";
            partList.insertNode(index, prev, node, attribute);
            return this;
        }

        @Override
        @Op("-") AttributeList! remove(Attribute attribute) {
            (Boolean found, Int index, Node? prev, Node? node) = findNode(attribute);
            if (found) {
                partList.deleteNode(index, prev, node);
            }
            return this;
        }

        @Override
        conditional AttributeList! removeIfPresent(Attribute attribute) {
            (Boolean found, Int index, Node? prev, Node? node) = findNode(attribute);
            if (found) {
                partList.deleteNode(index, prev, node);
                return True, this;
            }
            return False;
        }

        @Override
        AttributeList! delete(Int index) {
            (Boolean found, _, Node? prev, Node? node) = findNode(index);
            if (found) {
                partList.deleteNode(index, prev, node);
            }
            return this;
        }

        /**
         * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
         * needs to prevent or augment the mutation.
         */
        @Override
        AttributeList! clear() {
            // unlink the list of parts up to the first non-attribute
            Node? node = partList.child_;
            while (node.is(AttributeNode)) {
                node.parent_ = Null;
                Node prev = node;
                node = node.next_;
                prev.next_ = Null;
            }
            partList.child_ = node;
            if (partList.trailing_.is(AttributeNode)) {
                partList.trailing_ = Null;
            }
            partList.attributeCount = 0;
            partList.mod();
            return this;
        }

        /**
         * Advance to the specified index in the `List`.
         *
         * @param index  the `List` index to advance to
         *
         * @return found  `True` iff the specified index exists in the `List`
         * @return index  the index advanced to
         * @return prev   the `Node` located immediately before the specified index; otherwise, the
         *                last `Node` in the `List`
         * @return node   the `Node` at the specified index; otherwise, `Null`
         */
        (Boolean found, Int index, AttributeNode!? prev, AttributeNode!? node) findNode(Int index) {
            Int count = partList.attributeCount;
            if (count == 0) {
                return False, 0, Null, Null;
            }

            if (index >= count) {
                // TODO position at last attribute
                Node prev = partList.trailing_ ?: assert;
                return False, count, prev.as(AttributeNode), Null;
            }

            AttributeNode? prev = Null;
            AttributeNode  node = partList.child_.as(AttributeNode);
            for (Int i = 0; i < index; ++i) {
                prev = node;
                node = node.next_.as(AttributeNode);
            }
            return True, index, prev, node;
        }

        /**
         * Find the specified `Attribute` in the `List`, and return its location.
         *
         * @param part     the `Attribute` to search for
         * @param startAt  (optional) the index to start searching for the `Attribute` from
         *
         * @return found  `True` iff the `Attribute` was found
         * @return index  the index where the `Attribute` was found; otherwise, the index
         *                immediately beyond the end of the `List`
         * @return prev   the `AttributeNode` located immediately before the `Attribute` that was
         *                found; otherwise, the last `AttributeNode` in the `List`
         * @return node   the `AttributeNode` that is the `Attribute` that was found; otherwise,
         *                `Null`
         */
        (Boolean found, Int index, AttributeNode!? prev, AttributeNode!? node)
                findNode(Attribute attribute, Int startAt = 0) {
            TODO
        }
    }

    // ----- Attribute Map implementation ----------------------------------------------------------

    /**
     * An implementation of `Map<String, Attribute>` that represents the attributes in an
     * `ElementNode`.
     */
    protected static class AttributeMap(ElementNode partList)
            implements Map<String, Attribute> {
        // TODO
    }

    // ----- Element List implementation ---------------------------------------------------------

    /**
     * An implementation of `List<Element>` that represents the child elements in an `ElementNode`.
     *
     * This class must be a static child class, because the `ElementNode` has a type parameter named
     * `Element` and this child class also has a different and conflicting type parameter named
     * `Element`.
     */
    protected static class ElementList(ElementNode partList)
            implements List<Element> {
        // TODO
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected conditional Node allowsChild(Part part) {
        return part.is(Element) || part.is(Attribute) || part.is(Content)
                ? super(part)
                : False;
    }

    /**
     * The last [AttributeNode], or the last non-`AttributeNode`, or `Null`. Used as a cached
     * starting point for doing inserts, appends, and searches. If this value is non-`Null`, then
     * it is the last node from the `AttributeNode` list, or it is the last child [Node] iff it is
     * an [ElementNode] or [ContentNode].
     */
    private Node? trailing_ = Null;

    /**
     * Bit-encoded counts. Contains the [contentCount], [attributeCount], and [elementCount].
     */
    private UInt32 counts_ = 0;

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
     * This method sets up the [trailing_] property if the end of the attribute list is reached.
     *
     * @param name  the [Attribute] name to search for
     *
     * @return `True` iff an [Attribute] of the specified name was found
     * @return (conditional) the [AttributeNode] with the specified name
     */
    /* TODO CP protected */ conditional AttributeNode attributeByName(String name) {
        if (attributeCount == 0) {
            return False;
        }

        // attributes precede all other child nodes, so just go until the node is not an attribute
        Node? prev = Null;
        Node? node = child_;
        while (node.is(AttributeNode)) {
            if (node.name == name) {
                return True, node;
            }

            prev = node;
            node = prev.next_;
        }

        // didn't find that name, but we can still cache the location of the last AttributeNode
        trailing_ = prev.as(AttributeNode);
        return False;
    }

    /**
     * Find the last [Attribute] [Node] child of this `ElementNode`.
     *
     * @return the last child [AttributeNode]; otherwise, `Null`
     */
    protected AttributeNode? lastAttribute() {
        Int count = attributeCount;
        if (count == 0) {
            return Null;
        }

        // if the cached "trailing_" node is an AttributeNode, then it is the last AttributeNode
        return trailing_.is(AttributeNode)?;

        Node? node = child_;
        while (--count > 0) {
            node = node?.next_ : assert;
        }
        return trailing_ <- node.as(AttributeNode);
    }

    /**
     * Find the first `ElementNode` child of this `ElementNode`.
     *
     * @return prev   the [Node] located immediately before the first `ElementNode`, or `Null` if
     *                the first `ElementNode` is the first `Node` or does not exist
     * @return node   the [Node] that is the first [Content] `Node`; otherwise, `Null`
     */
    protected (Node? prev, ElementNode? node) firstElement() {
        if (elementCount == 0) {
            // by default, the elements follow the attributes and content
            return lastNode(), Null;
        }

        Node? prev = lastAttribute();       // will be Null if there are no attributes
        Node? node = prev?.next_ : child_;
        while (!node.is(ElementNode)) {
            prev = node;
            node = node?.next_ : assert; // we know there are ElementNodes in the list!
        }
        return prev, node;
    }

    @Override
    protected (Node? prev, ContentNode? node) firstContent() {
        if (contentCount == 0) {
            // by default, the content follows the attributes
            return lastAttribute(), Null;
        }

        Node? prev = lastAttribute();       // will be Null if there are no attributes
        Node? node = prev?.next_ : child_;
        while (!node.is(ContentNode)) {
            prev = node;
            node = node?.next_ : assert; // we know there are ContentNodes in the list!
        }
        return prev, node;
    }

    /**
     * Find the last child [Node] of this `ElementNode`.
     *
     * @return the last child [Node]; otherwise, `Null`
     */
    protected Node? lastNode() {
        Node? node = trailing_ ?: child_;
        if (node == Null) {
            return Null;
        }

        Node? next = node.next_;
        while (next != Null) {
            node = next;
            next = node.next_;
        }
        return trailing_ <- node;
    }
}