class HashSet<ElementType>
        implements Set<ElementType>
    {
    construct()
        {
        assert(ElementType instanceof Hashable);

        Hasher<ElementType> hasher = new NaturalHasher<Hashable+ElementType>();
        }

    public/private Hasher<ElementType> hasher;

    private class Entry<ElementType>(ElementType value, Entry? next);

    private Array<Entry<ElementType>?> buckets; // TODO

    @Override
    public/private Int size;

    @Override
    Boolean contains(ElementType value)
        {
        Int nHash   = hasher.hashOf(value);
        Int nBucket = nHash % buckets.length;

        Entry<ElementType>? entry = buckets[nBucket];
        while (entry != null)
            {
            if (hasher.areEqual(value, entry.value))
                {
                return true;
                }
            entry = entry.next;
            }

        return false;
        }

    // ...
    }
