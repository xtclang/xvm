/**
 * Represents internal details about a [Part] of an XML [Document].
 */
mixin Node
        into Part
        implements List<Part> {
    /**
     * `Node`s can be nested. A nested `Node` will have a non-`Null` parent `Node`.
     */
    protected Node!? parent_ = Null;

    /**
     * The next sibling `Node` after this `Node`, or `Null` if none.
     */
    protected Node!? next_ = Null;

    /**
     * The first `Node` that is nested under this `Node`, or `Null` if none.
     */
    protected Node!? child_ = Null;

    /**
     * Helper to link in a new child `Node`.
     *
     * @param prev  the `Node` preceding the new child `Node`
     * @param node  the new child `Node`
     */
    protected void link_(Node!? prev, Node! node) {
        if (prev == Null) {
            node.next_ = child_;
            child_     = node;
        } else {
            node.next_ = prev.next_;
            prev.next_ = node;
        }
        node.parent_ = this;
    }

    /**
     * Helper to unlink a child `Node`.
     *
     * @param prev  the `Node` preceding the child `Node` to unlink
     * @param node  the child `Node` to unlink
     */
    protected void unlink_(Node!? prev, Node! node) {
        if (prev == Null) {
            child_     = node.next_;
        } else {
            prev.next_ = node.next_;
        }
        node.next_   = Null;
        node.parent_ = Null;
    }

    /**
     * For a `Node` that is the result of parsing an XML document, this is the offset of the `Node`
     * within the original document. If the offset is unknown, then its value is `-1`.
     */
    protected Int offset_.get() = -1;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the length of the `Node`
     * within the original document. For some implementations of `Node`, it is expected that this
     * may be calculated on demand. If the length is unknown, then its value is `-1`.
     */
    protected Int length_.get() = -1;

    /**
     * Modification count, used to trigger cache invalidation, resynchronization, or an exception
     * for [ConcurrentModification].
     */
    protected UInt32 mods_ = 0;

    /**
     * Register a modification.
     */
    protected UInt32 mod() = ++mods_;

    /**
     * Check for any modification.
     *
     * @param expectedMods  a previous modification count
     *
     * @return `True` iff the data structure has been modified since the passed modification count
     *         was obtained
     */
    protected Boolean modified(Int expectedMods) = mods_ != expectedMods;

    /**
     * This method is called on each `Node` that is about to be frozen in place.
     */
    protected void prepareFreeze() {
        child_?.prepareFreeze();
        next_?.prepareFreeze();
    }

    // ----- List implementation -------------------------------------------------------------------

    @Override
    @RO List<Part> parts.get() = this;

    @Override
    @RO Boolean indexed.get() = False;

    @Override
    conditional Int knownSize() = empty ? (True, 0) : False;

    @Override
    @RO Int size.get() {
        Int   count = 0;
        Part? next  = child_;
        while (next != Null) {
            ++count;
            next = next.next_;
        }
        return count;
    }

    @Override
    @RO Boolean empty.get() = child_ == Null;

    @Override
    conditional Part first() {
        Part? first  = child_;
        return first == Null ? False : (True, first); // ... or: return first.is(Part);
    }

    @Override
    conditional Part last() {
        if (Node last ?= child_) {
            while (last ?= last.next_) {}
            return True, last;
        }
        return False;
    }

    @Override
    Iterator<Part> iterator() {
        return new Iterator() {
            private Node? node = child_;

            @Override
            conditional Part next() {
                if (Node prev ?= node) {
                    node = prev.next_;
                    return True, prev;
                }
                return False;
            }
        };
    }

    @Override
    Cursor cursor(Int index = 0) {
        CursorImpl cursor = new CursorImpl();
        if (index != 0) {
            cursor.index = index;
        }
        return cursor;
    }

    @Override
    @Op("[]") Part getElement(Index index) {
        assert:bounds index >= 0;
        Node? cur = child_;
        for (Int i = 0; i < index && cur != Null; ++i) {
            cur = cur.next_;
        }
        return cur.is(Part) ?: assert:bounds as $"Index {index} is out of range";
    }

    @Override
    @Op("[]=") void setElement(Index index, Part value) {
        (Boolean found, Int bounds, Node? prev, Node? node) = findNode(index);
        assert:bounds found as $"Index {index} out of bounds ({bounds})";
        replaceNode(index, prev, node, value);
    }

    @Override
    Boolean contains(Part value) = findNode(value);

    @Override
    conditional Int indexOf(Part value, Int startAt = 0) = findNode(value, startAt);

    @Override
    Node! add(Part part) {
        (_, Int index, Node? prev, Node? node) = findNode(Int.MaxValue);
        insertNode(index, prev, node, part);
        return this;
    }

    @Override
    Node! insert(Int index, Part part) {
        (Boolean found, Int bounds, Node? prev, Node? node) = findNode(index);
        assert:bounds index <= bounds as $"Index {index} out of bounds ({bounds})";
        insertNode(index, prev, node, part);
        return this;
    }

    @Override
    @Op("-") Node! remove(Part part) {
        (Boolean found, Int index, Node? prev, Node? node) = findNode(part);
        if (found) {
            deleteNode(index, prev, node);
        }
        return this;
    }

    @Override
    conditional Node! removeIfPresent(Part part) {
        (Boolean found, Int index, Node? prev, Node? node) = findNode(part);
        if (found) {
            deleteNode(index, prev, node);
            return True, this;
        }
        return False;
    }

    @Override
    Node! delete(Int index) {
        (Boolean found, _, Node? prev, Node? node) = findNode(index);
        if (found) {
            deleteNode(index, prev, node);
        }
        return this;
    }

    /**
     * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
     * needs to prevent or augment the mutation.
     */
    @Override
    Node! clear() {
        // unlink the list
        for (Node? node = child_; node != Null; ) {
            node.parent_ = Null;
            Node prev = node;
            node = node.next_;
            prev.next_ = Null;
        }

        // discard the head
        child_ = Null;

        mod();
        return this;
    }

    // ----- internal operations -------------------------------------------------------------------

    /**
     * Check if the specific `Part` is allowed to be a child of this `Node`, and if it is, produce
     * a `Node` that represents the `Part` (if the `Part` isn't already a `Node` that is ready to
     * add as a child to this `Node`.)
     *
     * Note: This can be overridden by `Node` implementations to restrict which child `Node` types
     * it may contain.
     *
     * @param part  the `Part` that is being added as a child to this `Node`
     *
     * @return `True` iff the `Part` can be added to this `Node` as a child `Node`
     * @return (conditional) the `Node` to add as a child
     */
    protected conditional Node! allowsChild(Part part) {
        return True, makeNode(part);
    }

    /**
     * Produce a `Node` that represents the `Part` (if the `Part` isn't already a `Node` that is
     * ready to add as a child to this `Node`.)
     *
     * @param part  a `Part` (which may be a `Node`) that is being added as a child to a `Node`
     *
     * @return the `Node` to use
     */
    protected static Node! makeNode(Part part) {
        if (part.is(Node) && part.parent_ == Null) {
            return part;
        }

        return switch (part.is(_)) {
            case xml.Element: new ElementNode(part);
            case Attribute:   new AttributeNode(part);
            case Data:        new @ContentNode Data(part);
            case CData:       new @ContentNode CData(part);
            case EntityRef:   new @ContentNode EntityRef(part);
            case Instruction: equalsCaseInsens(part.target, "xml") ? new XmlDeclNode(part) : new InstructionNode(part);
            case Comment:     new CommentNode(part);
            case Document:    new DocumentNode(part);
            default:          assert as $"Unsupported type: {&part.actualType}";
        };
    }

    /**
     * Internal operation: Replace a `Node` in the `List` with a `Node` representing the specified
     * `Part`.
     *
     * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
     * needs to prevent or augment the mutation.
     *
     * @param index  the index of the `Node` to replace
     * @param prev   the `Node` preceding the `Node` to replace
     * @param cur    the `Node` to replace
     * @param part   the `Part` to replace the specified `Node` with
     *
     * @return cur   the `Node` that was the result of the replacement
     * @return mods  the new mod count
     */
    protected (Node!? cur, UInt32 mods) replaceNode(Int index, Node!? prev, Node!? cur, Part part) {
        assert:arg Node node := allowsChild(part);
        node.parent_ = this;
        if (prev == Null) {
            child_ = node;
        } else {
            prev.next_ = node;
        }
        node.next_ = cur?.next_ : Null;

        cur?.parent_ = Null;
        cur?.next_   = Null;

        return node, mod();
    }

    /**
     * Internal operation: Insert a `Node` into the `List`.
     *
     * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
     * needs to prevent or augment the mutation.
     *
     * @param index  the index of the `Node` to insert
     * @param prev   the `Node` preceding the `Node` to insert
     * @param cur    the `Node` to insert in front of
     * @param part   the `Part` to insert
     *
     * @return cur   the `Node` that was inserted for the `Part`
     * @return mods  the new mod count
     */
    protected (Node!? cur, UInt32 mods) insertNode(Int index, Node!? prev, Node!? cur, Part part) {
        assert:arg Node node := allowsChild(part);
        node.parent_ = this;
        if (prev == Null) {
            child_ = node;
        } else {
            prev.next_ = node;
        }
        node.next_ = cur;
        return node, mod();
    }

    /**
     * Internal operation: Delete a `Node` from the `List`.
     *
     * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
     * needs to prevent or augment the mutation.
     *
     * @param index  the index of the `Node` to delete
     * @param prev   the `Node` preceding the `Node` to delete
     * @param cur    the `Node` to delete
     *
     * @return cur   the `Node` that followed the now-deleted `Node`
     * @return mods  the new mod count
     */
    protected (Node!? cur, UInt32 mods) deleteNode(Int index, Node!? prev, Node!? cur) {
        assert:bounds cur != Null as $"No Node exists at index {index}";
        assert:test cur.&parent_ == &this;
        Node? next = cur.next_;
        if (prev == Null) {
            assert:test &child_ == &cur;
            child_ = next;
        } else {
            assert:test prev.&next_ == &cur;
            prev.next_ = next;
        }
        cur.parent_ = Null;
        cur.next_   = Null;
        return next, mod();
    }

    /**
     * Advance to the specified index in the `List`.
     *
     * @param index  the `List` index to advance to
     *
     * @return found  `True` iff the specified index exists in the `List`
     * @return index  the index advanced to
     * @return prev   the `Node` located immediately before the specified index; otherwise, the last
     *                `Node` in the `List`
     * @return node   the `Node` at the specified index; otherwise, `Null`
     */
    (Boolean found, Int index, Node!? prev, Node!? node) findNode(Int index) {
        Node? prev = Null;
        Node? node = child_;
        for (Int i = 0; i < index; ++i) {
            if (node == Null) {
                // return an "insert at" location of the end of the list
                return False, i, prev, node;
            }
            prev = node;
            node = node.next_;
        }
        return node != Null, index, prev, node;
    }

    /**
     * Find the specified `Part` in the `List`, and return its location.
     *
     * @param part     the `Part` to search for
     * @param startAt  (optional) the index to start searching for the `Part` from
     *
     * @return found  `True` iff the `Part` was found
     * @return index  the index where the `Part` was found; otherwise, the index immediately beyond
     *                the end of the `List`
     * @return prev   the `Node` located immediately before the `Part` that was found; otherwise,
     *                the last `Node` in the `List`
     * @return node   the `Node` that is the `Part` that was found; otherwise, `Null`
     */
    (Boolean found, Int index, Node!? prev, Node!? node) findNode(Part part, Int startAt = 0) {
        Node? startPrev = Null;
        Node? startNode = child_;
        if (startAt > 0) {
            (Boolean found, Int index, startPrev, startNode) = findNode(startAt);
            if (!found) {
                return found, index, startPrev, startNode;
            }
        }

        // first, quick-scan for a matching reference
        Node? prev  = startPrev;
        Node? cur   = startNode;
        Int   index = startAt;
        while (cur != Null) {
            if (&cur == &part) {
                return True, index, prev, cur;
            }
            prev = cur;
            cur  = cur.next_;
            ++index;
        }

        // otherwise, slow-scan for a matching part using the equality test
        prev  = startPrev;
        cur   = startNode;
        index = startAt;
        while (cur != Null) {
            if (cur.as(Part) == part) {
                return True, index, prev, cur;
            }
            prev = cur;
            cur  = cur.next_;
            ++index;
        }

        // return an "insert at" location of the end of the list
        return False, index, prev, cur;
    }

    // ----- List Cursor implementation ------------------------------------------------------------

    protected class CursorImpl
            implements Cursor {
        // ----- constructors ------------------------------------------------------------------

        /**
         * Construct a `CursorImpl` that is sitting on the first item in the `List`.
         */
        construct() {
            index   = 0;
            prev    = Null;
            cur     = child_;
            lastMod = mods_;
        }

        // ----- properties --------------------------------------------------------------------

        /**
         * The `Node` before the current `Node`.
         */
        protected Node!? prev;
        /**
         * The current `Node`.
         */
        protected Node!? cur;
        /**
         * The last known mod count.
         */
        protected UInt32 lastMod;

        // ----- internal ----------------------------------------------------------------------

        void checkMod() {
            if (modified(lastMod)) {
                Int prevIndex = index;
                if (prevIndex == 0) {
                    // the cursor is already at the start; just reload the start
                    cur = child_;
                } else {
                    // force a rewind and then a fast-forward to where we already were
                    index = 0;
                    index = prevIndex;
                }
                lastMod = mods_;
            }
        }

        // ----- Cursor interface --------------------------------------------------------------

        @Override
        @RO Boolean bidirectional.get() = False;

        @Override
        Int index.set(Int newIndex) {
            checkMod();
            Int oldIndex = index;
            if (newIndex != oldIndex) {
                Node? newPrev;
                Node? newCur;
                if (newIndex > oldIndex) {
                    newPrev = prev;
                    newCur  = cur;
                } else {
                    // have to start at the head
                    oldIndex = 0;
                    newPrev  = Null;
                    newCur   = child_;
                }
                for (Int i = oldIndex; i < newIndex; ++i) {
                    newPrev = newCur;
                    newCur  = newCur?.next_;
                    assert:bounds newPrev != Null as $"Cursor index ({newIndex}) out of bounds";
                }
                prev = newPrev;
                cur  = newCur;
                super(index);
            }
        }

        @Override
        @RO Boolean exists.get() = cur != Null;

        @Override
        Part value {
            @Override
            Part get() {
                checkMod();
                return cur ?: assert:bounds;
            }

            @Override
            void set(Part value) {
                checkMod();
                (cur, lastMod) = replaceNode(index, prev, cur, value);
            }
        }

        @Override
        Boolean advance() {
            checkMod();
            if (cur == Null) {
                return False;
            }
            prev = cur;
            cur  = cur?.next_;
            return True;
        }

        @Override
        void insert(Part value) {
            checkMod();
            (cur, lastMod) = insertNode(index, prev, cur, value);
        }

        @Override
        void delete() {
            checkMod();
            (cur, lastMod) = deleteNode(index, prev, cur);
        }
    }
}