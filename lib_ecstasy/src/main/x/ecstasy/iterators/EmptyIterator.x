/**
 * A scaffold implementation of an empty iterator.
 */
const EmptyIterator<Element>
        implements Iterator<Element>
        implements Markable {
    @Override
    conditional Element next() = False;

    @Override
    Element take() = assert;

    @Override
    Boolean whileEach(function Boolean process(Element)) = True;

    @Override
    conditional Element untilAny(function Boolean process(Element)) = False;

    @Override
    void forEach(function void (Element) process) {}

    @Override
    conditional Element min(Orderer? order = Null) = False;

    @Override
    conditional Element max(Orderer? order = Null) = False;

    @Override
    conditional Range<Element> range(Orderer? order = Null) = False;

    @Override
    Int count() = 0;

    @Override
    Element[] toArray(Array.Mutability? mutability = Null) {
        return mutability == Null
                ? []
                : new Array<Element>(mutability);
    }

    @Override
    Boolean knownDistinct() = True;

    @Override
    conditional Orderer knownOrder() = False;

    @Override
    Boolean knownEmpty() = True;

    @Override
    conditional Int knownSize() = (True, 0);

    @Override
    Iterator<Element> concat(Iterator<Element> that) = that;

    @Override
    Iterator<Element> filter(function Boolean(Element) include) = this;

    @Override
    <Result> Iterator<Result> map(function Result (Element) apply) {
        return Result == Element
                ? this
                : Result.emptyIterator;
    }

    @Override
    <Result> Iterator<Result> flatMap(function Iterable<Result> (Element) flatten) {
        return Result == Element
                ? this
                : Result.emptyIterator;
    }

    @Override
    Iterator<Element> dedup() = this;

    @Override
    Iterator<Element> sorted(Orderer? order = Null) = this;

    @Override
    Iterator<Element> reversed() = this;

    @Override
    Iterator<Element> observe(function void observe(Element)) = this;

    @Override
    Iterator<Element> skip(Int count) = this;

    @Override
    Iterator<Element> limit(Int count) = this;

    @Override
    Iterator<Element> extract(Interval<Int> interval) = this;

    @Override
    (Iterator<Element>, Iterator<Element>) bifurcate() = (this, this);

    @Override
    Element reduce(Element identity, function Element accumulate(Element, Element)) = identity;

    @Override
    conditional Element reduce(function Element accumulate(Element, Element)) = False;

    @Override
    Iterator<Element> + Markable ensureMarkable() = this;

    @Override
    immutable Object mark() = this;

    @Override
    void restore(immutable Object mark, Boolean unmark = False) {}
}
