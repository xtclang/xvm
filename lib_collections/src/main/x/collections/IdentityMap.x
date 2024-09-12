import ecstasy.iterators.EmptyIterator;

import ecstasy.maps.CopyableMap;
import ecstasy.maps.CursorEntry;
import ecstasy.maps.MapEntries;
import ecstasy.maps.MapKeys;
import ecstasy.maps.MapValues;

import ecstasy.reflect.Ref.Identity;


/**
 * A Map implementation that organizes its keys by [reference identity](Ref.Identity).
 */
class IdentityMap<Key, Value>
        implements Map<Key, Value>
        implements Replicable
        incorporates CopyableMap.ReplicableCopier<Key, Value>
        incorporates conditional IdentityMapFreezer<Key extends Shareable, Value extends Shareable> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new [IdentityMap].
     *
     * @param initCapacity  the number of expected entries
     */
    @Override
    construct(Int initCapacity = 0) {
        storage = new HashMap<Identity, Tuple<Key, Value>>(initCapacity);
    }

    /**
     * Construct a new [IdentityMap].
     *
     * @param map  the map to use to store the underlying identity information
     */
    construct(Replicable + Duplicable + Map<Identity, Tuple<Key, Value>> map) {
        storage = map;
    }

    @Override
    construct(IdentityMap that) {
        this.storage = that.storage.duplicate();
    }

    assert() {
        if (Key.is(Type<Shareable>) && Value.is(Type<Shareable>)) {
            assert storage.is(Shareable);
        }
    }

    // ----- properties ----------------------------------------------------------------------------

    private Replicable + Duplicable + Map<Identity, Tuple<Key, Value>> storage;

    // ----- Map interface -------------------------------------------------------------------------

    @Override
    @RO Int size.get() = storage.size;

    @Override
    @RO Boolean empty.get() = storage.empty;

    @Override
    conditional Value get(Key key) {
        if (val tuple := storage.get(&key.identity)) {
            return True, tuple[1];
        }

        return False;
    }

    @Override
    Boolean contains(Key key) {
        return storage.contains(&key.identity);
    }

    @Override
    Iterator<Entry<Key, Value>> iterator() {
        return empty
                ? Entry.as(Type<Entry<Key, Value>>).emptyIterator
                : new EntryIterator(storage.iterator());
    }

    @Override
    IdentityMap put(Key key, Value value) {
        storage.put(&key.identity, (key, value));
        return this;
    }

    @Override
    IdentityMap remove(Key key) {
        storage.remove(&key.identity);
        return this;
    }

    @Override
    IdentityMap clear() {
        storage.clear();
        return this;
    }

    @Override
    @Lazy public/private Set<Key> keys.calc() = new MapKeys<Key, Value>(this);

    @Override
    @Lazy public/private Collection<Value> values.calc() = new MapValues<Key, Value>(this);

    @Override
    @Lazy public/private Collection<Entry<Key, Value>> entries.calc() = new MapEntries<Key, Value>(this);

    // ----- EntryIterator -------------------------------------------------------------------------

    typedef Entry<Identity, Tuple<Key, Value>> as StorageEntry;

    /**
     * An iterator over the map's entries.
     */
    class EntryIterator(Iterator<StorageEntry> storageIterator)
            implements Iterator<Entry<Key, Value>> {

        @Override
        conditional Element next() {
            if (val storageEntry := storageIterator.next()) {
                return True, cursorEntry.advance(storageEntry.value[0]);
            }

            return False;
        }

        private CursorEntry<Key, Value> cursorEntry = new CursorEntry(this.Map);

        @Override
        Int count() = storageIterator.count();

        @Override
        Boolean knownDistinct() = storageIterator.knownDistinct();

        @Override
        Boolean knownEmpty() = storageIterator.knownEmpty();

        @Override
        conditional Int knownSize() = storageIterator.knownSize();

        @Override
        Iterator<Element> skip(Int count) {
            storageIterator = storageIterator.skip(count);
            return this;
        }

        @Override
        Iterator<Element> limit(Int count) {
            storageIterator = storageIterator.skip(count);
            return this;
        }

        @Override
        Iterator<Element> extract(Interval<Int> interval) {
            storageIterator = storageIterator.extract(interval);
            return this;
        }

        @Override
        (Iterator<Element>, Iterator<Element>) bifurcate() {
            (val iter1, val iter2) = storageIterator.bifurcate();
            this.storageIterator = iter1;
            return this, new EntryIterator(iter2);
        }
    }

    // ----- IdentityMapFreezer --------------------------------------------------------------------

    /**
     * Mixin that makes IdentityMap Freezable if both Key and Value are Shareable.
     */
    static mixin IdentityMapFreezer<Key extends Shareable, Value extends Shareable>
            into IdentityMap<Key, Value>
            implements Freezable {

        @Override
        immutable IdentityMapFreezer freeze(Boolean inPlace = False) {
            // don't freeze the map if it is already frozen
            if (this.is(immutable)) {
                return this;
            }

            // if the only thing not frozen is the map itself, then just make it immutable
            if (this.storage.is(immutable)) {
                return inPlace
                        ? makeImmutable()
                        : duplicate().makeImmutable();
            }

            // freeze the map in-place by freezing its storage
            if (inPlace && this.inPlace) {
                this.storage.as(Freezable).freeze(inPlace=True);
                return makeImmutable();
            }

            // otherwise, just duplicate, freezing the storage
            IdentityMapFreezer that = duplicate();
            that.storage.as(Freezable).freeze(inPlace=False);
            return that.makeImmutable();
        }
    }
}