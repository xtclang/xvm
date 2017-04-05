class HashSet<ElementType>
        implements Set<ElementType>
    {
    construct HashSet(Hasher<ElementType> hasher = new NaturalHasher<>());

    public/private Hasher<ElementType> hasher;

    private class Entry<ElementType>(ElementType value, Entry? next);

    private Array<Entry<ElementType>?> buckets; // TODO

    public/private Int size;

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
