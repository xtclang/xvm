/**
 * An `OrderedMap` is an extension to the [Map] interface that exposes capabilities that are
 * dependent on an ordering of the keys (or the entries) in the `Map`.
 *
 * The `OrderedMap` requires that the `Key` be [Orderable], even though in theory an external
 * [Orderer] can be provided that would remove the requirement for the `Key` to be `Orderable`.
 * This was necessary in order to support the [Sliceable] interface, which requires its endpoints
 * to be `Orderable`.
 */
interface OrderedMap<Key extends Orderable, Value>
        extends Map<Key, Value>
        extends Sliceable<Key> {

    @Override
    conditional Orderer ordered();

    /**
     * Obtain the first key in the `OrderedMap`.
     *
     * @return `True` iff the `Map` is not empty
     * @return (conditional) the first key in the `OrderedMap`
     */
    conditional Key first();

    /**
     * Obtain the last key in the `OrderedMap`.
     *
     * @return the `True` iff the `Map` is not empty
     * @return (conditional) the last key in the `OrderedMap`
     */
    conditional Key last();

    /**
     * Obtain the key that comes immediately after the specified key in the `Map`.
     *
     * @param key  a key that may _or may not be_ already present in the `Map`
     *
     * @return the `True` iff the `Map` is not empty and has a key that comes after the specified
     *         key
     * @return (conditional) the next key
     */
    conditional Key next(Key key);

    /**
     * Obtain the key that comes immediately before the specified key in the `Map`.
     *
     * @param key  a key that may _or may not be_ already present in the `Map`
     *
     * @return the `True` iff the `Map` is not empty and has a key that comes before the specified
     *         key
     * @return (conditional) the previous key
     */
    conditional Key prev(Key key);

    /**
     * Obtain the key that comes at or immediately after the specified key in the `Map`.
     *
     * @param key  a key that may _or may not be_ already present in the `Map`
     *
     * @return the `True` iff the `Map` is not empty and has a key that comes at or after the
     *         specified key
     * @return (conditional) the key that was passed in, if it exists in the `Map`, otherwise the
     *         [next] key
     */
    conditional Key ceiling(Key key);

    /**
     * Obtain the key that comes at or immediately before the specified key in the `Map`.
     *
     * @param key  a key that may _or may not be_ already present in the `Map`
     *
     * @return the `True` iff the `Map` is not empty and has a key that comes at or before the
     *         specified key
     * @return (conditional) the key that was passed in, if it exists in the `Map`, otherwise the
     *         [prev] key
     */
    conditional Key floor(Key key);

    @Override
    @RO OrderedSet<Key> keys;

    // ----- equality ------------------------------------------------------------------------------

    /**
     * Two `OrderedMap`s are equal iff they are they contain the same keys in the same order, and
     * the value associated with each key in the first `OrderedMap` is equal to the value associated
     * with the same key in the second `OrderedMap`.
     */
    static <CompileType extends OrderedMap> Boolean equals(CompileType map1, CompileType map2) {
        // some simple optimizations: two empty maps are equal, and two maps of different sizes are
        // not equal
        if (Int size1 := map1.knownSize(), Int size2 := map2.knownSize()) {
            if (size1 != size2) {
                return False;
            } else if (size1 == 0) {
                return True;
            }
        } else {
            switch (map1.empty, map2.empty) {
            case (False, False):
                break;

            case (False, True ):
            case (True , False):
                return False;

            case (True , True ):
                return True;
            }
        }

        // compare all of the entries in the two ordered maps, in the order that they appear
        typedef Entry<CompileType.Key, CompileType.Value> as MapEntry;
        using (Iterator<MapEntry> iter1 = map1.entries.iterator(),
               Iterator<MapEntry> iter2 = map2.entries.iterator()) {
            while (MapEntry entry1 := iter1.next()) {
                if (MapEntry entry2 := iter2.next()) {
                    if (entry1 != entry2) {
                        return False;
                    }
                } else {
                    return False;
                }
            }

            return !iter2.next();
        }
    }
}
