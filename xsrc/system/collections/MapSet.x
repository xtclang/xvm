/**
 * A Set is a container data structure that represents a group of _distinct values_. While the Set's
 * interface is identical to that of the Collection, its default behavior is subtly different.
 */
class MapSet<Element>
        implements Set<Element>
    {
    construct(Map<Element, Nullable> map)
        {
        this.map = map;
        }

    /**
     * Instantiate a new MapSet for the given Map in order to support persistent data structure
     * functionality and other features that require a new MapSet to be created.
     *
     * Variably mutable implementations must implement this method.
     *
     * @param map  a new Map that requires a new MapSet
     *
     * @return a new MapSet for the specified Map
     */
    protected MapSet setFor(Map<Element, Nullable> map)
        {
        TODO using reflection?
        }

    /**
     * The underlying Map.
     */
    protected/private Map<Element, Nullable> map;

    @Override
    Mutability mutability.get()
        {
        return map.mutability;
        }

    @Override
    conditional Orderer sortedBy()
        {
        return map.keys.sortedBy();
        }

    @Override
    Boolean empty.get()
        {
        return map.empty;
        }

    @Override
    Int size.get()
        {
        return map.size;
        }

    @Override
    Iterator<Element> iterator()
        {
        return map.keys.iterator();
        }

    @Override
    Boolean contains(Element value)
        {
        return map.contains(value);
        }

    @Override
    Boolean containsAll(Iterable<Element> values)
        {
        return map.keys.containsAll(values);
        }

    @Override
    Element[] toArray(VariablyMutable.Mutability mutability = Persistent)
        {
        return map.keys.toArray(mutability);
        }


    // ----- mutating operations -------------------------------------------------------------------

    @Override
    @Op("+")
    MapSet add(Element value)
        {
        val oldMap = map;
        var newMap = oldMap;
        newMap := newMap.putIfAbsent(value, Null);
        return oldMap == newMap ? this : setFor(newMap);
        }

    @Override
    @Op("|")
    MapSet addAll(Iterable<Element> values)
        {
        val oldMap = map;
        var newMap = oldMap;
        for (Element value : values)
            {
            newMap := newMap.putIfAbsent(value, Null);
            }
        return oldMap == newMap ? this : setFor(newMap);
        }

    @Override
    conditional MapSet addIfAbsent(Element value)
        {
        val oldMap = map;
        if (val newMap := oldMap.putIfAbsent(value, Null))
            {
            return True, oldMap == newMap ? this : setFor(newMap);
            }
        else
            {
            return False;
            }
        }

    @Override
    @Op("-")
    MapSet remove(Element value)
        {
        val oldMap = map;
        val newMap = oldMap.remove(value);
        return oldMap == newMap ? this : setFor(newMap);
        }

    @Override
    @Op("-")
    MapSet removeAll(Iterable<Element> values)
        {
        val oldMap = map;
        var newMap = oldMap;
        for (Element value : values)
            {
            newMap = newMap.remove(value);
            }
        return oldMap == newMap ? this : setFor(newMap);
        }

    @Override
    conditional MapSet removeIfPresent(Element value)
        {
        val oldMap = map;
        if (val newMap := oldMap.remove(value, Null))
            {
            return True, oldMap == newMap ? this : setFor(newMap);
            }
        else
            {
            return False;
            }
        }

    @Override
    (MapSet, Int) removeIf(function Boolean (Element) shouldRemove)
        {
        val oldMap = map;
        var newMap = oldMap;
        var count  = 0;
        for (Element value : iterator())
            {
            if (shouldRemove(value))
                {
                newMap = newMap.remove(value);
                ++count;
                }
            }
        return oldMap == newMap ? this : setFor(newMap), count;
        }

    @Override
    @Op("&")
    MapSet retainAll(Iterable<Element> values)
        {
        if (empty)
            {
            return this;
            }

        switch (values.size)
            {
            case 0:
                return clear();

            case 1:
                assert Element value := values.iterator().next();
                return contains(value) ? this : clear();
            }

        // TODO so many possible optimizations for this method, including "ordered", creating set, etc.
        val oldMap = map;
        var newMap = oldMap;
        for (Element value : iterator())
            {
            if (!values.contains(value))
                {
                newMap = newMap.remove(value);
                }
            }
        return oldMap == newMap ? this : setFor(newMap);
        }

    @Override
    MapSet clear()
        {
        val oldMap = map;
        val newMap = oldMap.clear();
        return oldMap == newMap ? this : setFor(newMap);
        }

    @Override
    @Op("^")
    MapSet symmetricDifference(Set!<Element> values)
        {
        TODO
        }
    }
