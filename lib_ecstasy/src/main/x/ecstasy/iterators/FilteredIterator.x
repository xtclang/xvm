/**
 * An iterator that only iterates a sub-set of the underlying iterator, as defined by a filter.
 */
class FilteredIterator<Element>
        extends DelegatingIterator<Element> {

    construct(Iterator<Element> iter, function Boolean include(Element)) {
        this.include = include;
        construct DelegatingIterator(iter);
    }

    protected/private function Boolean include(Element);

    @Override
    conditional Element next() {
        while (Element el := iter.next()) {
            if (include(el)) {
                return True, el;
            }
        }
        return False;
    }

    @Override
    conditional Int knownSize() {
        if (Int size := iter.knownSize(), size == 0) {
            return True, 0;
        }
        return False;
    }
}