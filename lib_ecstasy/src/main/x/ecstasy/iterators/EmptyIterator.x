/**
 * A scaffold implementation of an empty iterator.
 */
const EmptyIterator<Element>
        implements Iterator<Element>
        implements Markable
    {
    @Override
    conditional Element next()
        {
        return False;
        }

    @Override
    Element take()
        {
        assert;
        }

    @Override
    Boolean whileEach(function Boolean process(Element))
        {
        return True;
        }

    @Override
    conditional Element untilAny(function Boolean process(Element))
        {
        return False;
        }

    @Override
    void forEach(function void (Element) process)
        {
        }

    @Override
    conditional Element min(Orderer? order = Null)
        {
        return False;
        }

    @Override
    conditional Element max(Orderer? order = Null)
        {
        return False;
        }

    @Override
    conditional Range<Element> range(Orderer? order = Null)
        {
        return False;
        }

    @Override
    Int count()
        {
        return 0;
        }

    @Override
    Element[] toArray(Array.Mutability? mutability = Null)
        {
        return mutability == Null
                ? []
                : new Array<Element>(mutability);
        }

    @Override
    Boolean knownDistinct()
        {
        return True;
        }

    @Override
    conditional Orderer knownOrder()
        {
        return False;
        }

    @Override
    Boolean knownEmpty()
        {
        return True;
        }

    @Override
    conditional Int knownSize()
        {
        return True, 0;
        }

    @Override
    Iterator<Element> concat(Iterator<Element> that)
        {
        return that;
        }

    @Override
    Iterator<Element> filter(function Boolean (Element) include)
        {
        return this;
        }

    @Override
    <Result> Iterator<Result> map(function Result (Element) apply)
        {
        return Result == Element
                ? this
                : new EmptyIterator<Result>();
        }

    @Override
    <Result> Iterator<Result> flatMap(function Iterator<Result> (Element) flatten)
        {
        return Result == Element
                ? this
                : new EmptyIterator<Result>();
        }

    @Override
    Iterator<Element> dedup()
        {
        return this;
        }

    @Override
    Iterator<Element> sorted(Orderer? order = Null)
        {
        return this;
        }

    @Override
    Iterator<Element> reversed()
        {
        return this;
        }

    @Override
    Iterator<Element> peek(function void observe(Element))
        {
        return this;
        }

    @Override
    Iterator<Element> skip(Int count)
        {
        return this;
        }

    @Override
    Iterator<Element> limit(Int count)
        {
        return this;
        }

    @Override
    Iterator<Element> extract(Interval<Int> interval)
        {
        return this;
        }

    @Override
    (Iterator<Element>, Iterator<Element>) bifurcate()
        {
        return this, this;
        }

    @Override
    Element reduce(Element identity, function Element accumulate(Element, Element))
        {
        return identity;
        }

    @Override
    conditional Element reduce(function Element accumulate(Element, Element))
        {
        return False;
        }

    @Override
    Iterator<Element> + Markable ensureMarkable()
        {
        return this;
        }

    @Override
    immutable Object mark()
        {
        return this;
        }

    @Override
    void restore(immutable Object mark, Boolean unmark = False)
        {
        }
    }
