/**
 * ListMap is an implementation of a Map on top of an Array to maintain the order of insertion.
 *
 * The ListMap design is predicated on two general assumptions:
 *
 * * The data structure is either in an initial append-intensive mode in which queries do not
 *   occur, or in post-append query-intensive mode, in which mutations are rare;
 * * Deletions (_particularly_ deletions of any entry other than the most recent entry added)
 *   are _extremely_ rare, and are allowed to be expensive.
 *
 * The ListMap uses a brute force, `O(N)` search. If the ListMap grows beyond an arbitrary size,
 * and if the `Key` type is [Hashable], then the ListMap will automatically create a size-optimized
 * hashing lookup data structure when the first such search occurs.
 */
class ListMap<Key, Value>
        implements Map<Key, Value>
        implements Replicable
        incorporates CopyableMap.ReplicableCopier<Key, Value>
        incorporates conditional ListMapIndex<Key extends immutable Hashable, Value>
        incorporates conditional MapFreezer<Key extends immutable Object, Value extends Shareable>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a mutable ListMap with an optional initial capacity.
     *
     * @param initCapacity  an optional suggested capacity for the map, expressed in terms of the
     *                      number of entries
     */
    construct(Int initCapacity = 0)
        {
        this.keyArray = new Array(initCapacity);
        this.valArray = new Array(initCapacity);
        this.inPlace  = True;
        }

    /**
     * Construct a persistent (Persistent or Constant) ListMap pre-populated with the specified
     * keys and values.
     *
     * @param keys  the keys for the map
     * @param vals  the values for the map
     */
    construct(Key[] keys, Value[] vals)
        {
        this.keyArray = keys.mutability == Persistent || keys.mutability == Constant
                            ? keys : keys.freeze(inPlace=False);
        this.valArray = vals.mutability == Persistent || vals.mutability == Constant
                            ? vals : vals.freeze(inPlace=False);
        this.inPlace  = False;

        // TODO various checks, and do we need to copy the array(s) if they aren't immutable?
        assert keys.size == vals.size;
        }
    finally
        {
        if (this.keyArray.is(immutable Array) && this.valArray.is(immutable Array))
            {
            makeImmutable();
            }
        }

    @Override
    construct(ListMap that)
        {
        this.keyArray = that.keyArray.toArray(Mutable);
        this.valArray = that.valArray.toArray(Mutable);
        this.inPlace  = True;
        }


    // ----- internal ------------------------------------------------------------------------------

    @Override
    public/private Boolean inPlace;

    /**
     * The list of keys in the map, in order of insertion.
     */
    protected Key[]   keyArray;

    /**
     * The values in the map, corresponding by index to [keyArray].
     */
    protected Value[] valArray;

    /**
     * A counter of the number of items added to the map. Used to detect illegal concurrent
     * modifications.
     */
    protected/private Int appends = 0;

    /**
     * A counter of the number of items deleted from the map. Used to detect illegal concurrent
     * modifications.
     */
    protected/private Int deletes = 0;

    /**
     * Find a key in the map's internal list of keys, returning its location in the list.
     *
     * @param key  the key to find
     *
     * @return True iff the key was found
     * @return the conditional index at which the key was found
     */
    protected conditional Int indexOf(Key key)
        {
        loop: for (Key eachKey : keyArray)
            {
            if (eachKey == key)
                {
                return True, loop.count;
                }
            }
        return False;
        }

    /**
     * Delete the entry at the specified index in the map's internal lists of keys and values.
     *
     * @param index  the index of the entry
     */
    protected void deleteEntryAt(Int index)
        {
        keyArray.delete(index);
        valArray.delete(index);
        ++deletes;
        }

    /**
     * Append an entry to the end of the map's internal lists of keys and values.
     *
     * @param key    the key to append
     * @param value  the value to append
     */
    protected void appendEntry(Key key, Value value)
        {
        keyArray += key;
        valArray += value;
        ++appends;
        }

    /**
     * Some operations require that the containing Map be mutable; this method throws an exception
     * if the Map is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not Mutable
     */
    protected Boolean verifyInPlace()
        {
        if (!inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace==True");
            }
        return True;
        }


    // ----- Map interface -------------------------------------------------------------------------

