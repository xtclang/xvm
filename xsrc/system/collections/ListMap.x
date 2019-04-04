import maps.ReifiedEntry;

/**
 * ListMap is an implementation of a Map on top of an Array to maintain the order of insertion.
 */
class ListMap<KeyType, ValueType>
        implements Map<KeyType, ValueType>
        implements MutableAble, FixedSizeAble, PersistentAble, ConstAble
        // TODO optimizations for: KeyType extends immutable Hashable
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Int initCapacity = 0)
        {
        listKeys = new Array(initCapacity);
        listVals = new Array(initCapacity);
        }


    // ----- internal ------------------------------------------------------------------------------

    private KeyType[]   listKeys;
    private ValueType[] listVals;

    protected/private Int appends = 0;
    protected/private Int deletes = 0;

    protected conditional Int indexOf(KeyType key)
        {
        AllKeys:
        for (KeyType eachKey : listKeys)
            {
            if (eachKey == key)
                {
                return True, AllKeys.count;
                }
            }
        return False;
        }

    /**
     * Some operations require that the containing Map be Mutable; this method throws an exception
     * if the Map is not Mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not Mutable
     */
    protected Boolean verifyMutable()
        {
        if (mutability != Mutable)
            {
            throw new ReadOnly("Map operation requires mutability==Mutable");
            }
        return True;
        }

    /**
     * Verify that the Map's mutability is non-persistent.
     *
     * @return True
     *
     * @throws ReadOnly if the Map's mutability is persistent
     */
    protected Boolean verifyNotPersistent()
        {
        if (mutability.persistent)
            {
            throw new ReadOnly("Map operation requires mutability.persistent==False");
            }
        return True;
        }


    // ----- Map properties and methods ------------------------------------------------------------

    @Override
    public/private MutabilityConstraint mutability = Mutable;

    @Override
    Int size.get()
        {
        return listKeys.size;
        }

    @Override
    Boolean contains(KeyType key)
        {
        return indexOf(key);
        }

    @Override
    conditional ValueType get(KeyType key)
        {
        if (Int index : indexOf(key))
            {
            return True, listVals[index];
            }
        return False;
        }

    @Override
    @Lazy Set<KeyType> keys.calc()
        {
        return new Keys();
        }

    @Override
    @Lazy Set<Entry> entries.calc()
        {
        return new Entries();
        }

    @Override
    @Lazy Collection<ValueType> values.calc()
        {
        return new Values();
        }

    @Override
    conditional ListMap put(KeyType key, ValueType value)
        {
        if (Int index : indexOf(key))
            {
            if (listVals[index] == value)
                {
                return False;
                }

            listVals[index] = value;
            }
        else
            {
            listKeys.addElement(key);
            listVals.addElement(value);
            ++appends;
            }

        return True, this;
        }

    @Override
    conditional ListMap remove(KeyType key)
        {
        if (Int index : indexOf(key))
            {
            listKeys.delete(index);
            listVals.delete(index);
            ++deletes;
            return True, this;
            }

        return False;
        }

    @Override
    conditional ListMap remove(KeyType key, ValueType value)
        {
        if (Int index : indexOf(key))
            {
            if (listVals[index] == value)
                {
                listKeys.delete(index);
                listVals.delete(index);
                ++deletes;
                return True, this;
                }
            }

        return False;
        }

    @Override
    conditional ListMap clear()
        {
        Int count = size;
        if (count == 0)
            {
            return False;
            }

        listKeys.clear();
        listVals.clear();
        deletes += count;
        return True, this;
        }

    @Override
    <ResultType> ResultType process(KeyType key, function ResultType (Map<KeyType, ValueType>.Entry) compute)
        {
        return compute(new CursorEntry(key));
        }

    @Override
    ListMap ensureMutable()
        {
        if (mutability == Mutable)
            {
            return this;
            }

        ListMap that = new ListMap(size);
        that.putAll(this);
        return that;
        }

    @Override
    ListMap ensureFixedSize(Boolean inPlace = False)
        {
        TODO
        }

    @Override
    ListMap ensurePersistent(Boolean inPlace = False)
        {
        TODO
        }

    @Override
    immutable ListMap ensureConst(Boolean inPlace = False)
        {
        TODO
        }


    // ----- Keys Set ------------------------------------------------------------------------------

    class Keys
            implements Set<KeyType>
        {
        @Override
        MutabilityConstraint mutability.get()
            {
            return Mutable;
            }

        @Override
        Int size.get()
            {
            return listKeys.size;
            }

        @Override
        Iterator<KeyType> iterator()
            {
            return new Iterator()
                {
                Int index       = 0;
                Int limit       = size;
                Int prevDeletes = deletes;
                @Unassigned KeyType key;

                @Override
                conditional KeyType next()
                    {
                    // the immediately previously iterated key is allowed to be deleted
                    if (deletes != prev)
                        {
                        if (deletes - prevDeletes == 1 && index > 0 && index < limit && listKeys[index-1] != key)
                            {
                            --limit;
                            --index;
                            ++prevDeletes;
                            }
                        else
                            {
                            throw new ConcurrentModification();
                            }
                        }

                    if (index < limit)
                        {
                        key = listKeys[index++];
                        return True, key;
                        }

                    return False;
                    }
                };
            }

        @Override
        conditional Keys remove(KeyType key)
            {
            verifyMutable();

            if (Int index : listKeys.indexOf(key))
                {
                listKeys.delete(index);
                listVals.delete(index);
                ++deletes;
                return True, this;
                }

            return False;
            }

        @Override
        conditional Keys removeIf(function Boolean (KeyType) shouldRemove)
            {
            verifyMutable();

            Int removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(listKeys[i-removed]))
                    {
                    listKeys.delete(i-removed);
                    listVals.delete(i-removed);
                    ++deletes;
                    ++removed;
                    }
                }

            return removed > 0, this;
            }

        @Override
        conditional Keys clear()
            {
            return verifyMutable() && ListMap.this.clear(), this;
            }

        @Override
        Stream<KeyType> stream()
            {
            TODO
            }

        @Override
        Set<KeyType> clone()
            {
            TODO could this just wrap a clone of the array of keys with a ListSet?
            }
        }


    // ----- Entry implementation ------------------------------------------------------------------

    /**
     * An implementation of Entry that can be used as a cursor over the keys and values.
     */
    protected class CursorEntry
            implements Entry
        {
        /**
         * Construct a CursorEntry for a single key that may or may not exist in the Map.
         *
         * @param key  the key for the entry
         */
        protected construct(KeyType key)
            {
            this.key    = key;
            this.expect = mods;
            }
        finally
            {
            if (index : indexOf(key))
                {
                exists = True;
                }
            }

        /**
         * Construct a CursorEntry in cursor mode.
         *
         * @param cursor  True to indicate that the Entry will be used in "cursor mode"
         */
        protected construct()
            {
            this.cursor = True;
            }

        /**
         * For an entry in cursor mode, advance the cursor to the specified index in the ListMap.
         *
         * @param key    the key for the entry
         */
        protected CursorEntry advance(Int index)
            {
            assert cursor;
            this.key    = listKeys[index];
            this.index  = index;
            this.exists = True;
            this.expect = mods;
            return this;
            }

        /**
         * Cursor mode is the ability to be re-used over a number of entries in the Map. The
         * opposite of Cursor mode is single key mode.
         */
        protected/private Boolean cursor;

        /**
         * The index of the key in the ListMap, assuming that the key [exists].
         */
        protected/private Int index;

        /**
         * The expected modification count for the ListMap.
         */
        protected/private Int expect;

        @Override
        @Unassigned
        public/private KeyType key.get()
            {
            verifyNoSurprises();
            return super();
            }

        @Override
        public/private Boolean exists.get()
            {
            return verifyNoSurprises() & super();
            }

        @Override
        ValueType value
            {
            @Override
            ValueType get()
                {
                if (exists)
                    {
                    return listVals[index];
                    }

                throw new OutOfBounds("key=" + key);
                }

            @Override
            void set(ValueType value)
                {
                verifyNotPersistent();
                if (exists)
                    {
                    listVals[index] = value;
                    registerMod();
                    }
                else if (cursor)
                    {
                    // disallow the entry from being re-added (since it lost its cursor position)
                    throw new ReadOnly();
                    }
                else
                    {
                    listKeys.addElement(key);
                    listVals.addElement(value);
                    index  = listKeys.size - 1;
                    exists = True;
                    registerMod();
                    }
                }
            }

        @Override
        void remove()
            {
            if (verifyNotPersistent() & exists)
                {
                listKeys.delete(index);
                listVals.delete(index);
                exists = False;
                registerMod();
                }
            }

        @Override
        Entry reify()
            {
            return new ReifiedEntry(ListMap.this, key);
            }

        protected void registerMod()
            {
            ++expect;
            ++mods;
            }

        protected Boolean verifyNoSurprises()
            {
            if (mods == expect)
                {
                return True;
                }

            throw new ConcurrentModification();
            }
        }


    // ----- Entries Set ---------------------------------------------------------------------------

    class Entries
            implements Set<Map<KeyType, ValueType>.Entry>
        {
        @Override
        MutabilityConstraint mutability.get()
            {
            return Mutable;
            }

        @Override
        Int size.get()
            {
            return listKeys.size;
            }

        @Override
        Iterator<Map<KeyType, ValueType>.Entry> iterator()      // REVIEW GG I should just be able to say "Entry"
            {
            return new Iterator()
                {
                Int         count  = 0;
                Int         expect = listKeys.size;
                CursorEntry entry  = new CursorEntry();

                @Override
                conditional Map<KeyType, ValueType>.Entry next()
                    {
                    Int actual = listKeys.size;
                    Int index  = count - (expect - actual);
                    if (index >= 0 && index < actual)
                        {
                        return True, entry.advance(index);
                        }

                    if (index == actual)
                        {
                        return False;
                        }

                    throw new ConcurrentModification();
                    }
                };
            }

        @Override
        conditional Entries remove(Map<KeyType, ValueType>.Entry entry)
            {
            verifyMutable();
// TODO
            if (Int index : listKeys.indexOf(entry.key))
                {
                if (listVals[index] == entry.value)
                    {
                    listKeys.delete(index);
                    listVals.delete(index);
                    return True, this;
                    }
                }

            return False;
            }

        @Override
        conditional Entries removeIf(function Boolean (KeyType) shouldRemove)
            {
// TODO
            verifyMutable();

            Int removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(listKeys[i-removed]))
                    {
                    listKeys.delete(i-removed);
                    listVals.delete(i-removed);
                    ++removed;
                    }
                }

            return removed > 0, this;
            }

        @Override
        conditional Entries clear()
            {
            return verifyMutable() && ListMap.this.clear(), this;
            }

        @Override
        Stream<Map<KeyType, ValueType>.Entry> stream()
            {
            TODO
            }

        @Override
        Set<Map<KeyType, ValueType>.Entry> clone()
            {
            TODO
            }
        }


    // ----- Values Collection ---------------------------------------------------------------------

    class Values
            implements Collection<ValueType>
        {
        @Override
        MutabilityConstraint mutability.get()
            {
            return Mutable;
            }

        @Override
        Int size.get()
            {
            return listVals.size;
            }

        @Override
        Iterator<ValueType> iterator()    // TODO mods
            {
            return new Iterator()
                {
                Int count  = 0;
                Int expect = listVals.size;

                @Override
                conditional ValueType next()
                    {
                    Int actual = listVals.size;
                    Int index  = count - (expect - actual);
                    if (index >= 0 && index < actual)
                        {
                        return True, listVals[index];
                        }

                    if (index == actual)
                        {
                        return False;
                        }

                    throw new ConcurrentModification();
                    }
                };
            }

        @Override
        conditional Values remove(ValueType value)
            {
            verifyMutable();

            if (Int index : listVals.indexOf(value))
                {
                listKeys.delete(index);
                listVals.delete(index);
                ++deletes;
                return True, this;
                }

            return False;
            }

        @Override
        conditional Values removeIf(function Boolean (ValueType) shouldRemove)
            {
            verifyMutable();

            Int removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(listVals[i-removed]))
                    {
                    listKeys.delete(i-removed);
                    listVals.delete(i-removed);
                    ++removed;
                    ++deletes;
                    }
                }

            return removed > 0, this;
            }

        @Override
        conditional Values clear()
            {
            return verifyMutable() && ListMap.this.clear(), this;
            }

        @Override
        Stream<ValueType> stream()
            {
            TODO
            }

        @Override
        Collection<ValueType> clone()
            {
            TODO
            }
        }
    }
