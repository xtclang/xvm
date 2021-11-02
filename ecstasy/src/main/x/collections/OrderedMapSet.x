/**
 * The OrderedMapSet is simple implementation of the [OrderedSet] interface that uses an underlying
 * [OrderedMap] instance as its storage.
 */
@Concurrent
class OrderedMapSet<Element extends Orderable>(OrderedMap<Element, Nullable> contents)
        extends MapSet<Element>(contents)
        implements OrderedSet<Element>
    {
    // ----- properties ----------------------------------------------------------------------------

    @Override
    protected OrderedMap<Element, Nullable> contents;


    // ----- read operations -----------------------------------------------------------------------

    @Override
    conditional Orderer ordered()
        {
        return contents.ordered();
        }

    @Override
    conditional Element first()
        {
        return contents.first();
        }

    @Override
    conditional Element last()
        {
        return contents.last();
        }

    @Override
    conditional Element next(Element element)
        {
        return contents.next(element);
        }

    @Override
    conditional Element prev(Element element)
        {
        return contents.prev(element);
        }

    @Override
    conditional Element ceiling(Element element)
        {
        return contents.ceiling(element);
        }

    @Override
    conditional Element floor(Element element)
        {
        return contents.floor(element);
        }

    @Override
    @Op("[..]") OrderedSet<Element> slice(Range<Element> keys)
        {
        return contents.slice(keys).keys;
        }

    @Override
    @Op("[[..]]") OrderedSet<Element> sliceInclusive(Range<Element> keys)
        {
        return contents.sliceInclusive(keys).keys;
        }

    @Override
    @Op("[[..)]") OrderedSet<Element> sliceExclusive(Range<Element> keys)
        {
        return contents.sliceExclusive(keys).keys;
        }

    @Override
    OrderedSet<Element> reify()
        {
        assert Orderer orderer := ordered();
        val result = new SkiplistSet<Element>(size, orderer);
        result.addAll(this);
        return result;
        }
    }