// TODO CP
//    @Override
//    public/private Mutability mutability = Mutable;

    @Override
    Int size.get()
        {
        return keyArray.size;
        }

    @Override
    Boolean contains(Key key)
        {
        return indexOf(key);
        }

    @Override
    conditional Value get(Key key)
        {
        if (Int index := indexOf(key))
            {
            return True, valArray[index];
            }
        return False;
        }

    @Override
    @Lazy Set<Key> keys.calc()
        {
        return new Keys();
        }

    @Override
    @Lazy Collection<Entry> entries.calc()
        {
        return new Entries();
        }

    @Override
    @Lazy Collection<Value> values.calc()
        {
        return new Values();
        }

    @Override
    ListMap put(Key key, Value value)
        {
        if (Int index := indexOf(key))
            {
            if (inPlace)
                {
                valArray[index] = value;
                return this;
                }

            // persistent
            Value[] vals = valArray.replace(index, value);
            return new ListMap(keyArray, vals);
            }

        if (inPlace)
            {
            appendEntry(key, value);
            return this;
            }

        // persistent
        Key[]   keys = keyArray.add(key);
        Value[] vals = valArray.add(value);
        return new ListMap(keys, vals);
        }

    @Override
    ListMap putAll(Map<Key, Value> that)
        {
        if (inPlace)
            {
            for ((Key key, Value value) : that)
                {
                put(key, value);
                }
            return this;
            }

        // persistent
        Int thatCount = that.size;
        if (thatCount == 0)
            {
            return this;
            }

        Value[] valsNew = valArray;
        Key[]   keysAdd = new Array<Key>(thatCount);
        Value[] valsAdd = new Array<Value>(thatCount);
        for ((Key key, Value value) : that)
            {
            if (Int index := indexOf(key))
                {
                valsNew = valsNew.replace(index, value);
                }
            else
                {
                keysAdd += key;
                valsAdd += value;
                }
            }

        Key[] keysNew;
        if (keysAdd.empty)
            {
            keysNew = keyArray;
            }
        else
            {
            keysNew = keyArray.addAll(keysAdd);
            valsNew = valsNew .addAll(valsAdd);
            }
        return new ListMap(keysNew, valsNew);
        }

    @Override
    ListMap remove(Key key)
        {
        if (Int index := indexOf(key))
            {
            if (inPlace)
                {
                deleteEntryAt(index);
                }
            else // persistent
                {
                Key[]   keys = keyArray.delete(index);
                Value[] vals = valArray.delete(index);
                return new ListMap(keys, vals);
                }
            }

        return this;
        }

    @Override
    conditional ListMap remove(Key key, Value value)
        {
        if (Int index := indexOf(key))
            {
            if (valArray[index] == value)
                {
                if (inPlace)
                    {
                    deleteEntryAt(index);
                    return True, this;
                    }
                else // persistent
                    {
                    Key[]   keys = keyArray.delete(index);
                    Value[] vals = valArray.delete(index);
                    return True, new ListMap(keys, vals);
                    }
                }
            }

        return False;
        }

    @Override
    ListMap clear()
        {
        Int count = size;
        if (count > 0)
            {
            if (inPlace)
                {
                keyArray.clear();
                valArray.clear();
                deletes += count;
                }
            else // persistent
                {
                return new ListMap([], []);
                }
            }

        return this;
        }

    @Override
    <Result> Result process(Key key, function Result (Entry) compute)
        {
        return compute(new CursorEntry(key));
        }

