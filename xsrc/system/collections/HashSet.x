class HashSet<Element>
        implements Set<Element>
    {
    construct()
        {
        assert(Element.is(Type<Hashable>));

        this.hasher = new NaturalHasher<Element>();
        }

    construct(Collection<Element> elements)
        {
        construct HashSet();
        }
    finally
        {
        addAll(elements);
        }

    construct(Hasher<Element> hasher)
        {
        this.hasher = hasher;
        }

    construct(Hasher<Element> hasher, Collection<Element> elements)
        {
        construct HashSet(hasher);
        }
    finally
        {
        addAll(elements);
        }

    public/private Hasher<Element> hasher;

    private class Entry(Element value, Entry? next);

    private Entry?[] buckets;

    @Override
    public/private Int size;

    @Override
    Boolean contains(Element value)
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
    Iterator<Element> iterator()
        {
        TODO
        }

    @Override
    Stream<Element> stream()
        {
        TODO
        }

    @Override
    HashSet clone()
        {
        return new HashSet(this);
        }
    }
