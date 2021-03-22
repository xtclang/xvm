/**
 * An implementation of the Collection for the [Map.entries] property that delegates back to the map
 * and to the map's [Map.keys] set.
 */
class KeyEntries<MapKey, MapValue>(Map<MapKey, MapValue> contents)
        implements Collection<Map<MapKey, MapValue>.Entry>
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
        return contents.keys.knownSize();
        }

    typedef Map<MapKey, MapValue>.Entry MapEntry;

    @Override
    Iterator<MapEntry> iterator()
        {
        return new EntryIterator(contents.keys.iterator());
        }

    /**
     * Iterator that relies on an iterator of keys to produce a corresponding sequence of entries.
     * TODO GG if this class is inside the iterator() method, compiler emits error about type param
     */
    protected class EntryIterator(Iterator<MapKey> keyIterator)
            implements Iterator<MapEntry>
        {
        @Override
        conditional MapEntry next()
            {
            if (MapKey key := keyIterator.next())
                {
                private CursorEntry<MapKey, MapValue> entry = new CursorEntry(this.KeyEntries.contents);
                return True, entry.advance(key);
                }

            return False;
            }

        @Override
        Boolean knownDistinct()
            {
            return True;
            }

        @Override
        conditional Int knownSize()
            {
            return keyIterator.knownSize();
            }

        @Override
        (Iterator<MapEntry>, Iterator<MapEntry>) duplicate()
            {
            (Iterator<MapKey> iter1, Iterator<MapKey> iter2) = keyIterator.duplicate();
            return new EntryIterator(iter1), new EntryIterator(iter2);
            }
        }

    @Override
    KeyEntries remove(Map<MapKey, MapValue>.Entry entry)
        {
        verifyInPlace();
        contents.remove(entry.key, entry.value);
        return this;
        }

    @Override
    (KeyEntries, Int) removeAll(
            function Boolean (Map<MapKey, MapValue>.Entry) shouldRemove)
        {
        verifyInPlace();

        CursorEntry<MapKey, MapValue> entry = new CursorEntry(contents);
        (_, Int removed) = contents.keys.removeAll(key -> shouldRemove(entry.advance(key)));

        return this, removed;
        }

    @Override
    KeyEntries clear()
        {
        verifyInPlace();
        contents.clear();
        return this;
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
