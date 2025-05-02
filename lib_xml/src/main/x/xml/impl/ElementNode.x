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
     * @param name    the [Element]'s name
     * @param value   (optional) the [Element]'s value
     */
    construct(String name, String? value) {
        construct ValueHolderNode(name, value);
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
                        // TODO GG if (&contentCount.nonZero) {
                        // TODO GG if (contentCount_nonZero) {
                        if (!&contentCount.isZero()) {
                            Int contentCount = this.contentCount;
                            Node? prev = lastAttribute();
                            Node? node = prev?.next_ : Null;
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
        // we defer creating a `Part` for the `Content` represented by the `value` until we have to
        Int totalCount = attributeCount + elementCount;
        if (&contentCount.isZero()) {
            if (value != Null) {
                ++totalCount;
            }
        } else {
            totalCount += contentCount;
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
    @RO Map<String, Attribute> attributesByName.get() = new AttributeMap(this);

    @Override
    Attribute setAttribute(String name, String value) {
        Node? prev  = Null;
        if (!&attributeCount.isZero()) {
            if (AttributeNode node := attributeByName(name)) {
                node.value = value;
                return node;
            }
            // if the attribute was NOT found, then `trailing_` will be the last attribute (the node
            // before the insertion point)
            prev = trailing_;
        }
        AttributeNode node = new AttributeNode(name, value);
        link_(prev, node);
        &attributeCount.inc();
        mod();
        return trailing_ <- node;
    }

    @Override
    @RO List<xml.Element> elements.get() = new ElementList(this);

    @Override
    Element add(String name, String? value = Null) {
        val node = insertNode(size, lastNode(), Null, new ElementNode(name, value));
        return trailing_ <- node.as(ElementNode);
    }

    // ----- Content List implementation -----------------------------------------------------------

    protected static class ContentList(ElementNode partList)
            extends ValueHolderNode.ContentList(partList) {
        @Override
        conditional Int knownSize() = (True, size);

        @Override
        @RO Int size.get() {
            if (partList.&contentCount.isZero()) {
                return partList.value == Null ? 0 : 1;
            }
            return partList.contentCount;
        }

        @Override
        @RO Boolean empty.get() = partList.&contentCount.isZero() && partList.value == Null;
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
            if (AttributeNode node ?= partList.lastAttribute()) {
                return True, node;
            }
            return False;
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
                partList.deleteNode(index, prev, node ?: assert);
            }
            return this;
        }

        @Override
        conditional AttributeList! removeIfPresent(Attribute attribute) {
            (Boolean found, Int index, Node? prev, Node? node) = findNode(attribute);
            if (found) {
                partList.deleteNode(index, prev, node ?: assert);
                return True, this;
            }
            return False;
        }

        @Override
        AttributeList! delete(Int index) {
            (Boolean found, _, Node? prev, Node? node) = findNode(index);
            if (found) {
                partList.deleteNode(index, prev, node ?: assert);
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
                AttributeNode prev = partList.lastAttribute() ?: assert;
                return False, count, prev, Null;
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
         * @param attribute  the `Attribute` to search for
         * @param startAt    (optional) the index to start searching for the `Attribute` from
         *
         * @return found    `True` iff the `Attribute` was found
         * @return index    the index where the `Attribute` was found; otherwise, the index
         *                  immediately beyond the end of the `List`
         * @return prev     the `AttributeNode` located immediately before the `Attribute` that was
         *                  found; otherwise, the last `AttributeNode` in the `List`
         * @return node     the `AttributeNode` that is the `Attribute` that was found; otherwise,
         *                  `Null`
         */
        (Boolean found, Int index, AttributeNode!? prev, AttributeNode!? node)
                findNode(Attribute attribute, Int startAt = 0) {
            Int count = partList.attributeCount;
            if (count == 0) {
                return False, 0, Null, Null;
            }

            if (startAt >= count) {
                AttributeNode prev = partList.lastAttribute() ?: assert;
                return False, count, prev, Null;
            }

            Int            index = 0;
            AttributeNode? prev  = Null;
            AttributeNode  node  = partList.child_.as(AttributeNode);
            // fast-forward if necessary
            if (index < startAt) {
                Node almost = node;
                while (index < startAt-1) {
                    almost = almost.next_ ?: assert;
                    ++index;
                }
                prev = almost.as(AttributeNode);
                node = prev.next_.as(AttributeNode);
            }
            // scan remaining attributes for a match
            String name  = attribute.name;
            String value = attribute.value;
            while (True) {
                if (node.name == name && node.value == value) {
                    return True, index, prev, node;
                }

                // advance to the next attribute node (or quit if we're past the last one)
                if (++index >= count) {
                    partList.trailing_ = node;          // remember the last attribute node
                    return False, index, node, Null;
                }
                prev = node;
                node = node.next_.as(AttributeNode);
            }
        }
    }

    // ----- Attribute Map implementation ----------------------------------------------------------

    /**
     * An implementation of `Map<String, Attribute>` that represents the attributes in an
     * `ElementNode`.
     */
    protected static class AttributeMap(ElementNode partList)
            extends ecstasy.maps.KeyBasedMap<String, Attribute> {
        @Override
        conditional Int knownSize() = (True, size);

        @Override
        Int size.get() = partList.attributeCount;

        @Override
        @RO Boolean empty.get() = partList.&attributeCount.isZero();

        @Override
        conditional Value get(Key key) {
            if ((_, _, AttributeNode? node) := findNode(key), node != Null) {
                return True, node;
            }
            return False;
        }

        @Override
        protected Iterator<Key> keyIterator() {
            return new Iterator<Key>() {
                private AttributeNode? node    = partList.child_.is(AttributeNode) ?: Null;
                private UInt32         lastMod = partList.mods_;

                @Override
                conditional Key next() {
                    if (AttributeNode node ?= node) {
                        ElementNode partList = partList;
                        if (partList.modified(lastMod)) {
                            // verify that the next node is still present
                            if (node.&parent_ != &partList) {
                                throw new ConcurrentModification();
                            }
                            lastMod = partList.mods_;
                        }

                        Node prev = node;
                        this.node = prev.next_.is(AttributeNode) ?: Null;
                        iteratorAdvanced(prev);
                        return True, prev.name.as(Key);
                    }
                    return False;
                }
            };
        }

        @Override
        AttributeMap put(Key key, Value value) {
            (Boolean found, Int index, AttributeNode? prev, AttributeNode? node) = findNode(key);
            if (found) {
                assert node != Null;
                (_, lastMod) = partList.replaceNode(index, prev, node, value);
            } else {
                (_, lastMod) = partList.insertNode(index, prev, node, value);
            }
            return this;
        }

        @Override
        AttributeMap remove(Key key) {
            if ((Int index, AttributeNode? prev, AttributeNode? node) := findNode(key)) {
                (_, lastMod) = partList.deleteNode(index, prev, node ?: assert);
            }
            return this;
        }

        /**
         * The last known mod count.
         */
        protected UInt32 lastMod;
        /**
         * A cached "previous node".
         */
        protected AttributeNode? cachePrev;
        /**
         * A cached "current node".
         */
        protected AttributeNode? cacheNode;

        /**
         * Verify the cache.
         */
        protected void checkMod() {
            if (partList.modified(lastMod)) {
                ElementNode partList = partList;
                if (cachePrev?.&parent_ != &partList) {
                    cachePrev = Null;
                    cacheNode = Null;
                } else {
                    // make sure that the cached node is always the node that follow the cached prev
                    cacheNode = cachePrev?.next_?.is(AttributeNode)? : Null;
                }
                lastMod = partList.mods_;
            }
        }

        /**
         * @param node  the `Attribute` being emitted by an `Iterator` over this `AttributeMap`
         */
        protected void iteratorAdvanced(AttributeNode node) {
            checkMod();
            if (cacheNode?.&next_ == &node) {
                // the iterator is advancing
                cachePrev = cacheNode;
                cacheNode = node;
            } else if (partList.&child_ == &node) {
                // it's the first attribute being iterated
                cachePrev = Null;
                cacheNode = node;
            }
        }

        /**
         * Find the specified `Attribute` in the `Map`, and return its location.
         *
         * @param name     the `Attribute` name to search for
         *
         * @return found  `True` iff the `Attribute` was found
         * @return index  the index of the `Attribute` or `-1` if the index was not calculated
         * @return prev   the `AttributeNode` located immediately before the `Attribute` that was
         *                found; otherwise, the last `AttributeNode` in the `List`
         * @return node   the `AttributeNode` that is the `Attribute` that was found; otherwise,
         *                `Null`
         */
        (Boolean found, Int index, AttributeNode? prev, AttributeNode? node) findNode(String name) {
            if (partList.&attributeCount.isZero()) {
                return False, 0, Null, Null;
            }

            // check cache
            checkMod();
            if (cacheNode?.name == name) {
                return True, -1, cachePrev, cacheNode;
            }

            AttributeNode? prev  = Null;
            AttributeNode  node  = partList.child_.as(AttributeNode);
            Int            index = 0;
            Int            count = partList.attributeCount;
            while (True) {
                if (node.name == name) {
                    cachePrev = prev;
                    cacheNode = node;
                    return True, index, prev, node;
                }

                // advance to the next attribute node (or quit if we're past the last one)
                if (++index >= count) {
                    partList.trailing_ = node;          // remember the last attribute node
                    return False, index, node, Null;
                }
                prev = node;
                node = node.next_.as(AttributeNode);
            }
        }
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
        @Override
        @RO Boolean indexed.get() = False;

        @Override
        @RO Int size.get() = partList.elementCount;

        @Override
        conditional ElementNode first() {
            (_, ElementNode? node) = partList.firstElement();
            return node.is(ElementNode);
        }

        @Override
        conditional ElementNode last() {
            if (ElementNode node ?= partList.lastElement()) {
                return True, node;
            }
            return False;
        }

        @Override
        Iterator<Element> iterator() {
            return new Iterator() {
                private Node? node = partList.child_;

                @Override
                conditional Element next() {
                    if (Node prev := node.is(ElementNode)) {
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
        @Op("[]") Element getElement(Index index) {
            assert:bounds index >= 0;
            Node? cur = partList.child_;
            for (Int i = 0; i < index && cur != Null; ++i) {
                cur = cur.next_;
            }
            return cur.is(ElementNode) ?: assert:bounds as $"Index {index} is out of range";
        }

        @Override
        @Op("[]=") void setElement(Index index, Element value) {
            (Boolean found, Int bounds, Node? prev, Node? node) = findNode(index);
            assert:bounds found as $"Index {index} out of bounds ({bounds})";
            partList.replaceNode(index, prev, node, value);
        }

        @Override
        Boolean contains(Element value) = findNode(value);

        @Override
        conditional Int indexOf(Element value, Int startAt = 0) = findNode(value, startAt);

        @Override
        ElementList! add(Element element) {
            (_, Int index, Node? prev, Node? node) = findNode(Int.MaxValue);
            partList.insertNode(index, prev, node, element);
            return this;
        }

        @Override
        ElementList! insert(Int index, Element element) {
            (Boolean found, Int bounds, Node? prev, Node? node) = findNode(index);
            assert:bounds index <= bounds as $"Index {index} out of bounds ({bounds})";
            partList.insertNode(index, prev, node, element);
            return this;
        }

        @Override
        @Op("-") ElementList! remove(Element element) {
            (Boolean found, Int index, Node? prev, Node? node) = findNode(element);
            if (found) {
                partList.deleteNode(index, prev, node ?: assert);
            }
            return this;
        }

        @Override
        conditional ElementList! removeIfPresent(Element element) {
            (Boolean found, Int index, Node? prev, Node? node) = findNode(element);
            if (found) {
                partList.deleteNode(index, prev, node ?: assert);
                return True, this;
            }
            return False;
        }

        @Override
        ElementList! delete(Int index) {
            (Boolean found, _, Node? prev, Node? node) = findNode(index);
            if (found) {
                partList.deleteNode(index, prev, node ?: assert);
            }
            return this;
        }

        /**
         * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
         * needs to prevent or augment the mutation.
         */
        @Override
        ElementList! clear() {
            // unlink the list of parts up to the first non-element
            Node? node = partList.child_;
            while (node.is(ElementNode)) {
                node.parent_ = Null;
                Node prev = node;
                node = node.next_;
                prev.next_ = Null;
            }
            partList.child_ = node;
            if (partList.trailing_.is(ElementNode)) {
                partList.trailing_ = Null;
            }
            partList.elementCount = 0;
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
        (Boolean found, Int index, ElementNode!? prev, ElementNode!? node) findNode(Int index) {
            Int count = partList.elementCount;
            if (count == 0) {
                return False, 0, Null, Null;
            }

            if (index >= count) {
                ElementNode prev = last() ?: assert;
                return False, count, prev, Null;
            }

            ElementNode? prev = Null;
            ElementNode  node = partList.child_.as(ElementNode);
            for (Int i = 0; i < index; ++i) {
                prev = node;
                node = node.next_.as(ElementNode);
            }
            return True, index, prev, node;
        }

        /**
         * Find the specified `Element` in the `List`, and return its location.
         *
         * @param part     the `Element` to search for
         * @param startAt  (optional) the index to start searching for the `Element` from
         *
         * @return found  `True` iff the `Element` was found
         * @return index  the index where the `Element` was found; otherwise, the index
         *                immediately beyond the end of the `List`
         * @return prev   the `ElementNode` located immediately before the `Element` that was
         *                found; otherwise, the last `ElementNode` in the `List`
         * @return node   the `ElementNode` that is the `Element` that was found; otherwise,
         *                `Null`
         */
        (Boolean found, Int index, ElementNode!? prev, ElementNode!? node)
                findNode(Element element, Int startAt = 0) {
            Int count = partList.elementCount;
            if (count == 0) {
                return False, 0, Null, Null;
            }

            if (startAt >= count) {
                ElementNode prev = last() ?: assert;
                return False, count, prev, Null;
            }

            Int            index = 0;
            ElementNode? prev  = Null;
            ElementNode  node  = partList.child_.as(ElementNode);
            // fast-forward if necessary
            if (index < startAt) {
                Node almost = node;
                while (index < startAt-1) {
                    almost = almost.next_ ?: assert;
                    ++index;
                }
                prev = almost.as(ElementNode);
                node = prev.next_.as(ElementNode);
            }
            // scan remaining elements for a match
            String  name  = element.name;
            String? value = element.value;
            while (True) {
                if (node.name == name && node.value == value) {
                    return True, index, prev, node;
                }

                // advance to the next element node (or quit if we're past the last one)
                if (++index >= count) {
                    partList.trailing_ = node;          // remember the last element node
                    return False, index, node, Null;
                }
                prev = node;
                node = node.next_.as(ElementNode);
            }
        }
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

    // TODO GG couldn't get this to work either
    // Boolean contentCount_nonZero.get() {
    //     return counts_ & &contentCount.Mask != 0;
    //     return counts_ & ElementNode.contentCount.Mask != 0;  // <- bad exception from this option
    // }

    @Override
    /* TODO GG protected */ Int contentCount {
        static Int    Bits  = 5;
        static UInt32 Ones  = (1 << Bits) - 1;
        static Int    Shift = 0;
        static UInt32 Mask  = Ones << Shift;

        // TODO GG
        // Boolean nonZero.get() = counts_ & Mask != 0;

        @Override
        Int get() {
            Int count = counts_ & Mask;
            if (count == Ones) {
                // TODO CP optimize
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

        /**
         * @return `True` iff the value is zero.
         */
        Boolean isZero() = counts_ & Mask == 0;

        /**
         * @return `True` iff the value is maxed aka overflowed.
         */
        Boolean isMaxed() = counts_ & Mask == Mask;

        /**
         * Increment the value.
         */
        void inc() {
            if (!isMaxed()) {
                set(get()+1);
            }
        }

        /**
         * Decrement the value.
         */
        void dec() {
            assert !isZero();
            if (!isMaxed()) {
                set(get()-1);
            }
        }
    }

    /**
     * The number of [Attribute] child [Node]s this `ElementNode` contains.
     */
    protected Int attributeCount {
        static Int    Bits  = 11;
        static UInt32 Ones  = (1 << Bits) - 1;
        static Int    Shift = contentCount.Bits;
        static UInt32 Mask  = Ones << Shift;

        @Override
        Int get() {
            Int count = counts_ & Mask >>> Shift;
            if (count == Ones) {
                count = 0;
                Node? node = child_;
                AttributeNode? last = Null;
                while (node.is(AttributeNode)) {
                    ++count;
                    last = node;
                }
                if (count < Ones) {
                    set(count);
                }
                trailing_ = last?;
            }
            return count;
        }

        @Override
        void set(Int newValue) {
            assert newValue >= 0;
            counts_ = counts_ & ~Mask | (newValue.notGreaterThan(Ones).toUInt32() << Shift);
        }

        /**
         * @return `True` iff the value is zero.
         */
        Boolean isZero() = counts_ & Mask == 0;

        /**
         * @return `True` iff the value is maxed aka overflowed.
         */
        Boolean isMaxed() = counts_ & Mask == Mask;

        /**
         * Increment the value.
         */
        void inc() {
            if (!isMaxed()) {
                set(get()+1);
            }
        }

        /**
         * Decrement the value.
         */
        void dec() {
            assert !isZero();
            if (!isMaxed()) {
                set(get()-1);
            }
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

        /**
         * @return `True` iff the value is zero.
         */
        Boolean isZero() = counts_ & Mask == 0;

        /**
         * @return `True` iff the value is maxed aka overflowed.
         */
        Boolean isMaxed() = counts_ & Mask == Mask;

        /**
         * Increment the value.
         */
        void inc() {
            if (!isMaxed()) {
                set(get()+1);
            }
        }

        /**
         * Decrement the value.
         */
        void dec() {
            assert !isZero();
            if (!isMaxed()) {
                set(get()-1);
            }
        }
    }

    @Override
    protected (Node cur, UInt32 mods) replaceNode(Int index, Node? prev, Node? cur, Part part) {
        (Node result, UInt32 mods) = super(index, prev, cur, part);
        Boolean sameType = False;
        switch (cur.is(_), result.is(_)) {
            case (ElementNode,   AttributeNode):
                &elementCount.dec();
                &attributeCount.inc();
                break;
            case (ElementNode,   ContentNode  ):
                &elementCount.dec();
                &contentCount.inc();
                break;
            case (AttributeNode, ElementNode  ):
                &attributeCount.dec();
                &elementCount.inc();
                break;
            case (AttributeNode, ContentNode  ):
                &attributeCount.dec();
                &contentCount.inc();
                break;
            case (ContentNode,   ElementNode  ):
                &contentCount.dec();
                &elementCount.inc();
                break;
            case (ContentNode,   AttributeNode):
                &contentCount.dec();
                &attributeCount.inc();
                break;
            default:
                sameType = True;
                break;
        }
        if (&trailing_ == &cur) {
            trailing_ = sameType ? result : Null;
        }
        return result, mods;
    }

    @Override
    protected (Node cur, UInt32 mods) insertNode(Int index, Node? prev, Node? cur, Part part) {
        (Node result, UInt32 mods) = super(index, prev, cur, part);
        if (result.is(ElementNode)) {
            &elementCount.inc();
            if (result.next_ == Null) {
                trailing_ = result;
            } else if (trailing_.is(ElementNode)) {
                trailing_ = Null;
            }
        } else if (result.is(AttributeNode)) {
            if (index == attributeCount++) {
                trailing_ = result;
            }
        } else {
            &contentCount.inc();
            if (result.next_ == Null) {
                trailing_ = result;
            } else if (trailing_.is(ContentNode)) {
                trailing_ = Null;
            }
        }
        return result, mods;
    }

    @Override
    protected (Node? cur, UInt32 mods) deleteNode(Int index, Node? prev, Node cur) {
        (Node? result, UInt32 mods) = super(index, prev, cur);
        if (cur.is(ElementNode)) {
            &elementCount.dec();
        } else if (cur.is(AttributeNode)) {
            &attributeCount.dec();
        } else {
            &contentCount.dec();
        }
        if (&cur == &trailing_) {
            trailing_ = Null;
        }
        return result, mods;
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
        if (&attributeCount.isZero()) {
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
        trailing_ = prev;
        return False;
    }

    /**
     * Find the last [Attribute] [Node] child of this `ElementNode`.
     *
     * @return the last child [AttributeNode]; otherwise, `Null`
     */
    protected AttributeNode? lastAttribute() {
        if (&attributeCount.isZero()) {
            return Null;
        }

        // if the cached "trailing_" node is an AttributeNode, then it is the last AttributeNode
        return trailing_.is(AttributeNode)?;

        Node? node  = child_;
        Int   count = attributeCount;
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
        if (&elementCount.isZero()) {
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

    /**
     * Find the last [Element] [Node] child of this `ElementNode`.
     *
     * @return the last child [ElementNode]; otherwise, `Null`
     */
    protected ElementNode? lastElement() {
        Int count = elementCount;
        if (count == 0) {
            return Null;
        }

        // if the cached "trailing_" node is an ElementNode, then it is the last ElementNode
        return trailing_.is(ElementNode)?;

        Node node = firstElement() ?: assert;
        while (True) {
            if (node.is(ElementNode), --count == 0) {
                return trailing_ <- node;
            }
            node = node.next_ ?: assert;
        }
    }

    @Override
    protected (Node? prev, ContentNode? node) firstContent() {
        if (&contentCount.isZero()) {
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