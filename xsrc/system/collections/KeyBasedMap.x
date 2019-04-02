/**
 * KeyBasedMap is an abstract implementation of a Map that is key-centric, and implements .
 */
class KeyBasedMap<KeyType, ValueType>
        implements Map<KeyType, ValueType>
    {
    @Override
    @RO Int size.get()
        {
        return keys.size;
        }

    @Override
    conditional Entry find(KeyType key)
        {
        TODO
        }

    @Override
    @RO Set<KeyType> keys
        {
        TODO
        }

    @Override
    @RO Set<Entry> entries
        {
        TODO
        }

    @Override
    @RO Collection<ValueType> values
        {
        }

    @Override
    @RO MutabilityConstraint mutability;

    @Override
    Map put(KeyType key, ValueType value);

    @Override
    Map remove(KeyType key);

    @Override
    Map clear();

    @Override
    <ResultType> ResultType process(KeyType key, function ResultType (ProcessableEntry) compute);

    // ----- keys set implementations --------------------------------------------------------------

    /**
     * An implementation of the Set for the {@link Map.keys} property that delegates back
     * to the map and to the map's {@link Map.entries entries}.
     */
    class EntryBasedKeySet
            implements Set<KeyType>
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
        Iterator<KeyType> iterator()
            {
            return new Iterator()
                {
                Iterator<Entry> entryIterator = Map.this.entries.iterator();

                @Override
                conditional KeyType next()
                    {
                    if (Entry entry : entryIterator.next())
                        {
                        return true, entry.key;
                        }
                    return false;
                    }
                };
            }

        @Override
        conditional EntryBasedKeySet remove(KeyType key)
            {
            Map newMap = Map.this.remove(key);
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        conditional EntryBasedKeySet removeIf(function Boolean (KeyType) shouldRemove)
            {
            Set<KeyType> oldKeys = Map.this.keys;
            oldKeys.removeIf(shouldRemove);
            assert Ref.equals(Map.this.keys, oldKeys);
            return true, this;
            }

        @Override
        conditional EntryBasedKeySet clear()
            {
            Map newMap = Map.this.clear();
            assert Ref.equals(Map.this, newMap);
            return true, this;
            }

        @Override
        Stream<KeyType> stream()
            {
            TODO
            }

        @Override
        EntryBasedKeySet clone()
            {
            return this;
            }
        }

    // ----- entries set implementations -----------------------------------------------------------

    /**
     * An implementation of the Set for the {@link Map.entries} property that delegates back to the
     * map and to the map's {@link Map.keys keys}.
     */
    class KeyBasedEntrySet
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
                        private KeyBasedCursorEntry entry = new KeyBasedCursorEntry(key);
                        return true, entry.advance(key);
                        }

                    return false;
                    }
                };
            }

        @Override
        conditional KeyBasedEntrySet remove(Entry entry)
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
        conditional KeyBasedEntrySet removeIf(
                function Boolean (Entry) shouldRemove)
            {
            Set<KeyType> oldKeys = Map.this.keys;

            KeyBasedCursorEntry? entry = null;
            if (Set<KeyType> newKeys : oldKeys.removeIf(key ->
                    {
                    entry = entry?.advance(key) : new KeyBasedCursorEntry(key);
                    return shouldRemove(entry.advance(key));
                    }))
                {
                assert &newKeys == &oldKeys;
                return true, this;
                }
            return false;
            }

        @Override
        conditional KeyBasedEntrySet clear()
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
        KeyBasedEntrySet clone()
            {
            return this;
            }
        }

    // ----- Entry implementations -----------------------------------------------------------------

    /**
     * The primordial implementation of a simple Entry.
     */
    static class SimpleEntry(KeyType key, ValueType value)
            implements Entry
        {
        @Override
        public/private KeyType key;

        @Override
        ValueType value;
        }

    /**
     * An implementation of ProcessableEntry that delegates back to the map for a specified key.
     */
    class KeyBasedEntry(KeyType key)
            implements ProcessableEntry
        {
        @Override
        public/protected KeyType key;

        @Override
        Boolean exists.get()
            {
            return Map.this.get(key);
            }

        @Override
        ValueType value
            {
            @Override
            ValueType get()
                {
                if (ValueType value : Map.this.get(key))
                    {
                    return value;
                    }
                throw new OutOfBounds();
                }

            @Override
            void set(ValueType value)
                {
                Map.this.put(key, value);
                }
            }

        @Override
        void remove()
            {
            Map newMap = Map.this.remove(key);
            assert Ref.equals(Map.this, newMap);
            }
        }

    /**
     * An implementation of ProcessableEntry that can be used as a cursor over any number of keys,
     * and delegates back to the map for its functionality.
     */
    class KeyBasedCursorEntry
            extends KeyBasedEntry
        {
        construct(KeyType key)
            {
            construct KeyBasedEntry(key);
            }

        /**
         * Specify the new "cursor key" for this Entry.
         *
         * @param key  the new key for this Entry
         *
         * @return this Entry
         */
        KeyBasedCursorEntry advance(KeyType key)
            {
            this.key = key;
            return this;
            }

        @Override
        Entry reify()
            {
            // this entry class is re-usable for different keys, so return an entry whose key cannot
            // be modified
            return new KeyBasedEntry(key);
            }
        }

    // ----- values collection implementations -----------------------------------------------------

    /**
     * An implementation of the Collection for the {@link Map.values} property that delegates back
     * to the map and to the map's {@link Map.keys keys}.
     */
    class KeyBasedValuesCollection
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
                Iterator<KeyType> keyIterator = Map.this.keys.iterator();

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
        conditional KeyBasedValuesCollection remove(ValueType value)
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
        conditional KeyBasedValuesCollection removeIf(function Boolean (ValueType) shouldRemove)
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
        conditional KeyBasedValuesCollection clear()
            {
            Map newMap = Map.this.clear();

            assert Ref.equals(Map.this, newMap);
            return true, this;
            }
        }
    }
