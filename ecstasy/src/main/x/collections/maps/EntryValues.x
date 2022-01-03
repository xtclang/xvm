/**
 * An implementation of the Collection for the [Map.values] property that delegates back
 * to the map and to the map's [Map.entries].
 */
class EntryValues<MapKey, MapValue>(Map<MapKey, MapValue> contents)
        implements Collection<MapValue>
        implements Freezable
    {
    public/private Map<MapKey, MapValue> contents;

    @Override
    Int size.get()
        {
        return contents.size;
        }

    @Override
    Boolean empty.get()
        {
        return contents.empty;
        }

    @Override
    conditional Int knownSize()
        {
        return contents.entries.knownSize();
        }

    @Override
    Iterator<MapValue> iterator()
        {
        return new ValueIterator(contents.entries.iterator());
        }

    protected typedef Iterator<Map<MapKey, MapValue>.Entry> as EntryIterator;

    /**
     * Iterator that relies on an iterator of entries to produce a corresponding sequence of values.
     * TODO GG if this class is inside the iterator() method, compiler emits error about type param
     */
    protected class ValueIterator(EntryIterator entryIterator)
            implements Iterator<MapValue>
        {
        @Override
        conditional MapValue next()
            {
            if (Map<MapKey, MapValue>.Entry entry := entryIterator.next())
                {
                return True, entry.value;
                }

            return False;
            }

        @Override
        conditional Int knownSize()
            {
            return entryIterator.knownSize();
            }

        @Override
        (Iterator<MapValue>, Iterator<MapValue>) bifurcate()
            {
            (EntryIterator iter1, EntryIterator iter2) = entryIterator.bifurcate();
            return new ValueIterator(iter1), new ValueIterator(iter2);
            }
        }

    @Override
    EntryValues remove(MapValue value)
        {
        verifyInPlace();

        contents.entries.iterator().untilAny(entry ->
            {
            if (entry.value == value)
                {
                contents.entries.remove(entry);
                return True;
                }
            return False;
            });

        return this;
        }

    @Override
    (EntryValues, Int) removeAll(function Boolean (MapValue) shouldRemove)
        {
        verifyInPlace();

        (_, Int removed) = contents.entries.removeAll(entry -> shouldRemove(entry.value));

        return this, removed;
        }

    @Override
    EntryValues clear()
        {
        verifyInPlace();
        contents.clear();
        return this;
        }

    @Override
    immutable EntryValues freeze(Boolean inPlace = False)
        {
        assert contents.is(immutable Map);
        return makeImmutable();
        }

    /**
     * Some operations require that the containing Map be mutable; this method throws an exception
     * if the Map is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not mutable
     */
    protected Boolean verifyInPlace()
        {
        if (!contents.inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace == True");
            }
        return True;
        }
    }
