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
        TODO
    }

    @Override
    @Op("[]") Part getElement(Index index) {
        TODO
    }

    @Override
    @Op("[]=") void setElement(Index index, Part value) {
        TODO
    }

    @Override
    Boolean contains(Part value) {
        TODO
    }

    @Override
    conditional Int indexOf(Part value, Int startAt = 0) {
        TODO
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

    @Override
    Node clear() {
        TODO
    }
}