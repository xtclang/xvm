/**
 * An iterator that issues an event on every "next" call.
 */
class PeekingIterator<Element>(Iterator<Element> iter, function void observe(Element))
        extends DelegatingIterator<Element>(iter) {

    protected/private function void observe(Element);

    @Override
    conditional Element next() {
        if (Element el := iter.next()) {
            observe(el);
            return True, el;
        }

        return False;
    }
}