// TODO CP
//    @Override
//    ListMap ensureMutable()
//        {
//        if (mutability == Mutable)
//            {
//            return this;
//            }
//
//        ListMap that = new ListMap(size);
//        that.putAll(this);
//        return that;
//        }
//
//    @Override
//    ListMap ensureFixedSize(Boolean inPlace = False)
//        {
//        TODO
//        }
//
//    @Override
//    ListMap ensurePersistent(Boolean inPlace = False)
//        {
//        TODO
//        }
//
//    @Override
//    immutable ListMap freeze(Boolean inPlace = False)
//        {
//        immutable Key[]   keys = keyArray.freeze(inPlace);
//        immutable Value[] vals = valArray.freeze(inPlace);
//
//        return inPlace
//                ? makeImmutable()
//                : new ListMap(keys, vals).as(immutable ListMap);
//        }

    // ----- Keys Set ------------------------------------------------------------------------------

    /**
     * A set of keys in the map. This is used to implement the [keys] property.
     */
    class Keys
            implements Set<Key>
            implements Freezable
        {
        @Override
        Int size.get()
            {
            return keyArray.size;
            }

        @Override
        Iterator<Key> iterator()
            {
            return new Iterator()
                {
                Int index       = 0;
                Int stop        = size;
                Int prevDeletes = deletes;
                @Unassigned Key key;

                @Override
                conditional Key next()
                    {
                    // the immediately previously iterated key is allowed to be deleted
                    if (deletes != prevDeletes)
                        {
                        if (deletes - prevDeletes == 1 && 0 < index < stop && keyArray[index-1] != key)
                            {
                            --stop;
                            --index;
                            ++prevDeletes;
                            }
                        else
                            {
                            throw new ConcurrentModification();
                            }
                        }

                    if (index < stop)
                        {
                        key = keyArray[index++];
                        return True, key;
                        }

                    return False;
                    }
                };
            }

        @Override
        Keys remove(Key key)
            {
            verifyInPlace();

            if (Int index := indexOf(key))
                {
                deleteEntryAt(index);
                }

            return this;
            }

        @Override
        (Keys, Int) removeAll(function Boolean (Key) shouldRemove)
            {
            verifyInPlace();

            Int removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(keyArray[i-removed]))
                    {
                    deleteEntryAt(i-removed);
                    ++removed;
                    }
                }

            return this, removed;
            }

        @Override
        Keys clear()
            {
            verifyInPlace();
            this.ListMap.clear();
            return this;
            }

        @Override
        Key[] toArray(Array.Mutability? mutability = Null)
            {
            return keyArray.toArray(mutability);
            }

        @Override
        immutable Keys freeze(Boolean inPlace = False)
            {
            assert this.ListMap.is(immutable ListMap);
            return makeImmutable();
            }
        }


    // ----- Entry implementation ------------------------------------------------------------------

    /**
     * An implementation of Entry that can be used as a cursor over the keys and values.
     *
     * The CursorEntry uses a fail-fast model for concurrent modification detection. Once the
     * CursorEntry is constructed (or a cursor [advance] occurs), any changes made to the ListMap
     * that do not occur via the same CursorEntry will cause the CursorEntry to be in invalid
     * state, and any subsequent operation on the CursorEntry will throw ConcurrentModification.
     */
    protected class CursorEntry
            implements Entry
        {
        /**
         * Construct a CursorEntry for a single key that may or may not exist in the Map.
         *
         * @param key  the key for the entry
         */
        protected construct(Key key)
            {
            this.key    = key;
            this.expect = appends + deletes;
            }
        finally
            {
            if (index := indexOf(key))
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
            this.key    = keyArray[index];
            this.index  = index;
            this.exists = True;
            this.expect = appends + deletes;
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
        @Unassigned
        protected/private Int index;

        /**
         * The expected modification count for the ListMap.
         */
        protected/private Int expect;

        @Override
        @Unassigned
        public/private Key key;

        @Override
        public/private Boolean exists.get()
            {
            return verifyNoSurprises() & super();
            }

        @Override
        Value value
            {
            @Override
            Value get()
                {
                if (exists)
                    {
                    return valArray[index];
                    }

                throw new OutOfBounds("key=" + key);
                }

            @Override
            void set(Value value)
                {
                verifyInPlace();
                if (exists)
                    {
                    valArray[index] = value;
                    }
                else if (cursor)
                    {
                    // disallow the entry from being re-added (since it lost its cursor position)
                    throw new ReadOnly();
                    }
                else
                    {
                    appendEntry(key, value);
                    index  = keyArray.size - 1;
                    exists = True;
                    ++expect;
                    }
                }
            }

        @Override
        void delete()
            {
            if (verifyInPlace() & exists)
                {
                deleteEntryAt(index);
                exists = False;
                ++expect;
                }
            }

        @Override
        Map<Key, Value>.Entry reify()
            {
            return reifyEntry(key);
            }

        /**
         * Check the expected modification count for the ListMap against the actual modification
         * count.
         *
         * @return True
         *
         * @throws ConcurrentModification if the Map has been subsequently modified in a manner
         *                                other than through this entry
         */
        protected Boolean verifyNoSurprises()
            {
            if (appends + deletes == expect)
                {
                return True;
                }

            throw new ConcurrentModification();
            }
        }


    // ----- Entries Set ---------------------------------------------------------------------------

    /**
     * The collection of map entries. This is used to implement the [entries] property.
     */
    class Entries
            implements Collection<Entry>
            implements Freezable
        {
        @Override
        Int size.get()
            {
            return keyArray.size;
            }

        @Override
        Iterator<Entry> iterator()
            {
            return new Iterator()
                {
                Int         index       = 0;
                Int         stop        = size;
                Int         prevDeletes = deletes;
                CursorEntry entry       = new CursorEntry();

                @Override
                conditional Entry next()
                    {
                    // the immediately previously iterated key is allowed to be deleted
                    if (deletes != prevDeletes)
                        {
                        if (deletes - prevDeletes == 1 && 0 < index < stop && !entry.exists)
                            {
                            --stop;
                            --index;
                            ++prevDeletes;
                            }
                        else
                            {
                            throw new ConcurrentModification();
                            }
                        }

                    if (index < stop)
                        {
                        entry.advance(index++);
                        return True, entry;
                        }

                    return False;
                    }
                };
            }

        @Override
        Entries remove(Entry entry)
            {
            verifyInPlace();

            if (Int index := indexOf(entry))
                {
                deleteEntryAt(index);
                }

            return this;
            }

        @Override
        (Entries, Int) removeAll(function Boolean (Entry) shouldRemove)
            {
            verifyInPlace();

            CursorEntry entry   = new CursorEntry();
            Int         removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(entry.advance(i-removed)))
                    {
                    entry.delete();
                    ++removed;
                    }
                }

            return this, removed;
            }

        @Override
        Entries clear()
            {
            verifyInPlace();
            this.ListMap.clear();
            return this;
            }

        /**
         * Find the specified entry in the [ListMap], and return its position.
         *
         * @param entry  the entry to find
         *
         * @return True iff the entry is in the ListMap
         * @return (conditional) the position in the ListMap of the found Entry
         */
        protected conditional Int indexOf(Entry entry)
            {
            // first, see if the entry knows its own index
            Key     key   = entry.key;
            Int     index = -1;
            Boolean found = False;
            if (entry.is(CursorEntry))
                {
                index = entry.index;
                if (0 <= index < size && keyArray[index] == key)
                    {
                    found = True;
                    }
                }

            // otherwise, search for the entry by key
            if (!found)
                {
                if (index := this.ListMap.indexOf(entry.key))
                    {
                    found = True;
                    }
                }

            // lastly, verify that the values match
            found &&= valArray[index] == entry.value;

            return found, index;
            }

        @Override
        immutable Entries freeze(Boolean inPlace = False)
            {
            assert this.ListMap.is(immutable ListMap);
            return makeImmutable();
            }
        }


    // ----- Values Collection ---------------------------------------------------------------------

    /**
     * The collection of values in the map.  This is used to implement the [values] property.
     */
    class Values
            implements Collection<Value>
            implements Freezable
        {
        @Override
        Int size.get()
            {
            return valArray.size;
            }

        @Override
        Iterator<Value> iterator()
            {
            return new Iterator()
                {
                Int index       = 0;
                Int stop        = size;
                Int prevDeletes = deletes;
                @Unassigned Key key;

                @Override
                conditional Value next()
                    {
                    // the immediately previously iterated key is allowed to be deleted
                    if (deletes != prevDeletes)
                        {
                        if (deletes - prevDeletes == 1 && 0 < index < stop && keyArray[index-1] != key)
                            {
                            --stop;
                            --index;
                            ++prevDeletes;
                            }
                        else
                            {
                            throw new ConcurrentModification();
                            }
                        }

                    if (index < stop)
                        {
                        key = keyArray[index];
                        return True, valArray[index++];
                        }

                    return False;
                    }
                };
            }

        @Override
        Values remove(Value value)
            {
            verifyInPlace();

            if (Int index := valArray.indexOf(value))
                {
                deleteEntryAt(index);
                }

            return this;
            }

        @Override
        (Values, Int) removeAll(function Boolean (Value) shouldRemove)
            {
            verifyInPlace();

            Int removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(valArray[i-removed]))
                    {
                    deleteEntryAt(i-removed);
                    ++removed;
                    }
                }

            return this, removed;
            }

        @Override
        Values clear()
            {
            verifyInPlace();
            this.ListMap.clear();
            return this;
            }

        @Override
        Value[] toArray(Array.Mutability? mutability = Null)
            {
            return valArray.toArray(mutability);
            }

        @Override
        immutable Values freeze(Boolean inPlace = False)
            {
            assert this.ListMap.is(immutable ListMap);
            return makeImmutable();
            }
        }


    // ----- Freezable -----------------------------------------------------------------------------

    @Override
    immutable ListMap makeImmutable()
        {
        this.inPlace = False;
        return super();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Instantiate a reified entry, which must be a child of the map.
     *
     * @param key  the key to obtain a reified `Entry` for
     *
     * @return a newly reified `Entry`
     */
    private Entry reifyEntry(Key key)
        {
        return new @maps.KeyEntry(key) Entry() {};
        }
    }