import maps.ReifiedEntry;

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
 * and if the `KeyType` is [Hashable], then the ListMap will automatically create a size-optimized
 * hashing lookup data structure when the first such search occurs.
 */
class ListMap<KeyType, ValueType>
        implements Map<KeyType, ValueType>
        implements MutableAble, FixedSizeAble, PersistentAble, ConstAble
        // TODO GG: incorporates conditional HashIndex<KeyType extends immutable Hashable, ValueType>
        incorporates Stringer
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(Int initCapacity = 0)
        {
        listKeys = new Array(initCapacity);
        listVals = new Array(initCapacity);
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * The list of keys in the map, in order of insertion.
     */
    private KeyType[]   listKeys;

    /**
     * The values in the map, corresponding by index to [listKeys].
     */
    private ValueType[] listVals;

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
     * Delete the entry at the specified index in the map's internal lists of keys and values.
     *
     * @param index  the index of the entry
     */
    protected void deleteEntryAt(Int index)
        {
        listKeys.delete(index);
        listVals.delete(index);
        ++deletes;
        }

    /**
     * Append an entry to the end of the map's internal lists of keys and values.
     *
     * @param key    the key to append
     * @param value  the value to append
     */
    protected void appendEntry(KeyType key, ValueType value)
        {
        listKeys.addElement(key);
        listVals.addElement(value);
        ++appends;
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
            appendEntry(key, value);
            }

        return True, this;
        }

    @Override
    conditional ListMap remove(KeyType key)
        {
        if (Int index : indexOf(key))
            {
            deleteEntryAt(index);
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
                deleteEntryAt(index);
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


    // ----- MutableIndex mixin --------------------------------------------------------------------

    /**
     * The HashIndex is a hashing data structure that exists solely to optimizes the
     * [ListMap.indexOf] method. Its design is predicated on the general assumptions of the ListMap:
     *
     * * The data structure is either in an initial append-intensive mode in which queries do not
     *   occur, or in post-append query-intensive mode, in which mutations are rare;
     * * Deletions (_particularly_ deletions of any entry other than the most recent entry added)
     *   are _extremely_ rare, and are allowed to be expensive.
     */
    protected mixin HashIndex<KeyType extends Hashable, ValueType>
            into ListMap<KeyType, ValueType>
        {
        /**
         * For keys with the same hash value, the indexes of those keys in the map are stored
         * together. If there is only one key with the hash value, then its index is stored as a
         * simple `Int`. If there are multiple keys with the same hash value, then their indexes
         * are stored as an `Int[]`.
         */
        protected typedef (Int | Int[]) OneOrN;

        /**
         * When multiple hash values modulo to the same bucket id, the `OneOrN` values for each
         * hash value are stored as a binary tree of `OneOrN` values in an array.
         */
        protected typedef OneOrN[] HashTree;

        /**
         * A bucket is either empty (`null`), contains indexes of one or more keys (`OneOrN`) for a
         * single hash value, or contains one or more keys for multiple different hash values (a
         * `HashTree`).
         */
        protected typedef (HashTree | OneOrN)? Bucket;

        /**
         * TODO
         */
        protected static Int MINSIZE = 10;

        /**
         * The sole information managed by the HashIndex is an array of buckets.
         */
        private Bucket[]? buckets;

        @Override
        protected conditional Int indexOf(KeyType key)
            {
            Bucket[]? buckets = this.buckets;
            if (buckets == null)
                {
                if (size > MINSIZE)
                    {
                    buildIndex();
                    buckets = this.buckets;
                    assert buckets != null;
                    }
                else
                    {
                    return super(key);
                    }
                }

            Int    keyhash = key.hash;
            Bucket bucket  = buckets[keyhash % buckets.size];
            if (bucket == null)
                {
                return False;
                }

            OneOrN indexes = 0; // TODO CP "=0" is required due to def asn bug
            search: if (bucket.is(HashTree))
                {
                // binary search the hash tree
                HashTree tree = bucket;
                Int lo = 0;
                Int hi = tree.size - 1;
                while (lo <= hi)
                    {
                    Int mid = (lo + hi) >>> 1;
                    indexes = tree[mid];
                    switch (hashFor(indexes) <=> keyhash)
                        {
                        case Equal:
                            break search;
                        case Lesser:
                            lo = mid + 1;
                            break;
                        case Greater:
                            hi = mid - 1;
                            break;
                        }
                    }
                return False;
                }
            else
                {
                indexes = bucket;
                }

            if (indexes.is(Int[]))
                {
                for (Int index : indexes)
                    {
                    if (listKeys[index] == key)
                        {
                        return True, index;
                        }
                    }
                }
            else
                {
                Int index = indexes;
                if (listKeys[index] == key)
                    {
                    return True, index;
                    }
                }

            return False;
            }

        @Override
        protected void deleteEntryAt(Int index)
            {
            if (buckets != null)
                {
                if (size < MINSIZE)
                    {
                    buckets = null;
                    }
                else
                    {
                    // update the index
                    Int keyhash  = listKeys[index].hash;
                    Int bucketid = keyhash % buckets.size;
                    buckets[bucketid] = removeKeyFrom(buckets[bucketid], keyhash, index);

                    if (index < size-1)
                        {
                        // all of the index information for keys located after the deleted index
                        // will now be incorrect; all such indexes must be decremented
                        decrementAllAfter(index);
                        }
                    }
                }

            super(index);
            }

        @Override
        protected void appendEntry(KeyType key, ValueType value)
            {
            super(key, value);

            if (buckets != null)
                {
                // update the index
                Int keyhash  = key.hash;
                Int bucketid = keyhash % buckets.size;
                buckets[bucketid] = addKeyTo(buckets[bucketid], keyhash, index);

                if (size > buckets.count)
                    {
                    // more keys than buckets; re-hash
                    buildIndex();
                    }
                }
            }

        /**
         * Create all of the hashing structures that make up the hash index.
         */
        protected void buildIndex()
            {
            Int bucketCount = HashMap.calcBucketCount(size);
            buckets = new Bucket[bucketCount];
            loop: for (KeyType key : listKeys)
                {
                Int keyhash  = key.hash;
                Int bucketid = keyhash % bucketCount;
                buckets[bucketid] = addKeyTo(buckets[bucketid], keyhash, loop.count);
                }
            }

        /**
         * Given a single index (an `Int`) or multiple indexes (an `Int[]`), all of which are
         * assumed to have the same hash value, calculate what that hash value is.
         *
         * @param indexes  one index or an array of indexes into the map's underlying list of keys
         *
         * @return the hash value of the key(s) indicated by the passed index(es)
         */
        protected Int hashFor(OneOrN indexes)
            {
            Int index = indexes.is(Int)
                    ? indexes
                    : indexes[0];
            return listKeys[index].hash;
            }

        /**
         * Given a single bucket (an `Int` key index, an `Int[]` of key indexes, or a binary hash
         * tree of those, or a null if the bucket is empty), add the specified key index to that
         * bucket.
         *
         * @param bucket   the previous structure for the bucket
         * @param keyhash  the hash value of the key
         * @param index    the index where the key is located in the map's underlying list of keys
         *
         * @return the new bucket structure
         */
        protected Bucket addKeyTo(Bucket bucket, Int keyhash, Int index)
            {
            if (bucket == null)
                {
                return index;
                }

            if (bucket.is(HashTree))
                {
                // binary search the hash tree (which is stored in an array)
                HashTree tree = bucket;
                Int lo = 0;
                Int hi = tree.size - 1;
                while (lo <= hi)
                    {
                    Int mid = (lo + hi) >>> 1;
                    OneOrN indexes = tree[mid];
                    switch (hashFor(indexes) <=> keyhash)
                        {
                        case Equal:
                            tree[mid] = addIndexTo(indexes, index);
                            return tree;

                        case Lesser:
                            lo = mid + 1;
                            break;
                        case Greater:
                            hi = mid - 1;
                            break;
                        }
                    }
                return tree.insert(mid, index);
                }

            OneOrN indexes = bucket;
            return (keyhash <=> hashFor(indexes))
                {
                case Lesser : [index, indexes];
                case Equal  : addIndexTo(indexes, index);
                case Greater: [indexes, index];
                }
            }

        /**
         * Given a an `Int` key index or an `Int[]` of key indexes, add the specified key index to
         * that structure.
         *
         * @param indexes  either an `Int` key index or an `Int[]` of key indexes
         * @param index    the index where the key is located in the map's underlying list of keys
         *
         * @return an `Int[]` of key indexes including the newly added index
         */
        protected OneOrN addIndexTo(OneOrN indexes, Int index)
            {
            return indexes.is(Int[])
                    ? indexes + index
                    : [indexes, index];
            }

        /**
         * Given a single bucket (an `Int` key index, an `Int[]` of key indexes, or a binary hash
         * tree of those), remove the specified key index from that bucket.
         *
         * @param bucket   the previous structure for the bucket
         * @param keyhash  the hash value of the key
         * @param index    the index where the key is located in the map's underlying list of keys
         *
         * @return the new bucket structure
         */
        protected Bucket removeKeyFrom(Bucket bucket, Int keyhash, Int index)
            {
            assert bucket != null;

            if (bucket.is(HashTree))
                {
                // binary search the hash tree
                HashTree tree = bucket;
                Int lo = 0;
                Int hi = tree.size - 1;
                while (lo <= hi)
                    {
                    Int mid = (lo + hi) >>> 1;
                    OneOrN indexes = tree[mid];
                    switch (hashFor(indexes) <=> keyhash)
                        {
                        case Equal:
                            OneOrN? remainder = removeIndexFrom(indexes, index);
                            if (remainder == null)
                                {
                                return removeNodeFrom(tree, mid);
                                }
                            else
                                {
                                tree[mid] = remainder;
                                return tree;
                                }

                        case Lesser:
                            lo = mid + 1;
                            break;
                        case Greater:
                            hi = mid - 1;
                            break;
                        }
                    }
                assert;
                }

            return removeIndexFrom(bucket, index);
            }

        /**
         * Given an `Int` key index or an `Int[]` of key indexes, remove the specified key index
         * from that structure.
         *
         * @param indexes  either an `Int` key index or an `Int[]` of key indexes containing the
         *                 specified `index`
         * @param index    the index where the key is located in the map's underlying list of keys
         *
         * @return an `Int` or `Int[]` of key indexes that no longer includes the specified index,
         *         or `null` if no key indexes remain
         */
        protected OneOrN? removeIndexFrom(OneOrN indexes, Int index)
            {
            if (indexes.is(Int[]))
                {
                switch (indexes.size)
                    {
                    case 0:
                    case 1:
                        assert;

                    case 2:
                        if (indexes[0] == index)
                            {
                            return indexes[1];
                            }

                        assert indexes[1] == index;
                        return indexes[0];

                    default:
                        return indexes.remove(index);
                    }
                }

            assert indexes == index;
            return null;
            }

        /**
         * Given a hash tree (a binary tree of `OneOrN` values stored in an array), remove the
         * `OneOrN` value at the specified offset in the array that holds the binary tree.
         *
         * @param tree   an array of 'OneOrN' values, stored in ascending order of the hash values
         *               of the corresponding keys
         * @param n      the array index to remove
         *
         * @return the resulting `Bucket` structure, which may be a `HashTree`, a `OneOrN` (an `Int`
         *         or `Int[]` of key indexes), or a null
         */
        protected Bucket removeNodeFrom(HashTree tree, Int n)
            {
            if (tree.size == 1)
                {
                assert n == 0;
                return null;
                }

            if (tree.size == 2)
                {
                return switch (n)
                    {
                    case 0: tree[1];
                    case 1: tree[0];
                    default: assert;
                    }
                }

            return tree.delete(n);
            }

        /**
         * Because the HashIndex is a structure full of indexes into the ListMap's underlying array
         * of keys, when one of those keys is removed from the ListMap, all of the indexes in the
         * HashIndex with a value greater than the index of the removed key will now be off-by-one.
         * Adjust all of those indexes accordingly.
         *
         * @param index  the index of the key that was removed (or is being removed) from the
         *               ListMap
         */
        protected decrementAllAfter(Int index)
            {
            // TODO
            loop: for (Bucket bucket : buckets?)
                {
                if (bucket != null)
                    {
                    if (bucket.is(HashTree))
                        {
                        buckets[loop.count] = decrementAllInHashTreeAfter(bucket, index);
                        }
                    else
                        {
                        }
                    }
                }
            }

        }


    // ----- Keys Set ------------------------------------------------------------------------------

    /**
     * A custom implementation of the [keys] property.
     */
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
                    if (deletes != prevDeletes)
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
                deleteEntryAt(index);
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
                    deleteEntryAt(i-removed);
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
        protected construct(KeyType key)
            {
            this.key    = key;
            this.expect = appends + deletes;
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
        CursorEntry advance(Int index)    // REVIEW GG I would like to have this "protected"
            {
            assert cursor;
            this.key    = listKeys[index];
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
        protected/private Int index;

        /**
         * The expected modification count for the ListMap.
         */
        protected/private Int expect;

        @Override
        @Unassigned
        public/private KeyType key;

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
                    }
                else if (cursor)
                    {
                    // disallow the entry from being re-added (since it lost its cursor position)
                    throw new ReadOnly();
                    }
                else
                    {
                    appendEntry(key, value);
                    index  = listKeys.size - 1;
                    exists = True;
                    ++expect;
                    }
                }
            }

        @Override
        void remove()
            {
            if (verifyNotPersistent() & exists)
                {
                deleteEntryAt(index);
                exists = False;
                ++expect;
                }
            }

        @Override
        Entry reify()
            {
            return new ReifiedEntry(ListMap.this, key);
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
                Int         index       = 0;
                Int         limit       = size;
                Int         prevDeletes = deletes;
                CursorEntry entry       = new CursorEntry();

                @Override
                conditional Map<KeyType, ValueType>.Entry next()
                    {
                    // the immediately previously iterated key is allowed to be deleted
                    if (deletes != prevDeletes)
                        {
                        if (deletes - prevDeletes == 1 && index > 0 && !entry.exists)
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
                        entry.advance(index++);
                        return True, entry;
                        }

                    return False;
                    }
                };
            }

        @Override
        conditional Entries remove(Entry entry)
            {
            verifyMutable();

            if (Int index : indexOf(entry))
                {
                deleteEntryAt(index);
                return True, this;
                }

            return False;
            }

        @Override
        conditional Entries removeIf(function Boolean (Entry) shouldRemove)
            {
            verifyMutable();

            CursorEntry entry   = new CursorEntry();
            Int         removed = 0;
            for (Int i = 0, Int c = size; i < c; ++i)
                {
                if (shouldRemove(entry.advance(i-removed)))
                    {
                    entry.remove();
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

        /**
         * TODO
         */
        protected conditional Int indexOf(Entry entry)
            {
            // first, see if the entry knows its own index
            KeyType key   = entry.key;
            Int     index = -1;
            Boolean found = False;
            if (entry.is(CursorEntry))
                {
                index = entry.index;
                if (index >= 0 && index < size && listKeys[index] == key)
                    {
                    found = True;
                    }
                }

            // otherwise, search for the entry by key
            if (!found)
                {
                if (index : listKeys.indexOf(entry.key))
                    {
                    found = True;
                    }
                }

            // lastly, verify that the values match
            found &&= listVals[index] == entry.value;

            return found, index;
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
        Iterator<ValueType> iterator()
            {
            return new Iterator()
                {
                Int index       = 0;
                Int limit       = size;
                Int prevDeletes = deletes;
                @Unassigned KeyType key;

                @Override
                conditional ValueType next()
                    {
                    // the immediately previously iterated key is allowed to be deleted
                    if (deletes != prevDeletes)
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
                        key = listKeys[index];
                        return True, listVals[index++];
                        }

                    return False;
                    }
                };
            }

        @Override
        conditional Values remove(ValueType value)
            {
            verifyMutable();

            if (Int index : listVals.indexOf(value))
                {
                deleteEntryAt(index);
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
                    deleteEntryAt(i-removed);
                    ++removed;
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
