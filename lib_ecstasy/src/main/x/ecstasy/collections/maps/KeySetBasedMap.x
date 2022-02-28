/**
 * An implementation of the [Map.entries] and [Map.values] API that delegates back to the map and
 * to the map's [Map.keys] set.
 */
mixin KeySetBasedMap<Key, Value>
        into Map<Key, Value>
    {
    @Override
    @Lazy public/private Collection<Map<Key, Value>.Entry> entries.calc()
        {
        return new KeyEntries();
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        return new KeyValues();
        }

    /**
     * An implementation of the Collection for the [Map.entries] property that delegates back to the map
     * and to the map's [Map.keys] set.
     */
    protected class KeyEntries
            implements Collection<Map.Entry>
            implements Freezable
        {
        Map<Key, Value> contents.get()
            {
            return outer;
            }

        @Override
        Int size.get()
            {
            return contents.size;
            }

        @Override
        Boolean empty.get()
            {
            return contents.empty;
            }

        @Override
        conditional Int knownSize()
            {
            return contents.keys.knownSize();
            }

        @Override
        Iterator<Map.Entry> iterator()
            {
            return new EntryIterator(contents.keys.iterator());
            }

        /**
         * Iterator that relies on an iterator of keys to produce a corresponding sequence of entries.
         * TODO GG if this class is inside the iterator() method, compiler emits error about type param
         */
        protected class EntryIterator(Iterator<Key> keyIterator)
                implements Iterator<Map.Entry>
            {
            @Override
            conditional Map.Entry next()
                {
                if (Key key := keyIterator.next())
                    {
                    private CursorEntry entry = new CursorEntry();
                    return True, entry.advance(key);
                    }

                return False;
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                return keyIterator.knownSize();
                }

            @Override
            (Iterator<Map.Entry>, Iterator<Map.Entry>) bifurcate()
                {
                (Iterator<Key> iter1, Iterator<Key> iter2) = keyIterator.bifurcate();
                return new EntryIterator(iter1), new EntryIterator(iter2);
                }
            }

        @Override
        KeyEntries remove(Map.Entry entry)
            {
            verifyInPlace();
            contents.remove(entry.key, entry.value);
            return this;
            }

        @Override
        (KeyEntries, Int) removeAll(
                function Boolean (Map.Entry) shouldRemove)
            {
            verifyInPlace();

            CursorEntry entry = new CursorEntry();
            (_, Int removed) = contents.keys.removeAll(key -> shouldRemove(entry.advance(key)));

            return this, removed;
            }

        @Override
        KeyEntries clear()
            {
            verifyInPlace();
            contents.clear();
            return this;
            }

        @Override
        immutable KeyEntries freeze(Boolean inPlace = False)
            {
            assert outer.is(immutable Map);
            return makeImmutable();
            }
        }

    /**
     * An implementation of the Collection for the [Map.values] property that delegates back
     * to the map and to the map's [Map.keys].
     */
    protected class KeyValues
            implements Collection<Value>
            implements Freezable
        {
        Map<Key, Value> contents.get()
            {
            return outer;
            }

        @Override
        Int size.get()
            {
            return contents.size;
            }

        @Override
        Boolean empty.get()
            {
            return contents.empty;
            }

        @Override
        conditional Int knownSize()
            {
            return contents.keys.knownSize();
            }

        @Override
        Iterator<Value> iterator()
            {
            return new ValueIterator(contents.keys.iterator());
            }

        /**
         * Iterator that relies on an iterator of keys to produce a corresponding sequence of values.
         * TODO GG if this class is inside the iterator() method, compiler emits errors like:
         *      COMPILER-145: Unresolvable type parameter(s): Value.
         */
        protected class ValueIterator(Iterator<Key> keyIterator)
                implements Iterator<Value>
            {
            @Override
            conditional Value next()
                {
                if (Key key := keyIterator.next())
                    {
                    return this.KeyValues.contents.get(key);
                    }

                return False;
                }

            @Override
            conditional Int knownSize()
                {
                return keyIterator.knownSize();
                }

            @Override
            (Iterator<Value>, Iterator<Value>) bifurcate()
                {
                (Iterator<Key> iter1, Iterator<Key> iter2) = keyIterator.bifurcate();
                return new ValueIterator(iter1), new ValueIterator(iter2);
                }
            }

        @Override
        KeyValues remove(Value value)
            {
            verifyInPlace();

            contents.keys.iterator().untilAny(key ->
                {
                if (Value test := contents.get(key))
                    {
                    if (test == value)
                        {
                        contents.remove(key);
                        return True;
                        }
                    }
                return False;
                });

            return this;
            }

        @Override
        (KeyValues, Int) removeAll(function Boolean (Value) shouldRemove)
            {
            verifyInPlace();

            (_, Int removed) = contents.keys.removeAll(key ->
                    {
                    assert Value value := contents.get(key);
                    return shouldRemove(value);
                    });
            return this, removed;
            }

        @Override
        KeyValues clear()
            {
            verifyInPlace();
            contents.clear();
            return this;
            }

        @Override
        immutable KeyValues freeze(Boolean inPlace = False)
            {
            assert outer.is(immutable Map);
            return makeImmutable();
            }
        }


    /**
     * An implementation of Entry that can be used as a cursor over any number of keys, and
     * delegates back to the map for its functionality.
     */
    protected class CursorEntry
            implements Map<Key, Value>.Entry
            incorporates KeyEntry<Key, Value>
        {
        construct()
            {
            construct KeyEntry();
            }

        construct(Key key)
            {
            construct KeyEntry(key);
            }


        // ----- Entry interface -----------------------------------------------------------------------

        @Override
        Map<Key, Value>.Entry reify()
            {
            return reifyEntry(key);
            }
        }


    // ----- internal helpers ----------------------------------------------------------------------

    /**
     * Instantiate a reified entry, which must be a child of the map.
     */
    private Entry reifyEntry(Key key)
        {
        return new @maps.KeyEntry(key) Entry() {};
        }

    /**
     * Some operations require that the containing Map be mutable; this method throws an exception
     * if the Map is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not mutable
     */
    protected Boolean verifyInPlace()
        {
        if (!inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace == True");
            }
        return True;
        }
    }
