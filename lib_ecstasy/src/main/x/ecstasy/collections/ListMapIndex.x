/**
 * The ListMapIndex is a hashing data structure that exists solely to optimizes the
 * [ListMap.indexOf] method. Its design is predicated on the general assumptions of the ListMap:
 *
 * * The data structure is either in an initial append-intensive mode in which queries do not
 *   occur, or in post-append query-intensive mode, in which mutations are rare;
 * * Deletions (_particularly_ deletions of any entry other than the most recent entry added)
 *   are _extremely_ rare, and are allowed to be expensive.
 */
mixin ListMapIndex<Key extends Hashable, Value>
        into ListMap<Key, Value>
    {
    /**
     * For keys with the same hash value, the indexes of those keys in the map are stored
     * together. If there is only one key with the hash value, then its index is stored as a
     * simple `Int`. If there are multiple keys with the same hash value, then their indexes
     * are stored as an `Int[]`.
     */
    protected typedef (Int | Int[]) as OneOrN;

    /**
     * When multiple hash values modulo to the same bucket id, the `OneOrN` values for each
     * hash value are stored as a binary tree of `OneOrN` values in an array.
     */
    protected typedef OneOrN[] as HashTree;

    /**
     * A bucket is either empty (`Null`), contains indexes of one or more keys (`OneOrN`) for a
     * single hash value, or contains one or more keys for multiple different hash values (a
     * `HashTree`).
     */
    protected typedef (HashTree | OneOrN)? as Bucket;

    /**
     * ListMaps smaller than this number of entries are not indexed; the assumption is that it is
     * faster just to linearly scan the map up to a certain size.
     */
    protected static Int MINSIZE = 10;

    /**
     * The sole information managed by the HashIndex is an array of buckets.
     */
    private Bucket[]? buckets;

    @Override
    protected conditional Int indexOf(Key key)
        {
        Bucket[]? buckets = this.buckets;
        if (buckets == Null)
            {
            if (size > MINSIZE && inPlace)
                {
                buildIndex();
                buckets = this.buckets;
                assert buckets != Null;
                }
            else
                {
                return super(key);
                }
            }

        Int    keyhash = Key.hashCode(key);
        Bucket bucket  = buckets[keyhash % buckets.size];
        if (bucket == Null)
            {
            return False;
            }

        private conditional Int indexOf(Key key, OneOrN indexes)
            {
            if (indexes.is(Int[]))
                {
                for (Int index : indexes)
                    {
                    if (keyArray[index] == key)
                        {
                        return True, index;
                        }
                    }
                }
            else
                {
                Int index = indexes;
                if (keyArray[index] == key)
                    {
                    return True, index;
                    }
                }
            return False;
            }

        if (bucket.is(OneOrN))
            {
            return indexOf(key, bucket);
            }

        // binary search the hash tree
        HashTree tree = bucket;
        Int      lo   = 0;
        Int      hi   = tree.size - 1;
        while (lo <= hi)
            {
            Int mid = (lo + hi) >>> 1;
            OneOrN indexes = tree[mid];
            switch (hashFor(indexes) <=> keyhash)
                {
                case Equal:
                    return indexOf(key, indexes);
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

    @Override
    protected void deleteEntryAt(Int index)
        {
        Bucket[]? buckets = this.buckets;
        if (buckets != Null)
            {
            if (size < MINSIZE)
                {
                this.buckets = Null;
                }
            else
                {
                // update the index
                Int keyhash  = Key.hashCode(keyArray[index]);
                Int bucketid = keyhash % buckets.size;
                buckets[bucketid] = removeKeyFrom(buckets[bucketid], keyhash, index);

                if (index < size-1)
                    {
                    // all of the index information for keys located after the deleted index
                    // will now be incorrect; all such indexes must be decremented
                    decrementIndexesAbove(index);
                    }
                }
            }

        super(index);
        }

    @Override
    protected void appendEntry(Key key, Value value)
        {
        super(key, value);

        Bucket[]? buckets = this.buckets;
        if (buckets != Null)
            {
            // update the index
            Int keyhash  = Key.hashCode(key);
            Int bucketid = keyhash % buckets.size;
            buckets[bucketid] = addKeyTo(buckets[bucketid], keyhash, keyArray.size-1);

            if (size > buckets.size)
                {
                // more keys than buckets; re-hash
                buildIndex();
                }
            }
        }

    @Override
    ListMapIndex clear()
        {
        if (inPlace)
            {
            buckets = Null;
            }
        return super();
        }

    @Override
    immutable ListMapIndex makeImmutable()
        {
        if (buckets == Null && size > MINSIZE && inPlace)
            {
            buildIndex();
            }

        return super();
        }

    /**
     * Create all of the hashing structures that make up the hash index.
     */
    protected void buildIndex()
        {
        Int bucketCount = HashMap.calcBucketCount(size);
        Bucket[] buckets = new Bucket[bucketCount];
        loop: for (Key key : keyArray)
            {
            Int keyhash  = Key.hashCode(key);
            Int bucketid = keyhash % bucketCount;
            buckets[bucketid] = addKeyTo(buckets[bucketid], keyhash, loop.count);
            }
        this.buckets = buckets;
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
        return Key.hashCode(keyArray[index]);
        }

    /**
     * Given a single bucket (an `Int` key index, an `Int[]` of key indexes, or a binary hash
     * tree of those, or a Null if the bucket is empty), add the specified key index to that
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
        if (bucket == Null)
            {
            return index;
            }

        if (bucket.is(OneOrN))
            {
            OneOrN indexes = bucket;
            return switch (keyhash <=> hashFor(indexes))
                {
                case Lesser : new Array<OneOrN>(2, i -> {return i == 0 ? index : indexes;});
                case Equal  : addIndexTo(indexes, index);
                case Greater: new Array<OneOrN>(2, i -> {return i == 0 ? indexes : index;});
                };
            }

        // binary search the hash tree (which is stored in an array)
        HashTree tree = bucket;
        Int      lo   = 0;
        Int      hi   = tree.size - 1;
        while (lo <= hi)
            {
            Int    mid     = (lo + hi) >>> 1;
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
        return tree.toArray(Mutable).insert(lo, index);
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
        assert bucket != Null;

        if (bucket.is(OneOrN))
            {
            return removeIndexFrom(bucket, index);
            }

        // binary search the hash tree
        HashTree tree = bucket;
        Int      lo   = 0;
        Int      hi   = tree.size - 1;
        while (lo <= hi)
            {
            Int    mid     = (lo + hi) >>> 1;
            OneOrN indexes = tree[mid];
            switch (hashFor(indexes) <=> keyhash)
                {
                case Equal:
                    OneOrN? remainder = removeIndexFrom(indexes, index);
                    if (remainder == Null)
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

    /**
     * Given an `Int` key index or an `Int[]` of key indexes, remove the specified key index
     * from that structure.
     *
     * @param indexes  either an `Int` key index or an `Int[]` of key indexes containing the
     *                 specified `index`
     * @param index    the index where the key is located in the map's underlying list of keys
     *
     * @return an `Int` or `Int[]` of key indexes that no longer includes the specified index,
     *         or `Null` if no key indexes remain
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
                    return indexes[0] == index
                            ? indexes[1]
                            : indexes[0];

                default:
                    return indexes.remove(index);
                }
            }

        assert indexes == index;
        return Null;
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
     *         or `Int[]` of key indexes), or a Null
     */
    protected Bucket removeNodeFrom(HashTree tree, Int n)
        {
        if (tree.size == 1)
            {
            assert n == 0;
            return Null;
            }

        if (tree.size == 2)
            {
            return switch (n)
                {
                case 0: tree[1];
                case 1: tree[0];
                default: assert;
                };
            }

        return tree.delete(n);
        }

    /**
     * Because the HashIndex is a structure full of indexes into the ListMap's underlying array
     * of keys, when one of those keys is removed from the ListMap, all of the indexes in the
     * HashIndex with a value greater than the index of the removed key will now be off-by-one.
     * Adjust all of those indexes accordingly.
     *
     * @param deleted  the index of the key that was removed (or is being removed) from the
     *                 ListMap
     */
    protected void decrementIndexesAbove(Int deleted)
        {
        Bucket[]? buckets = this.buckets;

        loop: for (Bucket bucket : buckets?)
            {
            if (bucket != Null)
                {
                if (bucket.is(OneOrN))
                    {
                    buckets[loop.count] = decrementOneOrNAbove(bucket, deleted);
                    }
                else
                    {
                    buckets[loop.count] = decrementAllInHashTreeAbove(bucket, deleted);
                    }
                }
            }
        }
    protected HashTree decrementAllInHashTreeAbove(HashTree tree, Int deleted)
        {
        loop: for (OneOrN indexes : tree)
            {
            tree[loop.count] = decrementOneOrNAbove(indexes, deleted);
            }
        return tree;
        }
    protected OneOrN decrementOneOrNAbove(OneOrN indexes, Int deleted)
        {
        if (indexes.is(Int[]))
            {
            loop: for (Int index : indexes)
                {
                if (index > deleted)
                    {
                    indexes = indexes.toArray(Mutable);
                    indexes[loop.count] = index - 1;
                    }
                }
            return indexes;
            }
        return indexes - 1;
        }
    }