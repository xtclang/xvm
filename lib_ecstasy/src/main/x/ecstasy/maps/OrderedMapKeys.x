import collections.OrderedSetSlice;

/**
 * An implementation of the Set for the [Map.keys] property that delegates its operations to the
 * [Map].
 */
class OrderedMapKeys<Key extends Orderable, Value>(OrderedMap<Key, Value> contents)
        extends MapKeys<Key, Value>
        implements OrderedSet<Key> {
    // ----- constructors --------------------------------------------------------------------------

    construct(OrderedMap<Key, Value> contents) {
        construct MapKeys(contents);
    }

    // ----- internal ------------------------------------------------------------------------------

    @Override
    protected @RO OrderedMap<Key, Value> contents;

    // ----- OrderedSet interface ------------------------------------------------------------------

    @Override
    conditional Orderer ordered() = contents.ordered();

    @Override
    conditional Key first() = contents.first();

    @Override
    conditional Key last() = contents.last();

    @Override
    conditional Key next(Key element) = contents.next(element);

    @Override
    conditional Key prev(Key element) = contents.prev(element);

    @Override
    conditional Key ceiling(Key element) = contents.ceiling(element);

    @Override
    conditional Key floor(Key element) = contents.floor(element);

    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") OrderedSet<Key> slice(Range<Key> indexes) = new OrderedSetSlice(this, indexes);
}