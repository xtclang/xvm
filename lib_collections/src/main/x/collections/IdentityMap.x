
import ecstasy.collections.maps.EntryKeys;
import ecstasy.collections.maps.EntryValues;

import ecstasy.iterators.EmptyIterator;

import ecstasy.reflect.Ref.Identity;


/**
 * A Map implementation that organizes its keys by [reference identity](Ref.Identity).
 */
class IdentityMap<Key, Value>
        implements Map<Key, Value>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new [IdentityMap].
     *
     * @param initCapacity  the number of expected entries
     */
    construct(Int initCapacity = 0)
        {
        storage = new HashMap<Identity, Tuple<Key, Value>>(initCapacity);
        }

    /**
     * Construct a new [IdentityMap].
     *
     * @param map  the map to use to store the underlying identity information
     */
    construct(Map<Identity, Tuple<Key, Value>> map)
        {
        storage = map;
        }


    // ----- properties ----------------------------------------------------------------------------

    private Map<Identity, Tuple<Key, Value>> storage;


    // ----- Map interface -------------------------------------------------------------------------

    @Override
    @RO Int size.get()
        {
        return storage.size;
        }

    @Override
    @RO Boolean empty.get()
        {
        return storage.empty;
        }

    @Override
    conditional Value get(Key key)
        {
        if (val tuple := storage.get(&key.identity))
            {
            return True, tuple[1];
            }

        return False;
        }

    @Override
    Boolean contains(Key key)
        {
        return storage.contains(&key.identity);
        }

    @Override
    IdentityMap put(Key key, Value value)
        {
        storage.put(&key.identity, (key, value));
        return this;
        }

    @Override
    IdentityMap remove(Key key)
        {
        storage.remove(&key.identity);
        return this;
        }

    @Override
    IdentityMap clear()
        {
        storage.clear();
        return this;
        }

    @Override
    @Lazy public/private Set<Key> keys.calc()
        {
        EntryKeys<Key, Value> keys = new EntryKeys<Key, Value>(this);
        if (this.IdentityMap.is(immutable))
            {
            keys = keys.makeImmutable();
            }
        return keys;
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        EntryValues<Key, Value> values = new EntryValues<Key, Value>(this);
        if (this.IdentityMap.is(immutable))
            {
            values = values.makeImmutable();
            }
        return values;
        }

    @Override
    @Lazy public/private Collection<Map<Key,Value>.Entry> entries.calc()
        {
        EntrySet entries = new EntrySet();
        if (this.IdentityMap.is(immutable))
            {
            entries = entries.makeImmutable();
            }
        return entries;
        }


    // ----- EntrySet implementation ---------------------------------------------------------------

    typedef Map<Identity, Tuple<Key, Value>>.Entry as StorageEntry;

    /**
     * A representation of all of the HashEntry objects in the Map.
     */
    class EntrySet
            implements Collection<Entry>
        {
        @Override
        Int size.get()
            {
            return this.IdentityMap.size;
            }

        @Override
        Boolean contains(Entry entry)
            {
            if (Value value := this.IdentityMap.get(entry.key))
                {
                return value == entry.value;
                }

            return False;
            }

        @Override
        Collection<Entry> remove(Entry entry)
            {
            this.IdentityMap.remove(entry.key, entry.value);
            return this;
            }

        @Override
        Iterator<Entry> iterator()
            {
            return empty
                    ? Entry.as(Type<Entry>).emptyIterator
                    : new EntryIterator(storage.entries.iterator());
            }

        /**
         * An iterator over the map's entries.
         */
        class EntryIterator(Iterator<StorageEntry> storageIterator)
                implements Iterator<Entry>
            {
            @Override
            conditional Element next()
                {
                if (val storageEntry := storageIterator.next())
                    {
                    return True, cursorEntry.advance(storageEntry);
                    }

                return False;
                }

            private CursorEntry cursorEntry = new CursorEntry();

            @Override
            Int count()
                {
                return storageIterator.count();
                }

            @Override
            Boolean knownDistinct()
                {
                return storageIterator.knownDistinct();
                }

            @Override
            Boolean knownEmpty()
                {
                return storageIterator.knownEmpty();
                }

            @Override
            conditional Int knownSize()
                {
                return storageIterator.knownSize();
                }

            @Override
            Iterator<Element> skip(Int count)
                {
                storageIterator = storageIterator.skip(count);
                return this;
                }

            @Override
            Iterator<Element> limit(Int count)
                {
                storageIterator = storageIterator.skip(count);
                return this;
                }

            @Override
            Iterator<Element> extract(Interval<Int> interval)
                {
                storageIterator = storageIterator.extract(interval);
                return this;
                }

            @Override
            (Iterator<Element>, Iterator<Element>) bifurcate()
                {
                (val iter1, val iter2) = storageIterator.bifurcate();
                this.storageIterator = iter1;
                return this, new EntryIterator(iter2);
                }
            }
        }


    // ----- Entry implementation ------------------------------------------------------------------

    /**
     * An implementation of Entry that can be used as a cursor.
     */
    protected class CursorEntry
            implements Entry
        {
        protected/private @Unassigned StorageEntry storageEntry;

        protected/private Boolean reified;

        protected CursorEntry advance(StorageEntry storageEntry)
            {
            this.key          = storageEntry.value[0];
            this.storageEntry = storageEntry;
            return this;
            }

        @Override
        public/private @Unassigned Key key;

        @Override
        public Boolean exists.get()
            {
            return storageEntry.exists;
            }

        @Override
        Value value
            {
            @Override
            Value get()
                {
                return storageEntry.value[1];
                }

            @Override
            void set(Value value)
                {
                storageEntry.value = (key, value);
                }
            }

        @Override
        void delete()
            {
            storageEntry.delete();
            }

        @Override
        Entry reify()
            {
            if (reified)
                {
                return this;
                }

            CursorEntry entry = new CursorEntry().advance(storageEntry.reify());
            entry.reified = True;
            return entry;
            }
        }
    }