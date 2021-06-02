/**
 * The MapSet is simple implementation of the [Set] interface that uses an underlying [Map] instance
 * as its storage.
 *
 * TODO persistent mode
 */
class MapSet<Element>
        implements Set<Element>
        incorporates conditional MapSetFreezer<Element extends ImmutableAble>
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Map<Element, Nullable> map)
        {
        assert map.inPlace;
        assert map.is(Freezable) || !Element.is(Type<Freezable>);
        contents = map;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying Map.
     */
    protected Map<Element, Nullable> contents;


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
    static mixin MapSetFreezer<Element extends ImmutableAble>
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

            return new MapSet<Element>(contents.as(Map<Element, Nullable> + Freezable).freeze(False))
                    .makeImmutable();
            }
        }
    }
