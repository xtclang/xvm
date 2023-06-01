/**
 * An Iterator that adds [Markable] functionality to another [Iterator].
 */
class MarkedIterator<Element>(Iterator<Element> that)
        implements Markable, Iterator<Element>, Closeable {
    /**
     * The underlying iterator.
     */
    protected/private Iterator<Element> that;

    /**
     * The buffer of marked elements.
     */
    protected/private Element[] buffer = new Element[];

    /**
     * The position of this iterator within the buffer.
     */
    protected/private Int position = 0;

    /**
     * The number of marks that are outstanding. This is the number of times that [mark()] has been
     * called without a corresponding call to [unmark()].
     */
    protected/private Int markCount = 0;

    /**
     * True iff the next item to iterate will come from the buffer.
     */
    protected Boolean useBuffer.get() {
        return position < buffer.size;
    }

    /**
     * True iff the iterator has been closed.
     */
    protected Boolean closed.get() {
        return position < 0;
    }


    // ----- Iterator ------------------------------------------------------------------------------

    @Override
    conditional Element next() {
        Int current  = position;
        Int buffered = buffer.size;
        if (current < buffered) {
            assert !closed;

            // the iterator is currently re-iterating over values that were previously buffered
            Element value = buffer[current++];
            if (current >= buffered && markCount == 0) {
                buffer.clear();
                position = 0;
            } else {
                position = current;
            }
            return True, value;
        }

        if (Element value := that.next()) {
            if (markCount > 0) {
                buffer.add(value);
                position = current + 1;
            }
            return True, value;
        } else {
            return False;
        }
    }


    // ----- Markable ------------------------------------------------------------------------------

    @Override
    immutable Object mark() {
        assert !closed;
        ++markCount;
        return position;
    }

    @Override
    void restore(immutable Object mark, Boolean unmark = False) {
        assert !closed;
        assert:arg mark.is(Int) && mark >= 0 && mark <= buffer.size;
        assert markCount > 0;
        position = mark;

        if (unmark) {
            this.unmark(mark);
        }
    }

    @Override
    void unmark(immutable Object mark) {
        if (!closed) {
            assert:arg mark.is(Int);
            assert markCount > 0;
            if (--markCount == 0 && !useBuffer) {
                buffer.clear();
                position = 0;
            }
        }
    }


    // ----- metadata ------------------------------------------------------------------------------

    @Override
    Boolean knownDistinct() {
        return that.knownDistinct();
    }

    @Override
    conditional Orderer knownOrder() {
        return that.knownOrder();
    }

    @Override
    Boolean knownEmpty() {
        return !useBuffer && that.knownEmpty();
    }

    @Override
    conditional Int knownSize() {
        if (Int count := that.knownSize()) {
            return True, count + buffer.size - position;
        }

        return False;
    }


    // ----- delegations ---------------------------------------------------------------------------

    @Override
    Element[] toArray(Array.Mutability? mutability = Null) {
        Element[] result;
        Element[] additional = that.toArray();

        if (markCount == 0) {
            // there's no way to "rewind" this iterator, so this operation can be implemented
            // destructively
            if (useBuffer) {
                if (position > 0) {
                    result = buffer[position ..< buffer.size].reify(mutability).addAll(additional);
                    buffer.clear();
                    position = 0;
                } else {
                    // steal the entire buffer, as is, as the basis for the result (and replace it
                    // so that any subsequent operations don't destroy the
                    result = buffer.addAll(additional);
                    buffer = new Element[];
                }
            } else {
                result = that.toArray();
            }
        } else {
            Int first = position;
            buffer.addAll(additional);
            Int last = buffer.size - 1;
            position = last + 1;
            result = first > last
                    ? []
                    : buffer[first .. last].reify(mutability);
        }

        return result.toArray(mutability, True);
    }

    @Override
    void close(Exception? cause = Null) {
        if (!closed) {
            markCount = 0;
            position  = -1;
            buffer.clear();

            Iterator<Element> that = this.that;
            if (that.is(Closeable)) {
                that.close(cause);
            }
        }
    }
}