import collections.OrderedSetSlice;

/**
 * An implementation of the Set for the [Map.keys] property that delegates its operations to the
 * [Map].
 */
class OrderedMapKeys<MapKey extends Orderable, MapValue>(OrderedMap<MapKey, MapValue> contents)
        extends MapKeys<MapKey, MapValue>
        implements OrderedSet<MapKey> {
    // ----- constructors --------------------------------------------------------------------------

    construct(OrderedMap<MapKey, MapValue> contents) {
        construct MapKeys(contents);
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected @RO OrderedMap<MapKey, MapValue> contents;

    // ----- OrderedSet interface ------------------------------------------------------------------

    @Override
    conditional Orderer ordered() = contents.ordered();

    @Override
    conditional MapKey first() = contents.first();

    @Override
    conditional MapKey last() = contents.last();

    @Override
    conditional MapKey next(MapKey element) = contents.next(element);

    @Override
    conditional MapKey prev(MapKey element) = contents.prev(element);

    @Override
    conditional MapKey ceiling(MapKey element) = contents.ceiling(element);

    @Override
    conditional MapKey floor(MapKey element) = contents.floor(element);

    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") OrderedSet<MapKey> slice(Range<MapKey> indexes) = new OrderedSetSlice(this, indexes);
}