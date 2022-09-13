/**
 * The OrderedMapSet is simple implementation of the [OrderedSet] interface that uses an underlying
 * [OrderedMap] instance as its storage.
 */
class OrderedMapSet<Element extends Orderable>
        extends MapSet<Element>(contents)
        implements OrderedSet<Element>
    {
    typedef CopyableMap<Element, Nullable>+OrderedMap<Element, Nullable> as CopyableOrderedMap;

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `MapSet` that provides `Set` capabilities by delegating to the specified `Map`.
     *
     * @param map   the Map to construct the `MapSet` on top of
     */
    construct(CopyableOrderedMap map)
        {
        super(map);
        }

    /**
     * [Duplicable] constructor.
     *
     * @param that  the [Duplicable] `MapSet` object to duplicate from
     */
    @Override
    construct(OrderedMapSet<Element> that)
        {
        super(that);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    protected CopyableOrderedMap contents;


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
    OrderedSet<Element> reify()
        {
        assert Orderer orderer := ordered();
        val result = new SkiplistSet<Element>(size, orderer);
        result.addAll(this);
        return result;
        }
    }