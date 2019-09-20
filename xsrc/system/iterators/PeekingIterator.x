/**
 * An iterator that issues an event on every "next" call.
 */
class PeekingIterator<Element>
        extends DelegatingIterator<Element>
    {
    construct(Iterator<Element> iter, function void accept(Element))
        {
        construct DelegatingIterator(iter);
        this.accept = accept;
        }

    protected/private function void accept(Element);

    @Override
    conditional Element next()
        {
        if (Element el := iter.next())
            {
            // TODO GG this line should not be required
            val accept = this.accept;
            accept(el);
            return True, el;
            }

        return False;
        }
    }
