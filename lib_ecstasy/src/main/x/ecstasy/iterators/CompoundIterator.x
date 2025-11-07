/**
 * An iterator that delegates to a sequence of iterators, as if they were together a single
 * iterator.
 */
class CompoundIterator<Element>
        implements Iterator<Element> {

    construct(Iterator<Element> iter1, Iterator<Element> iter2) {
        iter = iter1;
        tail = iter2;
    }

    protected/private Iterator<Element>  iter;
    protected/private Iterator<Element>? tail;

    /**
     * Add an iterator to the end of this iterator.
     *
     * @param iterator  the Iterator to add
     */
    protected void add(Iterator<Element> iter) {
        if (Iterator<Element> tail ?= tail) {
            if (tail.is(CompoundIterator)) {
                tail.add(iter);
            } else {
                this.tail = new CompoundIterator(tail, iter);
            }
        } else {
            tail = iter;
        }
    }

    @Override
    conditional Element next() {
        if (Element el := iter.next()) {
            return True, el;
        }

        if (Iterator<Element> tail ?= tail) {
            this.iter = tail;
            this.tail = Null;
            return iter.next();
        }

        return False;
    }

    @Override
    Boolean knownDistinct() = tail == Null && iter.knownDistinct();

    @Override
    conditional Orderer knownOrder() = tail == Null ? iter.knownOrder() : False;

    @Override
    Boolean knownEmpty() = iter.knownEmpty() && (tail?.knownEmpty() : True);

    @Override
    conditional Int knownSize() {
        if (Int iterSize := iter.knownSize()) {
            if (Iterator<Element> tail ?= tail) {
                if (Int tailSize := tail.knownSize()) {
                    return True, iterSize + tailSize;
                }
            } else {
                return True, iterSize;
            }
        }
        return False;
    }
}
