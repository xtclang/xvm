/**
 * KeyBasedMap is an abstract implementation of a Map that is key-centric, and implements .
 */
class KeyBasedMap<KeyType, ValueType>
        implements Map<KeyType, ValueType>
    {
    @Override
    @RO MutabilityConstraint mutability;

    @Override
    @RO Int size.get()
        {
        return keys.size;
        }

    @Override
    @RO Boolean empty.get()
        {
        return keys.empty;
        }

    @Override
    Entry entryFor(KeyType key)
        {
        return new ReifiedEntry(this, key);
        }

    @Override
    conditional ValueType get(KeyType key);

    @Override
    @RO Set<KeyType> keys;

    @Override
    @Lazy Collection<ValueType> values.calc()
        {
        return new ValuesCollection();
        }

    @Override
    @Lazy Set<Entry> entries.calc()
        {
        return new EntrySet();
        }

    @Override
    Map put(KeyType key, ValueType value);

    @Override
    Map remove(KeyType key)
        {
        keys.remove(key);
        }

    @Override
    Map clear();

    @Override
    <ResultType> ResultType process(KeyType key, function ResultType (Entry) compute);


    // ----- entries set implementations -----------------------------------------------------------

    /**
     * An implementation of the Set for the [entries] property that delegates back to the map and
     * to the map's [keys].
     */
    class EntrySet
            implements Set<Entry>
        {
        @Override
        Int size.get()
            {
            return Map.this.size;
            }

        @Override
        Boolean empty.get()
            {
            return Map.this.empty;
            }

        @Override
        Iterator<Entry> iterator()
            {
            return new Iterator()
                {
                Iterator<KeyType> keyIterator = Map.this.keys.iterator(); // TODO verify this is a private prop

                @Override
                conditional Entry next()
                    {
                    if (KeyType key : keyIterator.next())
                        {
                        private CursorEntry entry = new CursorEntry(key);
                        return true, entry.advance(key);
                        }

                    return false;
                    }
                };
            }

        @Override
        conditional EntrySet remove(Entry entry)
            {
            // value is an Entry; remove the requested entry from the map only if the specified
            // entry's key/value pair exists in the map
            if (ValueType value : Map.this.get(entry.key))
                {
                if (value == entry.value)
                    {
                    Map newMap = Map.this.remove(entry.key);
                    assert Ref.equals(Map.this, newMap);
                    return true, this;
                    }
                }
            return false;
            }

        @Override
        conditional EntrySet removeIf(
                function Boolean (Entry) shouldRemove)
            {
            Set<KeyType> oldKeys = Map.this.keys;

            CursorEntry? entry = null;
            if (Set<KeyType> newKeys : oldKeys.removeIf(key ->
                    {
                    entry = entry?.advance(key) : new CursorEntry(key);
                    return shouldRemove(entry.advance(key));
                    }))
                {
                assert &newKeys == &oldKeys;
                return true, this;
                }
            return false;
            }

        @Override
        conditional EntrySet clear()
            {
            Map newMap = Map.this.clear();
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        Stream<Entry> stream()
            {
            TODO
            }

        @Override
        EntrySet clone()
            {
            return this;
            }
        }

    // ----- Entry implementations -----------------------------------------------------------------

    /**
     * An implementation of Entry that can be used as a cursor over any number of keys, and
     * delegates back to the map for its functionality.
     */
    class CursorEntry
            extends ReifiedEntry<KeyType, ValueType>
            implements Entry
        {
        construct(KeyType key)
            {
            construct ReifiedEntry(Map.this, key);
            }

        /**
         * Specify the new "cursor key" for this Entry.
         *
         * @param key  the new key for this Entry
         *
         * @return this Entry
         */
        CursorEntry advance(KeyType key)
            {
            this.key = key;
            return this;
            }

        @Override
        Entry reify()
            {
            return Map.this.entryFor(key);
            }
        }

    // ----- values collection implementation ------------------------------------------------------

    /**
     * An implementation of the Collection for the [values] property that delegates back
     * to the map and to the map's [keys].
     */
    class ValuesCollection
            implements Collection<ValueType>
        {
        @Override
        Int size.get()
            {
            return Map.this.size;
            }

        @Override
        Boolean empty.get()
            {
            return Map.this.empty;
            }

        @Override
        Iterator<ValueType> iterator()
            {
            return new Iterator()
                {
                Iterator<KeyType> keyIterator = keys.iterator();

                @Override
                conditional ValueType next()
                    {
                    if (KeyType key : keyIterator.next())
                        {
                        return Map.this.get(key);
                        }

                    return false;
                    }
                };
            }

        @Override
        conditional ValuesCollection remove(ValueType value)
            {
            Map map = Map.this;
            Boolean modified = map.keys.iterator().untilAny(key ->
                {
                if (ValueType test : map.get(key))
                    {
                    if (test == value)
                        {
                        Map newMap = map.remove(key);
                        assert Ref.equals(map, newMap);
                        return true;
                        }
                    }
                return false;
                });

            return modified ? (true, this) : false;
            }

        @Override
        conditional ValuesCollection removeIf(function Boolean (ValueType) shouldRemove)
            {
            Map map = Map.this;
            if (Set<KeyType> newKeys : map.keys.removeIf(key ->
                    {
                    assert ValueType value : map.get(key);
                    return shouldRemove(value);
                    }))
                {
                assert Ref.equals(map.keys, newKeys);
                return true, this;
                }

            return false;
            }

        @Override
        conditional ValuesCollection clear()
            {
            if (Map newMap : Map.this.clear())
                {
                if (Ref.equals(Map.this, newMap))
                    {
                    return true, this;
                    }
                else
                    {
                    throw new ReadOnly();
                    }
                }

            return false;
            }
        }
    }
