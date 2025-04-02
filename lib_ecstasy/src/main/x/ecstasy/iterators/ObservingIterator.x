/**
 * An iterator that issues an event on every "next" call.
 */
class ObservingIterator<Element>(Iterator<Element> iter, function void observer(Element))
        extends DelegatingIterator<Element>(iter) {

    protected/private function void observer(Element);

    @Override
    conditional Element next() {
        if (Element el := iter.next()) {
            observer(el);
            return True, el;
        }

        return False;
    }
}
