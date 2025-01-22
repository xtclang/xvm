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
     * For a `Node` that is the result of parsing an XML document, this is the offset of the `Node`
     * within the original document.
     */
    protected UInt32 offset_ = 0;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the length of the `Node`
     * within the original document. For some implementations of `Node`, it is expected that this
     * may be calculated on demand.
     */
    protected UInt32 length_ = 0;

    /**
     * This method is called on each `Node` that is about to be frozen in place.
     */
    protected void prepareFreeze() {
        next_?.prepareFreeze();
        child_?.prepareFreeze();
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
        return first == Null ? False : (True, first);
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
            conditional Element next() {
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
        Node? prev = Null;
        Node? cur  = child_;
        for (Int i = 0; i < index; ++i) {
            prev = cur;
            cur  = cur?.next_;
            assert:bounds prev != Null as $"Cursor index ({index}) out of bounds";
        }
        return new CursorImpl(index, prev, cur);    // TODO need a mod count to detect changes?
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
        TODO
    }

    @Override
    Boolean contains(Part value) = indexOf(value);

    @Override
    conditional Int indexOf(Part value, Int startAt = 0) {
        // TODO advance to startAt and remember that node (use it instead of child_ below)

        // first, quick-scan for a matching reference
        Node? cur   = child_;   // TODO not child_
        Int   index = startAt;
        while (cur != Null) {
            if (&cur == &value) {
                return True, index;
            }
            cur = cur.next_;
            ++index;
        }

        // otherwise, slow-scan for a matching value using the equality test
        cur   = child_;         // TODO not child_
        index = startAt;
        while (cur != Null) {
            if (cur.as(Part) == value) {
                return True, index;
            }
            cur = cur.next_;
            ++index;
        }

        return False;
    }

    @Override
    Node add(Part v) {
        TODO
    }

    @Override
    Node insert(Int index, Part value) {
        TODO
    }

    @Override
    @Op("-") Node remove(Part value) {
        TODO
    }

// TODO optimizations
//    @Op("-") Node removeAll(Iterable<Part> values) {
//    conditional Node removeIfPresent(Part value) {

    @Override
    Node delete(Int index) {
        TODO
    }

    /**
     * Note: This is a mutating operation, and must be overridden by any `Node` implementation that
     * needs to prevent or augment the mutation.
     */
    @Override
    Node clear() {
        // unlink the list
        for (Node? node = child_; node != Null; ) {
            node.parent_ = Null;
            Node prev = node;
            node = node.next_;
            prev.next_ = Null;
        }
        // discard the head
        child_ = Null;
        return this;
    }

    // ----- internal mutation operations ----------------------------------------------------------

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
     * @return the `Node` that followed the now-deleted `Node`
     */
    protected Node? deleteNode(Int index, Node? prev, Node? cur) {
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
        return next;
    }

    // ----- List Cursor implementation ------------------------------------------------------------

    protected class CursorImpl(Int index, Node? prev, Node? cur)
            implements Cursor {
        // ----- constructors ------------------------------------------------------------------

        /**
         * Construct a `CursorImpl` that is sitting on the first item in the `List`.
         *
         * @param first  the first `Node`/`Part` in the linked list
         */
        construct(Node? first) = construct CursorImpl(0, Null, first);

        // ----- properties --------------------------------------------------------------------

        /**
         * The `Node` before the current `Node`.
         */
        protected Node? prev;
        /**
         * The current `Node`.
         */
        protected Node? cur;

        // ----- Cursor interface --------------------------------------------------------------

        @Override
        @RO Boolean bidirectional.get() = False;

        @Override
        Int index.set(Int newIndex) {
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
                return cur.as(Part);
            }

            @Override
            void set(Part value) {
                TODO
            }
        }

        @Override
        Boolean advance() {
            if (cur == Null) {
                return False;
            }
            prev = cur;
            cur  = cur?.next_;
            return True;
        }

        @Override
        void insert(Part value) {
            TODO
        }

        @Override
        void delete() {
            TODO
        }
    }
}