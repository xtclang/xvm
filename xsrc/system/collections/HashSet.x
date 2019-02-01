class HashSet<ElementType>
        implements Set<ElementType>
    {
    construct()
        {
        assert(ElementType.is(Type<Hashable>));

        this.hasher = new NaturalHasher<ElementType>();
        }

    construct(Collection<ElementType> elements)
        {
        construct HashSet();
        }
    finally
        {
        addAll(elements);
        }

    construct(Hasher<ElementType> hasher)
        {
        this.hasher = hasher;
        }

    construct(Hasher<ElementType> hasher, Collection<ElementType> elements)
        {
        construct HashSet(hasher);
        }
    finally
        {
        addAll(elements);
        }

    public/private Hasher<ElementType> hasher;

    private class Entry(ElementType value, Entry? next);

    private Entry?[] buckets;

    @Override
    public/private Int size;

    @Override
    Boolean contains(ElementType value)
        {
        Int nHash   = hasher.hashOf(value);
        Int nBucket = nHash % buckets.size;

        Entry? entry = buckets[nBucket];
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

    @Override
    Iterator<ElementType> iterator()
        {
        TODO
        }

    @Override
    Stream<ElementType> stream()
        {
        TODO
        }

    @Override
    HashSet clone()
        {
        return new HashSet(this);
        }
    }
