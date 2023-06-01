import collections.ListMap;

/**
 * An iterator that only iterates a sub-set of the underlying iterator, as defined by a filter.
 */
class DistinctIterator<Element>
        extends DelegatingIterator<Element> {

    construct(Iterator<Element> iter) {
        construct DelegatingIterator(iter);
    }

    private ListMap<Element, Nullable> previous = new ListMap();   // TODO CP switch to ListSet

    @Override
    conditional Element next() {
        while (Element el := iter.next()) {
            if (previous.putIfAbsent(el, Null)) {
                return True, el;
            }
        }

        return False;
    }

    @Override
    Boolean knownDistinct() {
        return True;
    }

    @Override
    conditional Int knownSize() {
        if (Int size := iter.knownSize(), size == 0) {
            return True, 0;
        }

        return False;
    }
}
