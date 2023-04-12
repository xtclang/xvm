/**
 * The MapSet is simple implementation of the [Set] interface that uses an underlying [Map] instance
 * as its storage.
 *
 * TODO persistent mode
 */
class MapSet<Element>
        implements Duplicable
        implements Set<Element>
        incorporates conditional MapSetFreezer<Element extends Shareable>
        incorporates conditional SetHasher<Element extends Hashable>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `MapSet` that provides `Set` capabilities by delegating to the specified `Map`.
     *
     * @param map   the Map to construct the `MapSet` on top of
     */
    construct(CopyableMap<Element, Nullable> map)
        {
        assert map.inPlace;

        // since this implementation auto-incorporates a freezer if the Element is a Shareable type,
        // the underlying map must also be Freezable
        assert map.is(Freezable) || !Element.is(Type<Shareable>);

        contents = map;
        }

    /**
     * [Duplicable] constructor.
     *
     * @param that  the [Duplicable] `MapSet` object to duplicate from
     */
    @Override
    construct(MapSet that)
        {
        this.contents = that.contents.duplicate();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying Map.
     */
    protected CopyableMap<Element, Nullable> contents;


    // ----- read operations -----------------------------------------------------------------------

    @Override
    conditional Orderer? ordered()
        {
        return contents.keys.ordered();
        }

    @Override
    Boolean empty.get()
        {
        return contents.empty;
        }

    @Override
    Int size.get()
        {
        return contents.size;
        }

    @Override
    Iterator<Element> iterator()
        {
        return contents.keys.iterator();
        }

    @Override
    Boolean contains(Element value)
        {
        return contents.contains(value);
        }

    @Override
    Boolean containsAll(Collection<Element> values)
        {
        return contents.keys.containsAll(values);
        }

    @Override
    Element[] toArray(Array.Mutability? mutability = Null)
        {
        return contents.keys.toArray(mutability);
        }


    // ----- write operations ----------------------------------------------------------------------

    @Override
    @Op("+") MapSet add(Element value)
        {
        contents.putIfAbsent(value, Null);
        return this;
        }

    @Override
    @Op("-") MapSet remove(Element value)
        {
        contents.remove(value);
        return this;
        }

    @Override
    @Op("&") MapSet retainAll(Iterable<Element> values)
        {
        contents.keys.retainAll(values);
        return this;
        }

    @Override
    MapSet clear()
        {
        contents.clear();
        return this;
        }


    // ----- Freezable interface ---------------------------------------------------------------

    /**
     * Conditional Freezable implementation.
     */
    static mixin MapSetFreezer<Element extends Shareable>
            into MapSet<Element>
            implements Freezable
        {
        @Override
        immutable MapSetFreezer freeze(Boolean inPlace = False)
            {
            if (this.is(immutable MapSetFreezer))
                {
                return this;
                }

            if (inPlace)
                {
                contents = contents.as(Map<Element, Nullable> + Freezable).freeze(True);
                return makeImmutable();
                }

            // duplicate the MapSet, which must also duplicate the underlying Map
            MapSetFreezer<Element> that = this.duplicate();
            assert this.&contents != that.&contents;

            // since the underlying Map has been duplicated already, freeze its contents in place
            that.contents = that.contents.as(Freezable).freeze(inPlace=True);

            return that.makeImmutable();
            }
        }
    }