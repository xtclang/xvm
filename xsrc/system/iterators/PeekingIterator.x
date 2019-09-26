/**
 * An iterator that issues an event on every "next" call.
 */
class PeekingIterator<Element>
        extends DelegatingIterator<Element>
// TODO GG - VERIFY-77: The property "iter" on "Ecstasy:iterators.DelegatingIterator" attempts to declare a Var property, but the setter on the base is private.
// class PeekingIterator<Element>(Iterator<Element> iter, function void observe(Element))
//         extends DelegatingIterator<Element>(iter)
    {
    construct(Iterator<Element> iter, function void observe(Element))
        {
        this.observe = observe;
        construct DelegatingIterator(iter);
        }

    protected/private function void observe(Element);

    @Override
    conditional Element next()
        {
        if (Element el := iter.next())
            {
            observe(el);
            return True, el;
            }

        return False;
        }
    }
